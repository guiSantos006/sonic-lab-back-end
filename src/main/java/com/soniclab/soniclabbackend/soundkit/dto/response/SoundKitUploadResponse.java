package com.soniclab.soniclabbackend.soundkit.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resposta do processamento e upload do pacote de arquivos do SoundKit")
public record SoundKitUploadResponse(
        @Schema(
                description = "Chave identificadora persistente do arquivo no storage (ex: soundkits/packages/<uuid>.zip)",
                example = "soundkits/packages/pack-uuid.zip"
        )
        String fileKey,

        @Schema(
                description = "Identificador persistente do arquivo mantido para retrocompatibilidade (mesmo valor de fileKey)",
                example = "soundkits/packages/pack-uuid.zip"
        )
        String fileUrl,

        @Schema(description = "Tamanho total do arquivo empacotado em bytes", example = "52428800")
        long sizeInBytes,

        @Schema(description = "Quantidade de arquivos de áudio válidos identificados e empacotados", example = "48")
        int totalAudioFiles
) {
    public SoundKitUploadResponse(String fileKey, long sizeInBytes, int totalAudioFiles) {
        this(fileKey, fileKey, sizeInBytes, totalAudioFiles);
    }
}
