package com.soniclab.soniclabbackend.soundkit.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class StorageConfigurationIntegrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    LocalStorageService.class,
                    AwsS3Config.class,
                    S3StorageService.class
            );

    @Test
    @DisplayName("Quando storage.type=local ou omitido, deve carregar LocalStorageService e não carregar S3StorageService")
    void shouldLoadLocalStorageServiceByDefault() {
        contextRunner
                .withPropertyValues("storage.local.dir=./test-uploads")
                .run(context -> {
                    assertThat(context).hasSingleBean(FileStorageService.class);
                    assertThat(context).hasSingleBean(LocalStorageService.class);
                    assertThat(context).doesNotHaveBean(S3StorageService.class);
                    assertThat(context).doesNotHaveBean(AwsS3Config.class);
                });
    }

    @Test
    @DisplayName("Quando storage.type=s3, deve carregar S3StorageService e AwsS3Config e não carregar LocalStorageService")
    void shouldLoadS3StorageServiceWhenConfigured() {
        contextRunner
                .withPropertyValues(
                        "storage.type=s3",
                        "storage.s3.bucket=my-test-bucket",
                        "storage.s3.region=us-east-2",
                        "storage.s3.access-key=dummy-access-key",
                        "storage.s3.secret-key=dummy-secret-key",
                        "storage.s3.endpoint=http://localhost:4566"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(FileStorageService.class);
                    assertThat(context).hasSingleBean(S3StorageService.class);
                    assertThat(context).hasSingleBean(AwsS3Config.class);
                    assertThat(context).hasSingleBean(S3Client.class);
                    assertThat(context).hasSingleBean(S3Presigner.class);
                    assertThat(context).hasSingleBean(AwsCredentialsProvider.class);
                    assertThat(context).doesNotHaveBean(LocalStorageService.class);
                });
    }

    @Test
    @DisplayName("Permite substituição de S3Client e S3Presigner por mocks em testes sem conflito de beans")
    void shouldAllowCustomBeansOverride() {
        S3Client mockS3 = mock(S3Client.class);
        S3Presigner mockPresigner = mock(S3Presigner.class);

        contextRunner
                .withPropertyValues(
                        "storage.type=s3",
                        "storage.s3.bucket=my-test-bucket"
                )
                .withBean(S3Client.class, () -> mockS3)
                .withBean(S3Presigner.class, () -> mockPresigner)
                .run(context -> {
                    assertThat(context).hasSingleBean(S3StorageService.class);
                    assertThat(context.getBean(S3Client.class)).isSameAs(mockS3);
                    assertThat(context.getBean(S3Presigner.class)).isSameAs(mockPresigner);
                });
    }
}
