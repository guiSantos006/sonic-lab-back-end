package com.soniclab.soniclabbackend.soundkit.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

@Schema(description = "Envelope padronizado de resposta paginada para consumo por tabelas e listas no front-end")
public record PageResponse<T>(
        @Schema(description = "Lista dos elementos retornados na página atual")
        List<T> content,

        @Schema(description = "Número da página atual (índice baseado em 0)", example = "0")
        int page,

        @Schema(description = "Quantidade de elementos por página solicitada", example = "20")
        int size,

        @Schema(description = "Quantidade total de registros encontrados no banco", example = "57")
        long totalElements,

        @Schema(description = "Total de páginas disponíveis", example = "3")
        int totalPages,

        @Schema(description = "Indica se esta é a primeira página", example = "true")
        boolean isFirst,

        @Schema(description = "Indica se esta é a última página", example = "false")
        boolean isLast
) {

    /**
     * Converte um Page<E> do Spring Data (entidade) para PageResponse<T> aplicando uma função de mapeamento.
     */
    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        List<T> mappedContent = page.getContent()
                .stream()
                .map(mapper)
                .toList();

        return new PageResponse<>(
                mappedContent,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }

    /**
     * Sobrecarga conveniente caso o Page já seja do tipo Page<T> (já mapeado).
     */
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }
}
