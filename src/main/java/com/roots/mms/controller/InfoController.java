package com.roots.mms.controller;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api")
public class InfoController {

    @GetMapping("/info")
    public Map<String, Object> getApiInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("name", "Member Management System API");
        info.put("version", "1.0.0");
        info.put("description", "Enterprise-grade Member Management System with JWT Authentication");
        info.put("java_version", System.getProperty("java.version"));
        info.put("timestamp", LocalDateTime.now());
        
        Map<String, String> features = new HashMap<>();
        features.put("authentication", "JWT with refresh tokens");
        features.put("authorization", "Role-based access control");
        features.put("database", "PostgreSQL");
        features.put("error_handling", "Enterprise-grade global exception handling");
        features.put("logging", "Structured logging with trace IDs");
        features.put("validation", "Bean validation with detailed error responses");
        
        info.put("features", features);
        return info;
    }

    @GetMapping("/health")
    public Map<String, Object> getHealth() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", LocalDateTime.now());
        health.put("service", "Member Management System");
        return health;
    }
}
