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

    public List<Float> extractWaveform(AudioInputStream stream, int points) throws IOException {
        AudioFormat srcFormat = stream.getFormat();

        // Converte para 16-bit PCM little-endian se necessário e suportado (compatível com 24-bit, 32-bit float, etc.)
        AudioInputStream pcmStream = stream;
        AudioFormat targetFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                srcFormat.getSampleRate(),
                16,
                srcFormat.getChannels(),
                srcFormat.getChannels() * 2,
                srcFormat.getSampleRate(),
                false // little-endian
        );

        if (AudioSystem.isConversionSupported(targetFormat, srcFormat)) {
            pcmStream = AudioSystem.getAudioInputStream(targetFormat, stream);
        }

        AudioFormat format = pcmStream.getFormat();
        int bytesPerFrame = format.getFrameSize();
        if (bytesPerFrame <= 0) {
            bytesPerFrame = format.getChannels() * (format.getSampleSizeInBits() / 8);
            if (bytesPerFrame <= 0) bytesPerFrame = 2;
        }

        byte[] allBytes = pcmStream.readAllBytes();
        int totalFrames = allBytes.length / bytesPerFrame;
        if (totalFrames <= 0) return Collections.emptyList();

        int framesPerBucket = Math.max(1, totalFrames / points);
        int channels = Math.max(1, format.getChannels());
        int sampleSizeInBits = format.getSampleSizeInBits();

        float[] rawRms = new float[points];
        float maxRms = 0.0f;

        for (int i = 0; i < points; i++) {
            int startFrame = i * framesPerBucket;
            int endFrame = Math.min(startFrame + framesPerBucket, totalFrames);
            double sumSquares = 0.0;
            int sampleCount = 0;

            for (int f = startFrame; f < endFrame; f++) {
                int frameOffset = f * bytesPerFrame;

                for (int c = 0; c < channels; c++) {
                    float normalized = decodeSample(allBytes, frameOffset, c, sampleSizeInBits, format);
                    sumSquares += (double) normalized * normalized;
                    sampleCount++;
                }
            }

            float rms = sampleCount > 0 ? (float) Math.sqrt(sumSquares / sampleCount) : 0.0f;
            rawRms[i] = rms;
            if (rms > maxRms) {
                maxRms = rms;
            }
        }

        // Normaliza os valores de RMS entre 0.0 e 1.0 para maximizar o contraste visual dinâmico
        List<Float> waveform = new ArrayList<>(points);
        for (float r : rawRms) {
            float normalized = maxRms > 0.0f ? (r / maxRms) : 0.0f;
            waveform.add(Math.round(normalized * 100.0f) / 100.0f);
        }

        return waveform;
    }

    private float decodeSample(byte[] bytes, int frameOffset, int channel, int bitsPerSample, AudioFormat format) {
        int bytesPerSample = bitsPerSample / 8;
        if (bytesPerSample <= 0) bytesPerSample = 2;
        int byteIndex = frameOffset + (channel * bytesPerSample);

        if (byteIndex + bytesPerSample > bytes.length) {
            return 0.0f;
        }

        boolean isBigEndian = format.isBigEndian();

        switch (bitsPerSample) {
            case 8:
                // 8-bit PCM é unsigned no Java Sound (0..255, centro em 128)
                int val8 = bytes[byteIndex] & 0xFF;
                return Math.abs((val8 - 128) / 128.0f);

            case 16:
                short val16;
                if (isBigEndian) {
                    val16 = (short) ((bytes[byteIndex] << 8) | (bytes[byteIndex + 1] & 0xFF));
                } else {
                    val16 = (short) ((bytes[byteIndex + 1] << 8) | (bytes[byteIndex] & 0xFF));
                }
                return Math.abs(val16 / 32768.0f);

            case 24:
                int val24;
                if (isBigEndian) {
                    val24 = (bytes[byteIndex] << 16) | ((bytes[byteIndex + 1] & 0xFF) << 8) | (bytes[byteIndex + 2] & 0xFF);
                } else {
                    val24 = (bytes[byteIndex + 2] << 16) | ((bytes[byteIndex + 1] & 0xFF) << 8) | (bytes[byteIndex] & 0xFF);
                }
                // Extensão de sinal para 32-bit (o 24º bit é o sinal)
                if ((val24 & 0x800000) != 0) {
                    val24 |= 0xFF000000;
                }
                return Math.abs(val24 / 8388608.0f);

            case 32:
                if (format.getEncoding() == AudioFormat.Encoding.PCM_FLOAT) {
                    int intBits;
                    if (isBigEndian) {
                        intBits = ((bytes[byteIndex] & 0xFF) << 24) | ((bytes[byteIndex + 1] & 0xFF) << 16) |
                                  ((bytes[byteIndex + 2] & 0xFF) << 8) | (bytes[byteIndex + 3] & 0xFF);
                    } else {
                        intBits = ((bytes[byteIndex + 3] & 0xFF) << 24) | ((bytes[byteIndex + 2] & 0xFF) << 16) |
                                  ((bytes[byteIndex + 1] & 0xFF) << 8) | (bytes[byteIndex] & 0xFF);
                    }
                    return Math.min(1.0f, Math.abs(Float.intBitsToFloat(intBits)));
                } else {
                    int val32;
                    if (isBigEndian) {
                        val32 = (bytes[byteIndex] << 24) | ((bytes[byteIndex + 1] & 0xFF) << 16) |
                                ((bytes[byteIndex + 2] & 0xFF) << 8) | (bytes[byteIndex + 3] & 0xFF);
                    } else {
                        val32 = (bytes[byteIndex + 3] << 24) | ((bytes[byteIndex + 2] & 0xFF) << 16) |
                                ((bytes[byteIndex + 1] & 0xFF) << 8) | (bytes[byteIndex] & 0xFF);
                    }
                    return Math.abs((float) (val32 / 2147483648.0));
                }

            default:
                // Fallback para 16-bit
                short fb = (short) ((bytes[byteIndex + 1] << 8) | (bytes[byteIndex] & 0xFF));
                return Math.abs(fb / 32768.0f);
        }
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
