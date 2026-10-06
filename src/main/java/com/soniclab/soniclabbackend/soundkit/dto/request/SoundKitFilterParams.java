package com.soniclab.soniclabbackend.soundkit.dto.request;

import com.soniclab.soniclabbackend.soundkit.entity.KitType;

import java.math.BigDecimal;
import java.util.List;

public record SoundKitFilterParams(
        String q,
        KitType type,
        List<String> tags,
        BigDecimal minPrice,
        BigDecimal maxPrice
) {}
