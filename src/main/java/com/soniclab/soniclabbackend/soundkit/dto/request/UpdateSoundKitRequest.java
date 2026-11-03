package com.soniclab.soniclabbackend.soundkit.dto.request;

import com.soniclab.soniclabbackend.soundkit.entity.KitType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record UpdateSoundKitRequest(
        @NotBlank(message = "O título é obrigatório")
        @Size(min = 3, max = 100, message = "O título deve ter entre 3 e 100 caracteres")
        String title,

        @Size(max = 1000, message = "A descrição pode ter no máximo 1000 caracteres")
        String description,

        @NotNull(message = "O preço é obrigatório")
        @DecimalMin(value = "0.0", inclusive = true, message = "O preço não pode ser negativo")
        BigDecimal price,

        @NotNull(message = "O tipo do kit é obrigatório")
        KitType type,

        @Size(max = 15, message = "O kit pode ter no máximo 15 tags")
        List<@NotBlank(message = "A tag não pode ser vazia")
        @Size(max = 30, message = "Cada tag pode ter no máximo 30 caracteres") String> tags,

        Map<String, String> specifications
) {
}