package com.soniclab.soniclabbackend.user.controller;

import com.soniclab.soniclabbackend.user.entity.User;
import com.soniclab.soniclabbackend.user.repository.UserRepository;
import com.soniclab.soniclabbackend.user.service.EmailService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@RestController
@RequestMapping("/teste")
public class UserController {

    private final EmailService emailService;
    private final UserRepository userRepository;

    public UserController(EmailService emailService ,UserRepository userRepository) {
        this.emailService = emailService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public String test(){
        return "Testando Segurança";
    }

    @GetMapping("/users")
    public ResponseEntity<List<User>> listUsers() {
        List<User> users = userRepository.findAll();
        return ResponseEntity.ok(users);
    }
}