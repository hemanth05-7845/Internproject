package com.mafia.entity;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Represents a game room.
 * roomCode is the short 6-char share code that players use to join.
 */
@Document(collection = "rooms")
public class Room {

    @Id
    private String id;

    /** Human-readable name given by the host */
    private String name;

    /** The username of the player who created the room (host) */
    private String hostUsername;

    /** 6-char uppercase alphanumeric share code, e.g. "XK92PL" */
    @Indexed(unique = true)
    private String roomCode;

    private int maxPlayers;
    private int minPlayers;
    private List<String> playerIds;
    private String status; // ACTIVE, CLOSED

    @Version
    private Long version;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Room() {}

    public Room(String name, String hostUsername, String roomCode, int maxPlayers) {
        this.name = name;
        this.hostUsername = hostUsername;
        this.roomCode = roomCode;
        this.maxPlayers = maxPlayers;
        this.minPlayers = 6;
        this.playerIds = new java.util.ArrayList<>(List.of(hostUsername));
        this.status = "ACTIVE";
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // ---- Getters & Setters ----

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getHostUsername() { return hostUsername; }
    public void setHostUsername(String hostUsername) { this.hostUsername = hostUsername; }

    public String getRoomCode() { return roomCode; }
    public void setRoomCode(String roomCode) { this.roomCode = roomCode; }

    public int getMaxPlayers() { return maxPlayers; }
    public void setMaxPlayers(int maxPlayers) { this.maxPlayers = maxPlayers; }

    public int getMinPlayers() { return minPlayers; }
    public void setMinPlayers(int minPlayers) { this.minPlayers = minPlayers; }

    public List<String> getPlayerIds() { return playerIds; }
    public void setPlayerIds(List<String> playerIds) { this.playerIds = playerIds; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
