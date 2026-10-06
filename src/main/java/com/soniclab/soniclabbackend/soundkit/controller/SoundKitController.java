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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/soundkits")
public class SoundKitController {

    private final SoundKitService soundKitService;

    public SoundKitController(SoundKitService soundKitService) {
        this.soundKitService = soundKitService;
    }

    // ---------- Público ----------

    @GetMapping
    public ResponseEntity<PageResponse<SoundKitResponse>> list(
            SoundKitFilterParams filterParams,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        var response = soundKitService.search(filterParams, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SoundKitResponse> getById(@PathVariable UUID id) {
        var response = soundKitService.getActiveById(id);
        return ResponseEntity.ok(response);
    }


    @GetMapping("/tags")
    public ResponseEntity<List<String>> listTags() {
        return ResponseEntity.ok(soundKitService.listTags());
    }

    // ---------- Quando tiver Autenticado ----------

    @PostMapping
    public ResponseEntity<SoundKitResponse> create(
            @Valid @RequestBody CreateSoundKitRequest request,
            @AuthenticationPrincipal JWTUserData user
    ) {
        var created = soundKitService.create(request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(SoundKitResponse.fromEntity(created));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal JWTUserData user) {
        soundKitService.deactivate(id, user);
        return ResponseEntity.noContent().build();
    }


}
