package com.soniclab.soniclabbackend.user.dto.response;

import com.soniclab.soniclabbackend.user.entity.User;
import com.soniclab.soniclabbackend.user.entity.UserRole;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String name,
        String email,
        UserRole role
) {
    public static UserResponse fromEntity(User user) {
        return new UserResponse(
                user.getId() != null ? user.getId().uuid() : null,
                user.getName(),
                user.getEmail(),
                user.getUserRole()
        );
    }
}
