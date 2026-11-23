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

    @Override
    public String upload(String path, InputStream in, long contentLength, String contentType, FileVisibility visibility) {
        if (in == null) {
            throw new IllegalArgumentException("O fluxo de dados (InputStream) não pode ser nulo.");
        }
        String key = normalizeKey(path);
        Path target = resolveSafely(key);

        try (in) {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            return key;
        } catch (IOException e) {
            throw new RuntimeException("Falha ao salvar arquivo localmente: " + key, e);
        }
    }

    @Override
    public String upload(String path, InputStream in, long contentLength, String contentType) {
        return upload(path, in, contentLength, contentType, inferVisibility(path));
    }

    @Override
    public String getPublicUrl(String key) {
        return baseUrl + normalizeKey(key);
    }

    /** Em teste local, o "download temporário" é só a URL pública (ttl ignorado). */
    @Override
    public String generateDownloadUrl(String key) {
        return getPublicUrl(key);
    }

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

    @Override
    public boolean exists(String keyOrUrl) {
        if (keyOrUrl == null || keyOrUrl.isBlank()) {
            return false;
        }
        try {
            String key = extractKey(keyOrUrl);
            return Files.exists(resolveSafely(key));
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean isPublic(String keyOrUrl) {
        if (keyOrUrl == null || keyOrUrl.isBlank()) {
            return false;
        }
        try {
            String key = extractKey(keyOrUrl);
            return inferVisibility(key) == FileVisibility.PUBLIC;
        } catch (Exception e) {
            return false;
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
        if (keyOrUrl == null || keyOrUrl.isBlank()) {
            throw new IllegalArgumentException("O caminho do arquivo não pode ser vazio.");
        }
        String trimmed = keyOrUrl.trim();
        int queryIndex = trimmed.indexOf('?');
        String urlWithoutQuery = queryIndex >= 0 ? trimmed.substring(0, queryIndex) : trimmed;

        if (urlWithoutQuery.startsWith(baseUrl)) {
            return normalizeKey(urlWithoutQuery.substring(baseUrl.length()));
        }
        if (urlWithoutQuery.startsWith("http://") || urlWithoutQuery.startsWith("https://")) {
            return normalizeKey(URI.create(urlWithoutQuery).getPath());
        }
        return normalizeKey(urlWithoutQuery);
    }

    private String normalizeKey(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("O caminho do arquivo não pode ser vazio.");
        }
        if (path.contains("\0")) {
            throw new IllegalArgumentException("Caminho contém caracteres nulos inválidos: " + path);
        }
        String key = path.replace("\\", "/");
        while (key.startsWith("/")) key = key.substring(1);
        key = key.replaceAll("/+", "/");

        for (String segment : key.split("/")) {
            if (segment.equals("..") || segment.equals(".")) {
                throw new IllegalArgumentException("Caminho inválido para o storage: " + path);
            }
        }
        return key;
    }
}
