package com.soniclab.soniclabbackend.soundkit.service;

import com.soniclab.soniclabbackend.soundkit.dto.response.SoundKitUploadResponse;
import com.soniclab.soniclabbackend.soundkit.entity.AudioDemo;
import com.soniclab.soniclabbackend.soundkit.storage.FileStorageService;
import com.soniclab.soniclabbackend.soundkit.storage.FileVisibility;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Service
public class SoundKitFileProcessor {

    private static final Logger log = LoggerFactory.getLogger(SoundKitFileProcessor.class);

    /** Extensões permitidas dentro do pacote do kit. */
    private static final Set<String> PACKAGE_EXTENSIONS = Set.of(
            "wav", "mp3", "aiff", "flac", "ogg", "mid", "midi", "fxp", "nki"
    );

    /** Extensões permitidas para o áudio de demonstração (pré-escuta). */
    private static final Set<String> DEMO_EXTENSIONS = Set.of(
            "wav", "mp3", "aiff", "flac", "ogg"
    );

    public static final int DEFAULT_WAVEFORM_POINTS = 100;

    // Limites de proteção
    private static final long MAX_DEMO_BYTES = 100L * 1024 * 1024;                  // 100 MB
    private static final int MAX_ZIP_ENTRIES = 1_000;
    private static final long MAX_ZIP_UNCOMPRESSED_BYTES = 2L * 1024 * 1024 * 1024; // 2 GB

    private final FileStorageService storageService;

    public SoundKitFileProcessor(FileStorageService storageService) {
        this.storageService = storageService;
    }

    public record AudioAnalysisResult(Integer durationInSeconds, List<Float> waveform) {}

    // =====================================================================
    // PACOTE (PRIVADO) — armazena com visibilidade PRIVATE e retorna a KEY
    // =====================================================================

    /**
     * Processa um ZIP ou lista de arquivos soltos, valida e armazena o ZIP final consolidado.
     * Retorna a chave persistente no storage (fileKey / fileUrl).
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
                totalSamples = copyAndInspectZip(files.get(0), tempZipFile);
            } else {
                totalSamples = packageFilesIntoZip(files, tempZipFile);
            }

            if (totalSamples == 0) {
                throw new IllegalArgumentException("O pacote não contém nenhum arquivo de áudio válido.");
            }

            long totalBytes = Files.size(tempZipFile);
            String storagePath = "soundkits/packages/" + UUID.randomUUID() + ".zip";

            try (InputStream is = Files.newInputStream(tempZipFile)) {
                String key = storageService.upload(storagePath, is, totalBytes, "application/zip", FileVisibility.PRIVATE);
                return new SoundKitUploadResponse(key, key, totalBytes, totalSamples);
            }

        } catch (IOException e) {
            throw new RuntimeException("Erro ao processar pacote de áudio: " + e.getMessage(), e);
        } finally {
            if (tempZipFile != null) {
                try {
                    Files.deleteIfExists(tempZipFile);
                } catch (IOException e) {
                    log.warn("Não foi possível remover o arquivo temporário {}", tempZipFile, e);
                }
            }
        }
    }

    // =====================================================================
    // DEMO (PÚBLICO) — retorna URL pública, duração e waveform
    // =====================================================================

    /**
     * Processa a demonstração de áudio: salva no storage com visibilidade pública,
     * decodifica o áudio (suportando MP3 e WAV), calcula duração e extrai a waveform.
     */
    public AudioDemo processAudioDemo(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Arquivo de demonstração de áudio não pode ser vazio.");
        }
        if (file.getSize() > MAX_DEMO_BYTES) {
            throw new IllegalArgumentException(
                    "O áudio de demonstração excede o limite de " + (MAX_DEMO_BYTES / 1024 / 1024) + " MB.");
        }

        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "demo.wav";
        validateExtension(originalFilename, DEMO_EXTENSIONS);

        String ext = originalFilename.substring(originalFilename.lastIndexOf('.')).toLowerCase(Locale.ROOT);
        String storagePath = "soundkits/demos/" + UUID.randomUUID() + ext;
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";

        try {
            // Lê uma única vez; os bytes servem tanto para upload quanto para análise
            byte[] audioBytes = file.getBytes();

            String key;
            try (InputStream uploadStream = new ByteArrayInputStream(audioBytes)) {
                key = storageService.upload(storagePath, uploadStream, audioBytes.length, contentType, FileVisibility.PUBLIC);
            }
            String fileUrl = storageService.getPublicUrl(key);

            // Decodificação real e análise de amplitude (suporte completo a MP3 e WAV)
            AudioAnalysisResult analysis = analyzeAudio(audioBytes, originalFilename, ext);

            return new AudioDemo(fileUrl, analysis.durationInSeconds(), analysis.waveform());

        } catch (IOException e) {
            throw new RuntimeException("Erro ao processar demo de áudio: " + e.getMessage(), e);
        }
    }

    /**
     * Roteia a análise do áudio para o decodificador adequado (JLayer para MP3, Java Sound para WAV/PCM).
     * Inclui mecanismo de fallback bidirecional para arquivos com extensões divergentes.
     */
    public AudioAnalysisResult analyzeAudio(byte[] audioBytes, String filename, String ext) {
        boolean isMp3 = ".mp3".equalsIgnoreCase(ext)
                || (filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".mp3"));

        if (isMp3) {
            try {
                return analyzeMp3(audioBytes);
            } catch (Exception e) {
                log.warn("Falha na decodificação MP3 via JLayer para '{}', tentando decodificação alternativa via Java Sound: {}", filename, e.getMessage());
                try {
                    return analyzeViaAudioSystem(audioBytes);
                } catch (Exception ex) {
                    log.warn("Formato de áudio sem suporte para análise: {}", filename);
                    return new AudioAnalysisResult(null, Collections.emptyList());
                }
            }
        } else {
            try {
                return analyzeViaAudioSystem(audioBytes);
            } catch (UnsupportedAudioFileException e) {
                // Caso seja um MP3 renomeado com outra extensão (ex: .wav)
                try {
                    return analyzeMp3(audioBytes);
                } catch (Exception ex) {
                    log.warn("Formato de áudio sem suporte para análise: {}", filename);
                    return new AudioAnalysisResult(null, Collections.emptyList());
                }
            } catch (Exception e) {
                log.warn("Falha ao analisar o áudio da demo {}", filename, e);
                return new AudioAnalysisResult(null, Collections.emptyList());
            }
        }
    }

    /**
     * Decodifica áudio MP3 (MPEG Layer 3) diretamente para PCM utilizando JLayer,
     * calcula a duração exata e extrai a forma de onda normalizada.
     */
    public AudioAnalysisResult analyzeMp3(byte[] audioBytes) throws Exception {
        Bitstream bitstream = new Bitstream(new ByteArrayInputStream(audioBytes));
        Decoder decoder = new Decoder();
        Header frameHeader;
        int sampleRate = 0;
        int channels = 0;
        List<short[]> pcmChunks = new ArrayList<>();
        int totalSamples = 0;

        try {
            while ((frameHeader = bitstream.readFrame()) != null) {
                SampleBuffer output = (SampleBuffer) decoder.decodeFrame(frameHeader, bitstream);
                if (sampleRate == 0) {
                    sampleRate = output.getSampleFrequency();
                    channels = output.getChannelCount();
                }
                short[] buffer = output.getBuffer();
                int len = output.getBufferLength();
                if (len > 0) {
                    short[] chunk = new short[len];
                    System.arraycopy(buffer, 0, chunk, 0, len);
                    pcmChunks.add(chunk);
                    totalSamples += len;
                }
                bitstream.closeFrame();
            }
        } finally {
            try {
                bitstream.close();
            } catch (Exception ignored) {
            }
        }

        if (totalSamples == 0 || channels <= 0 || sampleRate <= 0) {
            throw new IllegalArgumentException("Nenhum frame de áudio decodificado do arquivo MP3.");
        }

        int totalFrames = totalSamples / channels;
        int durationInSeconds = (int) Math.round((double) totalFrames / sampleRate);

        // Consolida todas as amostras PCM 16-bit
        short[] allSamples = new short[totalSamples];
        int destPos = 0;
        for (short[] chunk : pcmChunks) {
            System.arraycopy(chunk, 0, allSamples, destPos, chunk.length);
            destPos += chunk.length;
        }

        List<Float> waveform = calculateWaveformFromPcm16(allSamples, totalFrames, channels, DEFAULT_WAVEFORM_POINTS);
        return new AudioAnalysisResult(durationInSeconds, waveform);
    }

    /**
     * Analisa áudio PCM nativo (WAV, AIFF) via Java Sound AudioSystem.
     */
    public AudioAnalysisResult analyzeViaAudioSystem(byte[] audioBytes) throws Exception {
        try (AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(
                new BufferedInputStream(new ByteArrayInputStream(audioBytes)))) {

            AudioFormat format = audioInputStream.getFormat();
            long frames = audioInputStream.getFrameLength();
            Integer durationInSeconds = null;
            if (frames > 0 && format.getFrameRate() > 0) {
                durationInSeconds = (int) Math.round((double) frames / format.getFrameRate());
            }

            List<Float> waveform = extractWaveform(audioInputStream, DEFAULT_WAVEFORM_POINTS);

            return new AudioAnalysisResult(durationInSeconds, waveform);
        }
    }

    /**
     * Calcula o waveform a partir de um array PCM 16-bit contínuo, dividindo em buckets
     * de amplitude RMS e normalizando o pico máximo entre 0.0 e 1.0.
     */
    public List<Float> calculateWaveformFromPcm16(short[] samples, int totalFrames, int channels, int points) {
        if (totalFrames <= 0 || points <= 0 || samples.length == 0) {
            return Collections.emptyList();
        }

        int framesPerBucket = Math.max(1, totalFrames / points);
        float[] rawRms = new float[points];
        float maxRms = 0.0f;

        for (int i = 0; i < points; i++) {
            int startFrame = i * framesPerBucket;
            int endFrame = Math.min(startFrame + framesPerBucket, totalFrames);
            double sumSquares = 0.0;
            int sampleCount = 0;

            for (int f = startFrame; f < endFrame; f++) {
                int offset = f * channels;
                for (int c = 0; c < channels; c++) {
                    int idx = offset + c;
                    if (idx < samples.length) {
                        float normalized = Math.abs(samples[idx] / 32768.0f);
                        sumSquares += (double) normalized * normalized;
                        sampleCount++;
                    }
                }
            }

            float rms = sampleCount > 0 ? (float) Math.sqrt(sumSquares / sampleCount) : 0.0f;
            rawRms[i] = rms;
            if (rms > maxRms) {
                maxRms = rms;
            }
        }

        List<Float> waveform = new ArrayList<>(points);
        for (float r : rawRms) {
            float normalized = maxRms > 0.0f ? (r / maxRms) : 0.0f;
            waveform.add(Math.round(normalized * 100.0f) / 100.0f);
        }

        return waveform;
    }

    // =====================================================================
    // WAVEFORM (AudioInputStream para WAV / AIFF)
    // =====================================================================

    public List<Float> extractWaveform(AudioInputStream stream, int points) throws IOException {
        AudioFormat srcFormat = stream.getFormat();

        // Converte para 16-bit PCM little-endian quando suportado
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

        // Normaliza entre 0.0 e 1.0 para maximizar o contraste visual
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
                // Extensão de sinal para 32-bit
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
                // Fallback para 16-bit little-endian
                short fb = (short) ((bytes[byteIndex + 1] << 8) | (bytes[byteIndex] & 0xFF));
                return Math.abs(fb / 32768.0f);
        }
    }

    // =====================================================================
    // ZIP
    // =====================================================================

    private boolean isZipFile(MultipartFile file) {
        String name = file.getOriginalFilename();
        return name != null && name.toLowerCase(Locale.ROOT).endsWith(".zip");
    }

    /**
     * Copia o ZIP enviado para o arquivo temporário e então inspeciona a cópia,
     * validando nomes das entradas (zip slip), extensões e limites (zip bomb).
     */
    private int copyAndInspectZip(MultipartFile sourceZip, Path destination) throws IOException {
        try (InputStream in = sourceZip.getInputStream()) {
            Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
        }

        int count = 0;
        int entries = 0;
        long totalUncompressed = 0;
        byte[] buffer = new byte[8192];

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(destination))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (++entries > MAX_ZIP_ENTRIES) {
                    throw new IllegalArgumentException(
                            "O ZIP excede o limite de " + MAX_ZIP_ENTRIES + " entradas.");
                }

                String name = entry.getName();
                validateEntryName(name);

                if (entry.isDirectory() || isIgnorableEntry(name)) {
                    continue;
                }

                validateExtension(name, PACKAGE_EXTENSIONS);

                // Conta os bytes realmente descompactados (o tamanho do header pode ser falso)
                int read;
                while ((read = zis.read(buffer)) != -1) {
                    totalUncompressed += read;
                    if (totalUncompressed > MAX_ZIP_UNCOMPRESSED_BYTES) {
                        throw new IllegalArgumentException(
                                "O conteúdo descompactado do ZIP excede o limite permitido.");
                    }
                }
                count++;
            }
        }
        return count;
    }

    private int packageFilesIntoZip(List<MultipartFile> files, Path targetZip) throws IOException {
        int count = 0;
        Set<String> usedNames = new HashSet<>();

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(targetZip))) {
            for (MultipartFile file : files) {
                String filename = file.getOriginalFilename();
                if (filename == null || filename.isBlank()) {
                    continue;
                }

                // Mantém só o nome base (remove qualquer caminho enviado pelo cliente)
                String baseName = filename.replace("\\", "/");
                baseName = baseName.substring(baseName.lastIndexOf('/') + 1);

                validateExtension(baseName, PACKAGE_EXTENSIONS);

                // Evita ZipException por nomes duplicados
                String entryName = baseName;
                int suffix = 1;
                while (!usedNames.add(entryName)) {
                    int dot = baseName.lastIndexOf('.');
                    entryName = baseName.substring(0, dot) + "-" + suffix++ + baseName.substring(dot);
                }

                zos.putNextEntry(new ZipEntry(entryName));
                try (InputStream in = file.getInputStream()) {
                    in.transferTo(zos);
                }
                zos.closeEntry();
                count++;
            }
        }
        return count;
    }

    /** Rejeita caminhos absolutos e segmentos ".." (zip slip). */
    private void validateEntryName(String name) {
        String normalized = name.replace("\\", "/");
        if (normalized.startsWith("/") || normalized.matches("^[A-Za-z]:.*")) {
            throw new IllegalArgumentException("Caminho absoluto não permitido no ZIP: " + name);
        }
        for (String segment : normalized.split("/")) {
            if (segment.equals("..")) {
                throw new IllegalArgumentException("Caminho inválido no ZIP: " + name);
            }
        }
    }

    /** Metadados que macOS/Windows adicionam automaticamente e que não são samples. */
    private boolean isIgnorableEntry(String name) {
        String normalized = name.replace("\\", "/");
        String base = normalized.substring(normalized.lastIndexOf('/') + 1);
        return normalized.startsWith("__MACOSX/")
                || base.equals(".DS_Store")
                || base.equals("Thumbs.db")
                || base.startsWith("._");
    }

    private void validateExtension(String filename, Set<String> allowed) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1) {
            throw new IllegalArgumentException("Arquivo sem extensão não permitido: " + filename);
        }
        String ext = filename.substring(lastDot + 1).toLowerCase(Locale.ROOT);
        if (!allowed.contains(ext)) {
            throw new IllegalArgumentException("Extensão não permitida (" + ext + ") no arquivo: " + filename);
        }
    }
}
