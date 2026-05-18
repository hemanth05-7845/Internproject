package com.mafia.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mafia.dto.response.AggregatedGameSnapshot;
import com.mafia.service.GameStateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GameStateController.class)
class GameStateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GameStateService gameStateService;

    @Test
    void health_returnsOk() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.service").value("mafia-game-engine"));
    }

    @Test
    void gameState_returnsSnapshot() throws Exception {
        AggregatedGameSnapshot snap = new AggregatedGameSnapshot(
            "DAY_DISCUSSION", 1, 1, List.of(), List.of(), List.of(), null, null, null, null, "NONE", List.of(), List.of(), List.of(), "CODE", "host1", "now"
        );
        when(gameStateService.getSnapshot("room-1")).thenReturn(snap);

        mockMvc.perform(get("/api/game-state/room-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("DAY_DISCUSSION"));
    }

    @Test
    void startGame_returnsStartedStatus() throws Exception {
        doNothing().when(gameStateService).startGame("room-1");

        mockMvc.perform(post("/api/game-state/room-1/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("started"));
    }

    @Test
    void startGame_returnsErrorOnFailure() throws Exception {
        doThrow(new IllegalStateException("Need at least 6 players")).when(gameStateService).startGame("room-1");

        mockMvc.perform(post("/api/game-state/room-1/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Need at least 6 players"));
    }
}
