package com.soniclab.soniclabbackend.soundkit;

import com.soniclab.soniclabbackend.soundkit.dto.request.CreateSoundKitRequest;
import com.soniclab.soniclabbackend.soundkit.dto.response.SoundKitDownloadResponse;
import com.soniclab.soniclabbackend.soundkit.dto.response.SoundKitResponse;
import com.soniclab.soniclabbackend.soundkit.dto.response.SoundKitUploadResponse;
import com.soniclab.soniclabbackend.soundkit.entity.AudioDemo;
import com.soniclab.soniclabbackend.soundkit.entity.KitType;
import com.soniclab.soniclabbackend.soundkit.entity.SoundKit;
import com.soniclab.soniclabbackend.soundkit.entity.SoundKitId;
import com.soniclab.soniclabbackend.soundkit.repository.SoundKitRepository;
import com.soniclab.soniclabbackend.soundkit.service.SoundKitFileProcessor;
import com.soniclab.soniclabbackend.soundkit.service.SoundKitService;
import com.soniclab.soniclabbackend.soundkit.storage.FileStorageService;
import com.soniclab.soniclabbackend.soundkit.storage.FileVisibility;
import com.soniclab.soniclabbackend.soundkit.storage.S3StorageService;
import com.soniclab.soniclabbackend.user.config.JWTUserData;
import com.soniclab.soniclabbackend.user.entity.UserId;
import com.soniclab.soniclabbackend.user.entity.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class SoundKitFlowIntegrationTest {

    private static final String BUCKET = "meu-projeto-storage-dev-2026-512902042762-us-east-2-an";
    private static final String REGION = "us-east-2";
    private static final String PUBLIC_BASE_URL = "https://" + BUCKET + ".s3." + REGION + ".amazonaws.com/";

    private S3Client s3Client;
    private S3Presigner s3Presigner;
    private FileStorageService storageService;
    private SoundKitFileProcessor fileProcessor;
    private SoundKitRepository soundKitRepository;
    private MongoTemplate mongoTemplate;
    private SoundKitService soundKitService;

    @BeforeEach
    void setUp() {
        s3Client = mock(S3Client.class);
        s3Presigner = mock(S3Presigner.class);

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        storageService = new S3StorageService(
                s3Client,
                s3Presigner,
                BUCKET,
                REGION,
                PUBLIC_BASE_URL,
                Duration.ofMinutes(15)
        );

        fileProcessor = new SoundKitFileProcessor(storageService);
        soundKitRepository = mock(SoundKitRepository.class);
        mongoTemplate = mock(MongoTemplate.class);
        soundKitService = new SoundKitService(soundKitRepository, mongoTemplate, storageService);
    }

    @Test
    @DisplayName("Fluxo completo: Upload de ZIP privado, Cover público, Demo MP3 com waveform real, cadastro e download autenticado")
    void testCompleteSoundKitFlow() throws Exception {
        // 1. UPLOAD DE PACOTE ZIP (PRIVADO)
        ByteArrayOutputStream zipBaos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(zipBaos)) {
            zos.putNextEntry(new ZipEntry("kick.wav"));
            zos.write("dummy wav audio data".getBytes());
            zos.closeEntry();
        }
        MockMultipartFile zipFile = new MockMultipartFile(
                "files",
                "drums-pack.zip",
                "application/zip",
                zipBaos.toByteArray()
        );

        SoundKitUploadResponse packageResponse = fileProcessor.processAndStorePackage(List.of(zipFile));

        assertNotNull(packageResponse);
        assertNotNull(packageResponse.fileKey());
        assertTrue(packageResponse.fileKey().startsWith("soundkits/packages/"));
        assertTrue(packageResponse.fileKey().endsWith(".zip"));
        assertEquals(packageResponse.fileKey(), packageResponse.fileUrl());

        // Verifica que no S3 foi enviado com metadados FileVisibility.PRIVATE
        ArgumentCaptor<PutObjectRequest> putCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client, atLeastOnce()).putObject(putCaptor.capture(), any(RequestBody.class));
        PutObjectRequest capturedZipPut = putCaptor.getAllValues().stream()
                .filter(p -> p.key().startsWith("soundkits/packages/"))
                .findFirst()
                .orElseThrow();
        assertEquals("private", capturedZipPut.metadata().get("visibility"));

        // O pacote privado NUNCA deve permitir URL pública direta
        assertThrows(IllegalArgumentException.class, () -> storageService.getPublicUrl(packageResponse.fileKey()));

        // 2. UPLOAD DE COVER (PÚBLICO)
        byte[] coverBytes = new byte[]{1, 2, 3, 4};
        String coverKey = storageService.upload("soundkits/covers/cover-123.jpg",
                new java.io.ByteArrayInputStream(coverBytes),
                coverBytes.length,
                "image/jpeg",
                FileVisibility.PUBLIC);
        String coverUrl = storageService.getPublicUrl(coverKey);
        assertTrue(coverUrl.startsWith("https://" + BUCKET + ".s3." + REGION + ".amazonaws.com/soundkits/covers/"));

        // 3. UPLOAD DE AUDIODEMO MP3 (PÚBLICO + WAVEFORM REAL)
        File mp3TestFile = new File("src/test/resources/test-audio.mp3");
        byte[] mp3Bytes = Files.readAllBytes(mp3TestFile.toPath());
        MockMultipartFile demoFile = new MockMultipartFile(
                "file",
                "You.mp3",
                "audio/mpeg",
                mp3Bytes
        );

        AudioDemo demo = fileProcessor.processAudioDemo(demoFile);
        assertNotNull(demo);
        assertTrue(demo.url().startsWith("https://" + BUCKET + ".s3." + REGION + ".amazonaws.com/soundkits/demos/"));
        assertTrue(demo.url().endsWith(".mp3"));
        assertNotNull(demo.durationInSeconds());
        assertTrue(demo.durationInSeconds() > 0);
        assertNotNull(demo.waveformData());
        assertEquals(100, demo.waveformData().size());
        assertFalse(demo.waveformData().isEmpty());

        // 4. CADASTRO DO SOUNDKIT NO SERVICE (testando resiliência caso frontend passe localhost)
        UserId creatorId = new UserId();
        JWTUserData creatorUser = new JWTUserData(creatorId, "creator@soniclab.com", UserRole.USER);

        // Simulando envio onde cliente prefixou localhost
        String localhostFileUrl = "http://localhost:8080/" + packageResponse.fileKey();
        CreateSoundKitRequest createRequest = new CreateSoundKitRequest(
                "Trap Platinum Kit 2026",
                "Kit completo com 808s e samples exclusivos",
                new BigDecimal("89.90"),
                KitType.DRUM_KIT,
                coverUrl,
                localhostFileUrl,
                demo,
                List.of("TRAP", "808"),
                Map.of("samples", "150")
        );

        when(soundKitRepository.save(any(SoundKit.class))).thenAnswer(i -> i.getArgument(0));

        SoundKitResponse createdResponse = soundKitService.create(createRequest, creatorUser);

        assertNotNull(createdResponse);
        assertEquals("Trap Platinum Kit 2026", createdResponse.title());
        assertEquals(coverUrl, createdResponse.coverUrl());
        assertEquals(demo.url(), createdResponse.audioDemo().url());
        assertEquals(100, createdResponse.audioDemo().waveformData().size());

        // Captura e valida o documento persistido no MongoDB
        ArgumentCaptor<SoundKit> mongoCaptor = ArgumentCaptor.forClass(SoundKit.class);
        verify(soundKitRepository).save(mongoCaptor.capture());
        SoundKit savedEntity = mongoCaptor.getValue();

        // VALIDAÇÃO CHAVE DO PROBLEMA 1: fileUrl persistido NUNCA deve ser localhost
        assertFalse(savedEntity.getFileUrl().contains("localhost:8080"), "MongoDB não pode persistir URL local com localhost");
        assertEquals(packageResponse.fileKey(), savedEntity.getFileUrl(), "MongoDB deve persistir exclusivamente a chave de armazenamento estável");

        // VALIDAÇÃO CHAVE DO PROBLEMA 2: waveform real persistido no documento
        assertNotNull(savedEntity.getAudioDemo());
        assertEquals(100, savedEntity.getAudioDemo().waveformData().size());
        assertTrue(savedEntity.getAudioDemo().waveformData().stream().anyMatch(v -> v > 0.0f));

        // 5. DOWNLOAD AUTENTICADO DO PACOTE PRIVADO
        UUID kitUuid = createdResponse.id();
        when(soundKitRepository.findByIdAndActiveTrue(new SoundKitId(kitUuid))).thenReturn(Optional.of(savedEntity));

        String mockPresignedUrl = "https://" + BUCKET + ".s3." + REGION + ".amazonaws.com/" + packageResponse.fileKey() + "?X-Amz-Signature=sig123";
        URL presignedUrlObj = URI.create(mockPresignedUrl).toURL();
        PresignedGetObjectRequest presignedMock = mock(PresignedGetObjectRequest.class);
        when(presignedMock.url()).thenReturn(presignedUrlObj);
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presignedMock);

        JWTUserData buyerUser = new JWTUserData(new UserId(), "buyer@soniclab.com", UserRole.USER);
        SoundKitDownloadResponse downloadResponse = soundKitService.getDownloadUrl(kitUuid, buyerUser);

        assertNotNull(downloadResponse);
        assertEquals(mockPresignedUrl, downloadResponse.downloadUrl());
        assertEquals(900L, downloadResponse.expiresInSeconds());

        // 6. TENTATIVA DE DOWNLOAD NÃO AUTENTICADO DEVE FALHAR
        assertThrows(AccessDeniedException.class, () -> soundKitService.getDownloadUrl(kitUuid, null));
    }
}
