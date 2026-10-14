package com.soniclab.soniclabbackend.user.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Estrutura padrão de resposta de erro da API para tratamento no front-end")
public record ApiErrorResponse(
        @Schema(description = "Código de status HTTP do erro", example = "400")
        int status,

        @Schema(description = "Descrição curta do tipo de erro HTTP", example = "Bad Request")
        String error,

        @Schema(description = "Mensagem amigável explicando o motivo do erro", example = "Dados inválidos fornecidos.")
        String message,

        @Schema(description = "Mapeamento detalhado dos campos com falha de validação (quando aplicável)", example = "{\"title\": \"O título é obrigatório\", \"price\": \"O preço não pode ser negativo\"}")
        Map<String, String> details
) {
    public ApiErrorResponse(int status, String error, String message) {
        this(status, error, message, null);
    }
}
