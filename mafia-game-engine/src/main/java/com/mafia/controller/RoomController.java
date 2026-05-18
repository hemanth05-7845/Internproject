package com.mafia.controller;

import com.mafia.dto.request.CreateRoomRequest;
import com.mafia.dto.request.JoinRoomByCodeRequest;
import com.mafia.entity.Player;
import com.mafia.entity.Room;
import com.mafia.service.RoomService;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @PostMapping("/create")
    public Map<String, Object> createRoom(@RequestBody CreateRoomRequest req) {
        Room room = roomService.createRoom(req.roomName(), req.hostUsername());
        return roomToMap(room);
    }

    @PostMapping("/join-by-code")
    public Map<String, Object> joinByCode(@RequestBody JoinRoomByCodeRequest req) {
        Room room = roomService.joinRoomByCode(req.roomCode(), req.username());
        return roomToMap(room);
    }

    @GetMapping("/by-code/{code}")
    public Map<String, Object> getByCode(@PathVariable String code) {
        Room room = roomService.getRoomByCode(code);
        return roomToMap(room);
    }

    @GetMapping("/{roomId}/players")
    public List<Map<String, Object>> getPlayers(@PathVariable String roomId) {
        return roomService.getRoomPlayers(roomId).stream()
                .map(p -> Map.<String, Object>of(
                        "name", p.getUsername(),
                        "alive", "ALIVE".equals(p.getStatus()),
                        "role", p.getRole() != null ? p.getRole() : ""))
                .collect(Collectors.toList());
    }

    @GetMapping("/by-code/{code}/players")
    public List<Map<String, Object>> getPlayersByCode(@PathVariable String code) {
        Room room = roomService.getRoomByCode(code);
        return roomService.getRoomPlayers(room.getId()).stream()
                .map(p -> Map.<String, Object>of(
                        "name", p.getUsername(),
                        "alive", "ALIVE".equals(p.getStatus()),
                        "role", p.getRole() != null ? p.getRole() : ""))
                .collect(Collectors.toList());
    }

    // @PostMapping("/{roomId}/start")
    // public Map<String, String> startGame(@PathVariable String roomId) {
    //     try {
    //         roomService.getRoomById(roomId); // validate room exists
    //         return Map.of("roomId", roomId, "status", "started");
    //     } catch (Exception e) {
    //         return Map.of("status", "error", "message", e.getMessage());
    //     }
    // }

    private Map<String, Object> roomToMap(Room room) {
        return Map.of(
                "roomId", room.getId(),
                "roomCode", room.getRoomCode(),
                "roomName", room.getName(),
                "hostUsername", room.getHostUsername(),
                "playerCount", room.getPlayerIds().size(),
                "minPlayers", room.getMinPlayers(),
                "status", room.getStatus()
        );
    }
}
