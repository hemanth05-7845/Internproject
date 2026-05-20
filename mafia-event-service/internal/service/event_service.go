package service

import (
	"github.com/example/mafia-event-service/internal/models"
	"time"
)

func BuildEventFeed(roomID string, es *EventStore) []models.EventFeedItem {
	return es.GetEvents(roomID)
}
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
		UpdatedAt:     time.Now().UTC(),
	}
}
