package com.soniclab.soniclabbackend.soundkit.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resposta contendo a URL temporária pré-assinada para download do pacote privado do SoundKit")
public record SoundKitDownloadResponse(
        @Schema(
                description = "URL temporária gerada sob demanda (Presigned URL para S3 ou URL de download local)",
                example = "https://meu-bucket.s3.us-east-2.amazonaws.com/soundkits/packages/pack.zip?X-Amz-Algorithm=..."
        )
        String downloadUrl,

        @Schema(
                description = "Tempo de expiração da URL em segundos (TTL)",
                example = "900"
        )
        long expiresInSeconds
) {
}
