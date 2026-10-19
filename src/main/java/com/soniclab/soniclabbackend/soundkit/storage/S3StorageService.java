
package com.soniclab.soniclabbackend.soundkit.storage;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;

@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "s3")
public class S3StorageService implements FileStorageService {

    private final S3Client s3Client;
    private final S3Presigner presigner;
    private final String bucket;
    private final Duration urlDuration;

    public S3StorageService(
            @Value("${storage.s3.bucket}") String bucket,
            @Value("${storage.s3.region}") String region,
            @Value("${storage.s3.url-duration-minutes:15}")
            long durationMinutes
    ) {
        this.bucket = bucket;
        this.urlDuration = Duration.ofMinutes(durationMinutes);

        Region awsRegion = Region.of(region);
        var credentialsProvider = DefaultCredentialsProvider.create();

        this.s3Client = S3Client.builder()
                .region(awsRegion)
                .credentialsProvider(credentialsProvider)
                .build();

        this.presigner = S3Presigner.builder()
                .region(awsRegion)
                .credentialsProvider(credentialsProvider)
                .build();
    }

    @Override
    public String upload(
            String path,
            InputStream inputStream,
            String contentType
    ) {
        String key = normalizeKey(path);

        try (inputStream) {
            // Versão simples para o hackathon: carrega o arquivo em memória.
            byte[] content = inputStream.readAllBytes();

            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(
                            contentType != null
                                    ? contentType
                                    : "application/octet-stream"
                    )
                    .build();

            s3Client.putObject(request, RequestBody.fromBytes(content));

            return generatePresignedUrl(key);

        } catch (IOException e) {
            throw new RuntimeException(
                    "Falha ao ler arquivo para upload no S3: " + key, e
            );
        } catch (S3Exception e) {
            throw new RuntimeException(
                    "Falha ao enviar arquivo para o S3: " + key, e
            );
        }
    }

    private String generatePresignedUrl(String key) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        GetObjectPresignRequest presignRequest =
                GetObjectPresignRequest.builder()
                        .signatureDuration(urlDuration)
                        .getObjectRequest(getObjectRequest)
                        .build();

        return presigner.presignGetObject(presignRequest)
                .url()
                .toExternalForm();
    }

    @Override
    public void delete(String fileUrl) {
        String key = extractKey(fileUrl);

        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());

        } catch (S3Exception e) {
            throw new RuntimeException(
                    "Falha ao deletar arquivo no S3: " + key, e
            );
        }
    }

    private String extractKey(String fileUrl) {
        String key = fileUrl;

        if (fileUrl.startsWith("https://")
                || fileUrl.startsWith("http://")) {
            key = URI.create(fileUrl).getPath();
        }

        return normalizeKey(key);
    }

    private String normalizeKey(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException(
                    "O caminho do arquivo não pode ser vazio."
            );
        }

        String key = path.replace("\\", "/");

        while (key.startsWith("/")) {
            key = key.substring(1);
        }

        for (String segment : key.split("/")) {
            if (segment.equals("..") || segment.equals(".")) {
                throw new IllegalArgumentException(
                        "Caminho inválido para o storage: " + path
                );
            }
        }

        return key;
    }

    @PreDestroy
    void close() {
        presigner.close();
        s3Client.close();
    }
}
