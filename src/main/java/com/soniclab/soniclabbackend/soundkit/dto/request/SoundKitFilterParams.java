package com.soniclab.soniclabbackend.soundkit.dto.request;

import com.soniclab.soniclabbackend.soundkit.entity.KitType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Parâmetros para busca e filtragem no catálogo de SoundKits")
public record SoundKitFilterParams(
        @Schema(description = "Termo para busca textual no título do SoundKit (busca parcial, sem distinção de maiúsculas/minúsculas)", example = "trap")
        String q,

        @Schema(description = "Filtro pela categoria ou tipo do SoundKit", example = "DRUM_KIT")
        KitType type,

        @Schema(description = "Lista de tags para filtragem (retorna kits que contenham pelo menos uma das tags informadas)", example = "[\"trap\", \"808\"]")
        @Size(max = 12, message = "É possível filtrar por no máximo 12 tags")
        List<@Size(max = 50, message = "No máximo 50 caracteres por tag") String> tags,

        @Schema(description = "Valor mínimo de preço", example = "0.0")
        @DecimalMin(value = "0.0", message = "O preço mínimo não pode ser negativo")
        BigDecimal minPrice,

        @Schema(description = "Valor máximo de preço", example = "150.0")
        @DecimalMin(value = "0.0", message = "O preço máximo não pode ser negativo")
        BigDecimal maxPrice
) {
    @AssertTrue(message = "O preço mínimo não pode ser maior que o preço máximo")
    public boolean isPriceRangeValid() {
        return minPrice == null || maxPrice == null || minPrice.compareTo(maxPrice) <= 0;
    }
}
