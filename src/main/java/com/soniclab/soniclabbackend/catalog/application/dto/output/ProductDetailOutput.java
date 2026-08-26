package com.soniclab.soniclabbackend.catalog.application.dto.output;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

public record ProductDetailOutput(
        String id,
        String title,
        String description,
        BigDecimal priceAmount,
        String currency,
        String creatorId,
        String creatorName,
        String category,
        Set<String> tags,
        String coverImageUrl,
        AudioPreviewOutput audioPreview,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        Set<String> targetPlugins
) {}
