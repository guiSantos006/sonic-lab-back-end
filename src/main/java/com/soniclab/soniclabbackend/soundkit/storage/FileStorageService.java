package com.soniclab.soniclabbackend.soundkit.storage;

import java.io.InputStream;

public interface FileStorageService {
    /**
     * Salva um arquivo no storage e retorna a URL pública acessível.
     * @param path Caminho relativo desejado (ex: "kits/meu-pack-123.zip")
     * @param contentLength Tamanho do arquivo em bytes (exigido pelo S3)
     * @param inputStream Fluxo de bytes do arquivo
     * @param contentType Tipo MIME (ex: "application/zip", "image/png")
     * @return URL acessível do arquivo
     */
    String upload(String path, InputStream inputStream, long contentLength, String contentType);

    /**
     * Remove um arquivo dado a sua URL ou caminho.
     */
    void delete(String fileUrl);
}