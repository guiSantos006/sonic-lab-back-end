package com.soniclab.soniclabbackend.soundkit.storage;

import java.io.InputStream;
import java.time.Duration;

public interface FileStorageService {

    /** Envia o arquivo e retorna a KEY (caminho no bucket). Não gera URL. */
    String upload(String path, InputStream in, long contentLength, String contentType);

    /** URL permanente, só para arquivos públicos (capas, demos). */
    String getPublicUrl(String key);

    /** URL temporária, para arquivos privados (pacotes comprados). */
    String generateDownloadUrl(String key, Duration ttl);

    /** Remove por key ou URL. */
    void delete(String keyOrUrl);
}