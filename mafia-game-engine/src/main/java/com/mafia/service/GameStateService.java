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
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class GameStateService {

    private static final int MIN_PLAYERS = 6;

    private final GameStateRepository gameStateRepository;
    private final PlayerRepository playerRepository;
    private final GameEventRepository gameEventRepository;
    private final MessageRepository messageRepository;
    private final RoomRepository roomRepository;
    private final WinConditionService winConditionService;
    private final EventServiceClient eventServiceClient;
    private final RoleAssignmentService roleAssignmentService;

    public GameStateService(GameStateRepository gameStateRepository,
            PlayerRepository playerRepository,
            GameEventRepository gameEventRepository,
            MessageRepository messageRepository,
            RoomRepository roomRepository,
            WinConditionService winConditionService,
            EventServiceClient eventServiceClient,
            RoleAssignmentService roleAssignmentService) {
        this.gameStateRepository = gameStateRepository;
        this.playerRepository = playerRepository;
        this.gameEventRepository = gameEventRepository;
        this.messageRepository = messageRepository;
        this.roomRepository = roomRepository;
        this.winConditionService = winConditionService;
        this.eventServiceClient = eventServiceClient;
        this.roleAssignmentService = roleAssignmentService;
    }

    public void initializeGameState(String roomId) {
        gameStateRepository.save(new GameState(roomId));
        pushEvent(roomId, "GAME_INITIALIZED", "Room created");
    }

    public AggregatedGameSnapshot getSnapshot(String roomId) {
        GameState gs = requireGameState(roomId);
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found: " + roomId));
        List<Player> players = playerRepository.findByRoomId(roomId);

        List<Map<String, Object>> playerMaps = players.stream().map(p -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("name", p.getUsername());
            m.put("alive", "ALIVE".equals(p.getStatus()));
            m.put("role", p.getRole());
            m.put("voteEligibleDayNumber", p.getVoteEligibleDayNumber());
            return m;
        }).collect(Collectors.toList());

        List<Map<String, Object>> chatMaps = messageRepository
                .findByRoomIdOrderByCreatedAtDesc(roomId).stream()
                .map(m -> Map.of(
                        "sender", (Object) m.getSenderUsername(),
                        "message", (Object) m.getContent(),
                        "timestamp", (Object) m.getCreatedAt().toString()))
                .collect(Collectors.toList());

        List<Map<String, Object>> eventMaps = eventServiceClient.getEvents(roomId);
        if (eventMaps.isEmpty()) {
            eventMaps = gameEventRepository
                    .findByRoomIdOrderByCreatedAtDesc(roomId).stream()
                    .limit(20)
                    .map(e -> Map.<String, Object>of(
                            "event",       e.getEventType(),
                            "description", e.getDescription(),
                            "at",          e.getCreatedAt().toString()))
                    .collect(Collectors.toList());
        }

        boolean revealNight = isAtOrAfter(gs.getPhase(), "SUNRISE");
        String nightKill = revealNight ? gs.getNightKillTarget() : null;
        String policeGuess = (revealNight && Boolean.TRUE.equals(gs.getPoliceGuessCorrect()))
                ? gs.getPoliceGuessTarget() : null;
        Boolean policeCorrect = revealNight ? gs.getPoliceGuessCorrect() : null;
        Boolean nightKillFailed = revealNight ? gs.getNightKillFailed() : null;

        int remainingSeconds = eventServiceClient.getTimerRemainingSeconds(roomId);
        String phaseEndsAt = remainingSeconds > 0
                ? Instant.now().plusSeconds(remainingSeconds).toString() : null;

        return new AggregatedGameSnapshot(
                gs.getPhase(), gs.getDayNumber(), gs.getNightNumber(),
                playerMaps, gs.getAlivePlayers(), gs.getEliminatedPlayers(),
                nightKill, nightKillFailed, policeGuess, policeCorrect,
                gs.getWinner(), chatMaps, eventMaps, getAvailableActions(gs),
                room.getRoomCode(), room.getHostUsername(), phaseEndsAt);
    }

    public void startGame(String roomId) {
        GameState gs = requireGameState(roomId);
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found: " + roomId));
        List<Player> players = playerRepository.findByRoomId(roomId);

        if (players.size() < MIN_PLAYERS) {
            throw new IllegalStateException(
                    "Need at least " + MIN_PLAYERS + " players to start. Current: " + players.size());
        }

        roleAssignmentService.assignRoles(players);

        gs.setPhase("NIGHT");
        gs.setNightNumber(1);
        gs.setNightKillTarget(null);
        gs.setPoliceGuessTarget(null);
        gs.setPoliceGuessCorrect(null);
        gs.setDoctorSaveTargets(new ArrayList<>());
        gs.setNightKillFailed(null);
        gs.setPhaseStartTime(LocalDateTime.now());
        gs.setAlivePlayers(players.stream().map(Player::getUsername).collect(Collectors.toList()));
        gs.setUpdatedAt(LocalDateTime.now());
        gameStateRepository.save(gs);

        eventServiceClient.startPhaseTimer(roomId, "NIGHT", 30);

        room.setStatus("IN_GAME");
        room.setUpdatedAt(LocalDateTime.now());
        roomRepository.save(room);

        pushEvent(roomId, "GAME_STARTED", "Game started with " + players.size() + " players");
    }

    public GameState requireGameState(String roomId) {
        return gameStateRepository.findByRoomId(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Game not found for room: " + roomId));
    }

    public boolean isAtOrAfter(String phase, String target) {
        List<String> order = List.of(
                "LOBBY", "ROLE_ASSIGNMENT", "NIGHT", "POLICE_GUESS",
                "DOCTOR_SAVE", "SUNRISE", "DAY_DISCUSSION", "VOTING",
                "ELIMINATION", "WIN_CHECK", "GAME_OVER");
        return order.indexOf(phase) >= order.indexOf(target);
    }

    private void pushEvent(String roomId, String type, String msg) {
        gameEventRepository.save(new GameEvent(roomId, type, msg));
        eventServiceClient.pushEvent(roomId, type, msg);
    }

    private List<String> getAvailableActions(GameState gs) {
        List<String> actions = new ArrayList<>(List.of("send_message"));
        switch (gs.getPhase()) {
            case "LOBBY" -> actions.addAll(List.of("start_game", "leave_room"));
            case "NIGHT" -> actions.add("submit_night_kill");
            case "POLICE_GUESS" -> actions.add("submit_police_guess");
            case "DOCTOR_SAVE" -> actions.add("submit_doctor_save");
            case "VOTING" -> actions.add("submit_vote");
            case "GAME_OVER" -> actions.add("restart");
        }
        return actions;
    }
}