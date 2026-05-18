package com.mafia.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mafia.entity.Message;
import com.mafia.repository.MessageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MessageController.class)
class MessageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MessageRepository messageRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void postMessage_savesMessageAndReturnsSent() throws Exception {
        Map<String, String> req = Map.of("senderUsername", "userA", "content", "hello there");

        mockMvc.perform(post("/api/rooms/room-1/message")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("sent"))
                .andExpect(jsonPath("$.sender").value("userA"));

        verify(messageRepository).save(any(Message.class));
    }

    @Test
    void postMessage_returnsErrorIfEmpty() throws Exception {
        Map<String, String> req = Map.of("senderUsername", "userA", "content", "   ");

        mockMvc.perform(post("/api/rooms/room-1/message")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Empty message"));

        verify(messageRepository, never()).save(any());
    }

    @Test
    void postMessage_trimsLongContentAndDefaultsSender() throws Exception {
        String longContent = "x".repeat(350);
        Map<String, String> req = Map.of("content", longContent);

        mockMvc.perform(post("/api/rooms/room-1/message")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("sent"))
                .andExpect(jsonPath("$.sender").value("Unknown"));

        verify(messageRepository).save(argThat(message -> message.getContent().length() == 300));
    }

    @Test
    void getMessages_returnsMessageList() throws Exception {
        Message msg = new Message("room-1", "userA", "userA", "hello");
        when(messageRepository.findByRoomIdOrderByCreatedAtDesc("room-1")).thenReturn(List.of(msg));

        mockMvc.perform(get("/api/rooms/room-1/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sender").value("userA"))
                .andExpect(jsonPath("$[0].message").value("hello"));
    }

    @Test
    void getMessages_limitsToFifty() throws Exception {
        List<Message> messages = java.util.stream.IntStream.range(0, 55)
                .mapToObj(i -> new Message("room-1", "user" + i, "user" + i, "msg" + i))
                .toList();
        when(messageRepository.findByRoomIdOrderByCreatedAtDesc("room-1")).thenReturn(messages);

        mockMvc.perform(get("/api/rooms/room-1/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(50));
    }
}
