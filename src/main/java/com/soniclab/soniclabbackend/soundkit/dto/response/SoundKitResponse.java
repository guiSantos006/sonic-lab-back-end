package com.soniclab.soniclabbackend.soundkit.dto.response;

import com.soniclab.soniclabbackend.soundkit.entity.AudioDemo;
import com.soniclab.soniclabbackend.soundkit.entity.KitType;
import com.soniclab.soniclabbackend.soundkit.entity.SoundKit;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Resposta pública. NÃO expõe fileUrl (arquivo pago) - ele só deve ser
 * entregue após a compra, por um endpoint de download próprio.
 */
@Schema(description = "Dados públicos do SoundKit retornados pela API (não inclui o link direto para download do arquivo pago)")
public record SoundKitResponse(
        @Schema(description = "Identificador único do SoundKit (UUID)", example = "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d")
        UUID id,

        @Schema(description = "Título do SoundKit", example = "808 Mafia Trap Essentials")
        String title,

        @Schema(description = "Descrição detalhada do conteúdo e características do kit", example = "Mais de 150 sons exclusivos para produção de Trap...")
        String description,

        @Schema(description = "Preço do kit em BRL", example = "49.90")
        BigDecimal price,

        @Schema(description = "Identificador único (UUID) do produtor/criador do kit", example = "e0f49635-779d-43d9-a728-66258079a499")
        UUID creatorId,

        @Schema(description = "Categoria/Tipo do kit", example = "DRUM_KIT")
        KitType type,

        @Schema(description = "URL pública da imagem de capa", example = "http://localhost:8080/uploads/soundkits/covers/capa-trap.jpg")
        String coverUrl,

        @Schema(description = "Informações do áudio demo e forma de onda para reprodução no player")
        AudioDemo audioDemo,

        @Schema(description = "Lista de tags atribuídas ao kit", example = "[\"trap\", \"808\", \"drums\"]")
        List<String> tags,

        @Schema(description = "Especificações técnicas do pacote", example = "{\"formato\": \"WAV 24-bit 44.1kHz\", \"totalArquivos\": \"120\"}")
        Map<String, String> specifications,

        @Schema(description = "Status de ativação do kit no catálogo público", example = "true")
        boolean active,

        @Schema(description = "Timestamp ISO-8601 da data de criação", example = "2026-10-14T10:00:00Z")
        Instant createdAt,

        @Schema(description = "Timestamp ISO-8601 da última atualização", example = "2026-10-14T10:15:00Z")
        Instant updatedAt
) {
    public static SoundKitResponse fromEntity(SoundKit kit) {
        return new SoundKitResponse(
                kit.getId() != null ? kit.getId().uuid() : null,
                kit.getTitle(),
                kit.getDescription(),
                kit.getPrice(),
                kit.getCreatorId() != null ? kit.getCreatorId().uuid() : null,
                kit.getType(),
                kit.getCoverUrl(),
                kit.getAudioDemo(),
                kit.getTags(),
                kit.getSpecifications(),
                kit.isActive(),
                kit.getCreatedAt(),
                kit.getUpdatedAt()
        );
    }
}
