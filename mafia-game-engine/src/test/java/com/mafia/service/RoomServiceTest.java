package com.mafia.service;

import com.mafia.entity.Player;
import com.mafia.entity.Room;
import com.mafia.repository.PlayerRepository;
import com.mafia.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock RoomRepository roomRepository;
    @Mock PlayerRepository playerRepository;
    @Mock GameStateService gameStateService;

    @InjectMocks RoomService service;

    @BeforeEach
    void setup() {
        lenient().when(roomRepository.save(any())).thenAnswer(i -> {
            Room r = i.getArgument(0);
            if (r.getId() == null) r.setId("room-1");
            return r;
        });
        lenient().when(playerRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void createRoom_createsRoomAndAddsHost() {
        when(roomRepository.findByRoomCode(anyString())).thenReturn(Optional.empty());

        Room r = service.createRoom("My Room", "host_user");

        assertNotNull(r);
        assertEquals("My Room", r.getName());
        assertEquals("host_user", r.getHostUsername());
        assertEquals("ACTIVE", r.getStatus());
        assertEquals(6, r.getRoomCode().length());
        assertTrue(r.getPlayerIds().contains("host_user"));

        verify(roomRepository).save(any(Room.class));
        verify(playerRepository).save(argThat(p -> p.getUsername().equals("host_user")));
        verify(gameStateService).initializeGameState("room-1");
    }

    @Test
    void getRoomById_throwsIfNotFound() {
        when(roomRepository.findById("invalid")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.getRoomById("invalid"));
    }

    @Test
    void getRoomByCode_findsRoomAndIgnoresCase() {
        Room r = new Room();
        when(roomRepository.findByRoomCode("XYZ123")).thenReturn(Optional.of(r));
        assertEquals(r, service.getRoomByCode("xyz123"));
    }

    @Test
    void joinRoom_addsPlayerIfActiveAndNotFull() {
        Room r = new Room("R", "host", "CODE", 2);
        r.setId("room-1");
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(r));
        when(playerRepository.findByUsernameAndRoomId("p2", "room-1")).thenReturn(Optional.empty());

        Room updated = service.joinRoom("room-1", "p2");

        assertTrue(updated.getPlayerIds().contains("host"));
        assertTrue(updated.getPlayerIds().contains("p2"));
        assertEquals(2, updated.getPlayerIds().size());
        verify(playerRepository).save(any(Player.class));
    }

    @Test
    void joinRoom_throwsIfRoomNotActive() {
        Room r = new Room("R", "host", "CODE", 2);
        r.setId("room-1");
        r.setStatus("CLOSED");
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(r));

        assertThrows(IllegalStateException.class, () -> service.joinRoom("room-1", "p2"));
    }

    @Test
    void joinRoom_throwsIfRoomFull() {
        Room r = new Room("R", "host", "CODE", 2);
        r.setId("room-1");
        r.setPlayerIds(new ArrayList<>(List.of("host", "p2"))); // 2/2 full
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(r));

        assertThrows(IllegalStateException.class, () -> service.joinRoom("room-1", "p3"));
    }

    @Test
    void joinRoom_isIdempotent() {
        Room r = new Room("R", "host", "CODE", 2);
        r.setId("room-1");
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(r));

        Room updated = service.joinRoom("room-1", "host"); // host is already in

        assertEquals(1, updated.getPlayerIds().size());
        verify(playerRepository, never()).save(any(Player.class)); // don't re-save player
    }

    @Test
    void leaveRoom_removesPlayerAndClosesIfEmpty() {
        Room r = new Room("R", "host", "CODE", 2);
        r.setId("room-1");
        r.setPlayerIds(new ArrayList<>(List.of("host", "p2")));
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(r));
        Player p2 = new Player("p2", "room-1");
        when(playerRepository.findByUsernameAndRoomId("p2", "room-1")).thenReturn(Optional.of(p2));

        service.leaveRoom("room-1", "p2");

        assertFalse(r.getPlayerIds().contains("p2"));
        assertTrue(r.getPlayerIds().contains("host"));
        assertEquals("ACTIVE", r.getStatus());
        verify(playerRepository).delete(p2);
        verify(roomRepository).save(r);
    }

    @Test
    void leaveRoom_closesRoomWhenLastPlayerLeaves() {
        Room r = new Room("R", "host", "CODE", 2);
        r.setId("room-1");
        r.setPlayerIds(new ArrayList<>(List.of("host")));
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(r));

        service.leaveRoom("room-1", "host");

        assertTrue(r.getPlayerIds().isEmpty());
        assertEquals("CLOSED", r.getStatus());
        verify(roomRepository).save(r);
    }

    @Test
    void joinRoomByCode_works() {
        Room r = new Room("R", "host", "CODE", 2);
        r.setId("room-1");
        when(roomRepository.findByRoomCode("CODE")).thenReturn(Optional.of(r));
        
        service.joinRoomByCode("code", "p2");
        
        assertTrue(r.getPlayerIds().contains("p2"));
    }
}
