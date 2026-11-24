package com.soniclab.soniclabbackend.soundkit.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class S3StorageServiceTest {

    private static final String BUCKET = "test-soniclab-bucket";
    private static final String REGION = "us-east-2";
    private static final String CUSTOM_BASE_URL = "https://cdn.soniclab.com/";
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(15);

    private S3Client s3Client;
    private S3Presigner presigner;
    private S3StorageService s3StorageService;

    @BeforeEach
    void setUp() {
        s3Client = mock(S3Client.class);
        presigner = mock(S3Presigner.class);
        s3StorageService = new S3StorageService(
                s3Client,
                presigner,
                BUCKET,
                REGION,
                CUSTOM_BASE_URL,
                DEFAULT_TTL
        );
    }

    @Nested
    @DisplayName("Testes de Upload")
    class UploadTests {

        @Test
        @DisplayName("Deve fazer upload de arquivo público e configurar metadados e contentType")
        void testUploadPublicFileSuccess() {
            byte[] content = "test audio demo content".getBytes();
            InputStream in = new ByteArrayInputStream(content);

            when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .thenReturn(PutObjectResponse.builder().build());

            String path = "soundkits/demos/sample.mp3";
            String key = s3StorageService.upload(path, in, content.length, "audio/mpeg", FileVisibility.PUBLIC);

            assertEquals("soundkits/demos/sample.mp3", key);

            ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
            verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));

            PutObjectRequest captured = requestCaptor.getValue();
            assertEquals(BUCKET, captured.bucket());
            assertEquals("soundkits/demos/sample.mp3", captured.key());
            assertEquals("audio/mpeg", captured.contentType());
            assertEquals("public", captured.metadata().get("visibility"));
        }

        @Test
        @DisplayName("Deve fazer upload de arquivo privado e configurar metadados correspondentes")
        void testUploadPrivateFileSuccess() {
            byte[] content = "zip package binary data".getBytes();
            InputStream in = new ByteArrayInputStream(content);

            when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .thenReturn(PutObjectResponse.builder().build());

            String path = "soundkits/packages/pack-123.zip";
            String key = s3StorageService.upload(path, in, content.length, "application/zip", FileVisibility.PRIVATE);

            assertEquals("soundkits/packages/pack-123.zip", key);

            ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
            verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));

            PutObjectRequest captured = requestCaptor.getValue();
            assertEquals(BUCKET, captured.bucket());
            assertEquals("soundkits/packages/pack-123.zip", captured.key());
            assertEquals("application/zip", captured.contentType());
            assertEquals("private", captured.metadata().get("visibility"));
        }

        @Test
        @DisplayName("Deve fechar o InputStream após o upload")
        void testUploadClosesInputStream() throws IOException {
            InputStream in = spy(new ByteArrayInputStream("data".getBytes()));

            when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .thenReturn(PutObjectResponse.builder().build());

            s3StorageService.upload("soundkits/demos/demo.wav", in, 4, "audio/wav", FileVisibility.PUBLIC);

            verify(in, atLeastOnce()).close();
        }

        @Test
        @DisplayName("Deve rejeitar InputStream nulo")
        void testUploadRejectsNullInputStream() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    s3StorageService.upload("soundkits/demos/demo.wav", null, 10, "audio/wav")
            );
            assertTrue(ex.getMessage().contains("não pode ser nulo"));
        }

        @Test
        @DisplayName("Deve rejeitar contentLength negativo")
        void testUploadRejectsNegativeContentLength() {
            InputStream in = new ByteArrayInputStream("data".getBytes());
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    s3StorageService.upload("soundkits/demos/demo.wav", in, -1, "audio/wav")
            );
            assertTrue(ex.getMessage().contains("contentLength"));
        }

        @Test
        @DisplayName("Deve lançar RuntimeException informativa quando S3 retornar S3Exception no upload")
        void testUploadS3ExceptionHandling() {
            InputStream in = new ByteArrayInputStream("data".getBytes());

            S3Exception s3Exception = (S3Exception) S3Exception.builder()
                    .statusCode(403)
                    .awsErrorDetails(AwsErrorDetails.builder().errorMessage("Access Denied").build())
                    .message("Access Denied")
                    .build();

            when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .thenThrow(s3Exception);

            RuntimeException ex = assertThrows(RuntimeException.class, () ->
                    s3StorageService.upload("soundkits/packages/pack.zip", in, 4, "application/zip", FileVisibility.PRIVATE)
            );

            assertTrue(ex.getMessage().contains("Falha ao enviar arquivo para o S3"));
            assertEquals(s3Exception, ex.getCause());
        }

        @Test
        @DisplayName("Deve lançar RuntimeException quando ocorrer falha de rede/SdkClientException no upload")
        void testUploadSdkClientExceptionHandling() {
            InputStream in = new ByteArrayInputStream("data".getBytes());

            when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .thenThrow(SdkClientException.create("Connection reset"));

            RuntimeException ex = assertThrows(RuntimeException.class, () ->
                    s3StorageService.upload("soundkits/packages/pack.zip", in, 4, "application/zip", FileVisibility.PRIVATE)
            );

            assertTrue(ex.getMessage().contains("Falha de comunicação"));
        }
    }

    @Nested
    @DisplayName("Testes de URLs Públicas e Privadas")
    class UrlTests {

        @Test
        @DisplayName("Deve retornar URL pública formatada para capas de SoundKit")
        void testGetPublicUrlForCover() {
            String url = s3StorageService.getPublicUrl("soundkits/covers/cover-abc.jpg");
            assertEquals("https://cdn.soniclab.com/soundkits/covers/cover-abc.jpg", url);
        }

        @Test
        @DisplayName("Deve retornar URL pública formatada para demos de áudio")
        void testGetPublicUrlForDemo() {
            String url = s3StorageService.getPublicUrl("soundkits/demos/demo-abc.mp3");
            assertEquals("https://cdn.soniclab.com/soundkits/demos/demo-abc.mp3", url);
        }

        @Test
        @DisplayName("Deve bloquear obtenção de URL pública permanente para pacotes privados")
        void testGetPublicUrlBlockedForPrivatePackage() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    s3StorageService.getPublicUrl("soundkits/packages/complete-pack.zip")
            );
            assertTrue(ex.getMessage().contains("privado e não possui URL pública permanente"));
        }

        @Test
        @DisplayName("Deve gerar Presigned URL para pacote privado com TTL padrão de 15 minutos")
        void testGeneratePresignedUrlWithDefaultTtl() throws Exception {
            String expectedUrl = "https://test-soniclab-bucket.s3.us-east-2.amazonaws.com/soundkits/packages/pack.zip?X-Amz-Signature=xyz";
            URL mockUrl = URI.create(expectedUrl).toURL();

            PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
            when(presigned.url()).thenReturn(mockUrl);

            ArgumentCaptor<GetObjectPresignRequest> presignCaptor = ArgumentCaptor.forClass(GetObjectPresignRequest.class);
            when(presigner.presignGetObject(presignCaptor.capture())).thenReturn(presigned);

            String result = s3StorageService.generateDownloadUrl("soundkits/packages/pack.zip");

            assertEquals(expectedUrl, result);

            GetObjectPresignRequest presignRequest = presignCaptor.getValue();
            assertEquals(Duration.ofMinutes(15), presignRequest.signatureDuration());
            assertEquals(BUCKET, presignRequest.getObjectRequest().bucket());
            assertEquals("soundkits/packages/pack.zip", presignRequest.getObjectRequest().key());
        }

        @Test
        @DisplayName("Deve gerar Presigned URL com TTL customizado")
        void testGeneratePresignedUrlWithCustomTtl() throws Exception {
            String expectedUrl = "https://test-soniclab-bucket.s3.us-east-2.amazonaws.com/soundkits/packages/pack.zip?X-Amz-Signature=custom";
            URL mockUrl = URI.create(expectedUrl).toURL();

            PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
            when(presigned.url()).thenReturn(mockUrl);

            ArgumentCaptor<GetObjectPresignRequest> presignCaptor = ArgumentCaptor.forClass(GetObjectPresignRequest.class);
            when(presigner.presignGetObject(presignCaptor.capture())).thenReturn(presigned);

            Duration customTtl = Duration.ofHours(2);
            String result = s3StorageService.generateDownloadUrl("soundkits/packages/pack.zip", customTtl);

            assertEquals(expectedUrl, result);
            assertEquals(Duration.ofHours(2), presignCaptor.getValue().signatureDuration());
        }

        @Test
        @DisplayName("Deve rejeitar TTL nulo ou menor ou igual a zero")
        void testPresignedUrlRejectsInvalidTtl() {
            assertThrows(IllegalArgumentException.class, () ->
                    s3StorageService.generateDownloadUrl("soundkits/packages/pack.zip", Duration.ZERO)
            );
            assertThrows(IllegalArgumentException.class, () ->
                    s3StorageService.generateDownloadUrl("soundkits/packages/pack.zip", Duration.ofSeconds(-10))
            );
        }

        @Test
        @DisplayName("Deve rejeitar TTL maior que o limite de 7 dias da AWS")
        void testPresignedUrlRejectsTtlExceeding7Days() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    s3StorageService.generateDownloadUrl("soundkits/packages/pack.zip", Duration.ofDays(8))
            );
            assertTrue(ex.getMessage().contains("7 dias"));
        }

        @Test
        @DisplayName("Deve classificar corretamente isPublic para arquivos públicos e privados")
        void testIsPublicClassification() {
            assertTrue(s3StorageService.isPublic("soundkits/covers/cover.jpg"));
            assertTrue(s3StorageService.isPublic("soundkits/demos/demo.wav"));
            assertTrue(s3StorageService.isPublic("public/documents/terms.pdf"));

            assertFalse(s3StorageService.isPublic("soundkits/packages/pack.zip"));
            assertFalse(s3StorageService.isPublic("private/licenses/key.txt"));
        }
    }

    @Nested
    @DisplayName("Testes de Exclusão (Delete) e Extração de Chave")
    class DeleteTests {

        @Test
        @DisplayName("Deve excluir arquivo fornecendo a chave direta")
        void testDeleteWithDirectKey() {
            when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                    .thenReturn(DeleteObjectResponse.builder().build());

            s3StorageService.delete("soundkits/packages/pack-abc.zip");

            ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
            verify(s3Client).deleteObject(captor.capture());
            assertEquals(BUCKET, captor.getValue().bucket());
            assertEquals("soundkits/packages/pack-abc.zip", captor.getValue().key());
        }

        @Test
        @DisplayName("Deve excluir arquivo fornecendo URL pública do CDN customizado")
        void testDeleteWithCustomPublicUrl() {
            when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                    .thenReturn(DeleteObjectResponse.builder().build());

            s3StorageService.delete("https://cdn.soniclab.com/soundkits/covers/cover-123.jpg");

            ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
            verify(s3Client).deleteObject(captor.capture());
            assertEquals("soundkits/covers/cover-123.jpg", captor.getValue().key());
        }

        @Test
        @DisplayName("Deve excluir arquivo fornecendo Presigned URL com query string sem corromper a object key")
        void testDeleteWithPresignedUrlStripsQueryParams() {
            when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                    .thenReturn(DeleteObjectResponse.builder().build());

            String presignedUrl = "https://test-soniclab-bucket.s3.us-east-2.amazonaws.com/soundkits/packages/pack-123.zip" +
                    "?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Date=20261123T040000Z&X-Amz-Expires=900&X-Amz-Signature=abcdef123456";

            s3StorageService.delete(presignedUrl);

            ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
            verify(s3Client).deleteObject(captor.capture());
            assertEquals("soundkits/packages/pack-123.zip", captor.getValue().key());
        }

        @Test
        @DisplayName("Deve excluir arquivo fornecendo URL path-style (removendo prefixo do bucket)")
        void testDeleteWithPathStyleUrlStripsBucketPrefix() {
            when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                    .thenReturn(DeleteObjectResponse.builder().build());

            String pathStyleUrl = "http://localhost:4566/test-soniclab-bucket/soundkits/packages/pack-456.zip?query=param";

            s3StorageService.delete(pathStyleUrl);

            ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
            verify(s3Client).deleteObject(captor.capture());
            assertEquals("soundkits/packages/pack-456.zip", captor.getValue().key());
        }

        @Test
        @DisplayName("Deve rejeitar tentativa de exclusão com URL de outro bucket ou domínio externo")
        void testDeleteRejectsExternalDomainUrl() {
            String externalUrl = "https://attacker-bucket.s3.amazonaws.com/soundkits/packages/victim-pack.zip";

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    s3StorageService.delete(externalUrl)
            );
            assertTrue(ex.getMessage().contains("não pertence ao bucket"));
            verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
        }

        @Test
        @DisplayName("Deve tolerar exclusão de arquivo inexistente sem lançar exceção (idempotência)")
        void testDeleteNonExistentObjectSucceedsIdempotently() {
            when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                    .thenReturn(DeleteObjectResponse.builder().build());

            assertDoesNotThrow(() -> s3StorageService.delete("soundkits/packages/non-existent.zip"));
        }

        @Test
        @DisplayName("Deve lançar RuntimeException quando S3 retornar falha ao deletar")
        void testDeleteThrowsOnS3Exception() {
            S3Exception s3Exception = (S3Exception) S3Exception.builder()
                    .statusCode(500)
                    .message("Internal S3 Error")
                    .build();

            when(s3Client.deleteObject(any(DeleteObjectRequest.class))).thenThrow(s3Exception);

            RuntimeException ex = assertThrows(RuntimeException.class, () ->
                    s3StorageService.delete("soundkits/packages/pack.zip")
            );
            assertTrue(ex.getMessage().contains("Falha ao deletar arquivo no S3"));
        }
    }

    @Nested
    @DisplayName("Testes de Verificação de Existência (exists)")
    class ExistsTests {

        @Test
        @DisplayName("Deve retornar true quando HeadObject tiver sucesso")
        void testExistsReturnsTrueWhenObjectFound() {
            when(s3Client.headObject(any(HeadObjectRequest.class)))
                    .thenReturn(HeadObjectResponse.builder().build());

            assertTrue(s3StorageService.exists("soundkits/packages/pack.zip"));
        }

        @Test
        @DisplayName("Deve retornar false quando HeadObject retornar NoSuchKeyException")
        void testExistsReturnsFalseOnNoSuchKey() {
            when(s3Client.headObject(any(HeadObjectRequest.class)))
                    .thenThrow(NoSuchKeyException.builder().build());

            assertFalse(s3StorageService.exists("soundkits/packages/missing.zip"));
        }

        @Test
        @DisplayName("Deve retornar false quando HeadObject retornar S3Exception 404")
        void testExistsReturnsFalseOnS3Exception404() {
            S3Exception notFound = (S3Exception) S3Exception.builder().statusCode(404).message("Not Found").build();
            when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(notFound);

            assertFalse(s3StorageService.exists("soundkits/packages/missing.zip"));
        }

        @Test
        @DisplayName("Deve retornar false para chave nula ou vazia")
        void testExistsReturnsFalseOnEmptyKey() {
            assertFalse(s3StorageService.exists(null));
            assertFalse(s3StorageService.exists("   "));
        }
    }

    @Nested
    @DisplayName("Testes de Segurança e Normalização de Caminhos")
    class SecurityAndPathTraversalTests {

        @Test
        @DisplayName("Deve bloquear tentativa de path traversal com '..' nos caminhos")
        void testNormalizeKeyRejectsDotDotTraversal() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    s3StorageService.normalizeKey("../../etc/passwd")
            );
            assertTrue(ex.getMessage().contains("Caminho inválido"));
        }

        @Test
        @DisplayName("Deve bloquear caminhos que contenham segmento '.'")
        void testNormalizeKeyRejectsSingleDotSegment() {
            assertThrows(IllegalArgumentException.class, () ->
                    s3StorageService.normalizeKey("soundkits/./covers/test.png")
            );
        }

        @Test
        @DisplayName("Deve rejeitar caminhos com caracteres nulos")
        void testNormalizeKeyRejectsNullBytes() {
            assertThrows(IllegalArgumentException.class, () ->
                    s3StorageService.normalizeKey("soundkits/covers/pic.png\0.exe")
            );
        }

        @Test
        @DisplayName("Deve normalizar barras invertidas do Windows e barras duplicadas")
        void testNormalizeKeyHandlesBackslashesAndDuplicateSlashes() {
            String normalized = s3StorageService.normalizeKey("\\soundkits\\\\covers///pic.png");
            assertEquals("soundkits/covers/pic.png", normalized);
        }

        @Test
        @DisplayName("Deve rejeitar caminhos vazios ou somente espaços")
        void testNormalizeKeyRejectsBlankPaths() {
            assertThrows(IllegalArgumentException.class, () -> s3StorageService.normalizeKey(""));
            assertThrows(IllegalArgumentException.class, () -> s3StorageService.normalizeKey("   "));
            assertThrows(IllegalArgumentException.class, () -> s3StorageService.normalizeKey(null));
        }

        @Test
        @DisplayName("Deve rejeitar caminho que termina com barra indicando diretório")
        void testNormalizeKeyRejectsTrailingSlash() {
            assertThrows(IllegalArgumentException.class, () -> s3StorageService.normalizeKey("soundkits/covers/"));
        }
    }

    @Nested
    @DisplayName("Testes de Fechamento de Recursos")
    class LifecycleTests {

        @Test
        @DisplayName("Deve fechar presigner e s3Client no close()")
        void testCloseShutsDownBothClients() {
            s3StorageService.close();

            verify(presigner).close();
            verify(s3Client).close();
        }

        @Test
        @DisplayName("Deve tentar fechar s3Client mesmo se presigner lançar exceção no close()")
        void testCloseIsResilientToExceptions() {
            doThrow(new RuntimeException("Error closing presigner")).when(presigner).close();

            assertDoesNotThrow(() -> s3StorageService.close());
            verify(s3Client).close();
        }
    }
}
