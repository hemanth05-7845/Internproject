package com.mafia.repository;

import com.mafia.entity.GameEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataMongoTest
class GameEventRepositoryTest {

    @Autowired
    private GameEventRepository gameEventRepository;

    @AfterEach
    void cleanup() {
        gameEventRepository.deleteAll();
    }

    @Test
    void findByRoomIdOrderByCreatedAtDesc_returnsSorted() {
        GameEvent e1 = new GameEvent("room-1", "TYPE_A", "event 1");
        e1.setCreatedAt(LocalDateTime.now().minusMinutes(5));
        
        GameEvent e2 = new GameEvent("room-1", "TYPE_B", "event 2");
        e2.setCreatedAt(LocalDateTime.now());
        
        GameEvent e3 = new GameEvent("room-2", "TYPE_C", "event 3");
        
        gameEventRepository.save(e1);
        gameEventRepository.save(e2);
        gameEventRepository.save(e3);

        List<GameEvent> events = gameEventRepository.findByRoomIdOrderByCreatedAtDesc("room-1");

        assertEquals(2, events.size());
        assertEquals("TYPE_B", events.get(0).getEventType()); // newest first
        assertEquals("TYPE_A", events.get(1).getEventType()); // oldest second
    }
}
