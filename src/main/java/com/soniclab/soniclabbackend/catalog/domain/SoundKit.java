package com.soniclab.soniclabbackend.catalog.domain;

import lombok.Getter;

import java.util.Set;

@Getter
public class SoundKit extends Product {
    public SoundKit(ProductId id, String title, String description, Price price, String creatorId, String creatorName, Set<String> tags, String coverImageUrl, AudioPreview audioPreview, String securedFileStorageKey) {
        super(id, title, description, price, creatorId, creatorName, Category.SOUND_KIT, tags, coverImageUrl, audioPreview, securedFileStorageKey);
    }

    protected SoundKit() {}
}
