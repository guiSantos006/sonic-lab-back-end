package com.soniclab.soniclabbackend.catalog.domain;

import lombok.Getter;

import java.util.Collections;
import java.util.Set;

@Getter
public class PresetBank extends Product{

    private Set<String> targetPlugins;

    public PresetBank(ProductId id, String title, String description,
                      Price price, String creatorId, String creatorName,
                      Set<String> tags, String coverImageUrl, AudioPreview audioPreview,
                      String securedFileStorageKey, Set<String> targetPlugins) {
        super(id, title, description, price, creatorId, creatorName, Category.PRESET_BANK,
                tags, coverImageUrl, audioPreview, securedFileStorageKey);

        if (targetPlugins == null || targetPlugins.isEmpty()) {
            throw new IllegalArgumentException("The target plugins cannot be null or empty.");
        }

        this.targetPlugins = targetPlugins;
    }

    protected PresetBank() {
        super();
        this.targetPlugins = Collections.emptySet();
    }
}
