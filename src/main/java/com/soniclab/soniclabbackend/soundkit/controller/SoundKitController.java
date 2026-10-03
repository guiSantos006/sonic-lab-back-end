package com.soniclab.soniclabbackend.soundkit.controller;

import com.soniclab.soniclabbackend.soundkit.dto.request.CreateSoundKitRequest;
import com.soniclab.soniclabbackend.soundkit.dto.response.PageResponse;
import com.soniclab.soniclabbackend.soundkit.dto.response.SoundKitResponse;
import com.soniclab.soniclabbackend.soundkit.entity.KitType;
import com.soniclab.soniclabbackend.soundkit.service.SoundKitService;
import com.soniclab.soniclabbackend.user.config.JWTUserData;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/soundkits")
public class SoundKitController {

    private static final Set<String> SORTABLE_FIELDS = Set.of("createdAt", "price", "title");
    private static final int MAX_PAGE_SIZE = 50;

    private final SoundKitService soundKitService;

    public SoundKitController(SoundKitService soundKitService) {
        this.soundKitService = soundKitService;
    }

    // ---------- Público ----------

    @GetMapping
    public ResponseEntity<PageResponse<SoundKitResponse>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) KitType type,
            @RequestParam(required = false) List<String> tags,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction
    ) {
        var result = soundKitService.search(q, type, tags, null, null, buildPageable(page, size, sortBy, direction));
        return ResponseEntity.ok(PageResponse.from(result, SoundKitResponse::fromEntity));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SoundKitResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(SoundKitResponse.fromEntity(soundKitService.getActiveById(id)));
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



    private Pageable buildPageable(int page, int size, String sortBy, String direction) {
        String field = SORTABLE_FIELDS.contains(sortBy) ? sortBy : "createdAt";
        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), Sort.by(dir, field));
    }
}
