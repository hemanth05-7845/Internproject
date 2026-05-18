package com.mafia.dto.request;

public record VoteCommandRequest(String voterId, String targetPlayerId) {
}
