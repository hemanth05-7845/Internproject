package com.mafia.service;

import com.mafia.entity.Player;
import com.mafia.entity.Room;
import com.mafia.repository.PlayerRepository;
import com.mafia.repository.RoomRepository;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RoomService {

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;
    private static final SecureRandom RNG = new SecureRandom();

    private final RoomRepository roomRepository;
    private final PlayerRepository playerRepository;
    private final GameStateService gameStateService;

    public RoomService(RoomRepository roomRepository, PlayerRepository playerRepository,
                       GameStateService gameStateService) {
        this.roomRepository = roomRepository;
        this.playerRepository = playerRepository;
        this.gameStateService = gameStateService;
    }

    /** Create a room and register the host as the first player. */
    public Room createRoom(String roomName, String hostUsername) {
        String code = generateUniqueCode();
        Room room = new Room(roomName, hostUsername, code, 12);
        Room saved = roomRepository.save(room);
        playerRepository.save(new Player(hostUsername, saved.getId()));
        gameStateService.initializeGameState(saved.getId());
        return saved;
    }

    public Room getRoomById(String roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found: " + roomId));
    }

    public Room getRoomByCode(String roomCode) {
        return roomRepository.findByRoomCode(roomCode.toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Room not found for code: " + roomCode));
    }

    /** Join by the 6-char share code (what the UI uses). */
    public Room joinRoomByCode(String roomCode, String username) {
        return addPlayerToRoom(getRoomByCode(roomCode), username);
    }

    public Room joinRoom(String roomId, String username) {
        return addPlayerToRoom(getRoomById(roomId), username);
    }

    private Room addPlayerToRoom(Room room, String username) {
        if (!"ACTIVE".equals(room.getStatus())) {
            throw new IllegalStateException("Room is not active");
        }
        if (room.getPlayerIds().size() >= room.getMaxPlayers()) {
            throw new IllegalStateException("Room is full");
        }
        // Idempotent — already in room
        if (room.getPlayerIds().contains(username)) return room;

        if (playerRepository.findByUsernameAndRoomId(username, room.getId()).isEmpty()) {
            playerRepository.save(new Player(username, room.getId()));
        }
        List<String> ids = new java.util.ArrayList<>(room.getPlayerIds());
        ids.add(username);
        room.setPlayerIds(ids);
        room.setUpdatedAt(LocalDateTime.now());
        return roomRepository.save(room);
    }

    public void leaveRoom(String roomId, String username) {
        Room room = getRoomById(roomId);
        List<String> ids = new java.util.ArrayList<>(room.getPlayerIds());
        ids.remove(username);
        room.setPlayerIds(ids);
        playerRepository.findByUsernameAndRoomId(username, roomId).ifPresent(playerRepository::delete);
        if (ids.isEmpty()) room.setStatus("CLOSED");
        room.setUpdatedAt(LocalDateTime.now());
        roomRepository.save(room);
    }

    public List<Player> getRoomPlayers(String roomId) {
        return playerRepository.findByRoomId(roomId);
    }

    private String generateUniqueCode() {
        for (int i = 0; i < 20; i++) {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int j = 0; j < CODE_LENGTH; j++) {
                sb.append(CODE_CHARS.charAt(RNG.nextInt(CODE_CHARS.length())));
            }
            String code = sb.toString();
            if (roomRepository.findByRoomCode(code).isEmpty()) return code;
        }
        throw new IllegalStateException("Could not generate a unique room code");
    }
}
