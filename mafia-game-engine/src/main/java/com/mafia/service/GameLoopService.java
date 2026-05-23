package com.mafia.service;

import com.mafia.client.EventServiceClient;
import com.mafia.entity.GameEvent;
import com.mafia.entity.GameState;
import com.mafia.repository.GameEventRepository;
import com.mafia.repository.GameStateRepository;
import org.springframework.stereotype.Service;

@Service
public class GameLoopService {

    private final NightPhaseService nightPhaseService;
    private final VoteCountingService voteCountingService;
    private final GameStateRepository gameStateRepository;
    private final GameEventRepository gameEventRepository;
    private final EventServiceClient eventServiceClient;

    public GameLoopService(NightPhaseService nightPhaseService,
            VoteCountingService voteCountingService,
            GameStateRepository gameStateRepository,
            GameEventRepository gameEventRepository,
            EventServiceClient eventServiceClient) {
        this.nightPhaseService = nightPhaseService;
        this.voteCountingService = voteCountingService;
        this.gameStateRepository = gameStateRepository;
        this.gameEventRepository = gameEventRepository;
        this.eventServiceClient = eventServiceClient;
    }

    public void resolveVoting(String roomId) {
        GameState gs = gameStateRepository.findByRoomId(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Game not found"));

        if (!"VOTING".equals(gs.getPhase())) {
            throw new IllegalStateException("Not in VOTING phase");
        }

        String target = voteCountingService.getEliminationTarget(roomId, gs.getDayNumber());
        if (target != null) {
            voteCountingService.applyElimination(roomId, target);
            String elimMsg = target + " was eliminated by village vote";
            gameEventRepository.save(new GameEvent(roomId, "PLAYER_ELIMINATED", elimMsg));
            eventServiceClient.pushEvent(roomId, "PLAYER_ELIMINATED", elimMsg);
        } else {
            String tieMsg = "Voting ended in a tie — no one was eliminated";
            gameEventRepository.save(new GameEvent(roomId, "VOTING_COMPLETE", tieMsg));
            eventServiceClient.pushEvent(roomId, "VOTING_COMPLETE", tieMsg);
        }

        nightPhaseService.advancePhase(roomId);
    }
}