package com.mafia.dto.request;

public record ActionCommandRequest(String playerId, String actionType, String targetPlayerId) {
}
