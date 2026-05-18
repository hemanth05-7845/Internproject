package models

type EventFeedItem struct {
	RoomID      string `json:"roomId"`
	Event       string `json:"event"`
	Description string `json:"description"`
	At          string `json:"at"`
}

type PhaseTransitionEvent struct {
	RoomID    string `json:"roomId"`
	NextPhase string `json:"nextPhase"`
	Timestamp string `json:"timestamp"`
}
