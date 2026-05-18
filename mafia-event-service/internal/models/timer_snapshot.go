package models

type TimerSnapshot struct {
	RoomID        string `json:"roomId"`
	Phase         string `json:"phase"`
	RemainingTime int    `json:"remainingSeconds"`
	UpdatedAt     string `json:"updatedAt"`
}
