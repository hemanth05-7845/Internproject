package com.mafia.service;

import com.mafia.entity.GameState;
import com.mafia.entity.Player;
import com.mafia.repository.GameStateRepository;
import com.mafia.repository.PlayerRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class WinConditionService {

    private final PlayerRepository playerRepository;
    private final GameStateRepository gameStateRepository;

    public WinConditionService(
            PlayerRepository playerRepository,
            GameStateRepository gameStateRepository) {
        this.playerRepository = playerRepository;
        this.gameStateRepository = gameStateRepository;
    }

    public String checkWinCondition(String roomId) {
        List<Player> players = playerRepository.findByRoomId(roomId);

        long aliveMafia = players.stream()
                .filter(p -> "MAFIA".equals(p.getRole()) && "ALIVE".equals(p.getStatus())).count();
        long aliveAll = players.stream().filter(p -> "ALIVE".equals(p.getStatus())).count();
        long aliveSoldiers = players.stream()
            .filter(p -> "SOLDIER".equals(p.getRole()) && "ALIVE".equals(p.getStatus())).count();
        if (aliveMafia == 0) return "VILLAGERS";
        // Exclude soldiers from the non-mafia count used to determine Mafia victory
        if (aliveMafia >= (aliveAll - aliveMafia - aliveSoldiers)) return "MAFIA";
        return "NONE";
    }

    public boolean hasGameEnded(String roomId) {
        return !checkWinCondition(roomId).equals("NONE");
    }

    public void concludeGame(String roomId, String winner) {
        GameState gameState = gameStateRepository.findByRoomId(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Game not found"));

        gameState.setWinner(winner);
        gameState.setPhase("GAME_OVER");
        gameStateRepository.save(gameState);
    }
}
