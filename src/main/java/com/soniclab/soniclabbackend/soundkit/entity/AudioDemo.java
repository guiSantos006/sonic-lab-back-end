package com.soniclab.soniclabbackend.soundkit.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

@Schema(description = "Dados de demonstração de áudio para pré-escuta no player de áudio do front-end")
public record AudioDemo(

        @Schema(
                description = "URL pública do arquivo de áudio de demonstração (MP3 ou WAV)",
                example = "http://localhost:8080/uploads/soundkits/demos/demo_808_preview.mp3",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "A URL do áudio demo é obrigatório")
        @Pattern(regexp = "^https?://\\S+$", message = "A URL do áudio deve começar com http:// ou https://")
        String url,

        @Schema(
                description = "Duração total da amostra em segundos",
                example = "32"
        )
        @PositiveOrZero(message = "A duração não pode ser negativa")
        Integer durationInSeconds,

        @Schema(
                description = "Pontos normalizados de amplitude (forma de onda) para desenhar a barra de progresso sonora no canvas do React/WaveSurfer",
                example = "[0.12, 0.45, 0.88, 0.32, 0.95, 0.22, 0.05]"
        )
        List<Float> waveformData
) {}
