package com.mafia.repository;

import com.mafia.entity.GameEvent;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GameEventRepository extends MongoRepository<GameEvent, String> {
    List<GameEvent> findByRoomIdOrderByCreatedAtDesc(String roomId);
}
