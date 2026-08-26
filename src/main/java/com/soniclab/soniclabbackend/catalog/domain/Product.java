package com.soniclab.soniclabbackend.catalog.domain;

import lombok.Getter;
import org.springframework.data.annotation.Id;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.Set;

@Getter
public abstract class Product {

    private ProductId id;
    private String title;
    private String description;
    private Price price;
    private String creatorId;
    private String creatorName;
    private Category category;
    private Set<String> tags;
    private String coverImageUrl;
    private AudioPreview audioPreview;
    private String securedFileStorageKey; // Chave interna no S3/R2 (não a URL pública)

    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;

    public Product(ProductId id, String title, String description, Price price,
                   String creatorId, String creatorName, Category category,
                   Set<String> tags, String coverImageUrl,
                   AudioPreview audioPreview,
                   String securedFileStorageKey) {

        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Title must not be null or empty");
        }

        if (creatorId == null || creatorId.trim().isEmpty()) {
            throw new IllegalArgumentException("CreatorId must not be null or empty");
        }

        this.id = id != null ? id : new ProductId();
        this.title = title.trim();
        this.description = description;
        this.price = price;
        this.creatorId = creatorId;
        this.creatorName = creatorName;
        this.category = category;
        this.tags = tags != null ? tags : Collections.emptySet();
        this.coverImageUrl = coverImageUrl;
        this.audioPreview = audioPreview;
        this.securedFileStorageKey = securedFileStorageKey;
        this.active = true;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    protected Product() {
    }

    public void updateDetails(String title, String description, Price price, Set<String> tags) {
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Title cannot be null or empty.");
        }
        this.title = title.trim();
        this.description = description;
        this.price = price;
        this.tags = tags;
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = Instant.now();
    }

    public void activate() {
        this.active = true;
        this.updatedAt = Instant.now();
    }
}