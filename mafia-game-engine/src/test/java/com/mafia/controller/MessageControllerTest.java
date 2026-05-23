package com.mafia.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mafia.dto.request.MessageRequest;
import com.mafia.service.MessageService;
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
import java.util.Map;
import java.util.stream.Stream;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MessageController.class)
class MessageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MessageService messageService;

    @Autowired
    private ObjectMapper objectMapper;

    static Stream<Arguments> postMessageScenarios() {
        return Stream.of(
                Arguments.of(
                        Named.of("TestShouldReturnSuccessProvidedValidInput", (ServiceSetup) s -> when(s.postMessage("room-1", "userA", "hello there"))
                                .thenReturn(Map.of("status", "sent", "sender", "userA"))),
                        "hello there",
                        200,
                        jsonPath("$.status").value("sent"),
                        jsonPath("$.sender").value("userA")),
                Arguments.of(
                        Named.of("TestShouldReturn400WhenEmptyMessageProvided",
                                (ServiceSetup) s -> doThrow(new IllegalArgumentException("Empty message"))
                                        .when(s).postMessage("room-1", "userA", "   ")),
                        "   ",
                        400,
                        jsonPath("$.status").value("error"),
                        jsonPath("$.message").value("Empty message")),
                Arguments.of(
                        Named.of("TestShouldReturn400WhenIllegalStateProvided",
                                (ServiceSetup) s -> doThrow(new IllegalStateException("Room closed"))
                                        .when(s).postMessage("room-1", "userA", "hello there")),
                        "hello there",
                        400,
                        jsonPath("$.status").value("error"),
                        jsonPath("$.message").value("Room closed")),
                Arguments.of(
                        Named.of("TestShouldReturn500OnUnexpectedError",
                                (ServiceSetup) s -> doThrow(new RuntimeException("db down"))
                                        .when(s).postMessage("room-1", "userA", "hello there")),
                        "hello there",
                        500,
                        jsonPath("$.status").value("error"),
                        jsonPath("$.message").value("Internal server error")));
    }

    @ParameterizedTest
    @MethodSource("postMessageScenarios")
    void postMessage(ServiceSetup setup, String content, int expectedStatus,
            ResultMatcher bodyMatcher, ResultMatcher extraMatcher) throws Exception {
        setup.configure(messageService);

        MessageRequest req = new MessageRequest("userA", content);

        mockMvc.perform(postJson("/api/rooms/room-1/message", req))
                .andExpect(status().is(expectedStatus))
                .andExpect(bodyMatcher)
                .andExpect(extraMatcher);

        verify(messageService).postMessage("room-1", "userA", content);
    }

    static Stream<Arguments> getMessagesScenarios() {
        return Stream.of(
                Arguments.of(
                        Named.of("TestShouldReturnMessagesWhenRoomExists", (ServiceSetup) s -> when(s.getMessages("room-1"))
                                .thenReturn(List.of(
                                        Map.of("sender", "userA", "message", "hello")))),
                        200,
                        jsonPath("$[0].sender").value("userA")),
                Arguments.of(
                        Named.of("TestShouldReturn404WhenRoomNotFound",
                                (ServiceSetup) s -> doThrow(new IllegalArgumentException("Room not found"))
                                        .when(s).getMessages("room-1")),
                        404,
                        jsonPath("$.message").value("Room not found")),
                Arguments.of(
                        Named.of("TestShouldReturn500OnUnexpectedError",
                                (ServiceSetup) s -> doThrow(new RuntimeException("db down"))
                                        .when(s).getMessages("room-1")),
                        500,
                        jsonPath("$.message").value("Internal server error")));
    }

    @ParameterizedTest
    @MethodSource("getMessagesScenarios")
    void getMessages(ServiceSetup setup, int expectedStatus, ResultMatcher bodyMatcher) throws Exception {
        setup.configure(messageService);

        mockMvc.perform(get("/api/rooms/room-1/messages"))
                .andExpect(status().is(expectedStatus))
                .andExpect(bodyMatcher);

        verify(messageService).getMessages("room-1");
    }

    @FunctionalInterface
    interface ServiceSetup {
        void configure(MessageService s);
    }

    private MockHttpServletRequestBuilder postJson(String url, Object body) throws Exception {
        return post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
    }
}