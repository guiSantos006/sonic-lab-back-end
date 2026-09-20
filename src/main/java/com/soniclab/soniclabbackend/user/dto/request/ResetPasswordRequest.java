package com.soniclab.soniclabbackend.user.dto.request;

import jakarta.validation.constraints.NotEmpty;

public record ResetPasswordRequest(
        @NotEmpty(message = "Token cannot be empty") String token,
        @NotEmpty(message = "New password cannot be empty") String newPassword
) {
}