package com.soniclab.soniclabbackend.soundkit.dto.response;

import org.springframework.data.domain.Page;
import java.util.List;
import java.util.function.Function;

public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean isFirst,
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