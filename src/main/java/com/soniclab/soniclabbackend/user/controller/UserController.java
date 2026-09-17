package com.soniclab.soniclabbackend.user.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/teste")
public class UserController {

    @GetMapping
    public String test(){
        return "Testando Segurança";
    }
}
