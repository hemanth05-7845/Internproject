package com.mafia.service;

import com.mafia.entity.Message;
import com.mafia.entity.Player;
import com.mafia.repository.MessageRepository;
import com.mafia.repository.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.Assert;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    MessageRepository messageRepository;
    @Mock
    PlayerRepository playerRepository;

    @InjectMocks
    MessageService service;

    @Test
    void TestShouldSaveMessageAndReturnMapWhenAlivePlayer() {
        when(playerRepository.findByUsernameAndRoomId("userA", "room-1"))
                .thenReturn(Optional.of(alivePlayer("userA", "room-1")));

        Map<String, String> result = service.postMessage("room-1", "userA", "hello there");

        assertEquals("sent", result.get("status"));
        assertEquals("room-1", result.get("roomId"));
        assertEquals("userA", result.get("sender"));

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(captor.capture());
        assertEquals("hello there", captor.getValue().getContent());
    }

    @Test
    void TestShouldTrimContentTo300CharsWhenOver300() {
        when(playerRepository.findByUsernameAndRoomId("userA", "room-1"))
                .thenReturn(Optional.of(alivePlayer("userA", "room-1")));

        String longContent = "x".repeat(350);
        service.postMessage("room-1", "userA", longContent);

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(captor.capture());

        assertNotNull(captor.getValue().getContent());
        assertEquals(300, captor.getValue().getContent().length());
    }

    @Test
    void TestShouldThrowIllegalArgumentExceptionWhenContentIsNull() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.postMessage("room-1", "userA", null));

        assertEquals("Empty message", ex.getMessage());
        verifyNoInteractions(messageRepository, playerRepository);
    }

    @Test
    void TestShouldThrowIllegalArgumentExceptionWhenContentIsBlank() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.postMessage("room-1", "userA", "   "));

        assertEquals("Empty message", ex.getMessage());
        verifyNoInteractions(messageRepository, playerRepository);
    }

    @Test
    void TestShouldThrowIllegalStateExceptionWhenPlayerIsDead() {
        Player dead = new Player("userA", "room-1");
        dead.setStatus("ELIMINATED");
        when(playerRepository.findByUsernameAndRoomId("userA", "room-1"))
                .thenReturn(Optional.of(dead));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.postMessage("room-1", "userA", "hello"));

        assertEquals("Dead players cannot chat", ex.getMessage());
        verify(messageRepository, never()).save(any());
    }

    @Test
    void TestShouldThrowIllegalStateExceptionWhenPlayerNotFound() {
        when(playerRepository.findByUsernameAndRoomId("userA", "room-1"))
                .thenReturn(Optional.empty());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.postMessage("room-1", "userA", "hello"));

        assertEquals("Dead players cannot chat", ex.getMessage());
        verify(messageRepository, never()).save(any());
    }

    @Test
    void TestShouldReturnMappedMessagesOrderedByRepo() {
        Message m1 = testMessage("room-1", "userA", "hello");
        Message m2 = testMessage("room-1", "userB", "world");
        when(messageRepository.findByRoomIdOrderByCreatedAtDesc("room-1"))
                .thenReturn(List.of(m1, m2));

        List<Map<String, Object>> result = service.getMessages("room-1");

        assertEquals(2, result.size());
        assertEquals("userA", result.get(0).get("sender"));
        assertEquals("hello", result.get(0).get("message"));
        assertNotNull(result.get(0).get("timestamp"));
        assertEquals("userB", result.get(1).get("sender"));
    }

    @Test
    void TestShouldLimitMessagesTo50() {
        List<Message> messages = java.util.stream.IntStream.range(0, 55)
                .mapToObj(i -> testMessage("room-1", "user" + i, "msg" + i))
                .toList();
        when(messageRepository.findByRoomIdOrderByCreatedAtDesc("room-1"))
                .thenReturn(messages);

        List<Map<String, Object>> result = service.getMessages("room-1");

        assertEquals(50, result.size());
    }

    @Test
    void TestShouldReturnEmptyListWhenNoMessages() {
        when(messageRepository.findByRoomIdOrderByCreatedAtDesc("room-1"))
                .thenReturn(List.of());

        List<Map<String, Object>> result = service.getMessages("room-1");

        assertTrue(result.isEmpty());
    }

    private Player alivePlayer(String username, String roomId) {
        Player p = new Player(username, roomId);
        p.setStatus("ALIVE");
        return p;
    }

    private Message testMessage(String roomId, String sender, String content) {
        Message m = new Message(roomId, sender, content);
        m.setCreatedAt(LocalDateTime.now());
        return m;
    }
}