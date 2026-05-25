package com.mafia.controller;

import com.mafia.dto.request.DoctorSaveSubmitRequest;
import com.mafia.dto.request.NightActionSubmitRequest;
import com.mafia.dto.request.PoliceGuessSubmitRequest;
import com.mafia.service.GameLoopService;
import com.mafia.service.NightPhaseService;
import com.mafia.service.PhaseTransitionService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/game")
public class GamePhaseController {

    private final NightPhaseService nightPhaseService;
    private final PhaseTransitionService phaseTransitionService;
    private final GameLoopService gameLoopService;

    public GamePhaseController(NightPhaseService nightPhaseService,
            PhaseTransitionService phaseTransitionService,
            GameLoopService gameLoopService) {
        this.nightPhaseService = nightPhaseService;
        this.phaseTransitionService = phaseTransitionService;
        this.gameLoopService = gameLoopService;
    }

    @PostMapping("/{roomId}/advance-phase")
    public ResponseEntity<Map<String, String>> advancePhase(@PathVariable String roomId) {
        phaseTransitionService.advancePhase(roomId);
        return ResponseEntity.ok(Map.of("roomId", roomId, "status", "phase-advanced"));
    }

    @PostMapping("/{roomId}/submit-night-kill")
    public ResponseEntity<Map<String, String>> submitNightKill(
            @PathVariable String roomId, @RequestBody NightActionSubmitRequest req) {
        nightPhaseService.submitNightKill(roomId, req.targetPlayer());
        return ResponseEntity.ok(Map.of("roomId", roomId, "target", req.targetPlayer(), "status", "recorded"));
    }

    @PostMapping("/{roomId}/submit-police-guess")
    public ResponseEntity<Map<String, String>> submitPoliceGuess(
            @PathVariable String roomId, @RequestBody PoliceGuessSubmitRequest req) {
        nightPhaseService.submitPoliceGuess(roomId, req.suspectPlayer());
        return ResponseEntity.ok(Map.of("roomId", roomId, "suspect", req.suspectPlayer(), "status", "recorded"));
    }

    @PostMapping("/{roomId}/submit-doctor-save")
    public ResponseEntity<Map<String, String>> submitDoctorSave(
            @PathVariable String roomId, @RequestBody DoctorSaveSubmitRequest req) {
        nightPhaseService.submitDoctorSave(roomId, req.savedPlayer());
        return ResponseEntity.ok(Map.of("roomId", roomId, "saved", req.savedPlayer(), "status", "recorded"));
    }

    @PostMapping("/{roomId}/resolve-voting")
    public ResponseEntity<Map<String, String>> resolveVoting(@PathVariable String roomId) {
        gameLoopService.resolveVoting(roomId);
        return ResponseEntity.ok(Map.of("roomId", roomId, "status", "voting-resolved"));
    }
}