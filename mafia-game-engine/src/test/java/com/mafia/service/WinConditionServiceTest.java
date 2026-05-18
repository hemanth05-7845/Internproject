package com.mafia.service;

import com.mafia.entity.GameState;
import com.mafia.entity.Player;
import com.mafia.repository.GameStateRepository;
import com.mafia.repository.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WinConditionServiceTest {

    @Mock PlayerRepository playerRepository;
    @Mock GameStateRepository gameStateRepository;

    @InjectMocks WinConditionService service;

    private Player p(String role, String status) {
        Player p = new Player("p", "room-1");
        p.setRole(role);
        p.setStatus(status);
        return p;
    }

    @Test
    void checkWinCondition_mafiaWinsWhenEqualOrMore() {
        when(playerRepository.findByRoomId("room-1")).thenReturn(List.of(
            p("MAFIA", "ALIVE"), p("VILLAGER", "ALIVE")
        ));
        assertEquals("MAFIA", service.checkWinCondition("room-1"));
        assertTrue(service.hasGameEnded("room-1"));

        when(playerRepository.findByRoomId("room-1")).thenReturn(List.of(
            p("MAFIA", "ALIVE"), p("MAFIA", "ALIVE"), p("VILLAGER", "ALIVE")
        ));
        assertEquals("MAFIA", service.checkWinCondition("room-1"));
    }

    @Test
    void checkWinCondition_villagersWinWhenNoMafia() {
        when(playerRepository.findByRoomId("room-1")).thenReturn(List.of(
            p("VILLAGER", "ALIVE"), p("POLICE", "ALIVE"), p("MAFIA", "ELIMINATED")
        ));
        assertEquals("VILLAGERS", service.checkWinCondition("room-1"));
        assertTrue(service.hasGameEnded("room-1"));
    }

    @Test
    void checkWinCondition_noneWhenGameShouldContinue() {
        when(playerRepository.findByRoomId("room-1")).thenReturn(List.of(
            p("MAFIA", "ALIVE"), p("VILLAGER", "ALIVE"), p("POLICE", "ALIVE"), p("VILLAGER", "ALIVE")
        ));
        assertEquals("NONE", service.checkWinCondition("room-1"));
        assertFalse(service.hasGameEnded("room-1"));
    }

    @Test
    void concludeGame_setsWinnerAndPhase() {
        GameState gs = new GameState("room-1");
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));

        service.concludeGame("room-1", "MAFIA");

        assertEquals("MAFIA", gs.getWinner());
        assertEquals("GAME_OVER", gs.getPhase());
        verify(gameStateRepository).save(gs);
    }
}
