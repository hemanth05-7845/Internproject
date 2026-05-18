package com.mafia.repository;

import com.mafia.entity.Player;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlayerRepository extends MongoRepository<Player, String> {
    Optional<Player> findByUsernameAndRoomId(String username, String roomId);
    List<Player> findByRoomId(String roomId);
}
