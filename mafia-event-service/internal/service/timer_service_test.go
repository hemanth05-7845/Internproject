package service

import (
	"testing"
)

func TestBuildTimerSnapshot(t *testing.T) {
	snap := BuildTimerSnapshot("room-dummy")
	
	if snap.RoomID != "room-dummy" {
		t.Errorf("Expected room-dummy, got %s", snap.RoomID)
	}
	if snap.Phase != "LOBBY" {
		t.Errorf("Expected phase LOBBY, got %s", snap.Phase)
	}
	if snap.RemainingTime != 0 {
		t.Errorf("Expected 0 remaining time, got %d", snap.RemainingTime)
	}
}
