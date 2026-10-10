package com.soniclab.soniclabbackend.soundkit.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AudioDemo(

        @NotBlank(message = "A URL do áudio demo é obrigatório")
        @Pattern(regexp = "^https?://\\S+$", message = "A URL do áudio deve começar com http:// ou https://")
        String url,

        @PositiveOrZero(message = "A duração não pode ser negativa")
        Integer durationInSeconds,
        List<Float> waveformData
) {}
