package com.mafia.service;

import com.mafia.entity.GameState;
import com.mafia.entity.Vote;
import com.mafia.repository.GameStateRepository;
import com.mafia.repository.VoteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameCommandServiceTest {

    @Mock GameStateRepository gameStateRepository;
    @Mock VoteRepository voteRepository;

    @InjectMocks GameCommandService service;

    @Test
    void submitVote_recordsVoteIfVotingPhase() {
        GameState gs = new GameState("room-2");
        gs.setPhase("VOTING");
        gs.setDayNumber(2);
        when(gameStateRepository.findByRoomId("room-2")).thenReturn(Optional.of(gs));

        Map<String, String> res = service.submitVote("room-2", "voterA", "targetB");

        assertEquals("vote-recorded", res.get("status"));
        assertEquals("targetB", res.get("votedFor"));
        verify(voteRepository).save(any(Vote.class));
    }

    @Test
    void submitVote_returnsErrorIfWrongPhase() {
        GameState gs = new GameState("room-1");
        gs.setPhase("DAY_DISCUSSION");
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));

        Map<String, String> res = service.submitVote("room-1", "voterA", "targetB");

        assertEquals("error", res.get("status"));
        assertEquals("Voting phase not active", res.get("message"));
        verify(voteRepository, never()).save(any());
    }

    @Test
    void submitVote_returnsErrorIfGameNotFound() {
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.empty());

        Map<String, String> res = service.submitVote("room-1", "voterA", "targetB");

        assertEquals("error", res.get("status"));
        assertEquals("Game not found", res.get("message"));
    }
}
