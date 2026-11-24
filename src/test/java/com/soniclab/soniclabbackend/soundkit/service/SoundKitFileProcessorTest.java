package com.soniclab.soniclabbackend.soundkit.service;

import com.soniclab.soniclabbackend.soundkit.entity.AudioDemo;
import com.soniclab.soniclabbackend.soundkit.storage.FileStorageService;
import com.soniclab.soniclabbackend.soundkit.storage.FileVisibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SoundKitFileProcessorTest {

    private SoundKitFileProcessor fileProcessor;
    private FileStorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = Mockito.mock(FileStorageService.class);
        when(storageService.upload(anyString(), any(InputStream.class), anyLong(), anyString(), any(FileVisibility.class)))
                .thenReturn("soundkits/demos/test-demo-key.mp3");
        when(storageService.getPublicUrl(anyString()))
                .thenReturn("https://bucket.s3.us-east-2.amazonaws.com/soundkits/demos/test-demo-key.mp3");

        fileProcessor = new SoundKitFileProcessor(storageService);
    }

    @Test
    @DisplayName("Deve extrair waveform real de 100 pontos normalizados de um arquivo MP3 real de teste")
    void testExtractWaveformFromRealMp3() throws Exception {
        File mp3File = new File("src/test/resources/test-audio.mp3");
        assertTrue(mp3File.exists(), "O arquivo MP3 de teste deve existir em src/test/resources/test-audio.mp3");

        byte[] mp3Bytes = Files.readAllBytes(mp3File.toPath());

        SoundKitFileProcessor.AudioAnalysisResult result = fileProcessor.analyzeMp3(mp3Bytes);

        assertNotNull(result);
        assertNotNull(result.durationInSeconds());
        assertTrue(result.durationInSeconds() > 0, "Duração do MP3 deve ser maior que 0");

        List<Float> waveform = result.waveform();
        assertNotNull(waveform);
        assertEquals(SoundKitFileProcessor.DEFAULT_WAVEFORM_POINTS, waveform.size(), "Deve conter exatamente 100 pontos");

        // Todos os pontos devem estar estritamente normalizados entre 0.0 e 1.0
        for (float val : waveform) {
            assertTrue(val >= 0.0f && val <= 1.0f, "Valor da waveform fora do intervalo [0, 1]: " + val);
        }

        // Deve haver pico máximo atingindo 1.0 e variação dinâmica (não todos iguais)
        float max = waveform.stream().max(Float::compareTo).orElse(0.0f);
        float min = waveform.stream().min(Float::compareTo).orElse(0.0f);
        assertEquals(1.0f, max, 0.01f, "Pelo menos um ponto do waveform deve atingir o pico normalizado 1.0");
        assertTrue(max > min, "Deve haver contraste dinâmico real entre os pontos do MP3");
    }

    @Test
    @DisplayName("Deve extrair waveform de 100 pontos e calcular duração de arquivo WAV real")
    void testExtractWaveformFromRealWav() throws Exception {
        File wavFile = new File("src/test/resources/test-audio.wav");
        assertTrue(wavFile.exists(), "O arquivo WAV de teste deve existir em src/test/resources/test-audio.wav");

        byte[] wavBytes = Files.readAllBytes(wavFile.toPath());

        SoundKitFileProcessor.AudioAnalysisResult result = fileProcessor.analyzeViaAudioSystem(wavBytes);

        assertNotNull(result);
        assertNotNull(result.durationInSeconds());
        assertEquals(2, result.durationInSeconds(), "Duração do WAV de teste sintético deve ser de 2 segundos");

        List<Float> waveform = result.waveform();
        assertNotNull(waveform);
        assertEquals(SoundKitFileProcessor.DEFAULT_WAVEFORM_POINTS, waveform.size(), "Deve conter exatamente 100 pontos");

        for (float val : waveform) {
            assertTrue(val >= 0.0f && val <= 1.0f, "Valor da waveform fora de [0, 1]: " + val);
        }

        float max = waveform.stream().max(Float::compareTo).orElse(0.0f);
        assertEquals(1.0f, max, 0.01f, "Pico normalizado deve ser 1.0");
    }

    @Test
    @DisplayName("Deve processar AudioDemo completo via processAudioDemo garantindo que stream não seja consumido/invalidado")
    void testProcessAudioDemoWithMp3MultipartFile() throws Exception {
        File mp3File = new File("src/test/resources/test-audio.mp3");
        byte[] mp3Bytes = Files.readAllBytes(mp3File.toPath());

        MockMultipartFile multipartFile = new MockMultipartFile(
                "file",
                "You.mp3",
                "audio/mpeg",
                mp3Bytes
        );

        AudioDemo demo = fileProcessor.processAudioDemo(multipartFile);

        assertNotNull(demo);
        assertNotNull(demo.url());
        assertEquals("https://bucket.s3.us-east-2.amazonaws.com/soundkits/demos/test-demo-key.mp3", demo.url());
        assertNotNull(demo.durationInSeconds());
        assertTrue(demo.durationInSeconds() > 0);
        assertNotNull(demo.waveformData());
        assertEquals(100, demo.waveformData().size());
        assertFalse(demo.waveformData().isEmpty(), "waveformData NÃO pode permanecer vazia para MP3");

        // Verifica que o storage recebeu upload com visibilidade PUBLIC
        verify(storageService).upload(
                anyString(),
                any(InputStream.class),
                eq((long) mp3Bytes.length),
                eq("audio/mpeg"),
                eq(FileVisibility.PUBLIC)
        );
    }

    @Test
    @DisplayName("Deve tratar formato não suportado ou corrompido sem quebrar a aplicação")
    void testUnsupportedAudioFileHandling() {
        byte[] invalidAudioBytes = "ISTO_NAO_E_UM_AUDIO_VALIDO".getBytes();

        SoundKitFileProcessor.AudioAnalysisResult result = fileProcessor.analyzeAudio(
                invalidAudioBytes,
                "corrompido.mp3",
                ".mp3"
        );

        assertNotNull(result);
        assertTrue(result.waveform().isEmpty(), "Waveform deve ser vazia para arquivo inválido");
    }

    @Test
    void testExtractWaveformSynthetic16BitPcm() throws Exception {
        int sampleRate = 44100;
        int channels = 2;
        int durationSec = 2;
        int totalFrames = sampleRate * durationSec;
        byte[] data = new byte[totalFrames * channels * 2];

        // Gera uma onda senoidal crescente em amplitude
        for (int frame = 0; frame < totalFrames; frame++) {
            double amplitude = (double) frame / totalFrames; // de 0.0 a 1.0
            short sample = (short) (Math.sin(2 * Math.PI * 440 * frame / sampleRate) * amplitude * 32767);
            int offset = frame * 4;
            // Left
            data[offset] = (byte) (sample & 0xFF);
            data[offset + 1] = (byte) ((sample >> 8) & 0xFF);
            // Right
            data[offset + 2] = (byte) (sample & 0xFF);
            data[offset + 3] = (byte) ((sample >> 8) & 0xFF);
        }

        AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, sampleRate, 16, channels, 4, sampleRate, false);
        try (InputStream bais = new ByteArrayInputStream(data);
             AudioInputStream ais = new AudioInputStream(bais, format, totalFrames)) {

            List<Float> waveform = fileProcessor.extractWaveform(ais, 10);
            assertEquals(10, waveform.size());

            // Os primeiros valores devem ser menores que os últimos devido à amplitude crescente
            assertTrue(waveform.get(0) < waveform.get(9), "A waveform deve refletir amplitude crescente");
        }
    }
}
