package com.soniclab.soniclabbackend.user.dto.request;

import jakarta.validation.constraints.NotEmpty;

public record LoginRequest(@NotEmpty(message = "E-mail cannot be empty") String email,
                           @NotEmpty(message = "Password cannot be empty") String password) {
}
