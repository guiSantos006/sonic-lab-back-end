package com.soniclab.soniclabbackend.soundkit.storage;

import java.io.InputStream;
import java.time.Duration;

/**
 * Abstração de armazenamento de arquivos (Local, AWS S3, etc.).
 */
public interface FileStorageService {

    /**
     * Envia o arquivo especificando a visibilidade (público ou privado) e retorna a KEY (caminho no storage/bucket).
     */
    String upload(String path, InputStream in, long contentLength, String contentType, FileVisibility visibility);

    /**
     * Envia o arquivo e retorna a KEY (caminho no storage/bucket).
     * Mantido para retrocompatibilidade; a visibilidade é inferida a partir do caminho do arquivo.
     */
    default String upload(String path, InputStream in, long contentLength, String contentType) {
        return upload(path, in, contentLength, contentType, inferVisibility(path));
    }

    /**
     * Retorna a URL permanente, exclusiva para arquivos públicos (capas, demos).
     * Lança exceção se invocado para arquivos privados.
     */
    String getPublicUrl(String key);

    /**
     * Gera uma URL temporária com prazo de expiração (TTL) padrão configurado no serviço.
     */
    String generateDownloadUrl(String key);

    /**
     * Gera uma URL temporária com prazo de expiração (TTL) customizado, indicada para arquivos privados.
     */
    String generateDownloadUrl(String key, Duration ttl);

    /**
     * Remove o arquivo referenciado por KEY ou URL.
     */
    void delete(String keyOrUrl);

    /**
     * Verifica se o arquivo existe no storage.
     */
    boolean exists(String keyOrUrl);

    /**
     * Informa se a referência ou chave aponta para um arquivo de visibilidade pública.
     */
    boolean isPublic(String keyOrUrl);

    /**
     * Infere a visibilidade padrão com base na estrutura de pastas/prefixo do caminho.
     */
    default FileVisibility inferVisibility(String path) {
        if (path == null) {
            return FileVisibility.PRIVATE;
        }
        String normalized = path.replace("\\", "/").toLowerCase();
        if (normalized.startsWith("public/")
                || normalized.contains("/covers/")
                || normalized.contains("/demos/")) {
            return FileVisibility.PUBLIC;
        }
        return FileVisibility.PRIVATE;
    }
}
