package com.soniclab.soniclabbackend.soundkit.storage;

import java.io.InputStream;

public interface FileStorageService {
    /**
     * Salva um arquivo no storage e retorna a URL pública acessível.
     * @param path Caminho relativo desejado (ex: "kits/meu-pack-123.zip")
     * @param inputStream Fluxo de bytes do arquivo
     * @param contentType Tipo MIME (ex: "application/zip", "image/png")
     * @return URL acessível do arquivo
     */
    String upload(String path, InputStream inputStream, String contentType);

    /**
     * Remove um arquivo dado a sua URL ou caminho.
     */
    void delete(String fileUrl);
}