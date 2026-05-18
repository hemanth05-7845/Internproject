package com.mafia.repository;

import com.mafia.entity.Room;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataMongoTest
class RoomRepositoryTest {

    @Autowired
    private RoomRepository roomRepository;

    @AfterEach
    void cleanup() {
        roomRepository.deleteAll();
    }

    @Test
    void findByRoomCode_findsCorrectRoom() {
        Room r1 = new Room("Alpha", "host1", "CODE01", 8);
        Room r2 = new Room("Beta", "host2", "CODE02", 12);
        roomRepository.save(r1);
        roomRepository.save(r2);

        Optional<Room> found = roomRepository.findByRoomCode("CODE02");

        assertTrue(found.isPresent());
        assertEquals("Beta", found.get().getName());
        assertEquals("host2", found.get().getHostUsername());
    }

    @Test
    void findByRoomCode_returnsEmptyIfNotFound() {
        Optional<Room> found = roomRepository.findByRoomCode("INVALID");
        assertTrue(found.isEmpty());
    }
}
