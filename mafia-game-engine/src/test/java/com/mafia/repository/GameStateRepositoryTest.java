package com.mafia.repository;

import com.mafia.entity.GameState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataMongoTest
class GameStateRepositoryTest {

    @Autowired
    private GameStateRepository gameStateRepository;

    @AfterEach
    void cleanup() {
        gameStateRepository.deleteAll();
    }

    @Test
    void findByRoomId_findsGameState() {
        GameState gs = new GameState("room-1");
        gs.setPhase("NIGHT");
        gameStateRepository.save(gs);

        Optional<GameState> found = gameStateRepository.findByRoomId("room-1");

        assertTrue(found.isPresent());
        assertEquals("NIGHT", found.get().getPhase());
    }
}
