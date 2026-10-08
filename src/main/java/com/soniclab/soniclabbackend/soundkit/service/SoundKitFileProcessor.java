package com.soniclab.soniclabbackend.soundkit.service;

import com.soniclab.soniclabbackend.soundkit.dto.response.SoundKitUploadResponse;
import com.soniclab.soniclabbackend.soundkit.entity.AudioDemo;
import com.soniclab.soniclabbackend.soundkit.storage.FileStorageService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Service
public class SoundKitFileProcessor {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "wav", "mp3", "aiff", "flac", "ogg", "mid", "midi", "fxp", "nki"
    );

    private static final int DEFAULT_WAVEFORM_POINTS = 100;

    private final FileStorageService storageService;

    public SoundKitFileProcessor(FileStorageService storageService) {
        this.storageService = storageService;
    }

    /**
     * Processa um arquivo ZIP ou lista de arquivos soltos, valida e armazena o ZIP final consolidado.
     */
    public SoundKitUploadResponse processAndStorePackage(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("Nenhum arquivo enviado.");
        }

        Path tempZipFile = null;
        try {
            int totalSamples;
            tempZipFile = Files.createTempFile("kit-upload-", ".zip");

            if (files.size() == 1 && isZipFile(files.get(0))) {
                totalSamples = inspectAndCopyZip(files.get(0), tempZipFile);
            } else {
                totalSamples = packageFilesIntoZip(files, tempZipFile);
            }

            long totalBytes = Files.size(tempZipFile);
            String storagePath = "soundkits/packages/" + UUID.randomUUID() + ".zip";

            try (InputStream is = Files.newInputStream(tempZipFile)) {
                String fileUrl = storageService.upload(storagePath, is, "application/zip");
                return new SoundKitUploadResponse(fileUrl, totalBytes, totalSamples);
            }

        } catch (IOException e) {
            throw new RuntimeException("Erro ao processar pacote de áudio: " + e.getMessage(), e);
        } finally {
            if (tempZipFile != null) {
                try {
                    Files.deleteIfExists(tempZipFile);
                } catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * Processa a demonstração de áudio: salva no storage, calcula duração e extrai pontos da forma de onda.
     */
    public AudioDemo processAudioDemo(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Arquivo de demonstração de áudio não pode ser vazio.");
        }

        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "demo.wav";
        validateExtension(originalFilename);

        String ext = originalFilename.contains(".") ? originalFilename.substring(originalFilename.lastIndexOf(".")) : ".wav";
        String storagePath = "soundkits/demos/" + UUID.randomUUID() + ext;

        try {
            // Salva no storage
            String fileUrl = storageService.upload(storagePath, file.getInputStream(), file.getContentType());

            // Analisa áudio (duração e waveform)
            Integer durationInSeconds = null;
            List<Float> waveform = Collections.emptyList();

            try {
                byte[] audioBytes = file.getBytes();
                try (InputStream is = new ByteArrayInputStream(audioBytes);
                     AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(new BufferedInputStream(is))) {

                    AudioFormat format = audioInputStream.getFormat();
                    long frames = audioInputStream.getFrameLength();
                    if (frames > 0 && format.getFrameRate() > 0) {
                        durationInSeconds = (int) Math.round((double) frames / format.getFrameRate());
                    }

                    waveform = extractWaveform(audioInputStream, DEFAULT_WAVEFORM_POINTS);
                }
            } catch (Exception ignored) {
                // Caso seja formato como MP3 que não possui codec nativo no Java padrão,
                // a URL continua válida e o frontend pode fornecer ou complementar a waveform
            }

            return new AudioDemo(fileUrl, durationInSeconds, waveform);

        } catch (IOException e) {
            throw new RuntimeException("Erro ao processar demo de áudio: " + e.getMessage(), e);
        }
    }

    private List<Float> extractWaveform(AudioInputStream stream, int points) throws IOException {
        AudioFormat format = stream.getFormat();
        int bytesPerFrame = format.getFrameSize();
        if (bytesPerFrame <= 0) bytesPerFrame = 2;

        byte[] allBytes = stream.readAllBytes();
        int totalFrames = allBytes.length / bytesPerFrame;
        if (totalFrames <= 0) return Collections.emptyList();

        int framesPerBucket = Math.max(1, totalFrames / points);
        List<Float> waveform = new ArrayList<>(points);

        for (int i = 0; i < points; i++) {
            int startFrame = i * framesPerBucket;
            int endFrame = Math.min(startFrame + framesPerBucket, totalFrames);
            float maxAmplitude = 0.0f;

            for (int f = startFrame; f < endFrame; f++) {
                int byteIndex = f * bytesPerFrame;
                if (byteIndex + 1 < allBytes.length) {
                    // Amostra de 16-bit PCM little-endian
                    short sample = (short) ((allBytes[byteIndex + 1] << 8) | (allBytes[byteIndex] & 0xFF));
                    float normalized = Math.abs(sample) / 32768.0f;
                    if (normalized > maxAmplitude) {
                        maxAmplitude = normalized;
                    }
                }
            }

            // Arredonda para 2 casas decimais
            waveform.add(Math.round(maxAmplitude * 100.0f) / 100.0f);
        }

        return waveform;
    }

    private boolean isZipFile(MultipartFile file) {
        String name = file.getOriginalFilename();
        return name != null && name.toLowerCase().endsWith(".zip");
    }

    private int inspectAndCopyZip(MultipartFile sourceZip, Path destination) throws IOException {
        int count = 0;
        try (ZipInputStream zis = new ZipInputStream(sourceZip.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    validateExtension(entry.getName());
                    count++;
                }
            }
        }
        Files.copy(sourceZip.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
        return count;
    }

    private int packageFilesIntoZip(List<MultipartFile> files, Path targetZip) throws IOException {
        int count = 0;
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(targetZip))) {
            for (MultipartFile file : files) {
                String filename = file.getOriginalFilename();
                if (filename == null || filename.isBlank()) {
                    continue;
                }

                validateExtension(filename);

                ZipEntry entry = new ZipEntry(filename);
                zos.putNextEntry(entry);
                file.getInputStream().transferTo(zos);
                zos.closeEntry();
                count++;
            }
        }
        return count;
    }

    private void validateExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1) {
            throw new IllegalArgumentException("Arquivo sem extensão não permitido: " + filename);
        }
        String ext = filename.substring(lastDot + 1).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("Extensão não permitida (" + ext + ") no arquivo: " + filename);
        }
    }
}
