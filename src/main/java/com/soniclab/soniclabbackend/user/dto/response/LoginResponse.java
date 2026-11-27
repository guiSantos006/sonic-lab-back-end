package com.soniclab.soniclabbackend.user.dto.response;

public record LoginResponse(String token, String name, String email, String role) {}

