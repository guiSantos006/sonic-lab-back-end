package com.soniclab.soniclabbackend.soundkit.service;

import com.soniclab.soniclabbackend.soundkit.dto.request.CreateSoundKitRequest;
import com.soniclab.soniclabbackend.soundkit.dto.request.SoundKitFilterParams;
import com.soniclab.soniclabbackend.soundkit.dto.request.UpdateSoundKitRequest;
import com.soniclab.soniclabbackend.soundkit.dto.response.PageResponse;
import com.soniclab.soniclabbackend.soundkit.dto.response.SoundKitDownloadResponse;
import com.soniclab.soniclabbackend.soundkit.dto.response.SoundKitResponse;
import com.soniclab.soniclabbackend.soundkit.entity.SoundKit;
import com.soniclab.soniclabbackend.soundkit.entity.SoundKitId;
import com.soniclab.soniclabbackend.soundkit.exception.SoundKitNotFoundException;
import com.soniclab.soniclabbackend.soundkit.repository.SoundKitRepository;
import com.soniclab.soniclabbackend.soundkit.storage.FileStorageService;
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

import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class SoundKitService {

    private final SoundKitRepository soundKitRepository;
    private final MongoTemplate mongoTemplate;
    private final FileStorageService storageService;

    public SoundKitService(
            SoundKitRepository soundKitRepository,
            MongoTemplate mongoTemplate,
            FileStorageService storageService
    ) {
        this.soundKitRepository = soundKitRepository;
        this.mongoTemplate = mongoTemplate;
        this.storageService = storageService;
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
        soundKit.setFileUrl(normalizeFileKey(request.fileUrl()));
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
    public SoundKitResponse update(UUID id, UpdateSoundKitRequest request, JWTUserData user) {
        SoundKit soundKit = soundKitRepository.findByIdAndActiveTrue(new SoundKitId(id))
                .orElseThrow(() -> new SoundKitNotFoundException("SoundKit não encontrado: " + id));

        // somente o dono do kit pode editar
        if (!soundKit.getCreatorId().equals(user.userId())) {
            throw new AccessDeniedException("Você não tem permissão para editar esse SoundKit");
        }

        soundKit.updateDetails(
                request.title(),
                request.description(),
                request.price(),
                request.type(),
                request.tags(),
                request.specifications()
        );

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

    /**
     * Gera sob demanda a URL temporária para download do pacote do kit (ZIP privado).
     * Requer autenticação do usuário. Suporta compatibilidade com registros antigos no MongoDB.
     */
    @Transactional(readOnly = true)
    public SoundKitDownloadResponse getDownloadUrl(UUID id, JWTUserData user) {
        if (user == null) {
            throw new AccessDeniedException("Usuário não autenticado para download");
        }

        SoundKit soundKit = soundKitRepository.findByIdAndActiveTrue(new SoundKitId(id))
                .orElseThrow(() -> new SoundKitNotFoundException("SoundKit não encontrado: " + id));

        String persistentKey = normalizeFileKey(soundKit.getFileUrl());
        String downloadUrl = storageService.generateDownloadUrl(persistentKey);

        return new SoundKitDownloadResponse(downloadUrl, 900L);
    }

    /**
     * Extrai e normaliza a chave estável de armazenamento (ex: "soundkits/packages/xyz.zip")
     * a partir de URLs locais com localhost, URLs do S3 ou chaves relativas.
     */
    public static String normalizeFileKey(String rawKeyOrUrl) {
        if (rawKeyOrUrl == null || rawKeyOrUrl.isBlank()) {
            throw new IllegalArgumentException("A referência ao arquivo do kit (fileUrl/fileKey) é obrigatória.");
        }
        String trimmed = rawKeyOrUrl.trim();

        // Descarta query parameters se presentes
        int queryIdx = trimmed.indexOf('?');
        if (queryIdx >= 0) {
            trimmed = trimmed.substring(0, queryIdx);
        }

        // Se contiver o prefixo canônico do pacote, extrai a partir dele
        int pkgIdx = trimmed.indexOf("soundkits/packages/");
        if (pkgIdx >= 0) {
            return trimmed.substring(pkgIdx);
        }

        // Se for URL HTTP/HTTPS genérica
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            try {
                URI uri = URI.create(trimmed);
                String path = uri.getPath();
                while (path.startsWith("/")) {
                    path = path.substring(1);
                }
                if (path.startsWith("uploads/")) {
                    path = path.substring("uploads/".length());
                }
                return path;
            } catch (Exception ignored) {
            }
        }

        // Normalização de separadores
        String normalized = trimmed.replace("\\", "/");
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
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
