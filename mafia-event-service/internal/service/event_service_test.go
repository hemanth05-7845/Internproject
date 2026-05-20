package service

import (
	"github.com/stretchr/testify/assert"
	"testing"
	"time"
)

func TestBuildTimerSnapshotFromManager(t *testing.T) {
	tests := []struct {
		name              string
		setupTimer        bool
		phase             string
		minRemainingTime  int
		expectedPhase     string
		expectedRoomID    string
		expectedRemaining int
	}{
		{
			name:              "no timer",
			expectedRoomID:    "room1",
			expectedPhase:     "LOBBY",
			expectedRemaining: 0,
		},
		{
			name:             "with active timer",
			setupTimer:       true,
			phase:            "NIGHT",
			minRemainingTime: 1,
			expectedRoomID:   "room1",
			expectedPhase:    "NIGHT",
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			tm := NewTimerManager()
			if tt.setupTimer {
				tm.StartTimer("room1", tt.phase, 30)
			}
			snap := BuildTimerSnapshotFromManager("room1", tm)
			assert.Equal(t, tt.expectedRoomID, snap.RoomID)
			assert.Equal(t, tt.expectedPhase, snap.Phase)
			if tt.minRemainingTime > 0 {
				assert.GreaterOrEqual(t, snap.RemainingTime, tt.minRemainingTime)
			} else {
				assert.Equal(t, tt.expectedRemaining, snap.RemainingTime)
			}
			assert.WithinDuration(t, time.Now().UTC(), snap.UpdatedAt, time.Second)
		})
	}
}
func TestBuildEventFeed(t *testing.T) {
	tests := []struct {
		name            string
		seedEvents      bool
		expectedCount   int
		expectedType    string
		expectedMessage string
	}{
		{
			name:          "empty",
			expectedCount: 0,
		},
		{
			name:            "with events",
			seedEvents:      true,
			expectedCount:   1,
			expectedType:    "VOTE",
			expectedMessage: "player1 voted",
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			es := NewEventStore()
			if tt.seedEvents {
				es.PushEvent("room1", "VOTE", "player1 voted")
			}
			feed := BuildEventFeed("room1", es)
			assert.NotNil(t, feed)
			assert.Len(t, feed, tt.expectedCount)
			if tt.expectedCount > 0 {
				assert.Equal(t, tt.expectedType, feed[0].Event)
				assert.Equal(t, tt.expectedMessage, feed[0].Description)
			}
		})
	}
}
