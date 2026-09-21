package com.soniclab.soniclabbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class SonicLabBackEndApplication {

    public static void main(String[] args) {
        SpringApplication.run(SonicLabBackEndApplication.class, args);
    }

}
