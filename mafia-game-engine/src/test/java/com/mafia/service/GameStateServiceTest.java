package com.mafia.service;

import com.mafia.dto.response.AggregatedGameSnapshot;
import com.mafia.client.EventServiceClient;
import com.mafia.entity.GameEvent;
import com.mafia.entity.GameState;
import com.mafia.entity.Player;
import com.mafia.entity.Room;
import com.mafia.repository.GameEventRepository;
import com.mafia.repository.GameStateRepository;
import com.mafia.repository.MessageRepository;
import com.mafia.repository.PlayerRepository;
import com.mafia.repository.RoomRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameStateServiceTest {

    @Mock
    GameStateRepository gameStateRepository;
    @Mock
    PlayerRepository playerRepository;
    @Mock
    GameEventRepository gameEventRepository;
    @Mock
    MessageRepository messageRepository;
    @Mock
    RoomRepository roomRepository;
    @Mock
    WinConditionService winConditionService;
    @Mock
    EventServiceClient eventServiceClient;

    @InjectMocks
    GameStateService service;

    @Test
    void TestShouldInitializeGameStateAndSaveEvent() {
        service.initializeGameState("room-1");

        ArgumentCaptor<GameState> gsCaptor = ArgumentCaptor.forClass(GameState.class);
        verify(gameStateRepository).save(gsCaptor.capture());
        assertEquals("room-1", gsCaptor.getValue().getRoomId());

        ArgumentCaptor<GameEvent> captor = ArgumentCaptor.forClass(GameEvent.class);
        verify(gameEventRepository).save(captor.capture());
        assertEquals("GAME_INITIALIZED", captor.getValue().getEventType());
    }

    @Test
    void TestShouldReturnGameStateWhenFound() {
        GameState gs = new GameState("room-1");
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));

        GameState result = service.requireGameState("room-1");
        verify(gameStateRepository).findByRoomId("room-1");

        assertNotNull(result);
    }

    @Test
    void TestShouldThrowIllegalArgumentExceptionWhenGameNotFound() {
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.requireGameState("room-1"));
        verify(gameStateRepository).findByRoomId("room-1");

        assertTrue(ex.getMessage().contains("Game not found"));
    }

    @ParameterizedTest
    @CsvSource({
            "SUNRISE,   SUNRISE,   true",
            "DAY_DISCUSSION, SUNRISE, true",
            "VOTING,    SUNRISE,   true",
            "GAME_OVER, SUNRISE,   true",
            "NIGHT,     SUNRISE,   false",
            "LOBBY,     SUNRISE,   false",
            "POLICE_GUESS, SUNRISE, false"
    })
    void TestShouldReturnCorrectResultForIsAtOrAfter(String phase, String target, boolean expected) {
        assertEquals(expected, service.isAtOrAfter(phase, target));
    }

    @Test
    void TestShouldThrowIllegalStateExceptionWhenNotEnoughPlayers() {
        GameState gs = new GameState("room-1");
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(testRoom()));
        when(playerRepository.findByRoomId("room-1")).thenReturn(List.of(
                new Player("p1", "room-1"),
                new Player("p2", "room-1")));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.startGame("room-1"));

        assertTrue(ex.getMessage().contains("Need at least 6 players"));
        verify(gameStateRepository, never()).save(any());
    }

    @Test
    void TestShouldAssignRolesAndSetNightPhaseWhenEnoughPlayers() {
        GameState gs = new GameState("room-1");
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        Room room = testRoom();
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));
        when(playerRepository.findByRoomId("room-1")).thenReturn(sixPlayers());

        service.startGame("room-1");

        assertEquals("NIGHT", gs.getPhase());
        assertEquals(1, gs.getNightNumber());

        ArgumentCaptor<Player> playerCaptor = ArgumentCaptor.forClass(Player.class);
        verify(playerRepository, times(6)).save(playerCaptor.capture());
        List<Player> savedPlayers = playerCaptor.getAllValues();
        assertEquals(6, savedPlayers.size());
        List<String> assignedRoles = savedPlayers.stream()
            .map(Player::getRole)
            .collect(Collectors.toList());
        assertTrue(assignedRoles.contains("MAFIA"));
        assertTrue(assignedRoles.contains("POLICE"));
        assertTrue(assignedRoles.contains("DOCTOR"));
        assertTrue(assignedRoles.contains("VILLAGER"));

        verify(gameStateRepository).save(gs);
        assertEquals("IN_GAME", room.getStatus());
        verify(roomRepository).save(room);

        ArgumentCaptor<GameEvent> captor = ArgumentCaptor.forClass(GameEvent.class);
        verify(gameEventRepository).save(captor.capture());
        assertEquals("GAME_STARTED", captor.getValue().getEventType());
        assertTrue(captor.getValue().getDescription().contains("6 players"));
    }

    @Test
    void TestShouldThrowIllegalArgumentExceptionWhenGameandRoomNotFound() {
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.startGame("room-1"));
    }

    @Test
    void TestShouldHideNightDataWhenPhaseIsBeforeSunrise() {
        GameState gs = gameStateWithPhase("NIGHT");
        gs.setNightKillTarget("targetA");
        gs.setPoliceGuessTarget("suspectB");
        gs.setPoliceGuessCorrect(true);
        gs.setNightKillFailed(false);

        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(testRoom()));
        when(playerRepository.findByRoomId("room-1")).thenReturn(List.of());
        when(messageRepository.findByRoomIdOrderByCreatedAtDesc("room-1")).thenReturn(List.of());
        when(gameEventRepository.findByRoomIdOrderByCreatedAtDesc("room-1")).thenReturn(List.of());

        AggregatedGameSnapshot snap = service.getSnapshot("room-1");
        assertNull(snap.nightKillTarget());
        assertNull(snap.policeGuessTarget());
        assertNull(snap.policeGuessCorrect());
        assertNull(snap.nightKillFailed());
    }

    @Test
    void TestShouldRevealNightDataWhenPhaseIsAtOrAfterSunrise() {
        GameState gs = gameStateWithPhase("SUNRISE");
        gs.setNightKillTarget("targetA");
        gs.setPoliceGuessTarget("suspectB");
        gs.setPoliceGuessCorrect(true);
        gs.setNightKillFailed(false);

        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(testRoom()));
        when(playerRepository.findByRoomId("room-1")).thenReturn(List.of());
        when(messageRepository.findByRoomIdOrderByCreatedAtDesc("room-1")).thenReturn(List.of());
        when(gameEventRepository.findByRoomIdOrderByCreatedAtDesc("room-1")).thenReturn(List.of());

        AggregatedGameSnapshot snap = service.getSnapshot("room-1");

        assertEquals("targetA", snap.nightKillTarget());
        assertNotNull(snap.nightKillFailed());
    }

    @Test
    void TestShouldHidePoliceGuessWhenGuessIsIncorrect() {
        GameState gs = gameStateWithPhase("SUNRISE");
        gs.setPoliceGuessTarget("suspectB");
        gs.setPoliceGuessCorrect(false);

        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(testRoom()));
        when(playerRepository.findByRoomId("room-1")).thenReturn(List.of());
        when(messageRepository.findByRoomIdOrderByCreatedAtDesc("room-1")).thenReturn(List.of());
        when(gameEventRepository.findByRoomIdOrderByCreatedAtDesc("room-1")).thenReturn(List.of());

        AggregatedGameSnapshot snap = service.getSnapshot("room-1");

        assertNull(snap.policeGuessTarget()); 
    }

    @Test
    void TestShouldThrowIllegalArgumentExceptionWhenRoomNotFound() {
        GameState gs = gameStateWithPhase("LOBBY");
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(roomRepository.findById("room-1")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.getSnapshot("room-1"));
    }

    @ParameterizedTest
    @CsvSource({
            "LOBBY,        start_game",
            "NIGHT,        submit_night_kill",
            "POLICE_GUESS, submit_police_guess",
            "DOCTOR_SAVE,  submit_doctor_save",
            "VOTING,       submit_vote",
            "GAME_OVER,    restart"
    })
    void TestShouldIncludeCorrectAvailableActionForPhase(String phase, String expectedAction) {
        GameState gs = gameStateWithPhase(phase);
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(testRoom()));
        when(playerRepository.findByRoomId("room-1")).thenReturn(List.of());
        when(messageRepository.findByRoomIdOrderByCreatedAtDesc("room-1")).thenReturn(List.of());
        when(gameEventRepository.findByRoomIdOrderByCreatedAtDesc("room-1")).thenReturn(List.of());

        AggregatedGameSnapshot snap = service.getSnapshot("room-1");

        assertTrue(snap.allowedActions().contains(expectedAction),
                "Expected action '%s' for phase '%s'".formatted(expectedAction, phase));
        assertTrue(snap.allowedActions().contains("send_message"),
                "send_message should always be present");
    }

    private GameState gameStateWithPhase(String phase) {
        GameState gs = new GameState("room-1");
        gs.setPhase(phase);
        gs.setDayNumber(1);
        gs.setNightNumber(1);
        gs.setWinner("NONE");
        gs.setAlivePlayers(List.of());
        gs.setEliminatedPlayers(List.of());
        return gs;
    }

    private Room testRoom() {
        Room r = new Room("Test Room", "host1", "CODE01", 12);
        r.setId("room-1");
        return r;
    }

    private List<Player> sixPlayers() {
        return List.of(
                new Player("p1", "room-1"),
                new Player("p2", "room-1"),
                new Player("p3", "room-1"),
                new Player("p4", "room-1"),
                new Player("p5", "room-1"),
                new Player("p6", "room-1"));
    }
}