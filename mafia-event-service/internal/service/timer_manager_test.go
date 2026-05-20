package service

import (
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestTimerManager(t *testing.T) {
	tests := []struct {
		name    string
		setup   func(*TimerManager)
		assert  func(*testing.T, *TimerManager)
		cleanup func(*TimerManager)
	}{
		{
			name: "start timer initial state",
			setup: func(tm *TimerManager) {
				tm.StartTimer("room1", "VOTING", 5)
			},
			assert: func(t *testing.T, tm *TimerManager) {
				timer := tm.GetTimer("room1")
				require.NotNil(t, timer)
				assert.Equal(t, "VOTING", timer.CurrentPhase)
				assert.Equal(t, 5, timer.RemainingTime)
				assert.Equal(t, "room1", timer.RoomID)
			},
			cleanup: func(tm *TimerManager) { tm.StopTimer("room1") },
		},
		{
			name: "start timer replaces existing",
			setup: func(tm *TimerManager) {
				tm.StartTimer("room1", "DAY", 30)
				tm.StartTimer("room1", "NIGHT", 60)
			},
			assert: func(t *testing.T, tm *TimerManager) {
				timer := tm.GetTimer("room1")
				require.NotNil(t, timer)
				assert.Equal(t, "NIGHT", timer.CurrentPhase)
				assert.Equal(t, 60, timer.RemainingTime)
			},
			cleanup: func(tm *TimerManager) { tm.StopTimer("room1") },
		},
		{
			name: "start timer ticks",
			setup: func(tm *TimerManager) {
				tm.StartTimer("room1", "VOTING", 10)
				time.Sleep(1500 * time.Millisecond)
			},
			assert: func(t *testing.T, tm *TimerManager) {
				timer := tm.GetTimer("room1")
				require.NotNil(t, timer)
				assert.Less(t, timer.RemainingTime, 10, "remaining time should have decreased")
			},
			cleanup: func(tm *TimerManager) { tm.StopTimer("room1") },
		},
		{
			name: "start timer expires naturally",
			setup: func(tm *TimerManager) {
				tm.StartTimer("room1", "VOTING", 2)
				time.Sleep(2500 * time.Millisecond)
			},
			assert: func(t *testing.T, tm *TimerManager) {
				assert.Nil(t, tm.GetTimer("room1"), "expired timer should be removed from map")
			},
		},
		{
			name: "stop timer removes timer",
			setup: func(tm *TimerManager) {
				tm.StartTimer("room1", "VOTING", 30)
				tm.StopTimer("room1")
			},
			assert: func(t *testing.T, tm *TimerManager) {
				assert.Nil(t, tm.GetTimer("room1"))
			},
		},
		{
			name: "stop timer unknown room",
			assert: func(t *testing.T, tm *TimerManager) {
				assert.NotPanics(t, func() { tm.StopTimer("unknown-room") })
			},
		},
		{
			name: "get timer unknown room",
			assert: func(t *testing.T, tm *TimerManager) {
				assert.Nil(t, tm.GetTimer("unknown-room"))
			},
		},
		{
			name: "multiple rooms isolated",
			setup: func(tm *TimerManager) {
				tm.StartTimer("room1", "DAY", 30)
				tm.StartTimer("room2", "NIGHT", 60)
			},
			assert: func(t *testing.T, tm *TimerManager) {
				assert.Equal(t, "DAY", tm.GetTimer("room1").CurrentPhase)
				assert.Equal(t, "NIGHT", tm.GetTimer("room2").CurrentPhase)
				tm.StopTimer("room1")
				assert.Nil(t, tm.GetTimer("room1"))
				assert.NotNil(t, tm.GetTimer("room2"), "room2 should be unaffected")
			},
			cleanup: func(tm *TimerManager) { tm.StopTimer("room2") },
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			tm := NewTimerManager()
			if tt.setup != nil {
				tt.setup(tm)
			}
			if tt.assert != nil {
				tt.assert(t, tm)
			}
			if tt.cleanup != nil {
				tt.cleanup(tm)
			}
		})
	}
}
