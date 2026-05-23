package service

import (
	"sync"
	"time"

	"github.com/example/mafia-event-service/internal/models"
)

type EventStore struct {
	mu     sync.RWMutex
	events map[string][]models.EventFeedItem
}

var globalEventStore = &EventStore{
	events: make(map[string][]models.EventFeedItem),
}

func GetEventStore() *EventStore {
	return globalEventStore
}
func NewEventStore() *EventStore {
	return &EventStore{events: make(map[string][]models.EventFeedItem)}
} //Especially Testing purpose kaaga

func (es *EventStore) PushEvent(roomID, eventType, description string) {
	es.mu.Lock()
	defer es.mu.Unlock()
	item := models.EventFeedItem{
		RoomID:      roomID,
		Event:       eventType,
		Description: description,
		CreatedAt:          time.Now().UTC(),
	}
	es.events[roomID] = append(es.events[roomID], item)
	if len(es.events[roomID]) > 50 {
		es.events[roomID] = es.events[roomID][len(es.events[roomID])-50:]
	}
}

func (es *EventStore) GetEvents(roomID string) []models.EventFeedItem {
	es.mu.RLock()
	defer es.mu.RUnlock()
	items := es.events[roomID]
	if items == nil {
		return []models.EventFeedItem{}
	}
	out := make([]models.EventFeedItem, len(items)) //if not Copy, then people can change
	copy(out, items)
	return out
}
