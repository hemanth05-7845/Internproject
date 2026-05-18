package com.mafia.controller;

import com.mafia.dto.request.ActionCommandRequest;
import com.mafia.dto.request.VoteCommandRequest;
import com.mafia.entity.GameState;
import com.mafia.entity.Vote;
import com.mafia.repository.GameStateRepository;
import com.mafia.repository.VoteRepository;
import com.mafia.service.GameCommandService;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rooms")
public class GameCommandController {

    private final GameCommandService gameCommandService;
    private final GameStateRepository gameStateRepository;
    private final VoteRepository voteRepository;

    public GameCommandController(GameStateRepository gameStateRepository,
                                 VoteRepository voteRepository, GameCommandService gameCommandService) {
        this.gameStateRepository = gameStateRepository;
        this.voteRepository = voteRepository;
        this.gameCommandService = gameCommandService;
    }

    @PostMapping("/{roomId}/vote")
    public Map<String, String> submitVote(@PathVariable String roomId,
                                          @RequestBody VoteCommandRequest request) {
        // try {
        //     GameState gs = gameStateRepository.findByRoomId(roomId)
        //             .orElseThrow(() -> new IllegalArgumentException("Game not found"));
        //     if (!"VOTING".equals(gs.getPhase())) {
        //         return Map.of("status", "error", "message", "Voting phase not active");
        //     }
        //     voteRepository.save(new Vote(roomId, gs.getDayNumber(),
        //             request.voterId(), request.targetPlayerId()));
        //     return Map.of("roomId", roomId, "voterId", request.voterId(),
        //             "votedFor", request.targetPlayerId(), "status", "vote-recorded");
        // } catch (Exception e) {
        //     return Map.of("status", "error", "message", e.getMessage());
        // }
        return gameCommandService.submitVote(roomId, request.voterId(), request.targetPlayerId());
    }

    @PostMapping("/{roomId}/action")
    public Map<String, String> submitAction(@PathVariable String roomId,
                                            @RequestBody ActionCommandRequest request) {
        // Generic action endpoint kept for backward compatibility.
        // Specific actions (night-kill, police-guess) have dedicated endpoints.
        return Map.of("roomId", roomId, "action", request.actionType(),
                "target", request.targetPlayerId() == null ? "" : request.targetPlayerId(),
                "status", "action-recorded");
    }
}
