package com.mafia.controller;

import com.mafia.service.GameStateService;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class GameStateController {

    private final GameStateService gameStateService;

    public GameStateController(GameStateService gameStateService) {
        this.gameStateService = gameStateService;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
                "status", "ok",
                "service", "mafia-game-engine",
                "timestamp", Instant.now().toString());
    }

    @GetMapping("/game-state/{roomId}")
    public ResponseEntity<?> gameState(@PathVariable String roomId) {
        return ResponseEntity.ok(gameStateService.getSnapshot(roomId));
    }

    @PostMapping("/game-state/{roomId}/start")
    public ResponseEntity<Map<String, String>> startGame(@PathVariable String roomId) {
        gameStateService.startGame(roomId);
        return ResponseEntity.ok(Map.of("roomId", roomId, "status", "started"));
    }
}