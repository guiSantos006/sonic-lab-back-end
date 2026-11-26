package com.soniclab.soniclabbackend.soundkit.service;

import com.soniclab.soniclabbackend.soundkit.entity.AudioDemo;
import com.soniclab.soniclabbackend.soundkit.storage.FileStorageService;
import com.soniclab.soniclabbackend.soundkit.storage.FileVisibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SoundKitAudioTranscodingAndLimitsTest {

    private SoundKitFileProcessor fileProcessor;
    private FileStorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = Mockito.mock(FileStorageService.class);
        when(storageService.upload(anyString(), any(InputStream.class), anyLong(), anyString(), any(FileVisibility.class)))
                .thenReturn("soundkits/demos/transcoded-demo.mp3");
        when(storageService.getPublicUrl(anyString()))
                .thenReturn("https://bucket.s3.us-east-2.amazonaws.com/soundkits/demos/transcoded-demo.mp3");

        fileProcessor = new SoundKitFileProcessor(storageService);
    }

    @Test
    @DisplayName("Deve transcodificar WAV real para MP3 via JAVE/FFmpeg e gerar waveform")
    void shouldTranscodeWavDemoToMp3AndExtractWaveform() throws Exception {
        File wavFile = new File("src/test/resources/test-audio.wav");
        assertTrue(wavFile.exists(), "Arquivo test-audio.wav deve existir");

        byte[] wavBytes = Files.readAllBytes(wavFile.toPath());
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file",
                "sample-demo.wav",
                "audio/wav",
                wavBytes
        );

        AudioDemo demo = fileProcessor.processAudioDemo(multipartFile);

        assertNotNull(demo);
        assertEquals("https://bucket.s3.us-east-2.amazonaws.com/soundkits/demos/transcoded-demo.mp3", demo.url());
        assertNotNull(demo.durationInSeconds());
        assertEquals(2, demo.durationInSeconds());
        assertNotNull(demo.waveformData());
        assertEquals(SoundKitFileProcessor.DEFAULT_WAVEFORM_POINTS, demo.waveformData().size());

        verify(storageService).upload(
                anyString(),
                any(InputStream.class),
                anyLong(),
                eq("audio/mpeg"),
                eq(FileVisibility.PUBLIC)
        );
    }

    @Test
    @DisplayName("Deve rejeitar demo de áudio que ultrapasse o limite de 20 MB")
    void shouldRejectDemoExceeding20MbLimit() {
        MultipartFile oversizedMock = Mockito.mock(MultipartFile.class);
        when(oversizedMock.isEmpty()).thenReturn(false);
        when(oversizedMock.getSize()).thenReturn((20L * 1024 * 1024) + 1);
        when(oversizedMock.getOriginalFilename()).thenReturn("huge-demo.wav");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                fileProcessor.processAudioDemo(oversizedMock)
        );
        assertTrue(ex.getMessage().contains("20 MB"));
    }
}
