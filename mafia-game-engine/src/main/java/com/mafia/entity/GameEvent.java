package com.mafia.entity;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "gameEvents")
public class GameEvent {

    @Id
    private String id;
    private String roomId;
    private String eventType; // GAME_STARTED, ROLE_ASSIGNED, PLAYER_ELIMINATED, VOTE_CAST, PHASE_TRANSITIONED
    private String targetPlayerId;
    private String actor;
    private String description;
    private LocalDateTime createdAt;

    public GameEvent() {}

    public GameEvent(String roomId, String eventType, String description) {
        this.roomId = roomId;
        this.eventType = eventType;
        this.description = description;
        this.createdAt = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getTargetPlayerId() {
        return targetPlayerId;
    }

    public void setTargetPlayerId(String targetPlayerId) {
        this.targetPlayerId = targetPlayerId;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
