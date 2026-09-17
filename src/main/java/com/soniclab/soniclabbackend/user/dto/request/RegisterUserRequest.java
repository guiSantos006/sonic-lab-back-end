package com.soniclab.soniclabbackend.user.dto.request;

import jakarta.validation.constraints.NotEmpty;

public record RegisterUserRequest(@NotEmpty(message = "Name cannot be empty")String name,
                                  @NotEmpty(message = "E-mail cannot be empty")String email,
                                  @NotEmpty(message = "Password cannot be empty")String password) {
}
