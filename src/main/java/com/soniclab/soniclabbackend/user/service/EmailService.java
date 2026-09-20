package com.soniclab.soniclabbackend.user.service;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendPasswordResetEmail(String recipientEmail, String token) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(recipientEmail);
            message.setSubject("SonicLab  Recuperação de Senha");

            String resetUrl = "http://localhost:8080/auth/reset-password?token=" + token;

            message.setText("Olá,  Recebemos um pedido para redefinir a sua senha na SonicLab.\n\n" +
                    "Clique aqui para criar uma nova senha (válido por 15 minutos):\n" +
                    resetUrl + "\n\n" +
                    "Se voce não pediu, ignore esse email.");

            mailSender.send(message);
            System.out.println("E-MAIL DE RECUPERAÇÃO ENVIADO COM SUCESS");
        } catch (Exception e) {
            System.out.println("ERRO AO ENVIAR E-MAIL DE RECUPERAÇÃO: " + e.getMessage());
            e.printStackTrace();
        }
    }
}