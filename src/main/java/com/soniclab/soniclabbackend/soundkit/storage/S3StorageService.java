package com.soniclab.soniclabbackend.soundkit.storage;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.InputStream;

@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "s3")
public class S3StorageService implements FileStorageService {

    private final S3Client s3Client;
    private final String bucket;
    private final String baseUrl;

    public S3StorageService(
            @Value("${storage.s3.bucket}") String bucket,
            @Value("${storage.s3.region}") String region,
            // Opcional: URL do CloudFront ou domínio próprio. Se vazio, usa a URL padrão do bucket.
            @Value("${storage.s3.base-url:}") String customBaseUrl,
            // Opcionais: se vazios, usa a cadeia padrão (variáveis de ambiente, ~/.aws, IAM Role...)
            @Value("${storage.s3.access-key:}") String accessKey,
            @Value("${storage.s3.secret-key:}") String secretKey
    ) {
        this.bucket = bucket;

        var builder = S3Client.builder().region(Region.of(region));
        if (!accessKey.isBlank() && !secretKey.isBlank()) {
            builder.credentialsProvider(
                    StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)));
        }
        this.s3Client = builder.build();

        String url = customBaseUrl.isBlank()
                ? "https://" + bucket + ".s3." + region + ".amazonaws.com/"
                : customBaseUrl;
        this.baseUrl = url.endsWith("/") ? url : url + "/";
    }

    @Override
    public  String upload(String path, InputStream inputStream, long contentLength, String contentType) {
        String key = normalizeKey(path);

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .build();

            s3Client.putObject(request, RequestBody.fromInputStream(inputStream, contentLength));

            return baseUrl + key;
        } catch (S3Exception e) {
            throw new RuntimeException("Falha ao enviar arquivo para o S3: " + key, e);
        }
    }

    @Override
    public void delete(String fileUrl) {
        String key = normalizeKey(
                fileUrl.startsWith(baseUrl) ? fileUrl.substring(baseUrl.length()) : fileUrl);

        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
        } catch (S3Exception e) {
            throw new RuntimeException("Falha ao deletar arquivo no S3: " + key, e);
        }
    }

    /**
     * No S3 não existe directory traversal, mas ".." em uma chave é confuso e pode causar
     * problemas em CDNs/proxies. Normaliza separadores e rejeita chaves suspeitas.
     */
    private String normalizeKey(String path) {
        String key = path.replace("\\", "/");
        while (key.startsWith("/")) {
            key = key.substring(1);
        }
        if (key.isEmpty() || key.contains("../") || key.equals("..")) {
            throw new IllegalArgumentException("Caminho inválido para o storage: " + path);
        }
        return key;
    }

    @PreDestroy
    void close() {
        s3Client.close();
    }

}