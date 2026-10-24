
package com.soniclab.soniclabbackend.soundkit.storage;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
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
    private final String publicBaseUrl;

    public S3StorageService(
            @Value("${storage.s3.bucket}") String bucket,
            @Value("${storage.s3.region}") String region,
            @Value("${storage.s3.public-base-url:}") String customBaseUrl,
            @Value("${storage.s3.access-key:}") String accessKey,
            @Value("${storage.s3.secret-key:}") String secretKey
    ) {
        this.bucket = bucket;
        Region awsRegion = Region.of(region);

        AwsCredentialsProvider credentials =
                (!accessKey.isBlank() && !secretKey.isBlank())
                        ? StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey))
                        : DefaultCredentialsProvider.create();

        this.s3Client = S3Client.builder()
                .region(awsRegion).credentialsProvider(credentials).build();
        this.presigner = S3Presigner.builder()
                .region(awsRegion).credentialsProvider(credentials).build();

        String url = customBaseUrl.isBlank()
                ? "https://" + bucket + ".s3." + region + ".amazonaws.com/"
                : customBaseUrl;
        this.publicBaseUrl = url.endsWith("/") ? url : url + "/";
    }

    @Override
    public String upload(String path, InputStream in, long contentLength, String contentType) {
        String key = normalizeKey(path);
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType != null ? contentType : "application/octet-stream")
                    .build();

            s3Client.putObject(request, RequestBody.fromInputStream(in, contentLength));
            return key;
        } catch (S3Exception e) {
            throw new RuntimeException("Falha ao enviar arquivo para o S3: " + key, e);
        }
    }

    @Override
    public String getPublicUrl(String key) {
        return publicBaseUrl + normalizeKey(key);
    }

    @Override
    public String generateDownloadUrl(String key, Duration ttl) {
        GetObjectPresignRequest presign = GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(normalizeKey(key))
                        .build())
                .build();
        return presigner.presignGetObject(presign).url().toExternalForm();
    }

    @Override
    public void delete(String keyOrUrl) {
        String key = extractKey(keyOrUrl);
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket).key(key).build());
        } catch (S3Exception e) {
            throw new RuntimeException("Falha ao deletar arquivo no S3: " + key, e);
        }
    }

    private String extractKey(String keyOrUrl) {
        if (keyOrUrl.startsWith(publicBaseUrl)) {
            return normalizeKey(keyOrUrl.substring(publicBaseUrl.length()));
        }
        if (keyOrUrl.startsWith("http://") || keyOrUrl.startsWith("https://")) {
            return normalizeKey(URI.create(keyOrUrl).getPath());
        }
        return normalizeKey(keyOrUrl);
    }

    private String normalizeKey(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("O caminho do arquivo não pode ser vazio.");
        }
        String key = path.replace("\\", "/");
        while (key.startsWith("/")) key = key.substring(1);

        for (String segment : key.split("/")) {
            if (segment.equals("..") || segment.equals(".")) {
                throw new IllegalArgumentException("Caminho inválido para o storage: " + path);
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