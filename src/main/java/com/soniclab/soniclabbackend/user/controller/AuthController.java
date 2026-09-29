package com.soniclab.soniclabbackend.user.controller;

import com.soniclab.soniclabbackend.user.dto.request.ForgotPasswordRequest;
import com.soniclab.soniclabbackend.user.dto.request.LoginRequest;
import com.soniclab.soniclabbackend.user.dto.request.RegisterUserRequest;
import com.soniclab.soniclabbackend.user.dto.request.ResetPasswordRequest;
import com.soniclab.soniclabbackend.user.dto.response.LoginResponse;
import com.soniclab.soniclabbackend.user.dto.response.RegisterUserResponse;
import com.soniclab.soniclabbackend.user.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterUserResponse> register(@Valid @RequestBody RegisterUserRequest request) {
        RegisterUserResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        String message = authService.forgotPassword(request);
        return ResponseEntity.ok(message);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok("Senha redefinida com sucesso!");
    }
}
