package com.mafia.controller;

import com.mafia.dto.request.CreateRoomRequest;
import com.mafia.dto.request.JoinRoomByCodeRequest;
import com.mafia.entity.Player;
import com.mafia.entity.Room;
import com.mafia.service.RoomService;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @PostMapping("/create")
    public ResponseEntity<?> createRoom(@RequestBody CreateRoomRequest req) {
        return ResponseEntity.ok(roomToMap(roomService.createRoom(req.roomName(), req.hostUsername())));
    }

    @PostMapping("/join-by-code")
    public ResponseEntity<?> joinByCode(@RequestBody JoinRoomByCodeRequest req) {
        return ResponseEntity.ok(roomToMap(roomService.joinRoomByCode(req.roomCode(), req.username())));
    }

    @GetMapping("/by-code/{code}")
    public ResponseEntity<?> getByCode(@PathVariable String code) {
        return ResponseEntity.ok(roomToMap(roomService.getRoomByCode(code)));
    }

    @GetMapping("/{roomId}/players")
    public ResponseEntity<?> getPlayers(@PathVariable String roomId) {
        return ResponseEntity.ok(playersToList(roomService.getRoomPlayers(roomId)));
    }

    @GetMapping("/by-code/{code}/players")
    public ResponseEntity<?> getPlayersByCode(@PathVariable String code) {
        Room room = roomService.getRoomByCode(code);
        return ResponseEntity.ok(playersToList(roomService.getRoomPlayers(room.getId())));
    }

    private Map<String, Object> roomToMap(Room room) {
        return Map.of(
                "roomId", room.getId(),
                "roomCode", room.getRoomCode(),
                "roomName", room.getName(),
                "hostUsername", room.getHostUsername(),
                "playerCount", room.getPlayerIds().size(),
                "minPlayers", room.getMinPlayers(),
                "status", room.getStatus());
    }

    private List<Map<String, Object>> playersToList(List<Player> players) {
        return players.stream()
                .map(p -> Map.<String, Object>of(
                        "name", p.getUsername(),
                        "alive", "ALIVE".equals(p.getStatus()),
                        "role", p.getRole() != null ? p.getRole() : ""))
                .collect(Collectors.toList());
    }
}