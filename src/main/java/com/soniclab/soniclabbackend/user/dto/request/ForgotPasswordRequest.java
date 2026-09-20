package com.soniclab.soniclabbackend.user.dto.request;

import jakarta.validation.constraints.NotEmpty;

public record ForgotPasswordRequest(
        @NotEmpty(message = "E-mail cant be empty") String email
) {
}