package com.soniclab.soniclabbackend.soundkit.service;

import com.soniclab.soniclabbackend.soundkit.dto.request.SoundKitFilterParams;
import com.soniclab.soniclabbackend.soundkit.dto.response.PageResponse;
import com.soniclab.soniclabbackend.soundkit.dto.response.SoundKitResponse;
import com.soniclab.soniclabbackend.soundkit.entity.SoundKit;
import com.soniclab.soniclabbackend.soundkit.entity.SoundKitId;
import com.soniclab.soniclabbackend.soundkit.exception.SoundKitNotFoundException;
import com.soniclab.soniclabbackend.soundkit.repository.SoundKitRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.authentication.AuthenticationManager;
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

    public SoundKitResponse getActiveById(UUID id) {
        return soundKitRepository.findByIdAndActiveTrue(new SoundKitId(id))
                .map(SoundKitResponse::fromEntity)
                .orElseThrow(() -> new SoundKitNotFoundException("SoundKit não encontrado"));
    }
    /*
    Para ser implementados:


    soundKitService.listTags();
    soundKitService.create(request, user);
    soundKitService.deactivate(id, user);
    */


    private Query buildQuery(SoundKitFilterParams filter) {
        Query query = new Query();
        query.addCriteria(Criteria.where("active").is(true));
        if (filter.q() != null && !filter.q().isBlank()){
            query.addCriteria(Criteria.where("title").regex(Pattern.quote(filter.q().trim()), "i"));
        }

        // Filtro por tipo de kit
        if (filter.type() != null) {
            query.addCriteria(Criteria.where("type").is(filter.type()));
        }

        // Filtro por tags (se o kit contiver qualquer uma das tags enviadas)
        if (filter.tags() != null && !filter.tags().isEmpty()) {
            query.addCriteria(Criteria.where("tags").in(filter.tags()));
        }

        // Filtro por faixa de preço
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
