package com.soniclab.soniclabbackend.soundkit.service;

import com.soniclab.soniclabbackend.soundkit.dto.response.SoundKitUploadResponse;
import com.soniclab.soniclabbackend.soundkit.entity.AudioDemo;
import com.soniclab.soniclabbackend.soundkit.storage.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ws.schild.jave.Encoder;
import ws.schild.jave.MultimediaObject;
import ws.schild.jave.encode.AudioAttributes;
import ws.schild.jave.encode.EncodingAttributes;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
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

/**
 * Processa pacotes (ZIP) e demos de áudio de sound kits.
 *
 * Suporte a MP3 (e outros formatos) na análise de waveform/duração depende de
 * providers do Java Sound (SPI) presentes no classpath.
 */
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

    private static final int DEFAULT_WAVEFORM_POINTS = 100;

    /** Tamanho (em frames) dos blocos lidos durante a análise em streaming. */
    private static final int ANALYSIS_BLOCK_FRAMES = 256;

    // Limites de proteção (ajuste conforme sua necessidade)
    private static final long MAX_DEMO_BYTES = 100L * 1024 * 1024;                  // 100 MB
    private static final int MAX_ZIP_ENTRIES = 1_000;
    private static final long MAX_ZIP_UNCOMPRESSED_BYTES = 2L * 1024 * 1024 * 1024; // 2 GB

    private final FileStorageService storageService;

    public SoundKitFileProcessor(FileStorageService storageService) {
        this.storageService = storageService;
    }

    // =====================================================================
    // PACOTE (PRIVADO) — retorna a KEY, não uma URL
    // =====================================================================

    /**
     * Processa um ZIP ou lista de arquivos soltos, valida e armazena o ZIP final consolidado.
     * O campo "fileUrl" do response agora contém a KEY no bucket (renomeie para fileKey no DTO).
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
                String key = storageService.upload(storagePath, is, totalBytes, "application/zip");
                return new SoundKitUploadResponse(key, totalBytes, totalSamples);
            }

        } catch (IOException e) {
            throw new RuntimeException("Erro ao processar pacote de áudio: " + e.getMessage(), e);
        } finally {
            deleteQuietly(tempZipFile);
        }
    }

    // =====================================================================
    // DEMO (PÚBLICO) — retorna URL pública, duração e waveform
    // =====================================================================


    /**
     * Processa a demonstração de áudio:
     * - Se o arquivo não for MP3 (ex: WAV longo de 30-80MB), converte para MP3 otimizado para web.
     * - Salva no storage (S3/local).
     * - Calcula a duração exata e extrai a waveform.
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

        Path tempInput = null;
        Path tempMp3 = null;
        try {
            tempInput = Files.createTempFile("demo-upload-", ext);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, tempInput, StandardCopyOption.REPLACE_EXISTING);
            }

            // Arquivo que será armazenado e analisado
            Path finalAudioFile;
            String contentType;
            String storageExt;

            if (".mp3".equals(ext)) {
                finalAudioFile = tempInput;
                contentType = "audio/mpeg";
                storageExt = ".mp3";
            } else {
                // Converte WAV (ou FLAC/AIFF) para MP3 192kbps para otimização de streaming web
                tempMp3 = Files.createTempFile("demo-transcoded-", ".mp3");
                convertToMp3(tempInput.toFile(), tempMp3.toFile());
                finalAudioFile = tempMp3;
                contentType = "audio/mpeg";
                storageExt = ".mp3";
            }

            long sizeBytes = Files.size(finalAudioFile);
            String storagePath = "soundkits/demos/" + UUID.randomUUID() + storageExt;

            String key;
            try (InputStream uploadStream = Files.newInputStream(finalAudioFile)) {
                key = storageService.upload(storagePath, uploadStream, sizeBytes, contentType);
            }
            String fileUrl = storageService.getPublicUrl(key);

            Integer durationInSeconds = null;
            List<Float> waveform = Collections.emptyList();

            // Analisa a duração e forma de onda no MP3 consolidado
            try (AudioInputStream raw = AudioSystem.getAudioInputStream(finalAudioFile.toFile())) {
                AudioAnalysis analysis = analyze(raw, DEFAULT_WAVEFORM_POINTS);
                durationInSeconds = analysis.durationInSeconds;
                waveform = analysis.waveform;
            } catch (Exception e) {
                log.warn("Falha ao analisar o áudio da demo {}", originalFilename, e);
            }

            return new AudioDemo(fileUrl, durationInSeconds, waveform);

        } catch (IOException e) {
            throw new RuntimeException("Erro ao processar demo de áudio: " + e.getMessage(), e);
        } finally {
            deleteQuietly(tempInput);
            deleteQuietly(tempMp3);
        }
    }

    /**
     * Converte qualquer formato de áudio (WAV 16/24/32-bit, AIFF, FLAC) para MP3 estéreo 192 kbps 44.1 kHz.
     */
    private void convertToMp3(File source, File target) {
        try {
            AudioAttributes audio = new AudioAttributes();
            audio.setCodec("libmp3lame");
            audio.setBitRate(192_000); // 192 kbps
            audio.setChannels(2);
            audio.setSamplingRate(44_100);

            EncodingAttributes attrs = new EncodingAttributes();
            attrs.setOutputFormat("mp3");
            attrs.setAudioAttributes(audio);

            Encoder encoder = new Encoder();
            encoder.encode(new MultimediaObject(source), target, attrs);
        } catch (Exception e) {
            throw new RuntimeException("Falha na conversão do áudio para MP3: " + e.getMessage(), e);
        }
    }

    // =====================================================================
    // ANÁLISE DE ÁUDIO (duração + waveform) — em streaming
    // =====================================================================

    /** Resultado da análise de um áudio. */
    private static final class AudioAnalysis {
        final Integer durationInSeconds;
        final List<Float> waveform;

        AudioAnalysis(Integer durationInSeconds, List<Float> waveform) {
            this.durationInSeconds = durationInSeconds;
            this.waveform = waveform;
        }
    }

    /** Mantido por compatibilidade: extrai apenas a waveform de um stream de áudio. */
    public List<Float> extractWaveform(AudioInputStream stream, int points)
            throws IOException, UnsupportedAudioFileException {
        return analyze(stream, points).waveform;
    }

    /**
     * Decodifica o áudio para PCM, lê em blocos pequenos (sem carregar tudo na memória),
     * conta os frames reais decodificados (assim a duração funciona mesmo em MP3/VBR,
     * onde getFrameLength() retorna -1) e calcula a waveform normalizada (0.0 a 1.0).
     */
    private AudioAnalysis analyze(AudioInputStream source, int points)
            throws IOException, UnsupportedAudioFileException {

        AudioInputStream pcm = toPcm(source);
        try {
            AudioFormat format = pcm.getFormat();

            int channels = Math.max(1, format.getChannels());
            int bitsPerSample = format.getSampleSizeInBits();
            int frameSize = format.getFrameSize();
            if (frameSize <= 0) {
                frameSize = channels * (bitsPerSample / 8);
                if (frameSize <= 0) frameSize = channels * 2;
            }

            byte[] buffer = new byte[ANALYSIS_BLOCK_FRAMES * frameSize];
            List<Double> blockSumSquares = new ArrayList<>();
            List<Integer> blockFrames = new ArrayList<>();
            long totalFrames = 0;

            while (true) {
                int read = pcm.readNBytes(buffer, 0, buffer.length);
                int frames = read / frameSize;
                if (frames <= 0) break;

                double sumSquares = 0.0;
                for (int f = 0; f < frames; f++) {
                    int frameOffset = f * frameSize;
                    for (int c = 0; c < channels; c++) {
                        float s = decodeSample(buffer, frameOffset, c, bitsPerSample, format);
                        sumSquares += (double) s * s;
                    }
                }

                blockSumSquares.add(sumSquares);
                blockFrames.add(frames);
                totalFrames += frames;

                if (read < buffer.length) break; // fim do stream
            }

            if (totalFrames <= 0) {
                return new AudioAnalysis(null, Collections.emptyList());
            }

            // Duração
            float rate = format.getFrameRate() > 0 ? format.getFrameRate() : format.getSampleRate();
            Integer duration = rate > 0 ? (int) Math.round(totalFrames / (double) rate) : null;

            // Agrupa os blocos em "points" buckets
            double[] bucketSum = new double[points];
            long[] bucketSamples = new long[points];
            long position = 0;
            for (int i = 0; i < blockFrames.size(); i++) {
                int frames = blockFrames.get(i);
                long mid = position + frames / 2;
                int bucket = (int) Math.min(points - 1L, (mid * points) / totalFrames);
                bucketSum[bucket] += blockSumSquares.get(i);
                bucketSamples[bucket] += (long) frames * channels;
                position += frames;
            }

            float[] rawRms = new float[points];
            float maxRms = 0.0f;
            for (int i = 0; i < points; i++) {
                float rms = bucketSamples[i] > 0
                        ? (float) Math.sqrt(bucketSum[i] / bucketSamples[i])
                        : 0.0f;
                rawRms[i] = rms;
                if (rms > maxRms) maxRms = rms;
            }

            // Normaliza entre 0.0 e 1.0 para maximizar o contraste visual
            List<Float> waveform = new ArrayList<>(points);
            for (float r : rawRms) {
                float normalized = maxRms > 0.0f ? (r / maxRms) : 0.0f;
                waveform.add(Math.round(normalized * 100.0f) / 100.0f);
            }

            return new AudioAnalysis(duration, waveform);

        } finally {
            if (pcm != source) {
                try {
                    pcm.close();
                } catch (IOException ignored) {
                    // o stream original é fechado pelo chamador
                }
            }
        }
    }

    /**
     * Converte o stream para PCM 16-bit little-endian quando necessário
     * (caso do MP3, que chega como MPEG). Lança UnsupportedAudioFileException
     * se não houver conversão disponível para um formato não-PCM.
     */
    private AudioInputStream toPcm(AudioInputStream source) throws UnsupportedAudioFileException {
        AudioFormat src = source.getFormat();
        AudioFormat.Encoding enc = src.getEncoding();

        boolean alreadyPcm16Le = AudioFormat.Encoding.PCM_SIGNED.equals(enc)
                && src.getSampleSizeInBits() == 16
                && !src.isBigEndian();
        if (alreadyPcm16Le) {
            return source;
        }

        int channels = src.getChannels();
        float sampleRate = src.getSampleRate();
        if (channels > 0 && sampleRate > 0) {
            AudioFormat target = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    sampleRate,
                    16,
                    channels,
                    channels * 2,
                    sampleRate,
                    false // little-endian
            );
            if (AudioSystem.isConversionSupported(target, src)) {
                return AudioSystem.getAudioInputStream(target, source);
            }
        }

        // Sem conversão: só aceitamos se já for PCM (o decodeSample cobre 8/16/24/32 bits)
        boolean isPcm = AudioFormat.Encoding.PCM_SIGNED.equals(enc)
                || AudioFormat.Encoding.PCM_UNSIGNED.equals(enc)
                || AudioFormat.Encoding.PCM_FLOAT.equals(enc);
        if (isPcm) {
            return source;
        }

        throw new UnsupportedAudioFileException(
                "Não há conversor para PCM disponível para o formato: " + src);
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
                // 8-bit PCM unsigned (0..255, centro em 128); signed usa o byte direto
                if (AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding())) {
                    return Math.abs(bytes[byteIndex] / 128.0f);
                }
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
                    val24 = ((bytes[byteIndex] & 0xFF) << 16) | ((bytes[byteIndex + 1] & 0xFF) << 8) | (bytes[byteIndex + 2] & 0xFF);
                } else {
                    val24 = ((bytes[byteIndex + 2] & 0xFF) << 16) | ((bytes[byteIndex + 1] & 0xFF) << 8) | (bytes[byteIndex] & 0xFF);
                }
                // Extensão de sinal para 32-bit
                if ((val24 & 0x800000) != 0) {
                    val24 |= 0xFF000000;
                }
                return Math.abs(val24 / 8388608.0f);

            case 32:
                if (AudioFormat.Encoding.PCM_FLOAT.equals(format.getEncoding())) {
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
                        val32 = ((bytes[byteIndex] & 0xFF) << 24) | ((bytes[byteIndex + 1] & 0xFF) << 16) |
                                ((bytes[byteIndex + 2] & 0xFF) << 8) | (bytes[byteIndex + 3] & 0xFF);
                    } else {
                        val32 = ((bytes[byteIndex + 3] & 0xFF) << 24) | ((bytes[byteIndex + 2] & 0xFF) << 16) |
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

    // =====================================================================
    // UTIL
    // =====================================================================

    private void deleteQuietly(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Não foi possível remover o arquivo temporário {}", path, e);
        }
    }
}