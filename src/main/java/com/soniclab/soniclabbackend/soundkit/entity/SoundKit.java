package com.soniclab.soniclabbackend.soundkit.entity;

import com.soniclab.soniclabbackend.user.entity.UserId;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Document(collection = "soundkits")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SoundKit {

    @Id
    private SoundKitId id;

    private String title;
    private String description;
    private BigDecimal price;

    @Indexed
    private UserId creatorId;

    private KitType type;

    private String coverUrl;
    private String fileUrl;

    private AudioDemo audioDemo;

    private List<String> tags = new ArrayList<>();
    private Map<String, String> specifications = new HashMap<>();

    private boolean active = true;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    public SoundKit(String title, BigDecimal price, UserId creatorId, KitType type) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Título é obrigatório");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Preço inválido");
        }
        if (creatorId == null) {
            throw new IllegalArgumentException("Criador é obrigatório");
        }
        if (type == null) {
            throw new IllegalArgumentException("Tipo do kit é obrigatório");
        }

        this.title = title.trim();
        this.price = price;
        this.creatorId = creatorId;
        this.type = type;
        this.active = true;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }
}