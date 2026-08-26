package com.soniclab.soniclabbackend.catalog.application.dto.input;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.Set;

public record CreateSoundKitInput(
        @NotBlank(message = "Title is required")
        String title,

        String description,

        @NotNull(message = "Price amount is required")
        @PositiveOrZero(message = "Price cannot be negative")
        BigDecimal priceAmount,

        String currency,

        @NotBlank(message = "Creator ID is required")
        String creatorId,

        @NotBlank(message = "Creator name is required")
        String creatorName,

        Set<String> tags,
        String coverImageUrl,
        AudioPreviewInput audioPreview,

        @NotBlank(message = "Secured file storage key is required")
        String securedFileStorageKey
) {}
