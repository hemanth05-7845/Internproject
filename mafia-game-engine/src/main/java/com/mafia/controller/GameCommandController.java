package com.mafia.controller;

import com.mafia.dto.request.VoteCommandRequest;
import com.mafia.service.GameCommandService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rooms")
public class GameCommandController {

    private final GameCommandService gameCommandService;

    public GameCommandController(GameCommandService gameCommandService) {
        this.gameCommandService = gameCommandService;
    }

    @PostMapping("/{roomId}/vote")
    public ResponseEntity<Map<String, String>> submitVote(
            @PathVariable String roomId,
            @RequestBody VoteCommandRequest request) {
        gameCommandService.submitVote(roomId, request.voterId(), request.targetPlayerId());
        return ResponseEntity.ok(Map.of(
                "roomId", roomId,
                "voterId", request.voterId(),
                "votedFor", request.targetPlayerId(),
                "status", "vote-recorded"));
    }
}