package com.mafia.service;

import com.mafia.entity.GameEvent;
import com.mafia.entity.GameState;
import com.mafia.entity.Player;
import com.mafia.client.EventServiceClient;
import com.mafia.repository.GameEventRepository;
import com.mafia.repository.GameStateRepository;
import com.mafia.repository.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NightPhaseServiceTest {

    @Mock
    GameStateRepository gameStateRepository;
    @Mock
    PlayerRepository playerRepository;
    @Mock
    GameEventRepository gameEventRepository;
    @Mock
    WinConditionService winConditionService;
    @Mock
    EventServiceClient eventServiceClient;

    @InjectMocks
    NightPhaseService service;

    @Test
    void TestShouldSetTargetAndSaveEventWhenInNightPhase() {
        GameState gs = gameStateWithPhase("NIGHT");
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(playerRepository.findByUsernameAndRoomId("targetA", "room-1"))
                .thenReturn(Optional.of(alivePlayer("targetA", "room-1")));

        service.submitNightKill("room-1", "targetA");

        assertEquals("targetA", gs.getNightKillTarget());
        verify(gameStateRepository).save(gs);
        assertEventSaved("NIGHT_KILL");
    }

    @Test
    void TestShouldThrowIllegalStateExceptionWhenNotinNightPhase() {
        GameState gs = gameStateWithPhase("DAY_DISCUSSION");
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.submitNightKill("room-1", "targetA"));

        assertEquals("Not in NIGHT phase", ex.getMessage());
        verifyNoInteractions(gameEventRepository);
    }

    @Test
    void TestShouldThrowIllegalArgumentExceptionWhenPlayerNotFound() {
        GameState gs = gameStateWithPhase("NIGHT");
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(playerRepository.findByUsernameAndRoomId("targetA", "room-1"))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.submitNightKill("room-1", "targetA"));
    }

    @Test
    void TestShouldThrowIllegalStateExceptionWhenPlayerDead() {
        GameState gs = gameStateWithPhase("NIGHT");
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(playerRepository.findByUsernameAndRoomId("targetA", "room-1"))
                .thenReturn(Optional.of(deadPlayer("targetA", "room-1")));

        assertThrows(IllegalStateException.class,
                () -> service.submitNightKill("room-1", "targetA"));
    }

        @Test
        void TestShouldThrowIllegalStateExceptionWhenTargetIsMafia() {
        GameState gs = gameStateWithPhase("NIGHT");
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        Player mafia = alivePlayer("targetA", "room-1");
        mafia.setRole("MAFIA");
        when(playerRepository.findByUsernameAndRoomId("targetA", "room-1"))
            .thenReturn(Optional.of(mafia));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> service.submitNightKill("room-1", "targetA"));

        assertEquals("Mafia cannot target another Mafia", ex.getMessage());
        }

    @Test
    void TestShouldSetPoliceGuessTargetToCorrectWhenSuspectIsMafia() {
        GameState gs = gameStateWithPhase("POLICE_GUESS");
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));

        Player suspect = alivePlayer("suspectA", "room-1");
        suspect.setRole("MAFIA");
        when(playerRepository.findByUsernameAndRoomId("suspectA", "room-1"))
                .thenReturn(Optional.of(suspect));

        service.submitPoliceGuess("room-1", "suspectA");

        assertEquals("suspectA", gs.getPoliceGuessTarget());
        assertTrue(gs.getPoliceGuessCorrect());
        assertEventSavedWithDescription("POLICE_GUESS", "correctly identified");
    }

    @Test
    void TestShouldSetPoliceGuessTargetAndCorrectFalseWhenSuspectIsNotMafia() {
        GameState gs = gameStateWithPhase("POLICE_GUESS");
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));

        Player suspect = alivePlayer("suspectA", "room-1");
        suspect.setRole("VILLAGER");
        when(playerRepository.findByUsernameAndRoomId("suspectA", "room-1"))
                .thenReturn(Optional.of(suspect));

        service.submitPoliceGuess("room-1", "suspectA");

        assertFalse(gs.getPoliceGuessCorrect());
        assertEventSavedWithDescription("POLICE_GUESS", "incorrect");
    }

    @Test
    void TestShouldThrowIllegalStateExceptionWhenNotinPolicGuessPhase() {
        GameState gs = gameStateWithPhase("NIGHT");
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.submitPoliceGuess("room-1", "suspectA"));

        assertEquals("Not in POLICE_GUESS phase", ex.getMessage());
    }

    @Test
    void TestShouldAddSaveTargetWhenInDoctorSavePhase() {
        GameState gs = gameStateWithPhase("DOCTOR_SAVE");
        gs.setDoctorSaveTargets(new ArrayList<>());
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(playerRepository.findByUsernameAndRoomId("savedA", "room-1"))
                .thenReturn(Optional.of(alivePlayer("savedA", "room-1")));

        service.submitDoctorSave("room-1", "savedA");

        assertTrue(gs.getDoctorSaveTargets().contains("savedA"));
        verify(gameStateRepository).save(gs);
        assertEventSaved("DOCTOR_SAVE");
    }

    @Test
    void TestShouldSubmitTheSaveAndReturnNullListWhenEmpty() {
        GameState gs = gameStateWithPhase("DOCTOR_SAVE");
        gs.setDoctorSaveTargets(null);
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(playerRepository.findByUsernameAndRoomId("savedA", "room-1"))
                .thenReturn(Optional.of(alivePlayer("savedA", "room-1")));

        service.submitDoctorSave("room-1", "savedA");

        assertNotNull(gs.getDoctorSaveTargets());
        assertTrue(gs.getDoctorSaveTargets().contains("savedA"));
    }

    @Test
    void TestShouldNotAddDuplicateWhenAlreadySaved() {
        GameState gs = gameStateWithPhase("DOCTOR_SAVE");
        gs.setDoctorSaveTargets(new ArrayList<>(List.of("savedA")));
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(playerRepository.findByUsernameAndRoomId("savedA", "room-1"))
                .thenReturn(Optional.of(alivePlayer("savedA", "room-1")));

        service.submitDoctorSave("room-1", "savedA");

        assertEquals(1, gs.getDoctorSaveTargets().size());
    }

    @Test
    void TestShouldThrowIllegalStateExceptionWhenNotInDoctorSavePhase() {
        GameState gs = gameStateWithPhase("NIGHT");
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.submitDoctorSave("room-1", "savedA"));
        assertEquals("Not in DOCTOR_SAVE phase", ex.getMessage());
    }

    @Test
    void TestShouldTransitionNightToPoliceGuess() {
        GameState gs = gameStateWithPhase("NIGHT");
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));

        service.advancePhase("room-1");

        assertEquals("POLICE_GUESS", gs.getPhase());
        assertEventSaved("PHASE_TRANSITIONED");
    }

    @Test
    void TestShouldTransitionPoliceGuessToDoctorSave() {
        GameState gs = gameStateWithPhase("POLICE_GUESS");
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));

        service.advancePhase("room-1");

        assertEquals("DOCTOR_SAVE", gs.getPhase());
    }

    @Test
    void TestShouldTransitionDoctorSaveToSunrise_whenKillSucceeds() {
        GameState gs = gameStateWithPhase("DOCTOR_SAVE");
        gs.setNightKillTarget("victimA");
        gs.setAlivePlayers(new ArrayList<>(List.of("victimA", "p2")));
        gs.setEliminatedPlayers(new ArrayList<>());
        gs.setDoctorSaveTargets(new ArrayList<>());

        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(playerRepository.findByUsernameAndRoomId("victimA", "room-1"))
                .thenReturn(Optional.of(alivePlayer("victimA", "room-1")));
        when(winConditionService.checkWinCondition("room-1")).thenReturn("NONE");

        service.advancePhase("room-1");

        assertEquals("SUNRISE", gs.getPhase());
        assertFalse(gs.getNightKillFailed());
        assertFalse(gs.getAlivePlayers().contains("victimA"));
        assertFalse(gs.getDoctorSaveTargets().contains("victimA"));
    }

    @Test
    void TestShouldTransitionDoctorSaveToSunrise_whenKillBlockedByDoctor() {
        GameState gs = gameStateWithPhase("DOCTOR_SAVE");
        gs.setNightKillTarget("victimA");
        gs.setAlivePlayers(new ArrayList<>(List.of("victimA", "p2")));
        gs.setEliminatedPlayers(new ArrayList<>());
        gs.setDoctorSaveTargets(new ArrayList<>(List.of("victimA")));

        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(winConditionService.checkWinCondition("room-1")).thenReturn("NONE");

        service.advancePhase("room-1");

        assertEquals("SUNRISE", gs.getPhase());
        assertTrue(gs.getNightKillFailed());
        assertTrue(gs.getAlivePlayers().contains("victimA"));
        assertTrue(gs.getDoctorSaveTargets().contains("victimA"));
    }

    @Test
    void TestShouldTransitionDoctorSaveToSunrise_whenKillBlockedBySoldier() {
        GameState gs = gameStateWithPhase("DOCTOR_SAVE");
        gs.setNightKillTarget("soldierA");
        gs.setAlivePlayers(new ArrayList<>(List.of("soldierA", "p2")));
        gs.setEliminatedPlayers(new ArrayList<>());
        gs.setDoctorSaveTargets(new ArrayList<>());

        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));

        Player soldier = alivePlayer("soldierA", "room-1");
        soldier.setRole("SOLDIER");
        when(playerRepository.findByUsernameAndRoomId("soldierA", "room-1"))
                .thenReturn(Optional.of(soldier));
        when(winConditionService.checkWinCondition("room-1")).thenReturn("NONE");

        service.advancePhase("room-1");

        assertEquals("SUNRISE", gs.getPhase());
        assertTrue(gs.getNightKillFailed());
        assertTrue(gs.getAlivePlayers().contains("soldierA"));
        assertFalse(gs.getDoctorSaveTargets().contains("soldierA"));
    }

    @Test
    void TestShouldEliminatePoliceGuessTarget_whenCorrectAndAlive() {
        GameState gs = gameStateWithPhase("DOCTOR_SAVE");
        gs.setNightKillTarget(null);
        gs.setAlivePlayers(new ArrayList<>(List.of("policeTarget", "p2")));
        gs.setEliminatedPlayers(new ArrayList<>());
        gs.setDoctorSaveTargets(new ArrayList<>());
        gs.setPoliceGuessTarget("policeTarget");
        gs.setPoliceGuessCorrect(true);
        gs.setNightKillFailed(true);

        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(playerRepository.findByUsernameAndRoomId("policeTarget", "room-1"))
                .thenReturn(Optional.of(alivePlayer("policeTarget", "room-1")));
        when(winConditionService.checkWinCondition("room-1")).thenReturn("NONE");

        service.advancePhase("room-1");

        assertEquals("SUNRISE", gs.getPhase());
        assertFalse(gs.getAlivePlayers().contains("policeTarget"));
        assertTrue(gs.getEliminatedPlayers().contains("policeTarget"));
        assertTrue(gs.getPoliceGuessCorrect()); 
    }

    @Test
    void TestShouldTransitionDoctorSaveToGameOver_whenWinConditionMet() {
        GameState gs = gameStateWithPhase("DOCTOR_SAVE");
        gs.setNightKillTarget(null);
        gs.setAlivePlayers(new ArrayList<>(List.of("p1")));
        gs.setEliminatedPlayers(new ArrayList<>());
        gs.setDoctorSaveTargets(new ArrayList<>());

        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(winConditionService.checkWinCondition("room-1")).thenReturn("MAFIA");

        service.advancePhase("room-1");

        assertEquals("GAME_OVER", gs.getPhase());
        assertEquals("MAFIA", gs.getWinner());
        assertEventSaved("GAME_OVER");
    }

    @Test
    void TestShouldTransitionSunriseToDayDiscussion() {
        GameState gs = gameStateWithPhase("SUNRISE");
        gs.setDayNumber(1);
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));

        service.advancePhase("room-1");

        assertEquals("DAY_DISCUSSION", gs.getPhase());
        assertEquals(2, gs.getDayNumber()); 
        assertNull(gs.getNightKillTarget());
    }

    @Test
    void TestShouldTransitionDayDiscussionToVoting() {
        GameState gs = gameStateWithPhase("DAY_DISCUSSION");
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));

        service.advancePhase("room-1");

        assertEquals("VOTING", gs.getPhase());
    }

    @Test
    void TestShouldTransitionVotingToNight_whenNoWinner() {
        GameState gs = gameStateWithPhase("VOTING");
        gs.setNightNumber(1);
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(winConditionService.checkWinCondition("room-1")).thenReturn("NONE");

        service.advancePhase("room-1");

        assertEquals("NIGHT", gs.getPhase());
        assertEquals(2, gs.getNightNumber()); 
    }

    @Test
    void TestShouldBuildSunriseMessage_whenTargetedAndGuessCorrect() {
        GameState gs = gameStateWithPhase("DOCTOR_SAVE");
        gs.setNightKillTarget("victimA");
        gs.setAlivePlayers(new ArrayList<>(List.of("p2")));
        gs.setEliminatedPlayers(new ArrayList<>());
        gs.setDoctorSaveTargets(new ArrayList<>());
        gs.setPoliceGuessTarget("suspectA");
        gs.setPoliceGuessCorrect(true);

        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(winConditionService.checkWinCondition("room-1")).thenReturn("NONE");

        service.advancePhase("room-1");

        ArgumentCaptor<GameEvent> captor = ArgumentCaptor.forClass(GameEvent.class);
        verify(gameEventRepository, atLeastOnce()).save(captor.capture());
        assertTrue(captor.getAllValues().stream()
                .anyMatch(e -> e.getDescription().contains("was targeted during the night")
                        && e.getDescription().contains("Police correctly identified")));
    }

    @Test
    void TestShouldBuildSunriseMessage_whenGuessIncorrect() {
        GameState gs = gameStateWithPhase("DOCTOR_SAVE");
        gs.setNightKillTarget(null);
        gs.setAlivePlayers(new ArrayList<>(List.of("p2")));
        gs.setEliminatedPlayers(new ArrayList<>());
        gs.setDoctorSaveTargets(new ArrayList<>());
        gs.setPoliceGuessTarget("suspectB");
        gs.setPoliceGuessCorrect(false);

        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(winConditionService.checkWinCondition("room-1")).thenReturn("NONE");

        service.advancePhase("room-1");

        ArgumentCaptor<GameEvent> captor = ArgumentCaptor.forClass(GameEvent.class);
        verify(gameEventRepository, atLeastOnce()).save(captor.capture());
        assertTrue(captor.getAllValues().stream()
                .anyMatch(e -> e.getDescription().contains("Police guessed")
                        && e.getDescription().contains("but they were wrong")));
    }

    @Test
    void TestShouldTransitionVotingToGameOver_whenWinnerFound() {
        GameState gs = gameStateWithPhase("VOTING");
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(winConditionService.checkWinCondition("room-1")).thenReturn("VILLAGE");

        service.advancePhase("room-1");

        assertEquals("GAME_OVER", gs.getPhase());
        assertEquals("VILLAGE", gs.getWinner());
    }

    @Test
    void TestShouldTransitionEliminationToWinCheck_whenNoWinner() {
        GameState gs = gameStateWithPhase("ELIMINATION");
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(winConditionService.checkWinCondition("room-1")).thenReturn("NONE");

        service.advancePhase("room-1");

        assertEquals("WIN_CHECK", gs.getPhase());
    }

    @Test
    void TestShouldTransitionEliminationToGameOver_whenWinnerFound() {
        GameState gs = gameStateWithPhase("ELIMINATION");
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(winConditionService.checkWinCondition("room-1")).thenReturn("VILLAGER");

        service.advancePhase("room-1");

        assertEquals("GAME_OVER", gs.getPhase());
        assertEquals("VILLAGER", gs.getWinner());
    }

    @Test
    void TestShouldTransitionWinCheckToNight_whenNoWinner() {
        GameState gs = gameStateWithPhase("WIN_CHECK");
        gs.setNightNumber(2);
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(winConditionService.checkWinCondition("room-1")).thenReturn("NONE");

        service.advancePhase("room-1");

        assertEquals("NIGHT", gs.getPhase());
        assertEquals(3, gs.getNightNumber());
    }

    @Test
    void TestShouldThrowIllegalStateException_whenUnknownPhase() {
        GameState gs = gameStateWithPhase("LOBBY");
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.advancePhase("room-1"));

        assertTrue(ex.getMessage().contains("Cannot advance from phase: LOBBY"));
    }

    @Test
    void TestShouldEliminatePlayer_removesFromAliveAndAddsToEliminated() {
        GameState gs = gameStateWithPhase("NIGHT");
        gs.setAlivePlayers(new ArrayList<>(List.of("p1", "p2")));
        gs.setEliminatedPlayers(new ArrayList<>());
        gs.setDayNumber(2);

        Player p = alivePlayer("p1", "room-1");
        when(playerRepository.findByUsernameAndRoomId("p1", "room-1"))
                .thenReturn(Optional.of(p));

        service.eliminatePlayer("room-1", gs, "p1");

        assertFalse(gs.getAlivePlayers().contains("p1"));
        assertTrue(gs.getEliminatedPlayers().contains("p1"));
        assertEquals("ELIMINATED", p.getStatus());
        assertEquals(2, p.getVoteEligibleDayNumber());
        verify(playerRepository).save(p);
    }

    @Test
    void TestShouldNotEliminatePlayer_whenPlayerNotFound() {
        GameState gs = gameStateWithPhase("NIGHT");
        gs.setAlivePlayers(new ArrayList<>(List.of("p1")));
        gs.setEliminatedPlayers(new ArrayList<>());

        when(playerRepository.findByUsernameAndRoomId("ghost", "room-1"))
                .thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.eliminatePlayer("room-1", gs, "ghost"));
        verify(playerRepository, never()).save(any());
    }

    private GameState gameStateWithPhase(String phase) {
        GameState gs = new GameState("room-1");
        gs.setPhase(phase);
        gs.setDayNumber(1);
        gs.setNightNumber(1);
        gs.setAlivePlayers(new ArrayList<>());
        gs.setEliminatedPlayers(new ArrayList<>());
        gs.setWinner("NONE");
        return gs;
    }

    private Player alivePlayer(String username, String roomId) {
        Player p = new Player(username, roomId);
        p.setStatus("ALIVE");
        return p;
    }

    private Player deadPlayer(String username, String roomId) {
        Player p = new Player(username, roomId);
        p.setStatus("ELIMINATED");
        return p;
    }

    private void assertEventSaved(String expectedType) {
        ArgumentCaptor<GameEvent> captor = ArgumentCaptor.forClass(GameEvent.class);
        verify(gameEventRepository, atLeastOnce()).save(captor.capture());
        assertTrue(captor.getAllValues().stream()
                .anyMatch(e -> expectedType.equals(e.getEventType())),
                "" + expectedType);
    }

    private void assertEventSavedWithDescription(String expectedType, String descriptionContains) {
        ArgumentCaptor<GameEvent> captor = ArgumentCaptor.forClass(GameEvent.class);
        verify(gameEventRepository, atLeastOnce()).save(captor.capture());
        assertTrue(captor.getAllValues().stream()
                .anyMatch(e -> expectedType.equals(e.getEventType())
                        && e.getDescription().contains(descriptionContains)),
                "'%s' '%s'"
                        .formatted(expectedType, descriptionContains));
    }
}