package com.mafia.entity;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Game state document — one per room.
 * Phases: LOBBY → ROLE_ASSIGNMENT → NIGHT → POLICE_GUESS → SUNRISE
 *         → DAY_DISCUSSION → VOTING → ELIMINATION → WIN_CHECK → NIGHT …
 *         or WIN_CHECK → GAME_OVER
 */
@Document(collection = "gameStates")
public class GameState {

    @Id
    private String id;
    private String roomId;

    /**
     * LOBBY | ROLE_ASSIGNMENT | NIGHT | POLICE_GUESS | SUNRISE
     * DAY_DISCUSSION | VOTING | ELIMINATION | WIN_CHECK | GAME_OVER
     */
    private String phase;
    private int dayNumber;
    private int nightNumber;

    private List<String> alivePlayers;
    private List<String> eliminatedPlayers;

    /** Who mafia targeted this night (null until NIGHT phase ends) */
    private String nightKillTarget;
    /** Who police guessed this night (null until POLICE_GUESS ends) */
    private String policeGuessTarget;
    /** True if police correctly identified a Mafia member */
    private Boolean policeGuessCorrect;
    /** Which players were saved by doctors this night (null until DOCTOR_SAVE ends) */
    private java.util.List<String> doctorSaveTargets;
    /** True if the night kill failed (due to doctor save or soldier immunity) */
    private Boolean nightKillFailed;

    /** NONE | MAFIA | VILLAGERS */
    private String winner;

    private LocalDateTime phaseStartTime;
    private LocalDateTime phaseEndTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public GameState() {}

    public GameState(String roomId) {
        this.roomId = roomId;
        this.phase = "LOBBY";
        this.dayNumber = 0;
        this.nightNumber = 0;
        this.alivePlayers = new java.util.ArrayList<>();
        this.eliminatedPlayers = new java.util.ArrayList<>();
        this.nightKillTarget = null;
        this.policeGuessTarget = null;
        this.policeGuessCorrect = null;
        this.doctorSaveTargets = new java.util.ArrayList<>();
        this.nightKillFailed = null;
        this.winner = "NONE";
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // ---- Getters & Setters ----

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }

    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }

    public int getDayNumber() { return dayNumber; }
    public void setDayNumber(int dayNumber) { this.dayNumber = dayNumber; }

    public int getNightNumber() { return nightNumber; }
    public void setNightNumber(int nightNumber) { this.nightNumber = nightNumber; }

    public List<String> getAlivePlayers() { return alivePlayers; }
    public void setAlivePlayers(List<String> alivePlayers) { this.alivePlayers = alivePlayers; }

    public List<String> getEliminatedPlayers() { return eliminatedPlayers; }
    public void setEliminatedPlayers(List<String> eliminatedPlayers) { this.eliminatedPlayers = eliminatedPlayers; }

    public String getNightKillTarget() { return nightKillTarget; }
    public void setNightKillTarget(String nightKillTarget) { this.nightKillTarget = nightKillTarget; }

    public String getPoliceGuessTarget() { return policeGuessTarget; }
    public void setPoliceGuessTarget(String policeGuessTarget) { this.policeGuessTarget = policeGuessTarget; }

    public Boolean getPoliceGuessCorrect() { return policeGuessCorrect; }
    public void setPoliceGuessCorrect(Boolean policeGuessCorrect) { this.policeGuessCorrect = policeGuessCorrect; }

    public java.util.List<String> getDoctorSaveTargets() { return doctorSaveTargets; }
    public void setDoctorSaveTargets(java.util.List<String> doctorSaveTargets) { this.doctorSaveTargets = doctorSaveTargets; }

    public Boolean getNightKillFailed() { return nightKillFailed; }
    public void setNightKillFailed(Boolean nightKillFailed) { this.nightKillFailed = nightKillFailed; }

    public String getWinner() { return winner; }
    public void setWinner(String winner) { this.winner = winner; }

    public LocalDateTime getPhaseStartTime() { return phaseStartTime; }
    public void setPhaseStartTime(LocalDateTime phaseStartTime) { this.phaseStartTime = phaseStartTime; }

    public LocalDateTime getPhaseEndTime() { return phaseEndTime; }
    public void setPhaseEndTime(LocalDateTime phaseEndTime) { this.phaseEndTime = phaseEndTime; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
