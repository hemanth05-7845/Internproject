package com.mafia.repository;

import com.mafia.entity.Vote;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VoteRepository extends MongoRepository<Vote, String> {
    List<Vote> findByRoomIdAndDayNumber(String roomId, int dayNumber);
    boolean existsByRoomIdAndDayNumberAndVoterId(String roomId, int dayNumber, String voterId);
}
