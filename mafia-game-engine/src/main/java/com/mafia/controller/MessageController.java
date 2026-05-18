package com.mafia.controller;

import com.mafia.entity.Player;
import com.mafia.entity.Message;
import com.mafia.repository.PlayerRepository;
import com.mafia.repository.MessageRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rooms")
public class MessageController {

    private final MessageRepository messageRepository;
    private final PlayerRepository playerRepository;

    public MessageController(MessageRepository messageRepository, PlayerRepository playerRepository) {
        this.messageRepository = messageRepository;
        this.playerRepository = playerRepository;
    }

    /** Post a chat message to a room. Called by the gateway on behalf of a player. */
    @PostMapping("/{roomId}/message")
    public Map<String, String> postMessage(
            @PathVariable String roomId,
            @RequestBody Map<String, String> body) {

        String sender  = body.getOrDefault("senderUsername", "Unknown");
        String content = body.getOrDefault("content", "").trim();

        if (content.isEmpty()) {
            return Map.of("status", "error", "message", "Empty message");
        }
        if (content.length() > 300) {
            content = content.substring(0, 300);
        }

        Player senderPlayer = playerRepository.findByUsernameAndRoomId(sender, roomId)
                .orElseThrow(() -> new IllegalArgumentException("Player not found: " + sender));
        if (!"ALIVE".equals(senderPlayer.getStatus())) {
            return Map.of("status", "error", "message", "Dead players cannot chat");
        }

        messageRepository.save(new Message(roomId, sender, sender, content));
        return Map.of("status", "sent", "roomId", roomId, "sender", sender);
    }

    /** Get the last 50 messages for a room. */
    @GetMapping("/{roomId}/messages")
    public List<Map<String, Object>> getMessages(@PathVariable String roomId) {
        return messageRepository.findByRoomIdOrderByCreatedAtDesc(roomId)
                .stream()
                .limit(50)
                .map(m -> Map.<String, Object>of(
                        "sender",    m.getSenderUsername(),
                        "message",   m.getContent(),
                        "timestamp", m.getCreatedAt().toString()))
                .collect(Collectors.toList());
    }
}
