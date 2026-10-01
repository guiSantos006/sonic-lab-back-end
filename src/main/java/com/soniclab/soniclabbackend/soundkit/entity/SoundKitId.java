package com.soniclab.soniclabbackend.soundkit.entity;

import java.util.UUID;

public record SoundKitId(UUID uuid) {
    public SoundKitId(){
        this(UUID.randomUUID());
    }
}
