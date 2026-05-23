package com.mafia.service;

import com.mafia.entity.GameEvent;
import com.mafia.entity.GameState;
import com.mafia.entity.Player;
import com.mafia.client.EventServiceClient;
import com.mafia.repository.GameEventRepository;
import com.mafia.repository.GameStateRepository;
import com.mafia.repository.PlayerRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class NightPhaseService {

    private static final int PHASE_TIMER_SECONDS = 30;

    private final GameStateRepository gameStateRepository;
    private final PlayerRepository playerRepository;
    private final GameEventRepository gameEventRepository;
    private final WinConditionService winConditionService;
    private final EventServiceClient eventServiceClient;

    public NightPhaseService(GameStateRepository gameStateRepository,
            PlayerRepository playerRepository,
            GameEventRepository gameEventRepository,
            WinConditionService winConditionService,
            EventServiceClient eventServiceClient) {
        this.gameStateRepository = gameStateRepository;
        this.playerRepository = playerRepository;
        this.gameEventRepository = gameEventRepository;
        this.winConditionService = winConditionService;
        this.eventServiceClient = eventServiceClient;
    }

    public void submitNightKill(String roomId, String targetUsername) {
        GameState gs = requireGameState(roomId);
        if (!"NIGHT".equals(gs.getPhase())) {
            throw new IllegalStateException("Not in NIGHT phase");
        }
        requireAlivePlayer(targetUsername, roomId);
        Player target = playerRepository.findByUsernameAndRoomId(targetUsername, roomId)
                .orElseThrow(() -> new IllegalArgumentException("Player not found: " + targetUsername));
        if ("MAFIA".equals(target.getRole())) {
            throw new IllegalStateException("Mafia cannot target another Mafia");
        }
        gs.setNightKillTarget(targetUsername);
        gs.setUpdatedAt(LocalDateTime.now());
        gameStateRepository.save(gs);
        String nightKillMsg = "Mafia has chosen their target";
        gameEventRepository.save(new GameEvent(roomId, "NIGHT_KILL", nightKillMsg));
        eventServiceClient.pushEvent(roomId, "NIGHT_KILL", nightKillMsg);
    }

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
            String msg = "Police correctly identified a Mafia member: " + suspectUsername;
            gameEventRepository.save(new GameEvent(roomId, "POLICE_GUESS", msg));
            eventServiceClient.pushEvent(roomId, "POLICE_GUESS", msg);
        } else {
            String msg = "Police made a guess but it was incorrect.";
            gameEventRepository.save(new GameEvent(roomId, "POLICE_GUESS", msg));
            eventServiceClient.pushEvent(roomId, "POLICE_GUESS", msg);
        }
    }

    public void submitDoctorSave(String roomId, String savedUsername) {
        GameState gs = requireGameState(roomId);
        if (!"DOCTOR_SAVE".equals(gs.getPhase())) {
            throw new IllegalStateException("Not in DOCTOR_SAVE phase");
        }
        requireAlivePlayer(savedUsername, roomId);

        List<String> saves = gs.getDoctorSaveTargets();
        if (saves == null)
            saves = new ArrayList<>();
        if (!saves.contains(savedUsername))
            saves.add(savedUsername);
        gs.setDoctorSaveTargets(saves);
        gs.setUpdatedAt(LocalDateTime.now());
        gameStateRepository.save(gs);

        String doctorMsg = "A doctor has chosen a save target.";
        gameEventRepository.save(new GameEvent(roomId, "DOCTOR_SAVE", doctorMsg));
        eventServiceClient.pushEvent(roomId, "DOCTOR_SAVE", doctorMsg);
    }

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

    private void transitionToPoliceGuess(String roomId, GameState gs) {
        setPhase(roomId, gs, "POLICE_GUESS");
        gameStateRepository.save(gs);
        String msg = "Night is over. Police is investigating.";
        gameEventRepository.save(new GameEvent(roomId, "PHASE_TRANSITIONED", msg));
        eventServiceClient.pushEvent(roomId, "PHASE_TRANSITIONED", msg);
    }

    private void transitionToDoctorSave(String roomId, GameState gs) {
        setPhase(roomId, gs, "DOCTOR_SAVE");
        gameStateRepository.save(gs);
        String msg = "Doctors may now choose players to save.";
        gameEventRepository.save(new GameEvent(roomId, "PHASE_TRANSITIONED", msg));
        eventServiceClient.pushEvent(roomId, "PHASE_TRANSITIONED", msg);
    }

    private void transitionToSunrise(String roomId, GameState gs) {
        gs.setNightKillFailed(null);

        if (gs.getNightKillTarget() != null && gs.getAlivePlayers().contains(gs.getNightKillTarget())) {
            String victim = gs.getNightKillTarget();

            boolean protectedByDoctor = gs.getDoctorSaveTargets() != null
                    && gs.getDoctorSaveTargets().contains(victim);

            boolean isSoldier = playerRepository.findByUsernameAndRoomId(victim, roomId)
                    .map(p -> "SOLDIER".equals(p.getRole()))
                    .orElse(false);

            if (protectedByDoctor || isSoldier) {
                gs.setNightKillFailed(true);
            } else {
                eliminatePlayer(roomId, gs, victim);
                gs.setNightKillFailed(false);
            }
        }

        if (Boolean.TRUE.equals(gs.getPoliceGuessCorrect()) && gs.getPoliceGuessTarget() != null) {
            if (gs.getAlivePlayers().contains(gs.getPoliceGuessTarget())) {
                eliminatePlayer(roomId, gs, gs.getPoliceGuessTarget());
            }
        }

        String winner = winConditionService.checkWinCondition(roomId);
        if (!"NONE".equals(winner)) {
            gs.setWinner(winner);
            setPhase(roomId, gs, "GAME_OVER");
            gameStateRepository.save(gs);
            String gameOverMsg = winner + " wins!";
            gameEventRepository.save(new GameEvent(roomId, "GAME_OVER", gameOverMsg));
            eventServiceClient.pushEvent(roomId, "GAME_OVER", gameOverMsg);
            return;
        }

        setPhase(roomId, gs, "SUNRISE");
        gameStateRepository.save(gs);
        String sunriseMsg = buildSunriseMessage(gs);
        gameEventRepository.save(new GameEvent(roomId, "PHASE_TRANSITIONED", sunriseMsg));
        eventServiceClient.pushEvent(roomId, "PHASE_TRANSITIONED", sunriseMsg);
    }

    private void transitionToDayDiscussion(String roomId, GameState gs) {
        gs.setDayNumber(gs.getDayNumber() + 1);
        setPhase(roomId, gs, "DAY_DISCUSSION");
        gs.setNightKillTarget(null);
        gs.setPoliceGuessTarget(null);
        gs.setPoliceGuessCorrect(null);
        gs.setDoctorSaveTargets(new ArrayList<>());
        gs.setNightKillFailed(null);
        gameStateRepository.save(gs);
        String dayMsg = "Day " + gs.getDayNumber() + " discussion begins.";
        gameEventRepository.save(new GameEvent(roomId, "PHASE_TRANSITIONED", dayMsg));
        eventServiceClient.pushEvent(roomId, "PHASE_TRANSITIONED", dayMsg);
    }

    private void transitionToVoting(String roomId, GameState gs) {
        setPhase(roomId, gs, "VOTING");
        gameStateRepository.save(gs);
        String voteMsg = "Voting begins. Choose who to eliminate.";
        gameEventRepository.save(new GameEvent(roomId, "PHASE_TRANSITIONED", voteMsg));
        eventServiceClient.pushEvent(roomId, "PHASE_TRANSITIONED", voteMsg);
    }

    private void transitionAfterVoting(String roomId, GameState gs) {
        String winner = winConditionService.checkWinCondition(roomId);
        if (!"NONE".equals(winner)) {
            gs.setWinner(winner);
            setPhase(roomId, gs, "GAME_OVER");
            gameStateRepository.save(gs);
            String msg = winner + " wins!";
            gameEventRepository.save(new GameEvent(roomId, "GAME_OVER", msg));
            eventServiceClient.pushEvent(roomId, "GAME_OVER", msg);
        } else {
            gs.setNightNumber(gs.getNightNumber() + 1);
            gs.setNightKillTarget(null);
            gs.setPoliceGuessTarget(null);
            gs.setPoliceGuessCorrect(null);
            gs.setDoctorSaveTargets(new ArrayList<>());
            gs.setNightKillFailed(null);
            setPhase(roomId, gs, "NIGHT");
            gameStateRepository.save(gs);
            String nightMsg = "Night " + gs.getNightNumber() + " begins.";
            gameEventRepository.save(new GameEvent(roomId, "PHASE_TRANSITIONED", nightMsg));
            eventServiceClient.pushEvent(roomId, "PHASE_TRANSITIONED", nightMsg);
        }
    }

    private void checkWinAfterElimination(String roomId, GameState gs) {
        String winner = winConditionService.checkWinCondition(roomId);
        if (!"NONE".equals(winner)) {
            gs.setWinner(winner);
            setPhase(roomId, gs, "GAME_OVER");
            gameStateRepository.save(gs);
            String msg = winner + " wins!";
            gameEventRepository.save(new GameEvent(roomId, "GAME_OVER", msg));
            eventServiceClient.pushEvent(roomId, "GAME_OVER", msg);
        } else {
            String msg = "Win check complete. Game continues.";
            setPhase(roomId, gs, "WIN_CHECK");
            gameStateRepository.save(gs);
            gameEventRepository.save(new GameEvent(roomId, "PHASE_TRANSITIONED", msg));
            eventServiceClient.pushEvent(roomId, "PHASE_TRANSITIONED", msg);
        }
    }

    private void transitionToNextNight(String roomId, GameState gs) {
        String winner = winConditionService.checkWinCondition(roomId);
        if (!"NONE".equals(winner)) {
            gs.setWinner(winner);
            setPhase(roomId, gs, "GAME_OVER");
            gameStateRepository.save(gs);
            String msg = winner + " wins!";
            gameEventRepository.save(new GameEvent(roomId, "GAME_OVER", msg));
            eventServiceClient.pushEvent(roomId, "GAME_OVER", msg);
        } else {
            gs.setNightNumber(gs.getNightNumber() + 1);
            gs.setDoctorSaveTargets(new ArrayList<>());
            gs.setNightKillFailed(null);
            setPhase(roomId, gs, "NIGHT");
            gameStateRepository.save(gs);
            String nightMsg = "Night " + gs.getNightNumber() + " begins.";
            gameEventRepository.save(new GameEvent(roomId, "PHASE_TRANSITIONED", nightMsg));
            eventServiceClient.pushEvent(roomId, "PHASE_TRANSITIONED", nightMsg);
        }
    }

    public void eliminatePlayer(String roomId, GameState gs, String username) {
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

    private void setPhase(String roomId, GameState gs, String phase) {
        gs.setPhase(phase);
        gs.setPhaseStartTime(LocalDateTime.now());
        gs.setUpdatedAt(LocalDateTime.now());
        if ("GAME_OVER".equals(phase)) {
            eventServiceClient.cancelPhaseTimer(roomId);
            return;
        }
        eventServiceClient.startPhaseTimer(roomId, phase, PHASE_TIMER_SECONDS);
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
                sb.append("An attempted kill on ").append(gs.getNightKillTarget())
                        .append(" failed last night. ");
            } else if (Boolean.FALSE.equals(gs.getNightKillFailed())) {
                sb.append(gs.getNightKillTarget()).append(" was killed during the night. ");
            } else {
                sb.append( "A Player was targeted during the night. ");
            }
        } else {
            sb.append("Nobody was killed last night. ");
        }
        if (gs.getPoliceGuessTarget() != null) {
            if (Boolean.TRUE.equals(gs.getPoliceGuessCorrect())) {
                sb.append("Police correctly identified ").append(gs.getPoliceGuessTarget())
                        .append(" as Mafia!");
            } else {
                sb.append("Police guessed, but they were wrong.");
            }
        }
        return sb.toString();
    }
}