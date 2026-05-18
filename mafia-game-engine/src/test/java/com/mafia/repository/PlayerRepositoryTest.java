package com.mafia.repository;

import com.mafia.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataMongoTest
class PlayerRepositoryTest {

    @Autowired
    private PlayerRepository playerRepository;

    @AfterEach
    void cleanup() {
        playerRepository.deleteAll();
    }

    @Test
    void findByRoomId_returnsAllPlayersInRoom() {
        Player p1 = new Player("alice", "room-1");
        Player p2 = new Player("bob", "room-1");
        Player p3 = new Player("charlie", "room-2");
        playerRepository.save(p1);
        playerRepository.save(p2);
        playerRepository.save(p3);

        List<Player> players = playerRepository.findByRoomId("room-1");

        assertEquals(2, players.size());
        assertTrue(players.stream().anyMatch(p -> p.getUsername().equals("alice")));
        assertTrue(players.stream().anyMatch(p -> p.getUsername().equals("bob")));
    }

    @Test
    void findByUsernameAndRoomId_findsExactPlayer() {
        playerRepository.save(new Player("alice", "room-1"));
        playerRepository.save(new Player("alice", "room-2"));

        Optional<Player> found = playerRepository.findByUsernameAndRoomId("alice", "room-2");

        assertTrue(found.isPresent());
        assertEquals("room-2", found.get().getRoomId());
    }
}
