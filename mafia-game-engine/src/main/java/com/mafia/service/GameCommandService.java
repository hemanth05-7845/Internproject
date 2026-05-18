package com.mafia.service;

import com.mafia.entity.GameState;
import com.mafia.entity.Player;
import com.mafia.entity.Vote;
import com.mafia.repository.GameStateRepository;
import com.mafia.repository.PlayerRepository;
import com.mafia.repository.VoteRepository;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Thin command service kept for the generic action endpoint.
 * Specific commands (start, night-kill, police-guess) are handled
 * by GameStateService and GameLoopService directly.
 */
@Service
public class GameCommandService {

    private final GameStateRepository gameStateRepository;
    private final PlayerRepository playerRepository;
    private final VoteRepository voteRepository;

    public GameCommandService(GameStateRepository gameStateRepository,
                              PlayerRepository playerRepository,
                              VoteRepository voteRepository) {
        this.gameStateRepository = gameStateRepository;
        this.playerRepository = playerRepository;
        this.voteRepository = voteRepository;
    }

    public Map<String, String> submitVote(String roomId, String voterId, String votedFor) {
        try {
            GameState gs = gameStateRepository.findByRoomId(roomId)
                    .orElseThrow(() -> new IllegalArgumentException("Game not found"));
            if (!"VOTING".equals(gs.getPhase())) {
                return Map.of("status", "error", "message", "Voting phase not active");
            }
            if (voteRepository.existsByRoomIdAndDayNumberAndVoterId(roomId, gs.getDayNumber(), voterId)) {
                return Map.of("status", "error", "message", "Vote already submitted for this round");
            }

            Player voter = playerRepository.findByUsernameAndRoomId(voterId, roomId)
                    .orElseThrow(() -> new IllegalArgumentException("Player not found: " + voterId));
            boolean alive = "ALIVE".equals(voter.getStatus());
            boolean ghostVoteAllowed = voter.getVoteEligibleDayNumber() != null
                    && voter.getVoteEligibleDayNumber() == gs.getDayNumber();
            if (!alive && !ghostVoteAllowed) {
                return Map.of("status", "error", "message", "Dead players cannot vote now");
            }

            voteRepository.save(new Vote(roomId, gs.getDayNumber(), voterId, votedFor));
            return Map.of("roomId", roomId, "voterId", voterId,
                    "votedFor", votedFor, "status", "vote-recorded");
        } catch (Exception e) {
            return Map.of("status", "error", "message", e.getMessage());
        }
    }
}
