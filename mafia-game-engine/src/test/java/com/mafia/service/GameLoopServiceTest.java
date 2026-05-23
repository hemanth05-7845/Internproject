package com.mafia.service;

import com.mafia.entity.GameEvent;
import com.mafia.entity.GameState;
import com.mafia.repository.GameEventRepository;
import com.mafia.repository.GameStateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameLoopServiceTest {

    @Mock
    GameStateRepository gameStateRepository;
    @Mock
    GameEventRepository gameEventRepository;
    @Mock
    NightPhaseService nightPhaseService;
    @Mock
    VoteCountingService voteCountingService;

    @InjectMocks
    GameLoopService service;

    @Test
    void TestShouldResolveVotingAndEliminatePlayerWithMajorityVotes() {
        GameState gs = votingState("room-1", 2);
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(voteCountingService.getEliminationTarget("room-1", 2)).thenReturn("targetA");

        service.resolveVoting("room-1");

        verify(voteCountingService).applyElimination("room-1", "targetA");

        ArgumentCaptor<GameEvent> eventCaptor = ArgumentCaptor.forClass(GameEvent.class);
        verify(gameEventRepository).save(eventCaptor.capture());
        GameEvent saved = eventCaptor.getValue();
        assertEquals("PLAYER_ELIMINATED", saved.getEventType());
        assertTrue(saved.getDescription().contains("targetA"));
        verify(nightPhaseService).advancePhase("room-1");
    }

    @Test
    void TestShouldSaveTieEventAndAdvancePhaseWhenNoMajority() {
        GameState gs = votingState("room-1", 2);
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(voteCountingService.getEliminationTarget("room-1", 2)).thenReturn(null);

        service.resolveVoting("room-1");

        verify(voteCountingService, never()).applyElimination(any(), any());

        ArgumentCaptor<GameEvent> eventCaptor = ArgumentCaptor.forClass(GameEvent.class);
        verify(gameEventRepository).save(eventCaptor.capture());
        GameEvent saved = eventCaptor.getValue();
        assertEquals("VOTING_COMPLETE", saved.getEventType());
        assertTrue(saved.getDescription().contains("tie"));

        verify(nightPhaseService).advancePhase("room-1");
    }

    @Test
    void TestShouldAlwaysAdvancePhaseRegardlessOfElimination() {
        GameState gs = votingState("room-1", 1);
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(voteCountingService.getEliminationTarget("room-1", 1)).thenReturn("targetA");
        service.resolveVoting("room-1");
        verify(nightPhaseService).advancePhase("room-1");
    }

    @Test
    void TestShouldThrowIllegalArgumentExceptionWhenGameNotFound() {
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.resolveVoting("room-1"));

        assertEquals("Game not found", ex.getMessage());
        verifyNoInteractions(voteCountingService, nightPhaseService, gameEventRepository);
    }

    @Test
    void TestShouldThrowIllegalStateExceptionWhenNotInVotingPhase() {
        GameState gs = new GameState("room-1");
        gs.setPhase("NIGHT");
        gs.setDayNumber(1);
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.resolveVoting("room-1"));

        assertEquals("Not in VOTING phase", ex.getMessage());
        verifyNoInteractions(voteCountingService, nightPhaseService, gameEventRepository);
    }

    private static GameState votingState(String roomId, int dayNumber) {
        GameState gs = new GameState(roomId);
        gs.setPhase("VOTING");
        gs.setDayNumber(dayNumber);
        return gs;
    }
}