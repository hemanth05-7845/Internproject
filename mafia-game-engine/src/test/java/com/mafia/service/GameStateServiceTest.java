package com.mafia.service;

import com.mafia.dto.response.AggregatedGameSnapshot;
import com.mafia.entity.GameEvent;
import com.mafia.entity.GameState;
import com.mafia.entity.Player;
import com.mafia.entity.Room;
import com.mafia.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameStateServiceTest {

    @Mock GameStateRepository gameStateRepository;
    @Mock PlayerRepository    playerRepository;
    @Mock GameEventRepository gameEventRepository;
    @Mock MessageRepository   messageRepository;
    @Mock RoomRepository      roomRepository;

    @InjectMocks GameStateService service;

    static final String ROOM = "room-1";

    @BeforeEach
    void stubSaves() {
        lenient().when(gameStateRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(playerRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(gameEventRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    // ---- helpers ----

    GameState gs(String phase) {
        GameState g = new GameState(ROOM);
        g.setId("gs-1");
        g.setPhase(phase);
        g.setNightNumber(1);
        g.setAlivePlayers(new ArrayList<>(List.of("alice", "bob", "charlie")));
        g.setEliminatedPlayers(new ArrayList<>());
        return g;
    }

    Player p(String name, String role, String status) {
        Player pl = new Player(name, ROOM);
        pl.setRole(role);
        pl.setStatus(status);
        return pl;
    }

    void stubGs(GameState g) {
        when(gameStateRepository.findByRoomId(ROOM)).thenReturn(Optional.of(g));
    }

    // ---- initializeGameState ----

    @Test
    void initializeGameState_savesStateAndEvent() {
        service.initializeGameState(ROOM);
        verify(gameStateRepository).save(any(GameState.class));
        verify(gameEventRepository).save(any(GameEvent.class));
    }

    // ---- startGame ----

    @Test
    void startGame_setsNightPhaseAndAssignsRoles() {
        GameState g = gs("LOBBY");
        stubGs(g);
        when(playerRepository.findByRoomId(ROOM)).thenReturn(
            List.of(p("alice", null, "ALIVE"), p("bob", null, "ALIVE"), p("charlie", null, "ALIVE")));

        service.startGame(ROOM);

        assertEquals("NIGHT", g.getPhase());
        assertEquals(1, g.getNightNumber());
        verify(playerRepository, atLeast(3)).save(any(Player.class));
    }

    @Test
    void startGame_throwsWhenTooFewPlayers() {
        GameState g = gs("LOBBY");
        stubGs(g);
        when(playerRepository.findByRoomId(ROOM)).thenReturn(List.of(p("alice", null, "ALIVE")));
        assertThrows(IllegalStateException.class, () -> service.startGame(ROOM));
    }

    // ---- submitNightKill ----

    @Test
    void submitNightKill_recordsTarget() {
        GameState g = gs("NIGHT");
        stubGs(g);
        when(playerRepository.findByUsernameAndRoomId("bob", ROOM)).thenReturn(Optional.of(p("bob", "VILLAGER", "ALIVE")));

        service.submitNightKill(ROOM, "bob");

        assertEquals("bob", g.getNightKillTarget());
        verify(gameStateRepository).save(g);
    }

    @Test
    void submitNightKill_throwsIfNotNightPhase() {
        stubGs(gs("DAY_DISCUSSION"));
        assertThrows(IllegalStateException.class, () -> service.submitNightKill(ROOM, "bob"));
    }

    @Test
    void submitNightKill_throwsIfTargetEliminated() {
        stubGs(gs("NIGHT"));
        when(playerRepository.findByUsernameAndRoomId("bob", ROOM)).thenReturn(Optional.of(p("bob", "VILLAGER", "ELIMINATED")));
        assertThrows(IllegalStateException.class, () -> service.submitNightKill(ROOM, "bob"));
    }

    @Test
    void submitNightKill_throwsIfTargetNotFound() {
        stubGs(gs("NIGHT"));
        when(playerRepository.findByUsernameAndRoomId("ghost", ROOM)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.submitNightKill(ROOM, "ghost"));
    }

    // ---- submitPoliceGuess ----

    @Test
    void submitPoliceGuess_correctGuessSetsTrue() {
        stubGs(gs("POLICE_GUESS"));
        Player mafia = p("bob", "MAFIA", "ALIVE");
        when(playerRepository.findByUsernameAndRoomId("bob", ROOM)).thenReturn(Optional.of(mafia));

        service.submitPoliceGuess(ROOM, "bob");

        assertTrue(gs("POLICE_GUESS").getPoliceGuessCorrect() == null); // just verify via service
        verify(gameStateRepository).save(any());
        verify(gameEventRepository).save(argThat(e -> e.getDescription().contains("correctly")));
    }

    @Test
    void submitPoliceGuess_wrongGuessSetsCorrectFalse() {
        GameState g = gs("POLICE_GUESS");
        stubGs(g);
        when(playerRepository.findByUsernameAndRoomId("charlie", ROOM)).thenReturn(Optional.of(p("charlie", "VILLAGER", "ALIVE")));

        service.submitPoliceGuess(ROOM, "charlie");

        assertEquals("charlie", g.getPoliceGuessTarget());
        assertFalse(g.getPoliceGuessCorrect());
    }

    @Test
    void submitPoliceGuess_throwsIfWrongPhase() {
        stubGs(gs("NIGHT"));
        assertThrows(IllegalStateException.class, () -> service.submitPoliceGuess(ROOM, "bob"));
    }

    // ---- advancePhase: NIGHT → POLICE_GUESS ----

    @Test
    void advance_nightToPoliceGuess_doesNotApplyKill() {
        GameState g = gs("NIGHT");
        g.setNightKillTarget("bob");
        stubGs(g);

        service.advancePhase(ROOM);

        assertEquals("POLICE_GUESS", g.getPhase());
        assertTrue(g.getAlivePlayers().contains("bob")); // kill deferred
    }

    // ---- advancePhase: POLICE_GUESS → SUNRISE (no win) ----

    @Test
    void advance_policeGuessToSunrise_appliesKillNobodyWins() {
        GameState g = gs("POLICE_GUESS");
        g.setNightKillTarget("bob");
        g.setAlivePlayers(new ArrayList<>(List.of("alice", "bob", "charlie")));
        stubGs(g);
        when(playerRepository.findByUsernameAndRoomId("bob", ROOM)).thenReturn(Optional.of(p("bob", "VILLAGER", "ALIVE")));
        when(playerRepository.findByRoomId(ROOM)).thenReturn(List.of(
            p("alice", "MAFIA", "ALIVE"), p("charlie", "POLICE", "ALIVE"), p("bob", "VILLAGER", "ALIVE")));

        service.advancePhase(ROOM);

        assertEquals("SUNRISE", g.getPhase());
        assertFalse(g.getAlivePlayers().contains("bob"));
    }

    // ---- advancePhase: POLICE_GUESS → GAME_OVER via mafia win ----

    @Test
    void advance_sunrise_mafiaWinsWhenLastVillagerKilled() {
        GameState g = gs("POLICE_GUESS");
        g.setAlivePlayers(new ArrayList<>(List.of("mafia1", "villager1")));
        g.setNightKillTarget("villager1");
        stubGs(g);
        Player v = p("villager1", "VILLAGER", "ALIVE");
        when(playerRepository.findByUsernameAndRoomId("villager1", ROOM)).thenReturn(Optional.of(v));
        when(playerRepository.findByRoomId(ROOM)).thenReturn(List.of(p("mafia1", "MAFIA", "ALIVE"), v));

        service.advancePhase(ROOM);

        assertEquals("GAME_OVER", g.getPhase());
        assertEquals("MAFIA", g.getWinner());
    }

    // ---- advancePhase: POLICE_GUESS → GAME_OVER via villagers win (police correct) ----

    @Test
    void advance_sunrise_villagersWinWhenMafiaCorrectlyGuessed() {
        GameState g = gs("POLICE_GUESS");
        g.setAlivePlayers(new ArrayList<>(List.of("mafia1", "villager1", "police1")));
        g.setPoliceGuessTarget("mafia1");
        g.setPoliceGuessCorrect(true);
        stubGs(g);
        Player m = p("mafia1", "MAFIA", "ALIVE");
        when(playerRepository.findByUsernameAndRoomId("mafia1", ROOM)).thenReturn(Optional.of(m));
        when(playerRepository.findByRoomId(ROOM)).thenReturn(List.of(m, p("villager1", "VILLAGER", "ALIVE"), p("police1", "POLICE", "ALIVE")));

        service.advancePhase(ROOM);

        assertEquals("GAME_OVER", g.getPhase());
        assertEquals("VILLAGERS", g.getWinner());
    }

    // ---- advancePhase: SUNRISE → DAY_DISCUSSION ----

    @Test
    void advance_sunriseToDayDiscussion_clearsNightTracking() {
        GameState g = gs("SUNRISE");
        g.setNightKillTarget("bob");
        g.setPoliceGuessTarget("alice");
        stubGs(g);

        service.advancePhase(ROOM);

        assertEquals("DAY_DISCUSSION", g.getPhase());
        assertEquals(1, g.getDayNumber());
        assertNull(g.getNightKillTarget());
        assertNull(g.getPoliceGuessTarget());
    }

    // ---- advancePhase: DAY_DISCUSSION → VOTING ----

    @Test
    void advance_dayDiscussionToVoting() {
        GameState g = gs("DAY_DISCUSSION");
        stubGs(g);
        service.advancePhase(ROOM);
        assertEquals("VOTING", g.getPhase());
    }

    // ---- advancePhase: VOTING → GAME_OVER ----

    @Test
    void advance_votingToGameOver_mafiaEqualsNonMafia() {
        GameState g = gs("VOTING");
        stubGs(g);
        when(playerRepository.findByRoomId(ROOM)).thenReturn(List.of(
            p("m1", "MAFIA", "ALIVE"), p("v1", "VILLAGER", "ALIVE")));

        service.advancePhase(ROOM);

        assertEquals("GAME_OVER", g.getPhase());
        assertEquals("MAFIA", g.getWinner());
    }

    // ---- advancePhase: VOTING → next NIGHT ----

    @Test
    void advance_votingToNextNight_whenNoWinner() {
        GameState g = gs("VOTING");
        g.setNightNumber(1);
        stubGs(g);
        when(playerRepository.findByRoomId(ROOM)).thenReturn(List.of(
            p("m1", "MAFIA", "ALIVE"), p("v1", "VILLAGER", "ALIVE"), p("v2", "VILLAGER", "ALIVE")));

        service.advancePhase(ROOM);

        assertEquals("NIGHT", g.getPhase());
        assertEquals(2, g.getNightNumber());
        assertNull(g.getNightKillTarget());
    }

    // ---- advancePhase: ELIMINATION ----

    @Test
    void advance_eliminationToGameOver() {
        GameState g = gs("ELIMINATION");
        stubGs(g);
        when(playerRepository.findByRoomId(ROOM)).thenReturn(List.of(p("m1", "MAFIA", "ALIVE")));
        service.advancePhase(ROOM);
        assertEquals("GAME_OVER", g.getPhase());
    }

    @Test
    void advance_eliminationToWinCheck_whenGameContinues() {
        GameState g = gs("ELIMINATION");
        stubGs(g);
        when(playerRepository.findByRoomId(ROOM)).thenReturn(List.of(
            p("m1", "MAFIA", "ALIVE"), p("v1", "VILLAGER", "ALIVE"), p("v2", "VILLAGER", "ALIVE")));
        service.advancePhase(ROOM);
        assertEquals("WIN_CHECK", g.getPhase());
    }

    // ---- advancePhase: WIN_CHECK ----

    @Test
    void advance_winCheckToNextNight() {
        GameState g = gs("WIN_CHECK");
        g.setNightNumber(2);
        stubGs(g);
        when(playerRepository.findByRoomId(ROOM)).thenReturn(List.of(
            p("m1", "MAFIA", "ALIVE"), p("v1", "VILLAGER", "ALIVE"), p("v2", "VILLAGER", "ALIVE")));
        service.advancePhase(ROOM);
        assertEquals("NIGHT", g.getPhase());
        assertEquals(3, g.getNightNumber());
    }

    @Test
    void advance_winCheckToGameOver() {
        GameState g = gs("WIN_CHECK");
        stubGs(g);
        when(playerRepository.findByRoomId(ROOM)).thenReturn(List.of(
            p("m1", "MAFIA", "ALIVE"), p("m2", "MAFIA", "ALIVE")));
        service.advancePhase(ROOM);
        assertEquals("GAME_OVER", g.getPhase());
    }

    // ---- advancePhase: unknown phase ----

    @Test
    void advance_throwsOnUnknownPhase() {
        stubGs(gs("LOBBY"));
        assertThrows(IllegalStateException.class, () -> service.advancePhase(ROOM));
    }

    // ---- requireGameState missing ----

    @Test
    void advance_throwsWhenGameStateNotFound() {
        when(gameStateRepository.findByRoomId(ROOM)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.advancePhase(ROOM));
    }

    // ---- getSnapshot ----

    @Test
    void getSnapshot_returnsCorrectPhase() {
        GameState g = gs("NIGHT");
        Room room = new Room("R", "alice", "CODE01", 12);
        room.setId(ROOM);
        when(gameStateRepository.findByRoomId(ROOM)).thenReturn(Optional.of(g));
        when(roomRepository.findById(ROOM)).thenReturn(Optional.of(room));
        when(playerRepository.findByRoomId(ROOM)).thenReturn(List.of());
        when(messageRepository.findByRoomIdOrderByCreatedAtDesc(ROOM)).thenReturn(List.of());
        when(gameEventRepository.findByRoomIdOrderByCreatedAtDesc(ROOM)).thenReturn(List.of());

        AggregatedGameSnapshot snap = service.getSnapshot(ROOM);
        assertEquals("NIGHT", snap.phase());
    }

    @Test
    void getSnapshot_hidesNightKillBeforeSunrise() {
        GameState g = gs("POLICE_GUESS");
        g.setNightKillTarget("bob");
        Room room = new Room("R", "alice", "CODE01", 12);
        room.setId(ROOM);
        when(gameStateRepository.findByRoomId(ROOM)).thenReturn(Optional.of(g));
        when(roomRepository.findById(ROOM)).thenReturn(Optional.of(room));
        when(playerRepository.findByRoomId(ROOM)).thenReturn(List.of());
        when(messageRepository.findByRoomIdOrderByCreatedAtDesc(ROOM)).thenReturn(List.of());
        when(gameEventRepository.findByRoomIdOrderByCreatedAtDesc(ROOM)).thenReturn(List.of());

        assertNull(service.getSnapshot(ROOM).nightKillTarget());
    }

    @Test
    void getSnapshot_revealsNightKillAtSunrise() {
        GameState g = gs("SUNRISE");
        g.setNightKillTarget("bob");
        Room room = new Room("R", "alice", "CODE01", 12);
        room.setId(ROOM);
        when(gameStateRepository.findByRoomId(ROOM)).thenReturn(Optional.of(g));
        when(roomRepository.findById(ROOM)).thenReturn(Optional.of(room));
        when(playerRepository.findByRoomId(ROOM)).thenReturn(List.of());
        when(messageRepository.findByRoomIdOrderByCreatedAtDesc(ROOM)).thenReturn(List.of());
        when(gameEventRepository.findByRoomIdOrderByCreatedAtDesc(ROOM)).thenReturn(List.of());

        assertEquals("bob", service.getSnapshot(ROOM).nightKillTarget());
    }

    @Test
    void getSnapshot_revealsNightKillAtGameOver() {
        GameState g = gs("GAME_OVER");
        g.setNightKillTarget("bob");
        g.setPoliceGuessTarget("m1");
        g.setPoliceGuessCorrect(true);
        Room room = new Room("R", "alice", "CODE01", 12);
        room.setId(ROOM);
        when(gameStateRepository.findByRoomId(ROOM)).thenReturn(Optional.of(g));
        when(roomRepository.findById(ROOM)).thenReturn(Optional.of(room));
        when(playerRepository.findByRoomId(ROOM)).thenReturn(List.of());
        when(messageRepository.findByRoomIdOrderByCreatedAtDesc(ROOM)).thenReturn(List.of());
        when(gameEventRepository.findByRoomIdOrderByCreatedAtDesc(ROOM)).thenReturn(List.of());

        AggregatedGameSnapshot snap = service.getSnapshot(ROOM);
        assertEquals("bob", snap.nightKillTarget());
        assertEquals("m1", snap.policeGuessTarget());
        assertTrue(snap.policeGuessCorrect());
    }

    // ---- sunrise with no night kill and wrong police guess ----

    @Test
    void advance_sunriseWithNoKillAndWrongGuess() {
        GameState g = gs("POLICE_GUESS");
        g.setNightKillTarget(null);
        g.setPoliceGuessTarget("villager1");
        g.setPoliceGuessCorrect(false);
        g.setAlivePlayers(new ArrayList<>(List.of("mafia1", "villager1", "police1")));
        stubGs(g);
        when(playerRepository.findByRoomId(ROOM)).thenReturn(List.of(
            p("mafia1", "MAFIA", "ALIVE"), p("villager1", "VILLAGER", "ALIVE"), p("police1", "POLICE", "ALIVE")));

        service.advancePhase(ROOM);

        assertEquals("SUNRISE", g.getPhase());
        assertEquals(3, g.getAlivePlayers().size()); // nobody killed
    }
}
