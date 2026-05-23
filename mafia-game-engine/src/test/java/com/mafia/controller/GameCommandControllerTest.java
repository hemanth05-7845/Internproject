package com.mafia.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mafia.dto.request.VoteCommandRequest;
import com.mafia.service.GameCommandService;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

import java.util.stream.Stream;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GameCommandController.class)
class GameCommandControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GameCommandService gameCommandService;

    @Autowired
    private ObjectMapper objectMapper;

    static Stream<Arguments> submitVoteScenarios() {
        return Stream.of(
                Arguments.of(
                        Named.of("TestShouldRecordVoteSuccessfullyWithValidInput",
                                (ServiceSetup) s -> doNothing().when(s).submitVote("room-1", "voterA", "targetB")),
                        200,
                        jsonPath("$.status").value("vote-recorded")),
                Arguments.of(
                        Named.of("TestShouldReturn400WhenGameNotFound",
                                (ServiceSetup) s -> doThrow(new IllegalArgumentException("Game not found"))
                                        .when(s).submitVote("room-1", "voterA", "targetB")),
                        400,
                        jsonPath("$.message").value("Game not found")),
                Arguments.of(
                        Named.of("TestShouldReturn400WhenVotingPhaseNotActive",
                                (ServiceSetup) s -> doThrow(new IllegalStateException("Voting phase not active"))
                                        .when(s).submitVote("room-1", "voterA", "targetB")),
                        400,
                        jsonPath("$.message").value("Voting phase not active")),
                Arguments.of(
                        Named.of("TestShouldReturn500OnUnexpectedError",
                                (ServiceSetup) s -> doThrow(new RuntimeException("db down"))
                                        .when(s).submitVote("room-1", "voterA", "targetB")),
                        500,
                        jsonPath("$.message").value("Internal server error")));
    }

    @ParameterizedTest
    @MethodSource("submitVoteScenarios")
    void submitVote(ServiceSetup setup, int expectedStatus, ResultMatcher bodyMatcher) throws Exception {
        setup.configure(gameCommandService);

        VoteCommandRequest req = new VoteCommandRequest("voterA", "targetB");

        mockMvc.perform(post("/api/rooms/room-1/vote")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().is(expectedStatus))
                .andExpect(bodyMatcher);

        verify(gameCommandService).submitVote("room-1", "voterA", "targetB");
    }

    @FunctionalInterface
    interface ServiceSetup {
        void configure(GameCommandService service);
    }
}