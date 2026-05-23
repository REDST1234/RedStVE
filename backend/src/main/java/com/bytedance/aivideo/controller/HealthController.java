package com.bytedance.aivideo.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
public class HealthController {

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    @GetMapping({"/api/v1/system/health", "/api/health"})
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "profile", activeProfile,
                "timestamp", LocalDateTime.now().toString()
        );
    }
}
