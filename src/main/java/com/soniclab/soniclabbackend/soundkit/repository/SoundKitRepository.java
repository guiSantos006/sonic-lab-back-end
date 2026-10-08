package com.soniclab.soniclabbackend.soundkit.repository;

import com.soniclab.soniclabbackend.soundkit.entity.SoundKit;
import com.soniclab.soniclabbackend.soundkit.entity.SoundKitId;
import com.soniclab.soniclabbackend.user.entity.UserId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SoundKitRepository extends MongoRepository<SoundKit, SoundKitId> {

    Optional<SoundKit> findByIdAndActiveTrue(SoundKitId id);

    Page<SoundKit> findByActiveTrue(Pageable pageable);

    
    Page<SoundKit> findByCreatorIdAndActiveTrue(UserId creatorId, Pageable pageable);

    long countByCreatorIdAndActiveTrue(UserId creatorId);
}


