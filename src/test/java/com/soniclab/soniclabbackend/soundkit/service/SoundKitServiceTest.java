package com.soniclab.soniclabbackend.soundkit.service;

import com.soniclab.soniclabbackend.soundkit.dto.request.CreateSoundKitRequest;
import com.soniclab.soniclabbackend.soundkit.dto.request.UpdateSoundKitRequest;
import com.soniclab.soniclabbackend.soundkit.dto.response.SoundKitResponse;
import com.soniclab.soniclabbackend.soundkit.entity.AudioDemo;
import com.soniclab.soniclabbackend.soundkit.entity.KitType;
import com.soniclab.soniclabbackend.soundkit.entity.SoundKit;
import com.soniclab.soniclabbackend.soundkit.entity.SoundKitId;
import com.soniclab.soniclabbackend.soundkit.exception.SoundKitNotFoundException;
import com.soniclab.soniclabbackend.soundkit.repository.SoundKitRepository;
import com.soniclab.soniclabbackend.user.config.JWTUserData;
import com.soniclab.soniclabbackend.user.entity.UserId;
import com.soniclab.soniclabbackend.user.entity.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SoundKitServiceTest {

    private SoundKitRepository soundKitRepository;
    private MongoTemplate mongoTemplate;
    private SoundKitService soundKitService;

    @BeforeEach
    void setUp() {
        soundKitRepository = Mockito.mock(SoundKitRepository.class);
        mongoTemplate = Mockito.mock(MongoTemplate.class);
        soundKitService = new SoundKitService(soundKitRepository, mongoTemplate);
    }

    // ------------------------------------------------------------------
    // create (teste que já existia)
    // ------------------------------------------------------------------
    @Test
    void testCreateSoundKitPreservesAudioDemoWithWaveformAndDuration() {
        UserId userId = new UserId();
        JWTUserData user = new JWTUserData(userId, "producer@soniclab.com", UserRole.USER);

        AudioDemo demo = new AudioDemo(
                "http://localhost:8080/uploads/soundkits/demos/test-demo.wav",
                218,
                List.of(0.02f, 0.45f, 0.88f, 1.0f, 0.55f, 0.0f)
        );

        CreateSoundKitRequest request = new CreateSoundKitRequest(
                "Ultimate Drums 2026",
                "Kit com samples de alta fidelidade",
                new BigDecimal("49.90"),
                KitType.DRUM_KIT,
                "http://localhost:8080/uploads/soundkits/covers/cover.jpg",
                "soundkits/packages/package.zip",
                demo,
                List.of("TRAP", "DRUMS"),
                Map.of("samples", "150")
        );

        when(soundKitRepository.save(any(SoundKit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SoundKitResponse response = soundKitService.create(request, user);

        assertNotNull(response);
        assertEquals("Ultimate Drums 2026", response.title());
        assertNotNull(response.audioDemo());
        assertEquals("http://localhost:8080/uploads/soundkits/demos/test-demo.wav", response.audioDemo().url());
        assertEquals(218, response.audioDemo().durationInSeconds());
        assertNotNull(response.audioDemo().waveformData());
        assertEquals(6, response.audioDemo().waveformData().size());
        assertEquals(List.of(0.02f, 0.45f, 0.88f, 1.0f, 0.55f, 0.0f), response.audioDemo().waveformData());

        ArgumentCaptor<SoundKit> captor = ArgumentCaptor.forClass(SoundKit.class);
        verify(soundKitRepository).save(captor.capture());

        SoundKit captured = captor.getValue();
        assertEquals(demo, captured.getAudioDemo());
        assertEquals(218, captured.getAudioDemo().durationInSeconds());
        assertEquals(demo.waveformData(), captured.getAudioDemo().waveformData());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------
    private SoundKit existingKit(UserId creator) {
        SoundKit kit = new SoundKit("Kit Original", new BigDecimal("30.00"), creator, KitType.DRUM_KIT);
        kit.setId(new SoundKitId());
        kit.setDescription("Descrição original");
        kit.setCoverUrl("cover-original");
        kit.setFileUrl("file-original");
        kit.setTags(new ArrayList<>(List.of("trap")));
        return kit;
    }

    private UpdateSoundKitRequest updateRequest(List<String> tags, Map<String, String> specs) {
        return new UpdateSoundKitRequest(
                "  Kit Novo  ", "Nova descrição", new BigDecimal("59.90"), KitType.SAMPLE_PACK, tags, specs);
    }

    // ------------------------------------------------------------------
    // update
    // ------------------------------------------------------------------
    @Test
    void updateByOwnerChangesTextFieldsAndKeepsFiles() {
        UserId owner = new UserId();
        JWTUserData user = new JWTUserData(owner, "owner@soniclab.com", UserRole.USER);
        SoundKit kit = existingKit(owner);
        UUID id = kit.getId().uuid();

        when(soundKitRepository.findByIdAndActiveTrue(new SoundKitId(id))).thenReturn(Optional.of(kit));
        when(soundKitRepository.save(any(SoundKit.class))).thenAnswer(i -> i.getArgument(0));

        SoundKitResponse response = soundKitService.update(
                id, updateRequest(List.of("boom-bap", "lofi"), Map.of("formato", "WAV")), user);

        assertEquals("Kit Novo", response.title());
        assertEquals("Nova descrição", response.description());
        assertEquals(new BigDecimal("59.90"), response.price());
        assertEquals(KitType.SAMPLE_PACK, response.type());
        assertEquals(List.of("boom-bap", "lofi"), response.tags());
        assertEquals(Map.of("formato", "WAV"), response.specifications());

        // arquivos e dono não podem mudar
        assertEquals("cover-original", kit.getCoverUrl());
        assertEquals("file-original", kit.getFileUrl());
        assertEquals(owner, kit.getCreatorId());
    }

    @Test
    void updateWithNullTagsAndSpecificationsKeepsCurrentValues() {
        UserId owner = new UserId();
        JWTUserData user = new JWTUserData(owner, "owner@soniclab.com", UserRole.USER);
        SoundKit kit = existingKit(owner);
        UUID id = kit.getId().uuid();

        when(soundKitRepository.findByIdAndActiveTrue(new SoundKitId(id))).thenReturn(Optional.of(kit));
        when(soundKitRepository.save(any(SoundKit.class))).thenAnswer(i -> i.getArgument(0));

        SoundKitResponse response = soundKitService.update(id, updateRequest(null, null), user);

        assertEquals(List.of("trap"), response.tags());
    }

    @Test
    void updateByAnotherUserIsForbidden() {
        SoundKit kit = existingKit(new UserId());
        UUID id = kit.getId().uuid();
        JWTUserData intruder = new JWTUserData(new UserId(), "intruder@soniclab.com", UserRole.USER);

        when(soundKitRepository.findByIdAndActiveTrue(new SoundKitId(id))).thenReturn(Optional.of(kit));

        assertThrows(AccessDeniedException.class,
                () -> soundKitService.update(id, updateRequest(null, null), intruder));
        verify(soundKitRepository, never()).save(any(SoundKit.class));
        assertEquals("Kit Original", kit.getTitle());
    }

    @Test
    void updateNonexistentKitThrowsNotFound() {
        UUID id = UUID.randomUUID();
        JWTUserData user = new JWTUserData(new UserId(), "u@soniclab.com", UserRole.USER);

        when(soundKitRepository.findByIdAndActiveTrue(new SoundKitId(id))).thenReturn(Optional.empty());

        assertThrows(SoundKitNotFoundException.class,
                () -> soundKitService.update(id, updateRequest(null, null), user));
    }

    // ------------------------------------------------------------------
    // deactivate (delete)
    // ------------------------------------------------------------------
    @Test
    void deactivateByOwnerSetsActiveFalse() {
        UserId owner = new UserId();
        JWTUserData user = new JWTUserData(owner, "owner@soniclab.com", UserRole.USER);
        SoundKit kit = existingKit(owner);
        UUID id = kit.getId().uuid();

        when(soundKitRepository.findByIdAndActiveTrue(new SoundKitId(id))).thenReturn(Optional.of(kit));

        soundKitService.deactivate(id, user);

        assertFalse(kit.isActive());
        verify(soundKitRepository).save(kit);
    }

    @Test
    void deactivateByAnotherUserIsForbidden() {
        SoundKit kit = existingKit(new UserId());
        UUID id = kit.getId().uuid();
        JWTUserData intruder = new JWTUserData(new UserId(), "intruder@soniclab.com", UserRole.USER);

        when(soundKitRepository.findByIdAndActiveTrue(new SoundKitId(id))).thenReturn(Optional.of(kit));

        assertThrows(AccessDeniedException.class, () -> soundKitService.deactivate(id, intruder));
        assertTrue(kit.isActive());
        verify(soundKitRepository, never()).save(any(SoundKit.class));
    }

    @Test
    void deactivateNonexistentKitThrowsNotFound() {
        UUID id = UUID.randomUUID();
        JWTUserData user = new JWTUserData(new UserId(), "u@soniclab.com", UserRole.USER);

        when(soundKitRepository.findByIdAndActiveTrue(new SoundKitId(id))).thenReturn(Optional.empty());

        assertThrows(SoundKitNotFoundException.class, () -> soundKitService.deactivate(id, user));
    }
}