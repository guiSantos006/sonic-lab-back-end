package com.soniclab.soniclabbackend.soundkit.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

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
    public String upload(String path, InputStream inputStream, String contentType) {
        try {
            Path targetLocation = this.rootLocation.resolve(path).normalize();

            // Proteção contra Directory Traversal
            if (!targetLocation.startsWith(this.rootLocation)) {
                throw new SecurityException("Tentativa de gravação fora do diretório permitido.");
            }

            Files.createDirectories(targetLocation.getParent());
            Files.copy(inputStream, targetLocation, StandardCopyOption.REPLACE_EXISTING);

            return this.baseUrl + path.replace("\\", "/");
        } catch (IOException e) {
            throw new RuntimeException("Falha ao salvar arquivo localmente: " + path, e);
        }
    }

    @Override
    public void delete(String fileUrl) {
        try {
            String relativePath = fileUrl.replace(this.baseUrl, "");
            Path targetFile = this.rootLocation.resolve(relativePath).normalize();
            Files.deleteIfExists(targetFile);
        } catch (IOException e) {
            throw new RuntimeException("Falha ao deletar arquivo local: " + fileUrl, e);
        }
    }
}
