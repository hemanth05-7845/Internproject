package com.mafia.repository;

import com.mafia.entity.Vote;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataMongoTest
class VoteRepositoryTest {

    @Autowired
    private VoteRepository voteRepository;

    @AfterEach
    void cleanup() {
        voteRepository.deleteAll();
    }

    @Test
    void findByRoomIdAndDayNumber_returnsCorrectVotes() {
        voteRepository.save(new Vote("room-1", 1, "voter1", "targetA"));
        voteRepository.save(new Vote("room-1", 1, "voter2", "targetA"));
        voteRepository.save(new Vote("room-1", 2, "voter1", "targetB"));
        voteRepository.save(new Vote("room-2", 1, "voter3", "targetA"));

        List<Vote> votes = voteRepository.findByRoomIdAndDayNumber("room-1", 1);

        assertEquals(2, votes.size());
        assertTrue(votes.stream().allMatch(v -> v.getDayNumber() == 1));
        assertTrue(votes.stream().allMatch(v -> v.getRoomId().equals("room-1")));
    }
}
