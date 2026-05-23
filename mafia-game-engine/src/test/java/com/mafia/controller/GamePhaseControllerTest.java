package com.mafia.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mafia.dto.request.DoctorSaveSubmitRequest;
import com.mafia.dto.request.NightActionSubmitRequest;
import com.mafia.dto.request.PoliceGuessSubmitRequest;
import com.mafia.service.GameLoopService;
import com.mafia.service.NightPhaseService;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.stream.Stream;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GamePhaseController.class)
class GamePhaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NightPhaseService nightPhaseService;

    @MockBean
    private GameLoopService gameLoopService;

    @Autowired
    private ObjectMapper objectMapper;

    static Stream<Arguments> advancePhaseScenarios() {
        return Stream.of(
                Arguments.of(
                        Named.of("TestShouldReturnSucessWhenProvidedWithValidInput", (ServiceSetup) s -> doNothing().when(s.night).advancePhase("room-1")),
                        200,
                        jsonPath("$.status").value("phase-advanced")),
                Arguments.of(
                        Named.of("TestShouldReturn400WhenIllegalState",
                                (ServiceSetup) s -> doThrow(new IllegalStateException("No room state"))
                                        .when(s.night).advancePhase("room-1")),
                        400,
                        jsonPath("$.message").value("No room state")),
                Arguments.of(
                        Named.of("TestShouldReturn500OnUnexpectedError",
                                (ServiceSetup) s -> doThrow(new RuntimeException("db down"))
                                        .when(s.night).advancePhase("room-1")),
                        500,
                        jsonPath("$.message").value("Internal server error")));
    }

    @ParameterizedTest
    @MethodSource("advancePhaseScenarios")
    void advancePhase(ServiceSetup setup, int expectedStatus, ResultMatcher bodyMatcher) throws Exception {
        setup.configure(nightPhaseService, gameLoopService);

        mockMvc.perform(post("/api/game/room-1/advance-phase"))
                .andExpect(status().is(expectedStatus))
                .andExpect(bodyMatcher);

        verify(nightPhaseService).advancePhase("room-1");
    }

    static Stream<Arguments> submitNightKillScenarios() {
        return Stream.of(
                Arguments.of(
                        Named.of("TestShouldReturn200WhenNightKillSubmittedSuccessfully",
                                (ServiceSetup) s -> doNothing().when(s.night).submitNightKill("room-1", "targetA")),
                        200,
                        jsonPath("$.status").value("recorded"),
                        jsonPath("$.target").value("targetA")),
                Arguments.of(
                        Named.of("TestShouldReturn400WhenNotNightPhase",

                                (ServiceSetup) s -> doThrow(new IllegalStateException("Not night phase"))
                                        .when(s.night).submitNightKill("room-1", "targetA")),
                        400,
                        jsonPath("$.message").value("Not night phase"),
                        jsonPath("$.status").value("error")),
                Arguments.of(
                        Named.of("TestShouldReturn500OnUnexpectedError",
                                (ServiceSetup) s -> doThrow(new RuntimeException("crash"))
                                        .when(s.night).submitNightKill("room-1", "targetA")),
                        500,
                        jsonPath("$.message").value("Internal server error"),
                        jsonPath("$.status").value("error")));
    }

    @ParameterizedTest
    @MethodSource("submitNightKillScenarios")
    void submitNightKill(ServiceSetup setup, int expectedStatus,
            ResultMatcher bodyMatcher, ResultMatcher extraMatcher) throws Exception {
        setup.configure(nightPhaseService, gameLoopService);
        NightActionSubmitRequest req = new NightActionSubmitRequest("targetA");

        mockMvc.perform(postJson("/api/game/room-1/submit-night-kill", req))
                .andExpect(status().is(expectedStatus))
                .andExpect(bodyMatcher)
                .andExpect(extraMatcher);

        verify(nightPhaseService).submitNightKill("room-1", "targetA");
    }

    static Stream<Arguments> submitPoliceGuessScenarios() {
        return Stream.of(
                Arguments.of(
                        Named.of("TestShouldReturn200WhenPoliceGuessSubmittedSuccessfully",
                                (ServiceSetup) s -> doNothing().when(s.night).submitPoliceGuess("room-1", "suspectB")),
                        200,
                        jsonPath("$.status").value("recorded"),
                        jsonPath("$.suspect").value("suspectB")),
                Arguments.of(
                        Named.of("TestShouldReturn400WhenWrongPhase",
                                (ServiceSetup) s -> doThrow(new IllegalStateException("Wrong phase"))
                                        .when(s.night).submitPoliceGuess("room-1", "suspectB")),
                        400,
                        jsonPath("$.message").value("Wrong phase"),
                        jsonPath("$.status").value("error")),
                Arguments.of(
                        Named.of("TestShouldReturn500OnUnexpectedError",
                                (ServiceSetup) s -> doThrow(new RuntimeException("crash"))
                                        .when(s.night).submitPoliceGuess("room-1", "suspectB")),
                        500,
                        jsonPath("$.message").value("Internal server error"),
                        jsonPath("$.status").value("error")));
    }

    @ParameterizedTest
    @MethodSource("submitPoliceGuessScenarios")
    void submitPoliceGuess(ServiceSetup setup, int expectedStatus,
            ResultMatcher bodyMatcher, ResultMatcher extraMatcher) throws Exception {
        setup.configure(nightPhaseService, gameLoopService);
        PoliceGuessSubmitRequest req = new PoliceGuessSubmitRequest("suspectB");

        mockMvc.perform(postJson("/api/game/room-1/submit-police-guess", req))
                .andExpect(status().is(expectedStatus))
                .andExpect(bodyMatcher)
                .andExpect(extraMatcher);

        verify(nightPhaseService).submitPoliceGuess("room-1", "suspectB");
    }

    static Stream<Arguments> submitDoctorSaveScenarios() {
        return Stream.of(
                Arguments.of(
                        Named.of("TestShouldReturn200WhenDoctorSaveSubmittedSuccessfully",
                                (ServiceSetup) s -> doNothing().when(s.night).submitDoctorSave("room-1", "savedC")),
                        200,
                        jsonPath("$.status").value("recorded"),
                        jsonPath("$.saved").value("savedC")),
                Arguments.of(
                        Named.of("TestShouldReturn400WhenNotNightPhase",
                                (ServiceSetup) s -> doThrow(new IllegalStateException("Not night phase"))
                                        .when(s.night).submitDoctorSave("room-1", "savedC")),
                        400,
                        jsonPath("$.message").value("Not night phase"),
                        jsonPath("$.status").value("error")),
                Arguments.of(
                        Named.of("TestShouldReturn500OnUnexpectedError",
                                (ServiceSetup) s -> doThrow(new RuntimeException("crash"))
                                        .when(s.night).submitDoctorSave("room-1", "savedC")),
                        500,
                        jsonPath("$.message").value("Internal server error"),
                        jsonPath("$.status").value("error")));
    }

    @ParameterizedTest
    @MethodSource("submitDoctorSaveScenarios")
    void submitDoctorSave(ServiceSetup setup, int expectedStatus,
            ResultMatcher bodyMatcher, ResultMatcher extraMatcher) throws Exception {
        setup.configure(nightPhaseService, gameLoopService);
        DoctorSaveSubmitRequest req = new DoctorSaveSubmitRequest("savedC");

        mockMvc.perform(postJson("/api/game/room-1/submit-doctor-save", req))
                .andExpect(status().is(expectedStatus))
                .andExpect(bodyMatcher)
                .andExpect(extraMatcher);

        verify(nightPhaseService).submitDoctorSave("room-1", "savedC");
    }

    static Stream<Arguments> resolveVotingScenarios() {
        return Stream.of(
                Arguments.of(
                        Named.of("TestShouldReturn200WhenVotingResolvedSuccessfully", (ServiceSetup) s -> doNothing().when(s.loop).resolveVoting("room-1")),
                        200,
                        jsonPath("$.status").value("voting-resolved")),
                Arguments.of(
                        Named.of("TestShouldReturn400WhenNoVotesToResolve",
                                (ServiceSetup) s -> doThrow(new IllegalStateException("No votes to resolve"))
                                        .when(s.loop).resolveVoting("room-1")),
                        400,
                        jsonPath("$.message").value("No votes to resolve")),
                Arguments.of(
                        Named.of("TestShouldReturn500OnUnexpectedError",
                                (ServiceSetup) s -> doThrow(new RuntimeException("crash"))
                                        .when(s.loop).resolveVoting("room-1")),
                        500,
                        jsonPath("$.message").value("Internal server error")));
    }

    @ParameterizedTest
    @MethodSource("resolveVotingScenarios")
    void resolveVoting(ServiceSetup setup, int expectedStatus, ResultMatcher bodyMatcher) throws Exception {
        setup.configure(nightPhaseService, gameLoopService);

        mockMvc.perform(post("/api/game/room-1/resolve-voting"))
                .andExpect(status().is(expectedStatus))
                .andExpect(bodyMatcher);

        verify(gameLoopService).resolveVoting("room-1");
    }

    static class Services {
        final NightPhaseService night;
        final GameLoopService loop;

        Services(NightPhaseService n, GameLoopService l) {
            this.night = n;
            this.loop = l;
        }
    }

    @FunctionalInterface
    interface ServiceSetup {
        void apply(Services s);

        default void configure(NightPhaseService n, GameLoopService l) {
            apply(new Services(n, l));
        }
    }

    private MockHttpServletRequestBuilder postJson(String url, Object body) throws Exception {
        return post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
    }
}