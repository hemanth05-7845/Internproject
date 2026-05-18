package service

import (
	"time"

	"github.com/example/mafia-event-service/internal/models"
)

func BuildTimerSnapshot(roomID string) models.TimerSnapshot {
	return models.TimerSnapshot{
		RoomID:        roomID,
		Phase:         "LOBBY",
		RemainingTime: 0,
		UpdatedAt:     time.Now().UTC().String(),
	}
}
