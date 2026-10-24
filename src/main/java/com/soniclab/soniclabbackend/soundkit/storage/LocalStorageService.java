package com.soniclab.soniclabbackend.soundkit.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

/**
 * Storage em disco, apenas para desenvolvimento/testes.
 * Não há privacidade real: generateDownloadUrl devolve a mesma URL pública.
 */
@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements FileStorageService {

    private final Path rootLocation;
    private final String baseUrl;

    public LocalStorageService(
            @Value("${storage.local.dir:./uploads}") String uploadDir,
            @Value("${storage.local.base-url:http://localhost:8080/uploads/}") String baseUrl
    ) {
        this.rootLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        try {
            Files.createDirectories(this.rootLocation);
        } catch (IOException e) {
            throw new RuntimeException("Não foi possível inicializar o diretório de uploads local", e);
        }
    }

    /** Salva o arquivo e retorna a KEY (caminho relativo), igual ao S3. */
    @Override
    public String upload(String path, InputStream in, long contentLength, String contentType) {
        String key = normalizeKey(path);
        Path target = resolveSafely(key);

        try (in) {
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            return key;
        } catch (IOException e) {
            throw new RuntimeException("Falha ao salvar arquivo localmente: " + key, e);
        }
    }

    @Override
    public String getPublicUrl(String key) {
        return baseUrl + normalizeKey(key);
    }

    /** Em teste local, o "download temporário" é só a URL pública (ttl ignorado). */
    @Override
    public String generateDownloadUrl(String key, Duration ttl) {
        return getPublicUrl(key);
    }

    @Override
    public void delete(String keyOrUrl) {
        String key = extractKey(keyOrUrl);
        try {
            Files.deleteIfExists(resolveSafely(key));
        } catch (IOException e) {
            throw new RuntimeException("Falha ao deletar arquivo local: " + key, e);
        }
    }

    private Path resolveSafely(String key) {
        Path target = rootLocation.resolve(key).normalize();
        if (!target.startsWith(rootLocation)) {
            throw new SecurityException("Tentativa de acesso fora do diretório permitido.");
        }
        return target;
    }

    private String extractKey(String keyOrUrl) {
        if (keyOrUrl.startsWith(baseUrl)) {
            return normalizeKey(keyOrUrl.substring(baseUrl.length()));
        }
        if (keyOrUrl.startsWith("http://") || keyOrUrl.startsWith("https://")) {
            return normalizeKey(URI.create(keyOrUrl).getPath());
        }
        return normalizeKey(keyOrUrl);
    }

    private String normalizeKey(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("O caminho do arquivo não pode ser vazio.");
        }
        String key = path.replace("\\", "/");
        while (key.startsWith("/")) key = key.substring(1);

        for (String segment : key.split("/")) {
            if (segment.equals("..") || segment.equals(".")) {
                throw new IllegalArgumentException("Caminho inválido para o storage: " + path);
            }
        }
        return key;
    }
}