package com.mafia.controller;

import com.mafia.dto.response.AggregatedGameSnapshot;
import com.mafia.service.GameStateService;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

import java.util.List;
import java.util.stream.Stream;

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
    void TestShouldReturnOKWhenPinged() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.service").value("mafia-game-engine"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    static Stream<Arguments> gameStateScenarios() {
        return Stream.of(
                Arguments.of(
                        Named.of("TestShouldReturn200WhenGameStateRetrievedSuccessfully", (ServiceSetup) s -> {
                            AggregatedGameSnapshot snap = new AggregatedGameSnapshot(
                                    "DAY_DISCUSSION", 1, 1, List.of(), List.of(),
                                    List.of(), null, null, null, null,
                                    "NONE", List.of(), List.of(), List.of(),
                                    "CODE", "host1", "now");
                            when(s.getSnapshot("room-1")).thenReturn(snap);
                        }),
                        200,
                        jsonPath("$.phase").value("DAY_DISCUSSION")),
                Arguments.of(
                        Named.of("TestShouldReturn404WhenRoomNotFound",
                                (ServiceSetup) s -> doThrow(new IllegalArgumentException("Room not found"))
                                        .when(s).getSnapshot("room-1")),
                        404,
                        jsonPath("$.message").value("Room not found")),
                Arguments.of(
                        Named.of("TestShouldReturn500OnUnexpectedError",
                                (ServiceSetup) s -> doThrow(new RuntimeException("db down"))
                                        .when(s).getSnapshot("room-1")),
                        500,
                        jsonPath("$.message").value("Internal server error")));
    }

    @ParameterizedTest
    @MethodSource("gameStateScenarios")
    void gameState(ServiceSetup setup, int expectedStatus, ResultMatcher bodyMatcher) throws Exception {
        setup.configure(gameStateService);

        mockMvc.perform(get("/api/game-state/room-1"))
                .andExpect(status().is(expectedStatus))
                .andExpect(bodyMatcher);

        verify(gameStateService).getSnapshot("room-1");
    }

    static Stream<Arguments> startGameScenarios() {
        return Stream.of(
                Arguments.of(
                        Named.of("TestShouldReturn200WhenGameStartedSuccessfully", (ServiceSetup) s -> doNothing().when(s).startGame("room-1")),
                        200,
                        jsonPath("$.status").value("started")),
                Arguments.of(
                        Named.of("TestShouldReturn400WhenNotEnoughPlayers",
                                (ServiceSetup) s -> doThrow(new IllegalStateException("Need at least 6 players"))
                                        .when(s).startGame("room-1")),
                        400,
                        jsonPath("$.message").value("Need at least 6 players")),
                Arguments.of(
                        Named.of("TestShouldReturn400WhenIllegalArgumentProvided",
                                (ServiceSetup) s -> doThrow(new IllegalArgumentException("Room not found"))
                                        .when(s).startGame("room-1")),
                        400,
                        jsonPath("$.message").value("Room not found")),
                Arguments.of(
                        Named.of("TestShouldReturn500OnUnexpectedError",
                                (ServiceSetup) s -> doThrow(new RuntimeException("crash"))
                                        .when(s).startGame("room-1")),
                        500,
                        jsonPath("$.message").value("Internal server error")));
    }

    @ParameterizedTest
    @MethodSource("startGameScenarios")
    void startGame(ServiceSetup setup, int expectedStatus, ResultMatcher bodyMatcher) throws Exception {
        setup.configure(gameStateService);

        mockMvc.perform(post("/api/game-state/room-1/start"))
                .andExpect(status().is(expectedStatus))
                .andExpect(bodyMatcher);

        verify(gameStateService).startGame("room-1");
    }

    @FunctionalInterface
    interface ServiceSetup {
        void configure(GameStateService service);
        }
}
