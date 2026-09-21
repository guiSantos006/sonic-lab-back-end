package com.soniclab.soniclabbackend.user.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async
    public void sendPasswordResetEmail(String recipientEmail, String token) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(recipientEmail);
            message.setSubject("SonicLab - Recuperação de Senha");

            String resetUrl = frontendUrl + "/reset-password?token=" + token;

            message.setText("Olá,\n\n" +
                    "Recebemos uma solicitação para redefinir a sua senha na SonicLab.\n\n" +
                    "Acesse o link abaixo para criar uma nova senha (válido por 30 minutos):\n" +
                    resetUrl + "\n\n" +
                    "Se você não solicitou a alteração, ignore este e-mail.");

            mailSender.send(message);
            log.info("E-mail de recuperação de senha enviado com sucesso para: {}", recipientEmail);
        } catch (Exception e) {
            log.error("Erro ao enviar e-mail de recuperação para {}: {}", recipientEmail, e.getMessage(), e);
        }
    }
}
