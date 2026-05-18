package service

import (
	"testing"
	"time"
)

func TestTimerManager(t *testing.T) {
	tm := NewTimerManager()
	roomID := "test-room"

	// Test Start
	tm.StartTimer(roomID, "VOTING", 5)
	
	timer := tm.GetTimer(roomID)
	if timer == nil {
		t.Fatal("Timer should not be nil")
	}
	if timer.CurrentPhase != "VOTING" {
		t.Errorf("Expected phase VOTING, got %s", timer.CurrentPhase)
	}

	// Test Tick (Wait 1.5 seconds)
	time.Sleep(1500 * time.Millisecond)
	
	timer = tm.GetTimer(roomID)
	if timer.RemainingTime >= 5 {
		t.Errorf("Expected remaining time to decrease, got %d", timer.RemainingTime)
	}

	// Test Stop
	tm.StopTimer(roomID)
	if tm.GetTimer(roomID) != nil {
		t.Errorf("Expected timer to be removed after StopTimer")
	}

	// Test Stop on nil timer
	tm.StopTimer("unknown-room") // Should not panic
}
