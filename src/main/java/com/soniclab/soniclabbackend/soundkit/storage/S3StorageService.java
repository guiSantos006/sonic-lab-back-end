package com.soniclab.soniclabbackend.soundkit.storage;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Map;

/**
 * Implementação de armazenamento no AWS S3 com suporte a arquivos públicos e privados via Presigned URLs.
 */
@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "s3")
public class S3StorageService implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(S3StorageService.class);

    private final S3Client s3Client;
    private final S3Presigner presigner;
    private final String bucket;
    private final String region;
    private final String publicBaseUrl;
    private final Duration defaultTtl;

    @Autowired
    public S3StorageService(
            S3Client s3Client,
            S3Presigner presigner,
            @Value("${storage.s3.bucket}") String bucket,
            @Value("${storage.s3.region:us-east-2}") String region,
            @Value("${storage.s3.public-base-url:}") String customBaseUrl,
            @Value("${storage.s3.url-duration-minutes:15}") long defaultUrlDurationMinutes
    ) {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("O nome do bucket S3 (storage.s3.bucket) é obrigatório.");
        }
        this.s3Client = s3Client;
        this.presigner = presigner;
        this.bucket = bucket.trim();
        this.region = (region != null && !region.isBlank()) ? region.trim() : "us-east-2";
        this.defaultTtl = Duration.ofMinutes(defaultUrlDurationMinutes > 0 ? defaultUrlDurationMinutes : 15);

        String url = (customBaseUrl != null && !customBaseUrl.isBlank())
                ? customBaseUrl.trim()
                : "https://" + this.bucket + ".s3." + this.region + ".amazonaws.com/";
        this.publicBaseUrl = url.endsWith("/") ? url : url + "/";
    }

    /**
     * Construtor auxiliar para testes unitários ou instanciação manual.
     */
    public S3StorageService(
            S3Client s3Client,
            S3Presigner presigner,
            String bucket,
            String region,
            String customBaseUrl,
            Duration defaultTtl
    ) {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("O nome do bucket S3 é obrigatório.");
        }
        this.s3Client = s3Client;
        this.presigner = presigner;
        this.bucket = bucket.trim();
        this.region = (region != null && !region.isBlank()) ? region.trim() : "us-east-2";
        this.defaultTtl = defaultTtl != null ? defaultTtl : Duration.ofMinutes(15);

        String url = (customBaseUrl != null && !customBaseUrl.isBlank())
                ? customBaseUrl.trim()
                : "https://" + this.bucket + ".s3." + this.region + ".amazonaws.com/";
        this.publicBaseUrl = url.endsWith("/") ? url : url + "/";
    }

    @Override
    public String upload(String path, InputStream in, long contentLength, String contentType, FileVisibility visibility) {
        if (in == null) {
            throw new IllegalArgumentException("O fluxo de dados (InputStream) não pode ser nulo.");
        }
        if (contentLength < 0) {
            throw new IllegalArgumentException("O tamanho do conteúdo (contentLength) deve ser maior ou igual a zero.");
        }

        String key = normalizeKey(path);
        FileVisibility effectiveVisibility = (visibility != null) ? visibility : inferVisibility(key);

        try (in) {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType != null && !contentType.isBlank() ? contentType : "application/octet-stream")
                    .metadata(Map.of("visibility", effectiveVisibility.name().toLowerCase()))
                    .build();

            s3Client.putObject(request, RequestBody.fromInputStream(in, contentLength));
            log.info("Upload S3 concluído (bucket={}, key={}, visibility={})", bucket, key, effectiveVisibility);
            return key;
        } catch (S3Exception e) {
            String awsMsg = e.awsErrorDetails() != null ? e.awsErrorDetails().errorMessage() : e.getMessage();
            log.error("Falha S3 no upload (key={}, status={}, error={})", key, e.statusCode(), awsMsg);
            throw new RuntimeException("Falha ao enviar arquivo para o S3: " + key + " (status: " + e.statusCode() + ", awsError: " + awsMsg + ")", e);
        } catch (SdkException e) {
            log.error("Falha de comunicação AWS SDK no upload (key={}): {}", key, e.getMessage());
            throw new RuntimeException("Falha de comunicação com o S3 ao enviar arquivo: " + key, e);
        } catch (IOException e) {
            log.error("Falha de E/S ao ler stream para upload S3 (key={}): {}", key, e.getMessage());
            throw new RuntimeException("Falha de E/S ao ler arquivo para upload: " + key, e);
        }
    }

    @Override
    public String upload(String path, InputStream in, long contentLength, String contentType) {
        return upload(path, in, contentLength, contentType, inferVisibility(path));
    }

    @Override
    public String getPublicUrl(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("O caminho do arquivo não pode ser vazio.");
        }
        String normalizedKey = normalizeKey(key);
        if (!isPublic(normalizedKey)) {
            throw new IllegalArgumentException("Acesso negado: o arquivo '" + normalizedKey + "' é privado e não possui URL pública permanente. Utilize generateDownloadUrl().");
        }
        return publicBaseUrl + normalizedKey;
    }

    @Override
    public String generateDownloadUrl(String key) {
        return generateDownloadUrl(key, defaultTtl);
    }

    @Override
    public String generateDownloadUrl(String key, Duration ttl) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("O caminho do arquivo não pode ser vazio.");
        }
        Duration effectiveTtl = (ttl != null) ? ttl : defaultTtl;
        if (effectiveTtl.isNegative() || effectiveTtl.isZero()) {
            throw new IllegalArgumentException("O tempo de vida (TTL) da URL pré-assinada deve ser positivo.");
        }
        if (effectiveTtl.toDays() > 7) {
            throw new IllegalArgumentException("O tempo de vida (TTL) máximo permitido para URLs pré-assinadas é de 7 dias.");
        }

        String normalizedKey = normalizeKey(key);
        try {
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(effectiveTtl)
                    .getObjectRequest(GetObjectRequest.builder()
                            .bucket(bucket)
                            .key(normalizedKey)
                            .build())
                    .build();

            PresignedGetObjectRequest presigned = presigner.presignGetObject(presignRequest);
            return presigned.url().toExternalForm();
        } catch (S3Exception e) {
            log.error("Falha S3 ao gerar presigned URL (key={}, status={})", normalizedKey, e.statusCode());
            throw new RuntimeException("Falha ao gerar URL pré-assinada no S3 para a chave: " + normalizedKey + " (status: " + e.statusCode() + ")", e);
        } catch (SdkException e) {
            log.error("Falha AWS SDK ao gerar presigned URL (key={}): {}", normalizedKey, e.getMessage());
            throw new RuntimeException("Falha ao gerar URL pré-assinada para a chave: " + normalizedKey, e);
        }
    }

    @Override
    public void delete(String keyOrUrl) {
        String key = extractKey(keyOrUrl);
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
        } catch (S3Exception e) {
            log.error("Falha S3 ao deletar objeto (key={}, status={})", key, e.statusCode());
            throw new RuntimeException("Falha ao deletar arquivo no S3: " + key + " (status: " + e.statusCode() + ")", e);
        } catch (SdkException e) {
            log.error("Falha AWS SDK ao deletar objeto (key={}): {}", key, e.getMessage());
            throw new RuntimeException("Falha de comunicação com o S3 ao deletar arquivo: " + key, e);
        }
    }

    @Override
    public boolean exists(String keyOrUrl) {
        if (keyOrUrl == null || keyOrUrl.isBlank()) {
            return false;
        }
        try {
            String key = extractKey(keyOrUrl);
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            log.error("Falha S3 ao verificar existência do objeto (status={})", e.statusCode());
            throw new RuntimeException("Falha ao verificar existência do arquivo no S3: " + keyOrUrl, e);
        } catch (IllegalArgumentException e) {
            return false;
        } catch (SdkException e) {
            log.error("Falha AWS SDK ao verificar existência do objeto: {}", e.getMessage());
            throw new RuntimeException("Falha de comunicação com o S3 ao verificar existência: " + keyOrUrl, e);
        }
    }

    @Override
    public boolean isPublic(String keyOrUrl) {
        if (keyOrUrl == null || keyOrUrl.isBlank()) {
            return false;
        }
        try {
            String key = extractKey(keyOrUrl);
            return inferVisibility(key) == FileVisibility.PUBLIC;
        } catch (Exception e) {
            return false;
        }
    }

    public String extractKey(String keyOrUrl) {
        if (keyOrUrl == null || keyOrUrl.isBlank()) {
            throw new IllegalArgumentException("O caminho ou URL do arquivo não pode ser vazio.");
        }
        String trimmed = keyOrUrl.trim();

        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            URI uri;
            try {
                uri = URI.create(trimmed);
            } catch (Exception e) {
                throw new IllegalArgumentException("URL inválida para extração de chave: " + trimmed, e);
            }

            String host = uri.getHost();
            String path = uri.getPath(); // descarta query parameters e fragmentos automaticamente
            if (path == null || path.isBlank()) {
                throw new IllegalArgumentException("A URL informada não contém um caminho de objeto válido: " + trimmed);
            }

            // Validação de segurança de domínio/bucket para evitar manipulação cross-bucket
            if (host != null && !host.isBlank()) {
                boolean matchesPublicBase = (publicBaseUrl != null && !publicBaseUrl.isBlank() && trimmed.startsWith(publicBaseUrl));
                boolean matchesVirtualHost = host.equalsIgnoreCase(bucket + ".s3." + region + ".amazonaws.com")
                        || host.equalsIgnoreCase(bucket + ".s3.amazonaws.com")
                        || host.startsWith(bucket + ".");
                boolean isPathStyleS3 = host.startsWith("s3.") || host.equalsIgnoreCase("s3.amazonaws.com");
                boolean isLocalEndpoint = host.equalsIgnoreCase("localhost") || host.equalsIgnoreCase("127.0.0.1");

                if (isPathStyleS3 || (isLocalEndpoint && path.startsWith("/" + bucket + "/"))) {
                    if (!path.startsWith("/" + bucket + "/")) {
                        throw new IllegalArgumentException("A URL fornecida não pertence ao bucket configurado: " + bucket);
                    }
                } else if (!matchesPublicBase && !matchesVirtualHost && !isLocalEndpoint) {
                    throw new IllegalArgumentException("A URL fornecida não pertence ao bucket ou domínio configurado: " + trimmed);
                }
            }

            while (path.startsWith("/")) {
                path = path.substring(1);
            }
            if (path.startsWith(bucket + "/")) {
                path = path.substring(bucket.length() + 1);
            }

            return normalizeKey(path);
        }

        int queryIndex = trimmed.indexOf('?');
        String keyWithoutQuery = queryIndex >= 0 ? trimmed.substring(0, queryIndex) : trimmed;
        return normalizeKey(keyWithoutQuery);
    }

    public String normalizeKey(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("O caminho do arquivo não pode ser vazio.");
        }
        if (path.contains("\0")) {
            throw new IllegalArgumentException("Caminho contém caracteres nulos inválidos: " + path);
        }
        String key = path.replace("\\", "/");
        while (key.startsWith("/")) key = key.substring(1);
        key = key.replaceAll("/+", "/");

        for (String segment : key.split("/")) {
            if (segment.equals("..") || segment.equals(".")) {
                throw new IllegalArgumentException("Caminho inválido para o storage: " + path);
            }
        }
        if (key.endsWith("/")) {
            throw new IllegalArgumentException("O caminho do arquivo não pode ser um diretório: " + path);
        }
        return key;
    }

    public String getBucket() {
        return bucket;
    }

    public String getRegion() {
        return region;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public Duration getDefaultTtl() {
        return defaultTtl;
    }

    @PreDestroy
    void close() {
        try {
            if (presigner != null) {
                presigner.close();
            }
        } catch (Exception e) {
            log.warn("Erro ao fechar S3Presigner", e);
        }
        try {
            if (s3Client != null) {
                s3Client.close();
            }
        } catch (Exception e) {
            log.warn("Erro ao fechar S3Client", e);
        }
    }
}
