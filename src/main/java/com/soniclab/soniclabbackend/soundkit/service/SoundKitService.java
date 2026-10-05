package com.soniclab.soniclabbackend.soundkit.service;

import com.soniclab.soniclabbackend.soundkit.repository.SoundKitRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.stereotype.Service;

@Service
public class SoundKitService {

    private final SoundKitRepository soundKitRepository;
    private final AuthenticationManager authenticationManager;



    public SoundKitService(SoundKitRepository soundKitRepository) {
        this.soundKitRepository = soundKitRepository;
    }
}
