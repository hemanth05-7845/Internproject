package service

import (
	"testing"
)

func TestBuildEventFeed(t *testing.T) {
	store := GetEventStore()
	store.mu.Lock()
	delete(store.events, "room1")
	store.mu.Unlock()

	store.PushEvent("room1", "START", "desc")
	
	feed := BuildEventFeed("room1")
	if len(feed) != 1 {
		t.Errorf("Expected 1 event, got %d", len(feed))
	}

	feedEmpty := BuildEventFeed("room_empty")
	if len(feedEmpty) != 0 {
		t.Errorf("Expected 0 events, got %d", len(feedEmpty))
	}
}

func TestBuildTimerSnapshotFromManager(t *testing.T) {
	tm := NewTimerManager()
	
	snap := BuildTimerSnapshotFromManager("room1", tm)
	if snap.Phase != "LOBBY" {
		t.Errorf("Expected LOBBY for nil timer, got %s", snap.Phase)
	}

	tm.StartTimer("room1", "NIGHT", 30)
	snap = BuildTimerSnapshotFromManager("room1", tm)
	if snap.Phase != "NIGHT" {
		t.Errorf("Expected NIGHT, got %s", snap.Phase)
	}
}
