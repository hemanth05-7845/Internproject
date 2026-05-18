package com.mafia.entity;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "players")
public class Player {

    @Id
    private String id;
    private String username;
    private String roomId;
    private String role; // VILLAGER, MAFIA, POLICE
    private String status; // ALIVE, ELIMINATED
    private Integer voteEligibleDayNumber;
    private boolean ready;
    private LocalDateTime joinedAt;

    public Player() {}

    public Player(String username, String roomId) {
        this.username = username;
        this.roomId = roomId;
        this.status = "ALIVE";
        this.voteEligibleDayNumber = null;
        this.ready = false;
        this.joinedAt = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getVoteEligibleDayNumber() {
        return voteEligibleDayNumber;
    }

    public void setVoteEligibleDayNumber(Integer voteEligibleDayNumber) {
        this.voteEligibleDayNumber = voteEligibleDayNumber;
    }

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(LocalDateTime joinedAt) {
        this.joinedAt = joinedAt;
    }
}
