package com.soniclab.soniclabbackend.soundkit.service;

import com.soniclab.soniclabbackend.soundkit.dto.request.SoundKitFilterParams;
import com.soniclab.soniclabbackend.soundkit.dto.response.PageResponse;
import com.soniclab.soniclabbackend.soundkit.dto.response.SoundKitResponse;
import com.soniclab.soniclabbackend.soundkit.entity.SoundKit;
import com.soniclab.soniclabbackend.soundkit.repository.SoundKitRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SoundKitService {

    private final SoundKitRepository soundKitRepository;
    private final MongoTemplate mongoTemplate;

    public SoundKitService(SoundKitRepository soundKitRepository, MongoTemplate mongoTemplate) {
        this.soundKitRepository = soundKitRepository;
        this.mongoTemplate = mongoTemplate;
    }

    @Transactional(readOnly = true)
    public PageResponse<SoundKitResponse> search(SoundKitFilterParams filterParams, Pageable pageable) {

        Page<SoundKit> pageResult = soundKitRepository.findByActiveTrue(pageable);


    }
}
