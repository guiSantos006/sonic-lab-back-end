package com.soniclab.soniclabbackend.user.model;

import java.util.UUID;

public record UserId(UUID uuid) {
    public UserId(){
        this(UUID.randomUUID());
    }
}
