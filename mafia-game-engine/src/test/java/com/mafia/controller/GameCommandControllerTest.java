package com.mafia.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mafia.dto.request.ActionCommandRequest;
import com.mafia.dto.request.VoteCommandRequest;
import com.mafia.entity.GameState;
import com.mafia.entity.Vote;
import com.mafia.repository.GameStateRepository;
import com.mafia.repository.VoteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GameCommandController.class)
class GameCommandControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GameStateRepository gameStateRepository;

    @MockBean
    private VoteRepository voteRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void submitVote_recordsVote() throws Exception {
        GameState gs = new GameState("room-1");
        gs.setPhase("VOTING");
        gs.setDayNumber(2);
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));

        VoteCommandRequest req = new VoteCommandRequest("voterA", "targetB");

        mockMvc.perform(post("/api/rooms/room-1/vote")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("vote-recorded"));

        verify(voteRepository).save(any(Vote.class));
    }

    @Test
    void submitVote_failsIfNotVotingPhase() throws Exception {
        GameState gs = new GameState("room-1");
        gs.setPhase("DAY_DISCUSSION");
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));

        VoteCommandRequest req = new VoteCommandRequest("voterA", "targetB");

        mockMvc.perform(post("/api/rooms/room-1/vote")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Voting phase not active"));

        verify(voteRepository, never()).save(any());
    }

    @Test
    void submitVote_returnsErrorIfGameMissing() throws Exception {
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.empty());

        VoteCommandRequest req = new VoteCommandRequest("voterA", "targetB");

        mockMvc.perform(post("/api/rooms/room-1/vote")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Game not found"));

        verify(voteRepository, never()).save(any());
    }

    @Test
    void submitAction_returnsGenericOk() throws Exception {
        ActionCommandRequest req = new ActionCommandRequest("player1", "guess", "targetA");

        mockMvc.perform(post("/api/rooms/room-1/action")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("action-recorded"));
    }
}
