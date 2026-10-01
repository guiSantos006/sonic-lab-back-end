package com.soniclab.soniclabbackend.user.service;

import com.soniclab.soniclabbackend.user.config.TokenConfig;
import com.soniclab.soniclabbackend.user.dto.request.ForgotPasswordRequest;
import com.soniclab.soniclabbackend.user.dto.request.LoginRequest;
import com.soniclab.soniclabbackend.user.dto.request.RegisterUserRequest;
import com.soniclab.soniclabbackend.user.dto.request.ResetPasswordRequest;
import com.soniclab.soniclabbackend.user.dto.response.LoginResponse;
import com.soniclab.soniclabbackend.user.dto.response.RegisterUserResponse;
import com.soniclab.soniclabbackend.user.entity.User;
import com.soniclab.soniclabbackend.user.entity.UserRole;
import com.soniclab.soniclabbackend.user.exception.EmailAlreadyExistsException;
import com.soniclab.soniclabbackend.user.exception.InvalidTokenException;
import com.soniclab.soniclabbackend.user.exception.UserNotFoundException;
import com.soniclab.soniclabbackend.user.repository.UserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final TokenConfig tokenConfig;
    private final EmailService emailService;

    public AuthService(UserRepository userRepository,
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

    public LoginResponse login(LoginRequest request) {
        UsernamePasswordAuthenticationToken userAndPass =
                new UsernamePasswordAuthenticationToken(request.email(), request.password());
        Authentication authentication = authenticationManager.authenticate(userAndPass);

        User user = (User) authentication.getPrincipal();
        String token = tokenConfig.generateToken(user);

        return new LoginResponse(token);
    }

    public RegisterUserResponse register(RegisterUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException("E-mail já cadastrado.");
        }

        User newUser = new User(
                request.name(),
                request.email(),
                passwordEncoder.encode(request.password()),
                UserRole.USER
        );

        userRepository.save(newUser);

        return new RegisterUserResponse(newUser.getName(), newUser.getEmail());
    }

    public String forgotPassword(ForgotPasswordRequest request) {
        var userOpt = userRepository.findByEmail(request.email());

        if (userOpt.isEmpty()) {
            return "Se o e-mail estiver registrado, você receberá as instruções em breve.";
        }

        User user = userOpt.get();
        String resetToken = tokenConfig.generatePasswordResetToken(user);
        emailService.sendPasswordResetEmail(user.getEmail(), resetToken);

        return "E-mail de recuperação enviado com sucesso.";
    }

    public void resetPassword(ResetPasswordRequest request) {
        var decodedJWT = tokenConfig.validatePasswordResetToken(request.token())
                .orElseThrow(() -> new InvalidTokenException("Token inválido ou expirado."));

        String email = decodedJWT.getSubject();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("Usuário não encontrado."));

        String tokenPwdSig = decodedJWT.getClaim("pwdSig").asString();
        String currentPwdSig = TokenConfig.extractPasswordSignature(user.getPassword());

        if (tokenPwdSig == null || !tokenPwdSig.equals(currentPwdSig)) {
            throw new InvalidTokenException("Este link de recuperação já foi utilizado ou é inválido.");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }
}
