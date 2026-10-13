package com.soniclab.soniclabbackend.soundkit.service;

import com.soniclab.soniclabbackend.soundkit.storage.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SoundKitFileProcessorTest {

    private SoundKitFileProcessor fileProcessor;

    @BeforeEach
    void setUp() {
        FileStorageService storageService = Mockito.mock(FileStorageService.class);
        fileProcessor = new SoundKitFileProcessor(storageService);
    }

    @Test
    void testExtractWaveformWith24BitAudioDemo() throws Exception {
        File file = new File("uploads/soundkits/demos/fa56217c-0084-409f-8825-b2135f673735.wav");
        if (!file.exists()) {
            return;
        }

        try (AudioInputStream ais = AudioSystem.getAudioInputStream(file)) {
            List<Float> waveform = fileProcessor.extractWaveform(ais, 100);

            assertNotNull(waveform);
            assertEquals(100, waveform.size());

            // Verifica que NÃO são todos 1.0 (o problema reportado pelo usuário)
            long countOnes = waveform.stream().filter(v -> v == 1.0f).count();
            assertTrue(countOnes < 10, "A waveform não deve estar saturada em 1.0 (encontrado: " + countOnes + " vezes 1.0)");

            // Verifica que há variação dinâmica real
            float min = waveform.stream().min(Float::compareTo).orElse(0.0f);
            float max = waveform.stream().max(Float::compareTo).orElse(0.0f);
            assertTrue(max > min, "Deve haver variação dinâmica na forma de onda");
            assertTrue(max >= 0.8f, "O ponto mais alto deve atingir nível audível");
        }
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
