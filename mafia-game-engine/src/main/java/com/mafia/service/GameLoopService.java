package com.mafia.service;

import com.mafia.client.EventServiceClient;
import com.mafia.entity.GameEvent;
import com.mafia.entity.GameState;
import com.mafia.repository.GameEventRepository;
import com.mafia.repository.GameStateRepository;
import org.springframework.stereotype.Service;

@Service
public class GameLoopService {

    private final PhaseTransitionService phaseTransitionService;
    private final VoteCountingService voteCountingService;
    private final GameStateRepository gameStateRepository;
    private final GameEventRepository gameEventRepository;
    private final EventServiceClient eventServiceClient;

    public GameLoopService(PhaseTransitionService phaseTransitionService,
            VoteCountingService voteCountingService,
            GameStateRepository gameStateRepository,
            GameEventRepository gameEventRepository,
            EventServiceClient eventServiceClient) {
        this.phaseTransitionService = phaseTransitionService;
        this.voteCountingService = voteCountingService;
        this.gameStateRepository = gameStateRepository;
        this.gameEventRepository = gameEventRepository;
        this.eventServiceClient = eventServiceClient;
    }

    public void resolveVoting(String roomId) {
        GameState gs = gameStateRepository.findByRoomId(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Game not found"));
        if (!"VOTING".equals(gs.getPhase())) throw new IllegalStateException("Not in VOTING phase");

        String target = voteCountingService.getEliminationTarget(roomId, gs.getDayNumber());
        if (target != null) {
            voteCountingService.applyElimination(roomId, target);
            pushEvent(roomId, "PLAYER_ELIMINATED", target + " was eliminated by village vote");
        } else {
            pushEvent(roomId, "VOTING_COMPLETE", "Voting ended in a tie — no one was eliminated");
        }

        phaseTransitionService.advancePhase(roomId);
    }

    private void pushEvent(String roomId, String type, String message) {
        gameEventRepository.save(new GameEvent(roomId, type, message));
        eventServiceClient.pushEvent(roomId, type, message);
    }
}