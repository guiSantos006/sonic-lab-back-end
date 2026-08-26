package com.soniclab.soniclabbackend.catalog.application.dto.output;

import java.math.BigDecimal;
import java.util.Set;

public record ProductCardOutput(
        String id,
        String title,
        BigDecimal priceAmount,
        String currency,
        String creatorId,
        String creatorName,
        String category,
        String coverImageUrl,
        AudioPreviewOutput audioPreview,
        Set<String> tags
) {}
