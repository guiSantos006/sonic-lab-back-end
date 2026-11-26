package com.soniclab.soniclabbackend.user;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.soniclab.soniclabbackend.user.config.JWTUserData;
import com.soniclab.soniclabbackend.user.config.TokenConfig;
import com.soniclab.soniclabbackend.user.controller.AuthController;
import com.soniclab.soniclabbackend.user.entity.User;
import com.soniclab.soniclabbackend.user.entity.UserRole;
import com.soniclab.soniclabbackend.user.exception.GlobalExceptionHandler;
import com.soniclab.soniclabbackend.user.repository.UserRepository;
import com.soniclab.soniclabbackend.user.service.AuthService;
import com.soniclab.soniclabbackend.user.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerSecurityTest {

    private static final String TEST_SECRET = "test-secret-key-with-strong-entropy-for-unit-tests-256-bits-min";
    private static final String TEST_ISSUER = "sonic-lab-api";

    private MockMvc mockMvc;
    private TokenConfig tokenConfig;
    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private User testUser;

    @BeforeEach
    void setUp() {
        tokenConfig = new TokenConfig();
        ReflectionTestUtils.setField(tokenConfig, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(tokenConfig, "expirationHours", 24L);
        ReflectionTestUtils.setField(tokenConfig, "issuer", TEST_ISSUER);

        userRepository = mock(UserRepository.class);
        passwordEncoder = new BCryptPasswordEncoder();
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
        EmailService emailService = mock(EmailService.class);

        AuthService authService = new AuthService(
                userRepository,
                passwordEncoder,
                authenticationManager,
                tokenConfig,
                emailService
        );

        AuthController authController = new AuthController(authService);
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        testUser = new User("Producer", "producer@soniclab.com", passwordEncoder.encode("OldPass123"), UserRole.USER);
        when(userRepository.findByEmail("producer@soniclab.com")).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("Tokens de acesso (purpose=access) NÃO podem redefinir senhas")
    void accessTokenCannotResetPassword() throws Exception {
        String accessToken = tokenConfig.generateToken(testUser);
        String originalHash = testUser.getPassword();

        String body = """
                {
                  "token": "%s",
                  "newPassword": "NewPassword123"
                }
                """.formatted(accessToken);

        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        assertEquals(originalHash, testUser.getPassword(), "Senha não pode ser alterada com token de acesso");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Token de recuperação válido redefine senha e invalida uso posterior (pwdSig)")
    void validPasswordResetTokenWorksOnceAndRejectsReuse() throws Exception {
        String resetToken = tokenConfig.generatePasswordResetToken(testUser);
        String originalHash = testUser.getPassword();

        String firstResetBody = """
                {
                  "token": "%s",
                  "newPassword": "NewPassword123"
                }
                """.formatted(resetToken);

        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstResetBody))
                .andExpect(status().isOk())
                .andExpect(content().string("Senha redefinida com sucesso!"));

        assertNotEquals(originalHash, testUser.getPassword());
        assertTrue(passwordEncoder.matches("NewPassword123", testUser.getPassword()));

        // Tentativa de reutilização do mesmo token após troca de senha deve falhar (pwdSig alterado)
        String secondResetBody = """
                {
                  "token": "%s",
                  "newPassword": "AnotherPassword456"
                }
                """.formatted(resetToken);

        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondResetBody))
                .andExpect(status().isBadRequest());

        assertTrue(passwordEncoder.matches("NewPassword123", testUser.getPassword()),
                "Reutilização do token não deve alterar a senha novamente");
    }

    @Test
    @DisplayName("Token de recuperação expirado deve ser rejeitado")
    void expiredPasswordResetTokenIsRejected() throws Exception {
        String expiredToken = JWT.create()
                .withIssuer(TEST_ISSUER)
                .withSubject(testUser.getEmail())
                .withClaim("userId", testUser.getId().uuid().toString())
                .withClaim("purpose", TokenConfig.PURPOSE_PASSWORD_RESET)
                .withClaim("pwdSig", TokenConfig.extractPasswordSignature(testUser.getPassword()))
                .withExpiresAt(Instant.now().minus(5, ChronoUnit.MINUTES))
                .sign(Algorithm.HMAC256(TEST_SECRET));

        String body = """
                {
                  "token": "%s",
                  "newPassword": "NewPassword123"
                }
                """.formatted(expiredToken);

        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Token assinado com chave inválida ou malformado deve ser rejeitado")
    void invalidSignatureTokenIsRejected() throws Exception {
        String forgedToken = JWT.create()
                .withIssuer(TEST_ISSUER)
                .withSubject(testUser.getEmail())
                .withClaim("purpose", TokenConfig.PURPOSE_PASSWORD_RESET)
                .withClaim("pwdSig", TokenConfig.extractPasswordSignature(testUser.getPassword()))
                .withExpiresAt(Instant.now().plus(15, ChronoUnit.MINUTES))
                .sign(Algorithm.HMAC256("wrong-secret-key-forged-token-1234567890"));

        String body = """
                {
                  "token": "%s",
                  "newPassword": "NewPassword123"
                }
                """.formatted(forgedToken);

        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Token de recuperação NÃO pode ser usado para autenticação como access token")
    void passwordResetTokenRejectedAsAccessToken() {
        String resetToken = tokenConfig.generatePasswordResetToken(testUser);
        Optional<JWTUserData> result = tokenConfig.validateAccessToken(resetToken);
        assertTrue(result.isEmpty(), "Token password_reset deve ser rejeitado em validateAccessToken");
    }

    @Test
    @DisplayName("Token sem claim purpose deve ser rejeitado tanto para acesso quanto para reset")
    void tokenWithoutPurposeIsRejected() {
        String tokenWithoutPurpose = JWT.create()
                .withIssuer(TEST_ISSUER)
                .withSubject(testUser.getEmail())
                .withExpiresAt(Instant.now().plus(15, ChronoUnit.MINUTES))
                .sign(Algorithm.HMAC256(TEST_SECRET));

        assertTrue(tokenConfig.validateAccessToken(tokenWithoutPurpose).isEmpty());
        assertTrue(tokenConfig.validatePasswordResetToken(tokenWithoutPurpose).isEmpty());
    }
}
