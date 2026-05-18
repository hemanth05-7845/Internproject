package com.mafia.controller;

import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
public class HealthController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
                "status", "ok",
                "service", "mafia-game-engine",
                "timestamp", Instant.now().toString());
    }

    @GetMapping("/snapshot/demo")
    public Map<String, Object> snapshot() {
        return Map.of(
                "phase", "LOBBY",
                "players", 0,
                "source", "spring-engine-demo");
    }
}
