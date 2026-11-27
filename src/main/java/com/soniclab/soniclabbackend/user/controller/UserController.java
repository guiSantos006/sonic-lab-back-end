package com.soniclab.soniclabbackend.user.controller;

import com.soniclab.soniclabbackend.user.dto.response.UserResponse;
import com.soniclab.soniclabbackend.user.entity.User;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {

    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return UserResponse.fromEntity(user);
    }
}


