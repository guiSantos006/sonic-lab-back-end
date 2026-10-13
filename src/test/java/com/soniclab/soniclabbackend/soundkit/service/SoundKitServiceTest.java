package com.soniclab.soniclabbackend.soundkit.service;

import com.soniclab.soniclabbackend.soundkit.dto.request.CreateSoundKitRequest;
import com.soniclab.soniclabbackend.soundkit.dto.response.SoundKitResponse;
import com.soniclab.soniclabbackend.soundkit.entity.AudioDemo;
import com.soniclab.soniclabbackend.soundkit.entity.KitType;
import com.soniclab.soniclabbackend.soundkit.entity.SoundKit;
import com.soniclab.soniclabbackend.soundkit.repository.SoundKitRepository;
import com.soniclab.soniclabbackend.user.config.JWTUserData;
import com.soniclab.soniclabbackend.user.entity.UserId;
import com.soniclab.soniclabbackend.user.entity.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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
}
