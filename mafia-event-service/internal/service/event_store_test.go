package service

import (
	"fmt"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
)

func TestEventStore(t *testing.T) {
	tests := []struct {
		name   string
		setup  func(*EventStore)
		assert func(*testing.T, *EventStore)
	}{
		{
			name: "TestShouldAddEventtoEventStoreWithValidInput",
			setup: func(es *EventStore) {
				es.PushEvent("room1", "VOTE", "player1 voted")
			},
			assert: func(t *testing.T, es *EventStore) {
				feed := es.GetEvents("room1")
				assert.Len(t, feed, 1)
				assert.Equal(t, "VOTE", feed[0].Event)
				assert.Equal(t, "player1 voted", feed[0].Description)
				assert.Equal(t, "room1", feed[0].RoomID)
				assert.WithinDuration(t, time.Now().UTC(), feed[0].CreatedAt, time.Second)
			},
		},
		{
			name: "TestShouldReturnEmptyEventFeedWhenNoEventsExist",
			assert: func(t *testing.T, es *EventStore) {
				feed := es.GetEvents("room1")
				assert.NotNil(t, feed)
				assert.Len(t, feed, 0)
			},
		},
		{
			name: "TestShouldReturnCopyOfEventsandNotOriginalReference",
			setup: func(es *EventStore) {
				es.PushEvent("room1", "VOTE", "player1 voted")
			},
			assert: func(t *testing.T, es *EventStore) {
				feed := es.GetEvents("room1")
				feed[0].Event = "MUTATED"
				original := es.GetEvents("room1")
				assert.Equal(t, "VOTE", original[0].Event)
			},
		},
		{
			name: "TestShouldCapEventFeedAt50Items",
			setup: func(es *EventStore) {
				for i := 0; i < 55; i++ {
					es.PushEvent("room1", "VOTE", "event")
				}
			},
			assert: func(t *testing.T, es *EventStore) {
				feed := es.GetEvents("room1")
				assert.Len(t, feed, 50)
			},
		},
		{
			name: "TestShouldKeepNewest50Events",
			setup: func(es *EventStore) {
				for i := 0; i < 55; i++ {
					es.PushEvent("room1", "VOTE", fmt.Sprintf("event-%d", i))
				}
			},
			assert: func(t *testing.T, es *EventStore) {
				feed := es.GetEvents("room1")
				assert.Equal(t, "event-5", feed[0].Description)
				assert.Equal(t, "event-54", feed[len(feed)-1].Description)
				assert.Len(t, feed, 50)
			},
		},
		{
			name: "TestShouldIsolateEventsByRoomProvidedValideRoomID",
			setup: func(es *EventStore) {
				es.PushEvent("room1", "VOTE", "room1 event")
				es.PushEvent("room2", "CHAT", "room2 event")
			},
			assert: func(t *testing.T, es *EventStore) {
				assert.Len(t, es.GetEvents("room1"), 1)
				assert.Len(t, es.GetEvents("room2"), 1)
				assert.Equal(t, "VOTE", es.GetEvents("room1")[0].Event)
				assert.Equal(t, "CHAT", es.GetEvents("room2")[0].Event)
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			es := NewEventStore()
			if tt.setup != nil {
				tt.setup(es)
			}
			tt.assert(t, es)
		})
	}
}
