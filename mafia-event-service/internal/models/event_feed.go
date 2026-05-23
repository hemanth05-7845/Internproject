package models

import "time"

type EventFeedItem struct {
	RoomID      string    `json:"roomId"`
	Event       string    `json:"event"`
	Description string    `json:"description"`
	CreatedAt          time.Time `json:"at"`
}
type PhaseTransitionEvent struct {
	RoomID    string    `json:"roomId"`
	NextPhase string    `json:"nextPhase"`
	Timestamp time.Time `json:"timestamp"`
}
