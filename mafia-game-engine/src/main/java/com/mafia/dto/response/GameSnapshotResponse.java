package com.mafia.dto.response;

import java.util.List;

/**
 * Legacy thin snapshot — kept for backward compatibility.
 * New callers should use {@link AggregatedGameSnapshot}.
 */
public record GameSnapshotResponse(
        String phase,
        String phaseEndsAt,
        List<String> players,
        List<String> chatMessages,
        List<String> events,
        List<String> allowedActions
) {}
