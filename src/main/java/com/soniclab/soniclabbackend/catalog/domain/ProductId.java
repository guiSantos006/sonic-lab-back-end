package com.soniclab.soniclabbackend.catalog.domain;

import java.util.UUID;

public record ProductId(UUID uuid) {
    public ProductId() {
        this(UUID.randomUUID());
    }

}
