package com.mafia.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mafia.dto.request.CreateRoomRequest;
import com.mafia.dto.request.JoinRoomByCodeRequest;
import com.mafia.entity.Player;
import com.mafia.entity.Room;
import com.mafia.service.RoomService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
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

    @Test
    void createRoom_returnsRoomData() throws Exception {
        Room r = new Room("Test Room", "host1", "CODE01", 12);
        r.setId("room-1");
        when(roomService.createRoom("Test Room", "host1")).thenReturn(r);

        CreateRoomRequest req = new CreateRoomRequest("Test Room", "host1");

        mockMvc.perform(post("/api/rooms/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value("room-1"))
                .andExpect(jsonPath("$.roomCode").value("CODE01"));
    }

    @Test
    void joinByCode_returnsRoomData() throws Exception {
        Room r = new Room("Test Room", "host1", "CODE01", 12);
        r.setId("room-1");
        when(roomService.joinRoomByCode("CODE01", "p2")).thenReturn(r);

        JoinRoomByCodeRequest req = new JoinRoomByCodeRequest("CODE01", "p2");

        mockMvc.perform(post("/api/rooms/join-by-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value("room-1"));
    }

    @Test
    void getByCode_returnsRoomData() throws Exception {
        Room r = new Room("Test Room", "host1", "CODE01", 12);
        r.setId("room-1");
        when(roomService.getRoomByCode("CODE01")).thenReturn(r);

        mockMvc.perform(get("/api/rooms/by-code/CODE01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value("room-1"));
    }

    @Test
    void getPlayers_returnsListOfMaps() throws Exception {
        Player p = new Player("p1", "room-1");
        when(roomService.getRoomPlayers("room-1")).thenReturn(List.of(p));

        mockMvc.perform(get("/api/rooms/room-1/players"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("p1"))
                .andExpect(jsonPath("$[0].alive").value(true));
    }

    @Test
    void getPlayersByCode_returnsListOfMapsAndHandlesNullRole() throws Exception {
        Room r = new Room("Test Room", "host1", "CODE01", 12);
        r.setId("room-1");
        Player p = new Player("p2", "room-1");
        p.setStatus("ELIMINATED");
        p.setRole(null);
        when(roomService.getRoomByCode("CODE01")).thenReturn(r);
        when(roomService.getRoomPlayers("room-1")).thenReturn(List.of(p));

        mockMvc.perform(get("/api/rooms/by-code/CODE01/players"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("p2"))
                .andExpect(jsonPath("$[0].alive").value(false))
                .andExpect(jsonPath("$[0].role").value(""));
    }

    @Test
    void startGame_returnsStartedStatus() throws Exception {
        Room r = new Room();
        when(roomService.getRoomById("room-1")).thenReturn(r);

        mockMvc.perform(post("/api/rooms/room-1/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("started"));
    }

    @Test
    void startGame_returnsErrorWhenRoomMissing() throws Exception {
        when(roomService.getRoomById("room-1")).thenThrow(new IllegalArgumentException("Room not found"));

        mockMvc.perform(post("/api/rooms/room-1/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Room not found"));
    }
}
