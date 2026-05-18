package com.mafia.service;

import com.mafia.entity.GameEvent;
import com.mafia.entity.GameState;
import com.mafia.entity.Vote;
import com.mafia.repository.GameEventRepository;
import com.mafia.repository.GameStateRepository;
import com.mafia.repository.VoteRepository;
import org.springframework.stereotype.Service;

/**
 * Handles the VOTING→ELIMINATION branch of the game loop.
 * advancePhase() for VOTING calls applyVoteElimination here,
 * then GameStateService handles the resulting ELIMINATION phase.
 */
@Service
public class GameLoopService {

    private final GameStateService gameStateService;
    private final VoteCountingService voteCountingService;
    private final GameStateRepository gameStateRepository;
    private final GameEventRepository gameEventRepository;
    private final VoteRepository voteRepository;

    public GameLoopService(GameStateService gameStateService,
                           VoteCountingService voteCountingService,
                           GameStateRepository gameStateRepository,
                           GameEventRepository gameEventRepository,
                           VoteRepository voteRepository) {
        this.gameStateService = gameStateService;
        this.voteCountingService = voteCountingService;
        this.gameStateRepository = gameStateRepository;
        this.gameEventRepository = gameEventRepository;
        this.voteRepository = voteRepository;
    }

    /**
     * Resolves the current VOTING phase: counts votes, eliminates the top-voted player
     * (or nobody on a tie), then advances to ELIMINATION check.
     * Called by GamePhaseController when host finalises VOTING.
     */
    public void resolveVoting(String roomId) {
        GameState gs = gameStateRepository.findByRoomId(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Game not found"));

        if (!"VOTING".equals(gs.getPhase())) {
            throw new IllegalStateException("Not in VOTING phase");
        }

        String target = voteCountingService.getEliminationTarget(roomId, gs.getDayNumber());
        if (target != null) {
            voteCountingService.applyElimination(roomId, target);
            gameEventRepository.save(new GameEvent(roomId, "PLAYER_ELIMINATED",
                    target + " was eliminated by village vote"));
        } else {
            gameEventRepository.save(new GameEvent(roomId, "VOTING_COMPLETE",
                    "Voting ended in a tie — no one was eliminated"));
        }

        // Advance to ELIMINATION check
        gameStateService.advancePhase(roomId); // VOTING → ELIMINATION
    }
}
