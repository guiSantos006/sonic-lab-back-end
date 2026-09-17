package com.soniclab.soniclabbackend.user.config;

import com.soniclab.soniclabbackend.user.entity.UserId;
import lombok.Builder;

@Builder
public record JWTUserData(UserId userId, String email) {
}
