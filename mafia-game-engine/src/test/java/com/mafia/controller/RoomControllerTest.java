package com.mafia.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mafia.dto.request.CreateRoomRequest;
import com.mafia.dto.request.JoinRoomByCodeRequest;
import com.mafia.entity.Player;
import com.mafia.entity.Room;
import com.mafia.service.RoomService;
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

import java.util.List;
import java.util.stream.Stream;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RoomController.class)
class RoomControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @MockBean
        private RoomService roomService;

        @Autowired
        private ObjectMapper objectMapper;

        static Stream<Arguments> createRoomScenarios() {
                return Stream.of(
                                Arguments.of(
                                                Named.of("TestShouldReturnSucessWithValidInput", (ServiceSetup) s -> {
                                                        Room r = new Room("Test Room", "host1", "CODE01", 12);
                                                        r.setId("room-1");
                                                        when(s.createRoom("Test Room", "host1")).thenReturn(r);
                                                }),
                                                200,
                                                jsonPath("$.roomId").value("room-1"),
                                                jsonPath("$.roomCode").value("CODE01")),
                                Arguments.of(
                                                Named.of("TestShouldReturn400WhenInvalidRoomNameProvided",
                                                                (ServiceSetup) s -> doThrow(
                                                                                new IllegalArgumentException(
                                                                                                "Invalid room name"))
                                                                                .when(s)
                                                                                .createRoom("Test Room", "host1")),
                                                400,
                                                jsonPath("$.status").value("error"),
                                                jsonPath("$.message").value("Invalid room name")),
                                Arguments.of(
                                                Named.of("TestShouldReturn500OnUnexpectedError",

                                                                (ServiceSetup) s -> doThrow(
                                                                                new RuntimeException("db down"))
                                                                                .when(s)
                                                                                .createRoom("Test Room", "host1")),
                                                500,
                                                jsonPath("$.status").value("error"),
                                                jsonPath("$.message").value("Internal server error")));
        }

        @ParameterizedTest
        @MethodSource("createRoomScenarios")
        void createRoom(ServiceSetup setup, int expectedStatus,
                        ResultMatcher bodyMatcher, ResultMatcher extraMatcher) throws Exception {
                setup.configure(roomService);

                CreateRoomRequest req = new CreateRoomRequest("Test Room", "host1");

                mockMvc.perform(postJson("/api/rooms/create", req))
                                .andExpect(status().is(expectedStatus))
                                .andExpect(bodyMatcher)
                                .andExpect(extraMatcher);

                verify(roomService).createRoom("Test Room", "host1");
        }

        static Stream<Arguments> joinByCodeScenarios() {
                return Stream.of(
                                Arguments.of(
                                                Named.of("TestShouldReturnSuccessWithValidInput", (ServiceSetup) s -> {
                                                        Room r = new Room("Test Room", "host1", "CODE01", 12);
                                                        r.setId("room-1");
                                                        when(s.joinRoomByCode("CODE01", "p2")).thenReturn(r);
                                                }),
                                                200,
                                                jsonPath("$.roomId").value("room-1"),
                                                jsonPath("$.roomCode").value("CODE01")),
                                Arguments.of(
                                                Named.of("TestShouldReturn400WhenRoomNotFound",
                                                                (ServiceSetup) s -> doThrow(
                                                                                new IllegalArgumentException(
                                                                                                "Room not found"))
                                                                                .when(s)
                                                                                .joinRoomByCode("CODE01", "p2")),
                                                400,
                                                jsonPath("$.status").value("error"),
                                                jsonPath("$.message").value("Room not found")),
                                Arguments.of(
                                                Named.of("TestShouldReturn500OnUnexpectedError",

                                                                (ServiceSetup) s -> doThrow(
                                                                                new RuntimeException("db down"))
                                                                                .when(s)
                                                                                .joinRoomByCode("CODE01", "p2")),
                                                500,
                                                jsonPath("$.status").value("error"),
                                                jsonPath("$.message").value("Internal server error")));
        }

        @ParameterizedTest
        @MethodSource("joinByCodeScenarios")
        void joinByCode(ServiceSetup setup, int expectedStatus,
                        ResultMatcher bodyMatcher, ResultMatcher extraMatcher) throws Exception {
                setup.configure(roomService);

                JoinRoomByCodeRequest req = new JoinRoomByCodeRequest("CODE01", "p2");

                mockMvc.perform(postJson("/api/rooms/join-by-code", req))
                                .andExpect(status().is(expectedStatus))
                                .andExpect(bodyMatcher)
                                .andExpect(extraMatcher);

                verify(roomService).joinRoomByCode("CODE01", "p2");
        }

        static Stream<Arguments> getByCodeScenarios() {
                return Stream.of(
                                Arguments.of(
                                                Named.of("TestShouldReturnSuccessWithValidInput", (ServiceSetup) s -> {
                                                        Room r = new Room("Test Room", "host1", "CODE01", 12);
                                                        r.setId("room-1");
                                                        when(s.getRoomByCode("CODE01")).thenReturn(r);
                                                }),
                                                200,
                                                jsonPath("$.roomId").value("room-1"),
                                                jsonPath("$.roomCode").value("CODE01")),
                                Arguments.of(
                                                Named.of("TestShouldReturn404WhenRoomNotFound",
                                                                (ServiceSetup) s -> doThrow(
                                                                                new IllegalArgumentException(
                                                                                                "Room not found"))
                                                                                .when(s).getRoomByCode("CODE01")),
                                                404,
                                                jsonPath("$.status").value("error"),
                                                jsonPath("$.message").value("Room not found")),
                                Arguments.of(
                                                Named.of("TestShouldReturn500OnUnexpectedError",
                                                                (ServiceSetup) s -> doThrow(
                                                                                new RuntimeException("db down"))
                                                                                .when(s).getRoomByCode("CODE01")),
                                                500,
                                                jsonPath("$.status").value("error"),
                                                jsonPath("$.message").value("Internal server error")));
        }

        @ParameterizedTest
        @MethodSource("getByCodeScenarios")
        void getByCode(ServiceSetup setup, int expectedStatus,
                        ResultMatcher bodyMatcher, ResultMatcher extraMatcher) throws Exception {
                setup.configure(roomService);

                mockMvc.perform(get("/api/rooms/by-code/CODE01"))
                                .andExpect(status().is(expectedStatus))
                                .andExpect(bodyMatcher)
                                .andExpect(extraMatcher);

                verify(roomService).getRoomByCode("CODE01");
        }

        static Stream<Arguments> getPlayersScenarios() {
                return Stream.of(
                                Arguments.of(
                                                Named.of("TestShouldReturnSuccessWithValidInput", (ServiceSetup) s -> {
                                                        Player p = new Player("p1", "room-1");
                                                        when(s.getRoomPlayers("room-1")).thenReturn(List.of(p));
                                                }),
                                                200,
                                                jsonPath("$[0].name").value("p1"),
                                                jsonPath("$[0].alive").value(true)),
                                Arguments.of(
                                                Named.of("TestShouldReturn404WhenRoomNotFound",
                                                                (ServiceSetup) s -> doThrow(
                                                                                new IllegalArgumentException(
                                                                                                "Room not found"))
                                                                                .when(s).getRoomPlayers("room-1")),
                                                404,
                                                jsonPath("$.status").value("error"),
                                                jsonPath("$.message").value("Room not found")),
                                Arguments.of(
                                                Named.of("TestShouldReturn500OnUnexpectedError",
                                                                (ServiceSetup) s -> doThrow(
                                                                                new RuntimeException("db down"))
                                                                                .when(s).getRoomPlayers("room-1")),
                                                500,
                                                jsonPath("$.status").value("error"),
                                                jsonPath("$.message").value("Internal server error")));
        }

        @ParameterizedTest
        @MethodSource("getPlayersScenarios")
        void getPlayers(ServiceSetup setup, int expectedStatus,
                        ResultMatcher bodyMatcher, ResultMatcher extraMatcher) throws Exception {
                setup.configure(roomService);

                mockMvc.perform(get("/api/rooms/room-1/players"))
                                .andExpect(status().is(expectedStatus))
                                .andExpect(bodyMatcher)
                                .andExpect(extraMatcher);

                verify(roomService).getRoomPlayers("room-1");
        }

        static Stream<Arguments> getPlayersByCodeScenarios() {
                return Stream.of(
                                Arguments.of(
                                                Named.of("TestShouldReturnSuccessWithValidInput", (ServiceSetup) s -> {
                                                        Room r = new Room("Test Room", "host1", "CODE01", 12);
                                                        r.setId("room-1");
                                                        Player p = new Player("p2", "room-1");
                                                        p.setStatus("ELIMINATED");
                                                        p.setRole(null);
                                                        when(s.getRoomByCode("CODE01")).thenReturn(r);
                                                        when(s.getRoomPlayers("room-1")).thenReturn(List.of(p));
                                                }),
                                                200,
                                                jsonPath("$[0].name").value("p2"),
                                                jsonPath("$[0].alive").value(false)),
                                Arguments.of(
                                                Named.of("TestShouldReturn404WhenRoomNotFound",
                                                                (ServiceSetup) s -> doThrow(
                                                                                new IllegalArgumentException(
                                                                                                "Room not found"))
                                                                                .when(s).getRoomByCode("CODE01")),
                                                404,
                                                jsonPath("$.status").value("error"),
                                                jsonPath("$.message").value("Room not found")),
                                Arguments.of(
                                                Named.of("TestShouldReturn500OnUnexpectedError",
                                                                (ServiceSetup) s -> doThrow(
                                                                                new RuntimeException("db down"))
                                                                                .when(s).getRoomByCode("CODE01")),
                                                500,
                                                jsonPath("$.status").value("error"),
                                                jsonPath("$.message").value("Internal server error")),
                                Arguments.of(
                                                Named.of("TestShouldReturnSuccessWithValidInput",
                                                                (ServiceSetup) s -> {
                                                                        Room r = new Room("Test Room", "host1",
                                                                                        "CODE01", 12);
                                                                        r.setId("room-1");
                                                                        Player p = new Player("p2", "room-1");
                                                                        p.setStatus("ALIVE");
                                                                        p.setRole("MAFIA");
                                                                        when(s.getRoomByCode("CODE01")).thenReturn(r);
                                                                        when(s.getRoomPlayers("room-1"))
                                                                                        .thenReturn(List.of(p));
                                                                }),
                                                200,
                                                jsonPath("$[0].name").value("p2"),
                                                jsonPath("$[0].role").value("MAFIA")));
        }

        @ParameterizedTest
        @MethodSource("getPlayersByCodeScenarios")
        void getPlayersByCode(ServiceSetup setup, int expectedStatus,
                        ResultMatcher bodyMatcher, ResultMatcher extraMatcher) throws Exception {
                setup.configure(roomService);

                mockMvc.perform(get("/api/rooms/by-code/CODE01/players"))
                                .andExpect(status().is(expectedStatus))
                                .andExpect(bodyMatcher)
                                .andExpect(extraMatcher);

                verify(roomService).getRoomByCode("CODE01");
        }

        @FunctionalInterface
        interface ServiceSetup {
                void configure(RoomService s);
        }

        private MockHttpServletRequestBuilder postJson(String url, Object body) throws Exception {
                return post(url)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body));
        }
}