package com.soniclab.soniclabbackend.soundkit.controller;

import com.soniclab.soniclabbackend.soundkit.dto.request.CreateSoundKitRequest;
import com.soniclab.soniclabbackend.soundkit.dto.request.SoundKitFilterParams;
import com.soniclab.soniclabbackend.soundkit.dto.response.PageResponse;
import com.soniclab.soniclabbackend.soundkit.dto.response.SoundKitResponse;
import com.soniclab.soniclabbackend.soundkit.service.SoundKitService;
import com.soniclab.soniclabbackend.user.config.JWTUserData;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/soundkits")
public class SoundKitController {

    private final SoundKitService soundKitService;

    public SoundKitController(SoundKitService soundKitService) {
        this.soundKitService = soundKitService;
    }

    // ==========================================
    // Endpoints Públicos
    // ==========================================

    @GetMapping
    public ResponseEntity<PageResponse<SoundKitResponse>> list(
            SoundKitFilterParams filterParams,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(soundKitService.search(filterParams, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SoundKitResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(soundKitService.getActiveById(id));
    }

    @GetMapping("/tags")
    public ResponseEntity<List<String>> listTags() {
        return ResponseEntity.ok(soundKitService.listTags());
    }

    // ==========================================
    // Endpoints Autenticados
    // ==========================================

    @PostMapping
    public ResponseEntity<SoundKitResponse> create(
            @Valid @RequestBody CreateSoundKitRequest request,
            @AuthenticationPrincipal JWTUserData user
    ) {
        SoundKitResponse response = soundKitService.create(request, user);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/my-kits")
    public ResponseEntity<PageResponse<SoundKitResponse>> listMyKits(
            @AuthenticationPrincipal JWTUserData user,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(soundKitService.listByCreator(user, pageable));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal JWTUserData user
    ) {
        soundKitService.deactivate(id, user);
        return ResponseEntity.noContent().build();
    }
}
