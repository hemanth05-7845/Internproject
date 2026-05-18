package com.mafia.dto.response;

import java.util.List;
import java.util.Map;

/**
 * Full game snapshot returned by the engine to the gateway.
 * The gateway injects myRole and filters other players' roles before
 * sending to the frontend.
 */
public record AggregatedGameSnapshot(
        String phase,
        int dayNumber,
        int nightNumber,
        /** Each entry: {name, alive, role, status} */
        List<Map<String, Object>> players,
        List<String> alivePlayers,
        List<String> eliminatedPlayers,
        /** Revealed only at SUNRISE and onwards */
        String nightKillTarget,
        Boolean nightKillFailed,
        String policeGuessTarget,
        Boolean policeGuessCorrect,
        /** NONE | MAFIA | VILLAGERS */
        String winner,
        List<Map<String, Object>> chatMessages,
        List<Map<String, Object>> events,
        List<String> allowedActions,
        String roomCode,
        String hostUsername,
        String phaseEndsAt
) {}
