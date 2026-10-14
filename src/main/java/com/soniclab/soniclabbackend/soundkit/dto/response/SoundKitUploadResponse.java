package com.soniclab.soniclabbackend.soundkit.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resposta do processamento e upload do pacote de arquivos do SoundKit")
public record SoundKitUploadResponse(
        @Schema(description = "URL ou caminho do arquivo ZIP armazenado para inclusão na criação do SoundKit", example = "http://localhost:8080/uploads/soundkits/packages/pack-uuid.zip")
        String fileUrl,

        @Schema(description = "Tamanho total do arquivo empacotado em bytes", example = "52428800")
        long sizeInBytes,

        @Schema(description = "Quantidade de arquivos de áudio válidos identificados e empacotados", example = "48")
        int totalAudioFiles
) {
}
