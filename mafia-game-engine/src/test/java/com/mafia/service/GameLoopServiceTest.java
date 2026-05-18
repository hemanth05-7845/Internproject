package com.mafia.service;

import com.mafia.entity.GameEvent;
import com.mafia.entity.GameState;
import com.mafia.repository.GameEventRepository;
import com.mafia.repository.GameStateRepository;
import com.mafia.repository.VoteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameLoopServiceTest {

    @Mock GameStateService gameStateService;
    @Mock VoteCountingService voteCountingService;
    @Mock GameStateRepository gameStateRepository;
    @Mock GameEventRepository gameEventRepository;
    @Mock VoteRepository voteRepository;

    @InjectMocks GameLoopService service;

    @Test
    void resolveVoting_eliminatesTargetIfFound() {
        GameState gs = new GameState("room-1");
        gs.setPhase("VOTING");
        gs.setDayNumber(1);
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(voteCountingService.getEliminationTarget("room-1", 1)).thenReturn("targetA");

        service.resolveVoting("room-1");

        verify(voteCountingService).applyElimination("room-1", "targetA");
        verify(gameEventRepository).save(argThat(e -> e.getEventType().equals("PLAYER_ELIMINATED")));
        verify(gameStateService).advancePhase("room-1");
    }

    @Test
    void resolveVoting_handlesTieWithNoElimination() {
        GameState gs = new GameState("room-1");
        gs.setPhase("VOTING");
        gs.setDayNumber(1);
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(voteCountingService.getEliminationTarget("room-1", 1)).thenReturn(null);

        service.resolveVoting("room-1");

        verify(voteCountingService, never()).applyElimination(any(), any());
        verify(gameEventRepository).save(argThat(e -> e.getEventType().equals("VOTING_COMPLETE")));
        verify(gameStateService).advancePhase("room-1");
    }

    @Test
    void resolveVoting_throwsIfWrongPhase() {
        GameState gs = new GameState("room-1");
        gs.setPhase("DAY_DISCUSSION");
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));

        assertThrows(IllegalStateException.class, () -> service.resolveVoting("room-1"));
    }
}
