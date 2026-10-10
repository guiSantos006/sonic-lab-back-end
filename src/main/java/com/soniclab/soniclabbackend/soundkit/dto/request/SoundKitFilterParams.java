package com.soniclab.soniclabbackend.soundkit.dto.request;

import com.soniclab.soniclabbackend.soundkit.entity.KitType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record SoundKitFilterParams(
        String q,

        KitType type,

        @Size(max = 12, message = "É possível filtrar por no máximo 12 tags")
        @Size(max = 50, message = "No máximo 50 caracteres por tag")
        List<String> tags,

        @DecimalMin(value = "0.0", message = "O preço mínimo não pode ser negativo")
        BigDecimal minPrice,

        @DecimalMax(value = "0.0", message = "O preço maximo não pode ser negativo")
        BigDecimal maxPrice)

       { @AssertTrue(message = "O preço mínimo não pode ser maior que o preço máximo")
        public boolean isPriceRangeValid() {return minPrice == null || maxPrice == null || minPrice.compareTo(maxPrice) <= 0;}}

