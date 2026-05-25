package models

import "time"

type TimerSnapshot struct {
	RoomID        string    `json:"roomId"`
	Phase         string    `json:"phase"`
	RemainingTime int       `json:"remainingTime"`	
	UpdatedAt     time.Time `json:"updatedAt"`
}
