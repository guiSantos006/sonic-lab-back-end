package com.soniclab.soniclabbackend.soundkit.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * Configuração dos clientes AWS SDK para S3 e Presigner.
 * Ativo apenas quando storage.type=s3.
 */
@Configuration
@ConditionalOnProperty(name = "storage.type", havingValue = "s3")
public class AwsS3Config {

    private static final Logger log = LoggerFactory.getLogger(AwsS3Config.class);

    @Bean
    @ConditionalOnMissingBean
    public AwsCredentialsProvider awsCredentialsProvider(
            @Value("${storage.s3.access-key:}") String accessKey,
            @Value("${storage.s3.secret-key:}") String secretKey
    ) {
        if (accessKey != null && !accessKey.isBlank() && secretKey != null && !secretKey.isBlank()) {
            log.info("Inicializando AWS S3 com credenciais estáticas fornecidas (accessKey).");
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey.trim(), secretKey.trim()));
        }
        log.info("Inicializando AWS S3 com DefaultCredentialsProvider (IAM Roles / Vars de Ambiente).");
        return DefaultCredentialsProvider.create();
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public S3Client s3Client(
            AwsCredentialsProvider credentialsProvider,
            @Value("${storage.s3.region:us-east-2}") String region,
            @Value("${storage.s3.endpoint:}") String endpoint,
            @Value("${storage.s3.path-style-access:false}") boolean pathStyleAccess
    ) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(region.trim()))
                .credentialsProvider(credentialsProvider);

        if (endpoint != null && !endpoint.isBlank()) {
            log.info("Configurando endpoint customizado para S3Client: {}", endpoint);
            builder.endpointOverride(URI.create(endpoint.trim()));
        }

        if (pathStyleAccess) {
            builder.serviceConfiguration(S3Configuration.builder()
                    .pathStyleAccessEnabled(true)
                    .build());
        }

        return builder.build();
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public S3Presigner s3Presigner(
            AwsCredentialsProvider credentialsProvider,
            @Value("${storage.s3.region:us-east-2}") String region,
            @Value("${storage.s3.endpoint:}") String endpoint,
            @Value("${storage.s3.path-style-access:false}") boolean pathStyleAccess
    ) {
        S3Presigner.Builder builder = S3Presigner.builder()
                .region(Region.of(region.trim()))
                .credentialsProvider(credentialsProvider);

        if (endpoint != null && !endpoint.isBlank()) {
            log.info("Configurando endpoint customizado para S3Presigner: {}", endpoint);
            builder.endpointOverride(URI.create(endpoint.trim()));
        }

        if (pathStyleAccess) {
            builder.serviceConfiguration(S3Configuration.builder()
                    .pathStyleAccessEnabled(true)
                    .build());
        }

        return builder.build();
    }
}
