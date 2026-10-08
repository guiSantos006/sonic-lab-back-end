package com.soniclab.soniclabbackend.soundkit.service;

import com.soniclab.soniclabbackend.soundkit.dto.request.CreateSoundKitRequest;
import com.soniclab.soniclabbackend.soundkit.dto.request.SoundKitFilterParams;
import com.soniclab.soniclabbackend.soundkit.dto.response.PageResponse;
import com.soniclab.soniclabbackend.soundkit.dto.response.SoundKitResponse;
import com.soniclab.soniclabbackend.soundkit.entity.SoundKit;
import com.soniclab.soniclabbackend.soundkit.entity.SoundKitId;
import com.soniclab.soniclabbackend.soundkit.exception.SoundKitNotFoundException;
import com.soniclab.soniclabbackend.soundkit.repository.SoundKitRepository;
import com.soniclab.soniclabbackend.user.config.JWTUserData;
import com.soniclab.soniclabbackend.user.entity.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class SoundKitService {

    private final SoundKitRepository soundKitRepository;
    private final MongoTemplate mongoTemplate;

    public SoundKitService(SoundKitRepository soundKitRepository, MongoTemplate mongoTemplate) {
        this.soundKitRepository = soundKitRepository;
        this.mongoTemplate = mongoTemplate;
    }

    @Transactional(readOnly = true)
    public PageResponse<SoundKitResponse> search(SoundKitFilterParams filter, Pageable pageable) {
        Query query = buildQuery(filter);
        long total = mongoTemplate.count(query, SoundKit.class);

        List<SoundKit> items = mongoTemplate.find(query.with(pageable), SoundKit.class);
        Page<SoundKit> page = new PageImpl<>(items, pageable, total);

        return PageResponse.from(page, SoundKitResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public SoundKitResponse getActiveById(UUID id) {
        return soundKitRepository.findByIdAndActiveTrue(new SoundKitId(id))
                .map(SoundKitResponse::fromEntity)
                .orElseThrow(() -> new SoundKitNotFoundException("SoundKit não encontrado: " + id));
    }

    @Transactional(readOnly = true)
    public List<String> listTags() {
        Query query = new Query(Criteria.where("active").is(true));
        return mongoTemplate.findDistinct(query, "tags", SoundKit.class, String.class);
    }

    @Transactional(readOnly = true)
    public PageResponse<SoundKitResponse> listByCreator(JWTUserData user, Pageable pageable) {
        Page<SoundKit> page = soundKitRepository.findByCreatorIdAndActiveTrue(user.userId(), pageable);
        return PageResponse.from(page, SoundKitResponse::fromEntity);
    }

    @Transactional
    public SoundKitResponse create(CreateSoundKitRequest request, JWTUserData user) {
        SoundKit soundKit = new SoundKit(
                request.title(),
                request.price(),
                user.userId(),
                request.type()
        );

        soundKit.setId(new SoundKitId());
        soundKit.setDescription(request.description());
        soundKit.setCoverUrl(request.coverUrl());
        soundKit.setFileUrl(request.fileUrl());
        soundKit.setAudioDemo(request.audioDemo());

        if (request.tags() != null) {
            soundKit.setTags(request.tags());
        }

        if (request.specifications() != null) {
            soundKit.setSpecifications(request.specifications());
        }

        SoundKit saved = soundKitRepository.save(soundKit);
        return SoundKitResponse.fromEntity(saved);
    }

    @Transactional
    public void deactivate(UUID id, JWTUserData user) {
        SoundKit soundKit = soundKitRepository.findByIdAndActiveTrue(new SoundKitId(id))
                .orElseThrow(() -> new SoundKitNotFoundException("SoundKit não encontrado: " + id));

        boolean isOwner = soundKit.getCreatorId().equals(user.userId());
        boolean isAdmin = user.role() == UserRole.ADMIN;

        if (!isOwner && !isAdmin) {
            throw new AccessDeniedException("Você não tem permissão para desativar este SoundKit");
        }

        soundKit.setActive(false);
        soundKitRepository.save(soundKit);
    }

    private Query buildQuery(SoundKitFilterParams filter) {
        Query query = new Query();
        query.addCriteria(Criteria.where("active").is(true));

        if (filter == null) {
            return query;
        }

        if (filter.q() != null && !filter.q().isBlank()) {
            query.addCriteria(Criteria.where("title").regex(Pattern.quote(filter.q().trim()), "i"));
        }

        if (filter.type() != null) {
            query.addCriteria(Criteria.where("type").is(filter.type()));
        }

        if (filter.tags() != null && !filter.tags().isEmpty()) {
            query.addCriteria(Criteria.where("tags").in(filter.tags()));
        }

        if (filter.minPrice() != null || filter.maxPrice() != null) {
            Criteria priceCriteria = Criteria.where("price");
            if (filter.minPrice() != null) {
                priceCriteria.gte(filter.minPrice());
            }
            if (filter.maxPrice() != null) {
                priceCriteria.lte(filter.maxPrice());
            }
            query.addCriteria(priceCriteria);
        }

        return query;
    }
}
