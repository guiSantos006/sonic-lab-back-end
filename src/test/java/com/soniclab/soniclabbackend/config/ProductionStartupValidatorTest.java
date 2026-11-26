package com.soniclab.soniclabbackend.config;

import com.soniclab.soniclabbackend.health.HealthController;
import com.soniclab.soniclabbackend.user.config.RateLimitingFilter;
import com.soniclab.soniclabbackend.user.config.SecurityConfig;
import com.soniclab.soniclabbackend.user.config.SecurityFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ProductionStartupValidatorTest {

    private static final String STRONG_SECRET = "K9v#mP2x!qR8nL4wZ7cB5tY1hJ6dF3sA0eG9uN2pX4vQ7zM=";

    @Test
    @DisplayName("Em modo desenvolvimento, validações de produção não bloqueiam o startup")
    void developmentModeAllowsDefaultConfig() {
        MockEnvironment env = new MockEnvironment();
        ProductionStartupValidator validator = new ProductionStartupValidator(
                env,
                ProductionStartupValidator.DEV_JWT_SECRET,
                "http://localhost:3000",
                "mongodb://admin:password@localhost:27017/soniclab_db",
                "local",
                "",
                "us-east-2"
        );

        assertFalse(ProductionStartupValidator.isProduction(env));
        assertDoesNotThrow(validator::validateOnStartup);
    }

    @Test
    @DisplayName("Em produção (profile prod), configuração válida passa sem erros")
    void productionModeAcceptsValidConfiguration() {
        MockEnvironment env = new MockEnvironment().withProperty("RENDER", "false");
        env.setActiveProfiles("prod");

        ProductionStartupValidator validator = new ProductionStartupValidator(
                env,
                STRONG_SECRET,
                "https://sonic-lab.vercel.app/",
                "mongodb+srv://user:pass@cluster.mongodb.net/soniclab_db?retryWrites=true&w=majority",
                "s3",
                "soniclab-prod-bucket",
                "us-east-2"
        );

        assertTrue(ProductionStartupValidator.isProduction(env));
        assertDoesNotThrow(validator::validateOnStartup);
    }

    @Test
    @DisplayName("Em produção (RENDER=true mesmo sem profile prod), segredo de desenvolvimento deve falhar")
    void renderEnvRejectsDevJwtSecret() {
        MockEnvironment env = new MockEnvironment().withProperty("RENDER", "true");

        ProductionStartupValidator validator = new ProductionStartupValidator(
                env,
                ProductionStartupValidator.DEV_JWT_SECRET,
                "https://sonic-lab.vercel.app",
                "mongodb+srv://user:pass@cluster.mongodb.net/soniclab_db",
                "s3",
                "soniclab-prod-bucket",
                "us-east-2"
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, validator::validateOnStartup);
        assertTrue(ex.getMessage().contains("desenvolvimento"));
        assertFalse(ex.getMessage().contains(ProductionStartupValidator.DEV_JWT_SECRET));
    }

    @Test
    @DisplayName("Em produção, JWT_SECRET ausente, curto ou de baixa entropia deve falhar")
    void productionRejectsWeakOrMissingJwtSecrets() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        // Ausente
        ProductionStartupValidator missing = new ProductionStartupValidator(
                env, "", "https://sonic-lab.vercel.app", "mongodb+srv://u:p@cluster/db", "s3", "bucket", "us-east-2");
        assertThrows(IllegalStateException.class, missing::validateOnStartup);

        // Curto (< 32 bytes)
        ProductionStartupValidator shortSecret = new ProductionStartupValidator(
                env, "too-short-secret-12345", "https://sonic-lab.vercel.app", "mongodb+srv://u:p@cluster/db", "s3", "bucket", "us-east-2");
        assertThrows(IllegalStateException.class, shortSecret::validateOnStartup);

        // Repetitivo / baixa entropia
        ProductionStartupValidator lowEntropy = new ProductionStartupValidator(
                env, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "https://sonic-lab.vercel.app", "mongodb+srv://u:p@cluster/db", "s3", "bucket", "us-east-2");
        assertThrows(IllegalStateException.class, lowEntropy::validateOnStartup);
    }

    @Test
    @DisplayName("Em produção, CORS deve rejeitar '*' ou localhost e permitir apenas FRONTEND_URL")
    void productionCorsAllowsOnlyFrontendUrl() {
        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("prod");

        ProductionStartupValidator wildcardFrontend = new ProductionStartupValidator(
                prodEnv, STRONG_SECRET, "*", "mongodb+srv://u:p@cluster/db", "s3", "bucket", "us-east-2");
        assertThrows(IllegalStateException.class, wildcardFrontend::validateOnStartup);

        ProductionStartupValidator localhostFrontend = new ProductionStartupValidator(
                prodEnv, STRONG_SECRET, "http://localhost:3000", "mongodb+srv://u:p@cluster/db", "s3", "bucket", "us-east-2");
        assertThrows(IllegalStateException.class, localhostFrontend::validateOnStartup);

        SecurityConfig securityConfig = new SecurityConfig(
                mock(SecurityFilter.class),
                mock(RateLimitingFilter.class),
                prodEnv
        );
        org.springframework.test.util.ReflectionTestUtils.setField(
                securityConfig, "frontendUrl", "https://sonic-lab.vercel.app/");

        CorsConfigurationSource source = securityConfig.corsConfigurationSource();
        CorsConfiguration cors = source.getCorsConfiguration(new MockHttpServletRequest());

        assertNotNull(cors);
        assertEquals(List.of("https://sonic-lab.vercel.app"), cors.getAllowedOrigins());
        assertFalse(cors.getAllowedOrigins().contains("http://localhost:3000"));
        assertFalse(cors.getAllowedOrigins().contains("*"));
    }

    @Test
    @DisplayName("Em desenvolvimento, CORS preserva origens locais e FRONTEND_URL")
    void developmentCorsIncludesLocalOrigins() {
        MockEnvironment devEnv = new MockEnvironment();
        SecurityConfig securityConfig = new SecurityConfig(
                mock(SecurityFilter.class),
                mock(RateLimitingFilter.class),
                devEnv
        );
        org.springframework.test.util.ReflectionTestUtils.setField(
                securityConfig, "frontendUrl", "http://localhost:3000");

        CorsConfiguration cors = securityConfig.corsConfigurationSource()
                .getCorsConfiguration(new MockHttpServletRequest());

        assertNotNull(cors);
        assertTrue(cors.getAllowedOrigins().contains("http://localhost:3000"));
        assertTrue(cors.getAllowedOrigins().contains("http://127.0.0.1:3000"));
    }

    @Test
    @DisplayName("Endpoint /health retorna status UP sem expor detalhes sensíveis")
    void healthControllerReturnsSimpleUpStatus() {
        HealthController controller = new HealthController();
        ResponseEntity<Map<String, String>> response = controller.health();

        assertEquals(200, response.getStatusCode().value());
        assertEquals(Map.of("status", "UP"), response.getBody());
    }
}
