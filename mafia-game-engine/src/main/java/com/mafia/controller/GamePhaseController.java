package com.mafia.controller;

import com.mafia.dto.request.NightActionSubmitRequest;
import com.mafia.dto.request.PoliceGuessSubmitRequest;
import com.mafia.dto.request.DoctorSaveSubmitRequest;
import com.mafia.service.GameLoopService;
import com.mafia.service.GameStateService;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/game")
public class GamePhaseController {

    private final GameStateService gameStateService;
    private final GameLoopService gameLoopService;

    public GamePhaseController(GameStateService gameStateService, GameLoopService gameLoopService) {
        this.gameStateService = gameStateService;
        this.gameLoopService = gameLoopService;
    }

    /** Host advances the phase. Handles all business logic before transitioning. */
    @PostMapping("/{roomId}/advance-phase")
    public Map<String, String> advancePhase(@PathVariable String roomId) {
        try {
            gameStateService.advancePhase(roomId);
            return Map.of("roomId", roomId, "status", "phase-advanced");
        } catch (Exception e) {
            return Map.of("status", "error", "message", e.getMessage());
        }
    }

    /** Mafia submits their night kill target during NIGHT phase. */
    @PostMapping("/{roomId}/submit-night-kill")
    public Map<String, String> submitNightKill(@PathVariable String roomId,
                                               @RequestBody NightActionSubmitRequest req) {
        try {
            gameStateService.submitNightKill(roomId, req.targetPlayer());
            return Map.of("roomId", roomId, "target", req.targetPlayer(), "status", "recorded");
        } catch (Exception e) {
            return Map.of("status", "error", "message", e.getMessage());
        }
    }

    /** Police submits their guess during POLICE_GUESS phase. */
    @PostMapping("/{roomId}/submit-police-guess")
    public Map<String, String> submitPoliceGuess(@PathVariable String roomId,
                                                 @RequestBody PoliceGuessSubmitRequest req) {
        try {
            gameStateService.submitPoliceGuess(roomId, req.suspectPlayer());
            return Map.of("roomId", roomId, "suspect", req.suspectPlayer(), "status", "recorded");
        } catch (Exception e) {
            return Map.of("status", "error", "message", e.getMessage());
        }
    }

    /** Doctors submit their save during DOCTOR_SAVE phase. Multiple doctors may save independently. */
    @PostMapping("/{roomId}/submit-doctor-save")
    public Map<String, String> submitDoctorSave(@PathVariable String roomId,
                                                @RequestBody DoctorSaveSubmitRequest req) {
        try {
            gameStateService.submitDoctorSave(roomId, req.savedPlayer());
            return Map.of("roomId", roomId, "saved", req.savedPlayer(), "status", "recorded");
        } catch (Exception e) {
            return Map.of("status", "error", "message", e.getMessage());
        }
    }

    /** Host finalises voting — counts votes, eliminates, checks win condition. */
    @PostMapping("/{roomId}/resolve-voting")
    public Map<String, String> resolveVoting(@PathVariable String roomId) {
        try {
            gameLoopService.resolveVoting(roomId);
            return Map.of("roomId", roomId, "status", "voting-resolved");
        } catch (Exception e) {
            return Map.of("status", "error", "message", e.getMessage());
        }
    }
}
