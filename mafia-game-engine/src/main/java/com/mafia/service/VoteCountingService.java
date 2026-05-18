package com.mafia.service;

import com.mafia.entity.GameState;
import com.mafia.entity.Player;
import com.mafia.entity.Vote;
import com.mafia.repository.GameStateRepository;
import com.mafia.repository.PlayerRepository;
import com.mafia.repository.VoteRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class VoteCountingService {

    private final VoteRepository voteRepository;
    private final PlayerRepository playerRepository;
    private final GameStateRepository gameStateRepository;

    public VoteCountingService(
            VoteRepository voteRepository,
            PlayerRepository playerRepository,
            GameStateRepository gameStateRepository) {
        this.voteRepository = voteRepository;
        this.playerRepository = playerRepository;
        this.gameStateRepository = gameStateRepository;
    }

    public Map<String, Integer> countVotes(String roomId, int dayNumber) {
        List<Vote> votes = voteRepository.findByRoomIdAndDayNumber(roomId, dayNumber);
        
        return votes.stream()
                .collect(Collectors.groupingBy(
                        Vote::getVotedFor,
                        Collectors.summingInt(v -> 1)));
    }

    public String getEliminationTarget(String roomId, int dayNumber) {
        Map<String, Integer> voteCounts = countVotes(roomId, dayNumber);

        if (voteCounts.isEmpty()) {
            return null; // No votes
        }
        String target = voteCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
        if (target != null) {
            int maxVotes = voteCounts.get(target);
            long tieCounts = voteCounts.values().stream()
                    .filter(count -> count == maxVotes)
                    .count();

            if (tieCounts > 1) {
                return null;
            }
        }
        Player player = playerRepository.findByUsernameAndRoomId(target, roomId).orElse(null);
        if (player == null || !player.getStatus().equals("ALIVE")) {
            return null;
        }
        return target;
    }

    public void applyElimination(String roomId, String eliminatedPlayer) {
        GameState gameState = gameStateRepository.findByRoomId(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Game not found"));

        Player player = playerRepository.findByUsernameAndRoomId(eliminatedPlayer, roomId)
                .orElseThrow(() -> new IllegalArgumentException("Player not found"));

        player.setStatus("ELIMINATED");
        player.setVoteEligibleDayNumber(null);
        playerRepository.save(player);

        List<String> eliminated = new java.util.ArrayList<>(gameState.getEliminatedPlayers());
        eliminated.add(eliminatedPlayer);
        gameState.setEliminatedPlayers(eliminated);

        List<String> alive = new java.util.ArrayList<>(gameState.getAlivePlayers());
        alive.remove(eliminatedPlayer);
        gameState.setAlivePlayers(alive);

        gameStateRepository.save(gameState);
    }

    public int getPlayerCountByRole(String roomId, String role) {
        List<Player> players = playerRepository.findByRoomId(roomId);
        return (int) players.stream()
                .filter(p -> role.equals(p.getRole()) && "ALIVE".equals(p.getStatus()))
                .count();
    }
}
