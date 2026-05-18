package service

import (
	"sync"
	"time"

	"github.com/example/mafia-event-service/internal/models"
)

// EventStore holds an in-memory ordered event log per room.
type EventStore struct {
	mu     sync.RWMutex
	events map[string][]models.EventFeedItem
}

var globalEventStore = &EventStore{
	events: make(map[string][]models.EventFeedItem),
}

func GetEventStore() *EventStore { return globalEventStore }

// PushEvent appends an event to the room's feed.
func (es *EventStore) PushEvent(roomID, eventType, description string) {
	es.mu.Lock()
	defer es.mu.Unlock()
	item := models.EventFeedItem{
		RoomID:      roomID,
		Event:       eventType,
		Description: description,
		At:          time.Now().UTC().Format(time.RFC3339),
	}
	es.events[roomID] = append(es.events[roomID], item)
	// Keep last 50 events per room
	if len(es.events[roomID]) > 50 {
		es.events[roomID] = es.events[roomID][len(es.events[roomID])-50:]
	}
}

// GetEvents returns the event feed for a room (newest last).
func (es *EventStore) GetEvents(roomID string) []models.EventFeedItem {
	es.mu.RLock()
	defer es.mu.RUnlock()
	items := es.events[roomID]
	if items == nil {
		return []models.EventFeedItem{}
	}
	// Return a copy
	out := make([]models.EventFeedItem, len(items))
	copy(out, items)
	return out
}
