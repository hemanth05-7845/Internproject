package com.mafia.service;

import com.mafia.entity.GameState;
import com.mafia.entity.Player;
import com.mafia.entity.Vote;
import com.mafia.repository.GameStateRepository;
import com.mafia.repository.PlayerRepository;
import com.mafia.repository.VoteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VoteCountingServiceTest {

    @Mock VoteRepository voteRepository;
    @Mock PlayerRepository playerRepository;
    @Mock GameStateRepository gameStateRepository;

    @InjectMocks VoteCountingService service;

    private Vote v(String voter, String target) {
        return new Vote("room-1", 1, voter, target);
    }

    private Player p(String username, String status) {
        Player p = new Player(username, "room-1");
        p.setStatus(status);
        return p;
    }

    @Test
    void countVotes_groupsCorrectly() {
        when(voteRepository.findByRoomIdAndDayNumber("room-1", 1)).thenReturn(List.of(
            v("p1", "targetA"), v("p2", "targetA"), v("p3", "targetB")
        ));

        Map<String, Integer> counts = service.countVotes("room-1", 1);
        assertEquals(2, counts.get("targetA"));
        assertEquals(1, counts.get("targetB"));
    }

    @Test
    void getEliminationTarget_returnsMajorityIfAlive() {
        when(voteRepository.findByRoomIdAndDayNumber("room-1", 1)).thenReturn(List.of(
            v("p1", "targetA"), v("p2", "targetA"), v("p3", "targetB")
        ));
        when(playerRepository.findByUsernameAndRoomId("targetA", "room-1")).thenReturn(Optional.of(p("targetA", "ALIVE")));

        assertEquals("targetA", service.getEliminationTarget("room-1", 1));
    }

    @Test
    void getEliminationTarget_returnsNullOnTie() {
        when(voteRepository.findByRoomIdAndDayNumber("room-1", 1)).thenReturn(List.of(
            v("p1", "targetA"), v("p2", "targetB")
        ));
        assertNull(service.getEliminationTarget("room-1", 1));
    }

    @Test
    void getEliminationTarget_returnsNullIfTargetIsDead() {
        when(voteRepository.findByRoomIdAndDayNumber("room-1", 1)).thenReturn(List.of(
            v("p1", "targetA")
        ));
        when(playerRepository.findByUsernameAndRoomId("targetA", "room-1")).thenReturn(Optional.of(p("targetA", "ELIMINATED")));

        assertNull(service.getEliminationTarget("room-1", 1));
    }

    @Test
    void applyElimination_updatesPlayerAndGameState() {
        GameState gs = new GameState("room-1");
        gs.setAlivePlayers(new ArrayList<>(List.of("targetA", "p2")));
        gs.setEliminatedPlayers(new ArrayList<>());
        Player p = p("targetA", "ALIVE");

        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(playerRepository.findByUsernameAndRoomId("targetA", "room-1")).thenReturn(Optional.of(p));

        service.applyElimination("room-1", "targetA");

        assertEquals("ELIMINATED", p.getStatus());
        verify(playerRepository).save(p);

        assertFalse(gs.getAlivePlayers().contains("targetA"));
        assertTrue(gs.getEliminatedPlayers().contains("targetA"));
        verify(gameStateRepository).save(gs);
    }

    @Test
    void getPlayerCountByRole_returnsAliveCountOnly() {
        Player p1 = p("p1", "ALIVE"); p1.setRole("MAFIA");
        Player p2 = p("p2", "ELIMINATED"); p2.setRole("MAFIA");
        Player p3 = p("p3", "ALIVE"); p3.setRole("VILLAGER");

        when(playerRepository.findByRoomId("room-1")).thenReturn(List.of(p1, p2, p3));

        assertEquals(1, service.getPlayerCountByRole("room-1", "MAFIA"));
        assertEquals(1, service.getPlayerCountByRole("room-1", "VILLAGER"));
        assertEquals(0, service.getPlayerCountByRole("room-1", "POLICE"));
    }
}
