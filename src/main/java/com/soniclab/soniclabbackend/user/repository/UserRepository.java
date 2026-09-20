package com.soniclab.soniclabbackend.user.repository;

import com.soniclab.soniclabbackend.user.entity.User;
import com.soniclab.soniclabbackend.user.entity.UserId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<User, UserId> {
    Optional<UserDetails> findUserByEmail(String email);
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

}
