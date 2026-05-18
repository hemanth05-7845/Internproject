package com.mafia.service;

import com.mafia.entity.GameEvent;
import com.mafia.entity.GameState;
import com.mafia.entity.Player;
import com.mafia.entity.Room;
import com.mafia.dto.response.AggregatedGameSnapshot;
import com.mafia.repository.GameEventRepository;
import com.mafia.repository.GameStateRepository;
import com.mafia.repository.MessageRepository;
import com.mafia.repository.PlayerRepository;
import com.mafia.repository.RoomRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import com.mafia.service.WinConditionService;
@Service
public class GameStateService {

    private final WinConditionService winConditionService;
    private static final int MIN_PLAYERS = 2;
    private final GameStateRepository gameStateRepository;
    private final PlayerRepository playerRepository;
    private final GameEventRepository gameEventRepository;
    private final MessageRepository messageRepository;
    private final RoomRepository roomRepository;

    public GameStateService(GameStateRepository gameStateRepository,
                            PlayerRepository playerRepository,
                            GameEventRepository gameEventRepository,
                            MessageRepository messageRepository,
                            RoomRepository roomRepository, WinConditionService winConditionService) {
        this.gameStateRepository = gameStateRepository;
        this.playerRepository = playerRepository;
        this.gameEventRepository = gameEventRepository;
        this.messageRepository = messageRepository;
        this.roomRepository = roomRepository;
        this.winConditionService = winConditionService;
    }

    /** Called by RoomService when a room is created. */
    public void initializeGameState(String roomId) {
        gameStateRepository.save(new GameState(roomId));
        gameEventRepository.save(new GameEvent(roomId, "GAME_INITIALIZED", "Room created"));
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
            m.put("role", p.getRole()); // gateway will filter per-user
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

        List<Map<String, Object>> eventMaps = gameEventRepository
                .findByRoomIdOrderByCreatedAtDesc(roomId).stream()
                .limit(20)
                .map(e -> Map.of(
                        "type", (Object) e.getEventType(),
                        "description", (Object) e.getDescription(),
                        "at", (Object) e.getCreatedAt().toString()))
                .collect(Collectors.toList());

        // Reveal night/police results only from SUNRISE onwards
        boolean revealNight = isAtOrAfter(gs.getPhase(), "SUNRISE");
        String nightKill = revealNight ? gs.getNightKillTarget() : null;
        // Only reveal police guess if it was correct; an incorrect guess should remain hidden
        String policeGuess = (revealNight && Boolean.TRUE.equals(gs.getPoliceGuessCorrect())) ? gs.getPoliceGuessTarget() : null;
        Boolean policeCorrect = revealNight ? gs.getPoliceGuessCorrect() : null;
        Boolean nightKillFailed = revealNight ? gs.getNightKillFailed() : null;

        return new AggregatedGameSnapshot(
                gs.getPhase(),
                gs.getDayNumber(),
                gs.getNightNumber(),
                playerMaps,
                gs.getAlivePlayers(),
                gs.getEliminatedPlayers(),
                nightKill,
                nightKillFailed,
                policeGuess,
                policeCorrect,
                gs.getWinner(),
                chatMaps,
                eventMaps,
                getAvailableActions(gs),
                room.getRoomCode(),
                room.getHostUsername(),
                Instant.now().toString()
        );
    }

    // ---- Game start ----

    public void startGame(String roomId) {
        GameState gs = requireGameState(roomId);
        List<Player> players = playerRepository.findByRoomId(roomId);

        if (players.size() < MIN_PLAYERS) {
            throw new IllegalStateException(
                    "Need at least " + MIN_PLAYERS + " players to start. Current: " + players.size());
        }
        assignRoles(players);

        gs.setPhase("NIGHT");
        gs.setNightNumber(1);
        gs.setNightKillTarget(null);
        gs.setPoliceGuessTarget(null);
        gs.setPoliceGuessCorrect(null);
        gs.setDoctorSaveTargets(new java.util.ArrayList<>());
        gs.setNightKillFailed(null);
        gs.setPhaseStartTime(LocalDateTime.now());
        gs.setAlivePlayers(players.stream().map(Player::getUsername).collect(Collectors.toList()));
        gs.setUpdatedAt(LocalDateTime.now());
        gameStateRepository.save(gs);

        gameEventRepository.save(new GameEvent(roomId, "GAME_STARTED",
                "Game started with " + players.size() + " players"));
    }

    // ---- Night kill submission ----

    public void submitNightKill(String roomId, String targetUsername) {
        GameState gs = requireGameState(roomId);
        if (!"NIGHT".equals(gs.getPhase())) {
            throw new IllegalStateException("Not in NIGHT phase");
        }
        requireAlivePlayer(targetUsername, roomId);
        gs.setNightKillTarget(targetUsername);
        gs.setUpdatedAt(LocalDateTime.now());
        gameStateRepository.save(gs);
        gameEventRepository.save(new GameEvent(roomId, "NIGHT_KILL",
                "Mafia has chosen their target"));
    }

    // ---- Police guess submission ----

    public void submitPoliceGuess(String roomId, String suspectUsername) {
        GameState gs = requireGameState(roomId);
        if (!"POLICE_GUESS".equals(gs.getPhase())) {
            throw new IllegalStateException("Not in POLICE_GUESS phase");
        }
        requireAlivePlayer(suspectUsername, roomId);

        Player suspect = playerRepository.findByUsernameAndRoomId(suspectUsername, roomId)
                .orElseThrow(() -> new IllegalArgumentException("Player not found: " + suspectUsername));
        boolean correct = "MAFIA".equals(suspect.getRole());

        gs.setPoliceGuessTarget(suspectUsername);
        gs.setPoliceGuessCorrect(correct);
        gs.setUpdatedAt(LocalDateTime.now());
        gameStateRepository.save(gs);

        if (correct) {
            gameEventRepository.save(new GameEvent(roomId, "POLICE_GUESS",
                "Police correctly identified a Mafia member: " + suspectUsername));
        } else {
            // Do not include the suspect's name when the guess is wrong to avoid revealing info
            gameEventRepository.save(new GameEvent(roomId, "POLICE_GUESS",
                "Police made a guess but it was incorrect."));
        }
    }

    // ---- Doctor save submission ----

    public void submitDoctorSave(String roomId, String savedUsername) {
        GameState gs = requireGameState(roomId);
        if (!"DOCTOR_SAVE".equals(gs.getPhase())) {
            throw new IllegalStateException("Not in DOCTOR_SAVE phase");
        }
        requireAlivePlayer(savedUsername, roomId);

        java.util.List<String> saves = gs.getDoctorSaveTargets();
        if (saves == null) {
            saves = new java.util.ArrayList<>();
        }
        if (!saves.contains(savedUsername)) saves.add(savedUsername);
        gs.setDoctorSaveTargets(saves);
        gs.setUpdatedAt(java.time.LocalDateTime.now());
        gameStateRepository.save(gs);

        gameEventRepository.save(new GameEvent(roomId, "DOCTOR_SAVE",
            "A doctor has chosen a save target."));
    }

    // ---- Phase management ----
    /**
     * Advances to the next phase, applying any pending business logic.
     * Called by the host via POST /api/game/{roomId}/advance-phase.
     */
    public void advancePhase(String roomId) {
        GameState gs = requireGameState(roomId);
        String current = gs.getPhase();

        switch (current) {
            case "NIGHT" -> transitionToPoliceGuess(roomId, gs);
            case "POLICE_GUESS" -> transitionToDoctorSave(roomId, gs);
            case "DOCTOR_SAVE" -> transitionToSunrise(roomId, gs);
            case "SUNRISE" -> transitionToDayDiscussion(roomId, gs);
            case "DAY_DISCUSSION" -> transitionToVoting(roomId, gs);
            case "VOTING" -> transitionAfterVoting(roomId, gs);
            case "ELIMINATION" -> checkWinAfterElimination(roomId, gs);
            case "WIN_CHECK" -> transitionToNextNight(roomId, gs);
            default -> throw new IllegalStateException("Cannot advance from phase: " + current);
        }
    }

    // ---- Phase transitions ----

    private void transitionToPoliceGuess(String roomId, GameState gs) {
        // Night kill is NOT applied here — victim stays in alivePlayers so police
        // cannot deduce who was killed before sunrise is announced.
        setPhase(gs, "POLICE_GUESS");
        gameStateRepository.save(gs);
        gameEventRepository.save(new GameEvent(roomId, "PHASE_TRANSITIONED",
                "Night is over. Police is investigating."));
    }

    private void transitionToDoctorSave(String roomId, GameState gs) {
        setPhase(gs, "DOCTOR_SAVE");
        gameStateRepository.save(gs);
        gameEventRepository.save(new GameEvent(roomId, "PHASE_TRANSITIONED",
                "Doctors may now choose players to save."));
    }

    private void transitionToSunrise(String roomId, GameState gs) {
        // Apply night kill NOW (hidden from police during POLICE_GUESS/DOCTOR_SAVE phases)
        // Reset nightKillFailed
        gs.setNightKillFailed(null);
        if (gs.getNightKillTarget() != null && gs.getAlivePlayers().contains(gs.getNightKillTarget())) {
            String victim = gs.getNightKillTarget();
            boolean protectedByDoctor = gs.getDoctorSaveTargets() != null && gs.getDoctorSaveTargets().contains(victim);
            // Soldier immunity: if victim is a Soldier, kill fails
            boolean isSoldier = false;
            try {
                isSoldier = playerRepository.findByUsernameAndRoomId(victim, roomId)
                        .map(p -> "SOLDIER".equals(p.getRole())).orElse(false);
            } catch (Exception ignored) {}

            if (protectedByDoctor || isSoldier) {
                gs.setNightKillFailed(true);
            } else {
                eliminatePlayer(roomId, gs, victim);
                gs.setNightKillFailed(false);
            }
        }
        // Apply police guess elimination if correct
        if (Boolean.TRUE.equals(gs.getPoliceGuessCorrect()) && gs.getPoliceGuessTarget() != null) {
            if (gs.getAlivePlayers().contains(gs.getPoliceGuessTarget())) {
                eliminatePlayer(roomId, gs, gs.getPoliceGuessTarget());
            }
        }
        // Check if any elimination ended the game
        String winner = computeWinner(playerRepository.findByRoomId(roomId));
        if (!"NONE".equals(winner)) {
            gs.setWinner(winner);
            setPhase(gs, "GAME_OVER");
            gameStateRepository.save(gs);
            gameEventRepository.save(new GameEvent(roomId, "GAME_OVER", winner + " wins!"));
            return;
        }
        setPhase(gs, "SUNRISE");
        gameStateRepository.save(gs);
        gameEventRepository.save(new GameEvent(roomId, "PHASE_TRANSITIONED",
                buildSunriseMessage(gs)));
    }

    private void transitionToDayDiscussion(String roomId, GameState gs) {
        gs.setDayNumber(gs.getDayNumber() + 1);
        setPhase(gs, "DAY_DISCUSSION");
        // Clear night-round tracking for next cycle
        gs.setNightKillTarget(null);
        gs.setPoliceGuessTarget(null);
        gs.setPoliceGuessCorrect(null);
        gs.setDoctorSaveTargets(new java.util.ArrayList<>());
        gs.setNightKillFailed(null);
        gameStateRepository.save(gs);
        gameEventRepository.save(new GameEvent(roomId, "PHASE_TRANSITIONED",
                "Day " + gs.getDayNumber() + " discussion begins."));
    }

    private void transitionToVoting(String roomId, GameState gs) {
        setPhase(gs, "VOTING");
        gameStateRepository.save(gs);
        gameEventRepository.save(new GameEvent(roomId, "PHASE_TRANSITIONED",
                "Voting begins. Choose who to eliminate."));
    }

    private void transitionAfterVoting(String roomId, GameState gs) {
        // Win check runs immediately after elimination (already applied by GameLoopService)
        String winner = computeWinner(playerRepository.findByRoomId(roomId));
        if (!"NONE".equals(winner)) {
            gs.setWinner(winner);
            setPhase(gs, "GAME_OVER");
            gameStateRepository.save(gs);
            gameEventRepository.save(new GameEvent(roomId, "GAME_OVER", winner + " wins!"));
        } else {
            // No winner — start next night
            gs.setNightNumber(gs.getNightNumber() + 1);
            gs.setNightKillTarget(null);
            gs.setPoliceGuessTarget(null);
            gs.setPoliceGuessCorrect(null);
            gs.setDoctorSaveTargets(new java.util.ArrayList<>());
            gs.setNightKillFailed(null);
            setPhase(gs, "NIGHT");
            gameStateRepository.save(gs);
            gameEventRepository.save(new GameEvent(roomId, "PHASE_TRANSITIONED",
                    "Night " + gs.getNightNumber() + " begins."));
        }
    }

    private void checkWinAfterElimination(String roomId, GameState gs) {
        String winner = computeWinner(playerRepository.findByRoomId(roomId));
        if (!"NONE".equals(winner)) {
            gs.setWinner(winner);
            setPhase(gs, "GAME_OVER");
            gameStateRepository.save(gs);
            gameEventRepository.save(new GameEvent(roomId, "GAME_OVER",
                    winner + " wins!"));
        } else {
            setPhase(gs, "WIN_CHECK");
            gameStateRepository.save(gs);
            gameEventRepository.save(new GameEvent(roomId, "PHASE_TRANSITIONED",
                    "Win check complete. Game continues."));
        }
    }

    private void transitionToNextNight(String roomId, GameState gs) {
        String winner = computeWinner(playerRepository.findByRoomId(roomId));
        if (!"NONE".equals(winner)) {
            gs.setWinner(winner);
            setPhase(gs, "GAME_OVER");
            gameStateRepository.save(gs);
            gameEventRepository.save(new GameEvent(roomId, "GAME_OVER", winner + " wins!"));
        } else {
            gs.setNightNumber(gs.getNightNumber() + 1);
            gs.setDoctorSaveTargets(new java.util.ArrayList<>());
            gs.setNightKillFailed(null);
            setPhase(gs, "NIGHT");
            gameStateRepository.save(gs);
            gameEventRepository.save(new GameEvent(roomId, "PHASE_TRANSITIONED",
                    "Night " + gs.getNightNumber() + " begins."));
        }
    }

    // ---- Helpers ----

    private void eliminatePlayer(String roomId, GameState gs, String username) {
        playerRepository.findByUsernameAndRoomId(username, roomId).ifPresent(p -> {
            p.setStatus("ELIMINATED");
            p.setVoteEligibleDayNumber(gs.getDayNumber());
            playerRepository.save(p);
        });
        List<String> alive = new ArrayList<>(gs.getAlivePlayers());
        List<String> elim = new ArrayList<>(gs.getEliminatedPlayers());
        alive.remove(username);
        elim.add(username);
        gs.setAlivePlayers(alive);
        gs.setEliminatedPlayers(elim);
    }

    private void setPhase(GameState gs, String phase) {
        gs.setPhase(phase);
        gs.setPhaseStartTime(LocalDateTime.now());
        gs.setUpdatedAt(LocalDateTime.now());
    }

    private void requireAlivePlayer(String username, String roomId) {
        Player p = playerRepository.findByUsernameAndRoomId(username, roomId)
                .orElseThrow(() -> new IllegalArgumentException("Player not found: " + username));
        if (!"ALIVE".equals(p.getStatus())) {
            throw new IllegalStateException("Player is already eliminated: " + username);
        }
    }

    private GameState requireGameState(String roomId) {
        return gameStateRepository.findByRoomId(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Game not found for room: " + roomId));
    }

    private String buildSunriseMessage(GameState gs) {
        StringBuilder sb = new StringBuilder("Sunrise! ");
        if (gs.getNightKillTarget() != null) {
            if (Boolean.TRUE.equals(gs.getNightKillFailed())) {
                sb.append("An attempted kill on ").append(gs.getNightKillTarget()).append(" failed last night. ");
            } else if (Boolean.FALSE.equals(gs.getNightKillFailed())) {
                sb.append(gs.getNightKillTarget()).append(" was killed during the night. ");
            } else {
                // nightKillFailed unknown (shouldn't happen) — fall back
                sb.append(gs.getNightKillTarget()).append(" was targeted during the night. ");
            }
        } else {
            sb.append("Nobody was killed last night. ");
        }
        if (gs.getPoliceGuessTarget() != null) {
            if (Boolean.TRUE.equals(gs.getPoliceGuessCorrect())) {
                sb.append("Police correctly identified ").append(gs.getPoliceGuessTarget())
                        .append(" as Mafia!");
            } else {
                sb.append("Police guessed ").append(gs.getPoliceGuessTarget())
                        .append(" — but they were wrong.");
            }
        }
        return sb.toString();
    }

    private void assignRoles(List<Player> players) {
        int n = players.size();
        int mafiaCount = Math.max(1, n / 3);
        int policeCount = 1;
        // 1 doctor per 4 players (rounded down). Ensure at least one doctor for medium+ games.
        int doctorCount = (n >= 4) ? Math.max(1, n / 4) : 0;
        List<String> roles = new ArrayList<>();
        for (int i = 0; i < mafiaCount; i++) roles.add("MAFIA");
        for (int i = 0; i < policeCount; i++) roles.add("POLICE");
        for (int i = 0; i < doctorCount; i++) roles.add("DOCTOR");
        // Try to include one soldier if space permits
        if (roles.size() < n) roles.add("SOLDIER");
        while (roles.size() < n) roles.add("VILLAGER");

        Collections.shuffle(roles);
        for (int i = 0; i < players.size(); i++) {
            players.get(i).setRole(roles.get(i));
            playerRepository.save(players.get(i));
        }
    }

    private String computeWinner(List<Player> players) {
        return winConditionService.checkWinCondition(players.get(0).getRoomId());
    }

    private boolean isAtOrAfter(String phase, String target) {
        List<String> order = List.of("LOBBY", "ROLE_ASSIGNMENT", "NIGHT", "POLICE_GUESS",
                "DOCTOR_SAVE", "SUNRISE", "DAY_DISCUSSION", "VOTING", "ELIMINATION", "WIN_CHECK", "GAME_OVER");
        return order.indexOf(phase) >= order.indexOf(target);
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
