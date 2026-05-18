package service

import (
	"time"

	"github.com/example/mafia-event-service/internal/models"
)

// BuildEventFeed returns the in-memory event log for a room.
func BuildEventFeed(roomID string) []models.EventFeedItem {
	return GetEventStore().GetEvents(roomID)
}

// BuildTimerSnapshot reads the live TimerManager state.
func BuildTimerSnapshotFromManager(roomID string, tm *TimerManager) models.TimerSnapshot {
	timer := tm.GetTimer(roomID)
	phase := "LOBBY"
	remaining := 0
	if timer != nil {
		phase = timer.CurrentPhase
		remaining = timer.RemainingTime
	}
	return models.TimerSnapshot{
		RoomID:        roomID,
		Phase:         phase,
		RemainingTime: remaining,
		UpdatedAt:     time.Now().UTC().Format(time.RFC3339),
	}
}
