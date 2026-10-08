package com.soniclab.soniclabbackend.user.controller;

import com.soniclab.soniclabbackend.user.config.TokenConfig;
import com.soniclab.soniclabbackend.user.dto.request.ForgotPasswordRequest;
import com.soniclab.soniclabbackend.user.dto.request.LoginRequest;
import com.soniclab.soniclabbackend.user.dto.request.RegisterUserRequest;
import com.soniclab.soniclabbackend.user.dto.request.ResetPasswordRequest;
import com.soniclab.soniclabbackend.user.dto.response.LoginResponse;
import com.soniclab.soniclabbackend.user.dto.response.RegisterUserResponse;
import com.soniclab.soniclabbackend.user.entity.User;
import com.soniclab.soniclabbackend.user.entity.UserRole;
import com.soniclab.soniclabbackend.user.repository.UserRepository;
import com.soniclab.soniclabbackend.user.service.EmailService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final TokenConfig tokenConfig;
    private final EmailService emailService;

    public AuthController(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          AuthenticationManager authenticationManager,
                          TokenConfig tokenConfig,
                          EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.tokenConfig = tokenConfig;
        this.emailService = emailService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        UsernamePasswordAuthenticationToken userAndPass = new UsernamePasswordAuthenticationToken(request.email(), request.password());
        Authentication authentication = authenticationManager.authenticate(userAndPass);

        User user = (User) authentication.getPrincipal();
        String token = tokenConfig.generateToken(user);

        return ResponseEntity.ok(new LoginResponse(token));
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterUserResponse> register(@Valid @RequestBody RegisterUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        User newUser = new User(
                request.name(),
                request.email(),
                passwordEncoder.encode(request.password()),
                UserRole.USER
        );

        userRepository.save(newUser);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new RegisterUserResponse(newUser.getName(), newUser.getEmail()));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        var user = userRepository.findByEmail(request.email());

        if (user.isEmpty()) {
            return ResponseEntity.ok("Se o e-mail estiver registrado, você receberá as instruções em breve.");
        }

        String resetToken = tokenConfig.generatePasswordResetToken(user.get());
        emailService.sendPasswordResetEmail(user.get().getEmail(), resetToken);

        return ResponseEntity.ok("E-mail de recuperação enviado com sucesso.");
    }

    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@RequestBody @Valid ResetPasswordRequest request) {
        try {
            var tokenData = tokenConfig.validateToken(request.token());

            if (tokenData.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Token inválido ou expirado.");
            }

            String email = tokenData.get().email();

            var userOpt = userRepository.findByEmail(email);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Usuário não encontrado.");
            }

            var user = userOpt.get();
            user.setPassword(passwordEncoder.encode(request.newPassword()));
            userRepository.save(user);

            return ResponseEntity.ok("Senha redefinida com sucesso!");

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Erro ao redefinir senha: Token inválido ou expirado.");
        }
    }
}
