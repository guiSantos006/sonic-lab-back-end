package com.soniclab.soniclabbackend.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Validação fail-fast de configurações críticas no startup da aplicação.
 * Em ambiente de produção (profile "prod"/"production" ou variável RENDER=true),
 * impede a inicialização caso JWT_SECRET, FRONTEND_URL ou MONGODB_URI estejam ausentes ou inseguros.
 */
@Component
public class ProductionStartupValidator {

    private static final Logger log = LoggerFactory.getLogger(ProductionStartupValidator.class);

    public static final String DEV_JWT_SECRET = "soniclab-super-secret-key-for-development-and-tests-2026";
    private static final int MIN_JWT_SECRET_BYTES = 32;
    private static final int MIN_JWT_DISTINCT_CHARS = 10;
    private static final double MIN_JWT_ENTROPY_BITS = 128.0;

    private final Environment environment;
    private final String jwtSecret;
    private final String frontendUrl;
    private final String mongoUri;
    private final String storageType;
    private final String s3Bucket;
    private final String awsRegion;

    public ProductionStartupValidator(
            Environment environment,
            @Value("${api.security.token.secret:}") String jwtSecret,
            @Value("${app.frontend.url:}") String frontendUrl,
            @Value("${spring.data.mongodb.uri:}") String mongoUri,
            @Value("${storage.type:local}") String storageType,
            @Value("${storage.s3.bucket:}") String s3Bucket,
            @Value("${storage.s3.region:us-east-2}") String awsRegion
    ) {
        this.environment = environment;
        this.jwtSecret = jwtSecret;
        this.frontendUrl = frontendUrl;
        this.mongoUri = mongoUri;
        this.storageType = storageType;
        this.s3Bucket = s3Bucket;
        this.awsRegion = awsRegion;
    }

    public static boolean isProduction(Environment environment) {
        if (environment == null) {
            return false;
        }
        return environment.acceptsProfiles(Profiles.of("prod", "production"))
                || "true".equalsIgnoreCase(environment.getProperty("RENDER"));
    }

    @PostConstruct
    public void validateOnStartup() {
        boolean production = isProduction(environment);
        log.info("Inicializando Sonic Lab Backend (modo={}, storage.type={})",
                production ? "production" : "development", storageType);

        if ("s3".equalsIgnoreCase(storageType)) {
            log.info("Storage S3 configurado (bucket={}, region={})", s3Bucket, awsRegion);
        } else {
            log.info("Storage local configurado.");
        }

        if (!production) {
            return;
        }

        validateJwtSecret(jwtSecret);
        validateFrontendUrl(frontendUrl);
        validateMongoUri(mongoUri);

        log.info("Validação de segurança para produção concluída com sucesso (CORS origin={}).",
                normalizeOrigin(frontendUrl));
    }

    static void validateJwtSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "Configuração inválida em produção: JWT_SECRET é obrigatório e não pode estar vazio.");
        }

        String trimmed = secret.trim();
        if (DEV_JWT_SECRET.equals(trimmed)
                || trimmed.toLowerCase().contains("for-development-and-tests")
                || trimmed.toLowerCase().contains("changeme")) {
            throw new IllegalStateException(
                    "Configuração inválida em produção: JWT_SECRET não pode utilizar o segredo padrão de desenvolvimento.");
        }

        byte[] bytes = trimmed.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_JWT_SECRET_BYTES) {
            throw new IllegalStateException(
                    "Configuração inválida em produção: JWT_SECRET deve possuir pelo menos 32 bytes (256 bits) para HS256.");
        }

        long distinctChars = trimmed.chars().distinct().count();
        double totalEntropy = calculateTotalEntropyBits(trimmed);
        if (distinctChars < MIN_JWT_DISTINCT_CHARS || totalEntropy < MIN_JWT_ENTROPY_BITS) {
            throw new IllegalStateException(
                    "Configuração inválida em produção: JWT_SECRET possui baixa entropia. Utilize uma chave criptograficamente aleatória (ex: openssl rand -base64 64).");
        }
    }

    static void validateFrontendUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalStateException(
                    "Configuração inválida em produção: FRONTEND_URL é obrigatória para configuração de CORS.");
        }
        String normalized = normalizeOrigin(url);
        if ("*".equals(normalized)) {
            throw new IllegalStateException(
                    "Configuração inválida em produção: FRONTEND_URL não pode ser '*'.");
        }
        URI uri;
        try {
            uri = URI.create(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Configuração inválida em produção: FRONTEND_URL não é uma URI válida.");
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme))
                || host == null || host.isBlank()) {
            throw new IllegalStateException(
                    "Configuração inválida em produção: FRONTEND_URL deve ser uma URL HTTP/HTTPS válida com host.");
        }
        if ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)) {
            throw new IllegalStateException(
                    "Configuração inválida em produção: FRONTEND_URL não pode apontar para localhost.");
        }
    }

    static void validateMongoUri(String uri) {
        if (uri == null || uri.isBlank()) {
            throw new IllegalStateException(
                    "Configuração inválida em produção: MONGODB_URI é obrigatória em produção.");
        }
        String lower = uri.trim().toLowerCase();
        if (lower.contains("@localhost") || lower.contains("//localhost") || lower.contains("127.0.0.1")) {
            throw new IllegalStateException(
                    "Configuração inválida em produção: MONGODB_URI não pode apontar para localhost (configure o MongoDB Atlas).");
        }
    }

    public static String normalizeOrigin(String origin) {
        if (origin == null) {
            return "";
        }
        return origin.trim().replaceAll("/+$", "");
    }

    private static double calculateTotalEntropyBits(String value) {
        Map<Integer, Integer> frequencies = new HashMap<>();
        value.chars().forEach(c -> frequencies.merge(c, 1, Integer::sum));
        double len = value.length();
        double shannonPerChar = 0.0;
        for (int count : frequencies.values()) {
            double p = count / len;
            shannonPerChar -= p * (Math.log(p) / Math.log(2));
        }
        return shannonPerChar * len;
    }
}
