package service

import (
	"testing"
	"github.com/example/mafia-event-service/internal/models"
)

func TestEventStore(t *testing.T) {
	store := GetEventStore()

	// Clear existing state from global store if any
	store.mu.Lock()
	store.events = make(map[string][]models.EventFeedItem)
	store.mu.Unlock()

	roomID := "test-room"

	// Initially empty
	events := store.GetEvents(roomID)
	if len(events) != 0 {
		t.Errorf("Expected 0 events initially, got %d", len(events))
	}

	// Push some events
	store.PushEvent(roomID, "GAME_START", "Game started")
	store.PushEvent(roomID, "PLAYER_JOINED", "Alice joined")

	events = store.GetEvents(roomID)
	if len(events) != 2 {
		t.Errorf("Expected 2 events, got %d", len(events))
	}

	if events[0].Event != "GAME_START" {
		t.Errorf("Expected first event GAME_START, got %s", events[0].Event)
	}

	// Test max events boundary (50 limit)
	for i := 0; i < 60; i++ {
		store.PushEvent(roomID, "SPAM", "Spam event")
	}

	events = store.GetEvents(roomID)
	if len(events) != 50 {
		t.Errorf("Expected exactly 50 events after limit reached, got %d", len(events))
	}
}
