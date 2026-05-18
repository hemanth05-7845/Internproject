package com.mafia.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mafia.dto.request.NightActionSubmitRequest;
import com.mafia.dto.request.PoliceGuessSubmitRequest;
import com.mafia.service.GameLoopService;
import com.mafia.service.GameStateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GamePhaseController.class)
class GamePhaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GameStateService gameStateService;

    @MockBean
    private GameLoopService gameLoopService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void advancePhase_returnsSuccess() throws Exception {
        doNothing().when(gameStateService).advancePhase("room-1");

        mockMvc.perform(post("/api/game/room-1/advance-phase"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("phase-advanced"));
    }

    @Test
    void advancePhase_returnsErrorOnFailure() throws Exception {
        doThrow(new IllegalStateException("No room state")).when(gameStateService).advancePhase("room-1");

        mockMvc.perform(post("/api/game/room-1/advance-phase"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("No room state"));
    }

    @Test
    void submitNightKill_returnsSuccess() throws Exception {
        doNothing().when(gameStateService).submitNightKill("room-1", "targetA");
        NightActionSubmitRequest req = new NightActionSubmitRequest("targetA");

        mockMvc.perform(post("/api/game/room-1/submit-night-kill")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("recorded"))
                .andExpect(jsonPath("$.target").value("targetA"));
    }

    @Test
    void submitNightKill_returnsErrorOnFailure() throws Exception {
        doThrow(new IllegalStateException("Not night phase")).when(gameStateService).submitNightKill("room-1", "targetA");
        NightActionSubmitRequest req = new NightActionSubmitRequest("targetA");

        mockMvc.perform(post("/api/game/room-1/submit-night-kill")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Not night phase"));
    }

    @Test
    void submitPoliceGuess_returnsSuccess() throws Exception {
        doNothing().when(gameStateService).submitPoliceGuess("room-1", "suspectB");
        PoliceGuessSubmitRequest req = new PoliceGuessSubmitRequest("suspectB");

        mockMvc.perform(post("/api/game/room-1/submit-police-guess")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("recorded"))
                .andExpect(jsonPath("$.suspect").value("suspectB"));
    }

    @Test
    void submitPoliceGuess_returnsErrorOnFailure() throws Exception {
        doThrow(new IllegalStateException("Wrong phase")).when(gameStateService).submitPoliceGuess("room-1", "suspectB");
        PoliceGuessSubmitRequest req = new PoliceGuessSubmitRequest("suspectB");

        mockMvc.perform(post("/api/game/room-1/submit-police-guess")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Wrong phase"));
    }

    @Test
    void resolveVoting_returnsSuccess() throws Exception {
        doNothing().when(gameLoopService).resolveVoting("room-1");

        mockMvc.perform(post("/api/game/room-1/resolve-voting"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("voting-resolved"));
    }

    @Test
    void resolveVoting_returnsErrorOnFailure() throws Exception {
        doThrow(new IllegalStateException("No votes to resolve")).when(gameLoopService).resolveVoting("room-1");

        mockMvc.perform(post("/api/game/room-1/resolve-voting"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("No votes to resolve"));
    }
}
