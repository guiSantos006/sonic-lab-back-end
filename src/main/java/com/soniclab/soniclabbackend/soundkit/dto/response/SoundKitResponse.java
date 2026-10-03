package com.soniclab.soniclabbackend.soundkit.dto.response;

import com.soniclab.soniclabbackend.soundkit.entity.AudioDemo;
import com.soniclab.soniclabbackend.soundkit.entity.KitType;
import com.soniclab.soniclabbackend.soundkit.entity.SoundKit;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Resposta pública. NÃO expõe fileUrl (arquivo pago) - ele só deve ser
 * entregue após a compra, por um endpoint de download próprio.
 */
public record SoundKitResponse(
        UUID id,
        String title,
        String description,
        BigDecimal price,
        UUID creatorId,
        KitType type,
        String coverUrl,
        AudioDemo audioDemo,
        List<String> tags,
        Map<String, String> specifications,
        boolean active,
        Instant createdAt,
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