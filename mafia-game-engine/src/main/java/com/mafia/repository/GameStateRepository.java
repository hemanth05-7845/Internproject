package com.mafia.repository;

import com.mafia.entity.GameState;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GameStateRepository extends MongoRepository<GameState, String> {

    Optional<GameState> findByRoomId(String roomId);
}
