package com.soniclab.soniclabbackend.user.config;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.soniclab.soniclabbackend.user.entity.User;
import com.soniclab.soniclabbackend.user.entity.UserId;
import com.soniclab.soniclabbackend.user.entity.UserRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Component
public class TokenConfig {

    @Value("${api.security.token.secret}")
    private String secret;

    @Value("${api.security.token.expiration-hours:24}")
    private Long expirationHours;

    @Value("${api.security.token.issuer:sonic-lab-api}")
    private String issuer;

    public String generateToken(User user) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            return JWT.create()
                    .withIssuer(issuer)
                    .withSubject(user.getEmail())
                    .withClaim("userId", user.getId().uuid().toString())
                    .withClaim("role", user.getUserRole() != null ? user.getUserRole().name() : UserRole.USER.name())
                    .withExpiresAt(getExpirationDate())
                    .sign(algorithm);
        } catch (JWTCreationException exception) {
            throw new RuntimeException("Erro ao gerar token JWT", exception);
        }
    }

    public String generatePasswordResetToken(User user) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            return JWT.create()
                    .withIssuer(issuer)
                    .withSubject(user.getEmail())
                    .withClaim("userId", user.getId().uuid().toString())
                    .withClaim("purpose", "password_reset")
                    .withExpiresAt(Instant.now().plus(30, ChronoUnit.MINUTES))
                    .sign(algorithm);
        } catch (JWTCreationException exception) {
            throw new RuntimeException("Erro ao gerar token de redefinição de senha", exception);
        }
    }

    public Optional<JWTUserData> validateToken(String token) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            DecodedJWT decodedJWT = JWT.require(algorithm)
                    .withIssuer(issuer)
                    .build()
                    .verify(token);

            String rawUserId = decodedJWT.getClaim("userId").asString();
            String email = decodedJWT.getSubject();
            String rawRole = decodedJWT.getClaim("role").asString();

            UserId userId = (rawUserId != null && !rawUserId.isBlank())
                    ? new UserId(UUID.fromString(rawUserId))
                    : null;

            UserRole role = (rawRole != null && !rawRole.isBlank())
                    ? UserRole.valueOf(rawRole)
                    : UserRole.USER;

            JWTUserData userData = JWTUserData.builder()
                    .userId(userId)
                    .email(email)
                    .role(role)
                    .build();

            return Optional.of(userData);
        } catch (JWTVerificationException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private Instant getExpirationDate() {
        return Instant.now().plus(expirationHours, ChronoUnit.HOURS);
    }
}
