package service

import (
	"sync"
	"time"
)

type TimerManager struct {
	timers map[string]*RoomTimer
	mu     sync.RWMutex
}
type RoomTimer struct {
	RoomID           string
	CurrentPhase     string
	PhaseStartTime   time.Time
	PhaseDurationSec int
	RemainingTime    int
	ticker           *time.Ticker
}

func NewTimerManager() *TimerManager {
	return &TimerManager{
		timers: make(map[string]*RoomTimer),
	}
}
func (tm *TimerManager) StartTimer(roomID string, phase string, durationSec int) {
	tm.mu.Lock()
	defer tm.mu.Unlock()

	if existing, ok := tm.timers[roomID]; ok {
		existing.Stop()
	}
	timer := &RoomTimer{
		RoomID:           roomID,
		CurrentPhase:     phase,
		PhaseStartTime:   time.Now(),
		PhaseDurationSec: durationSec,
		RemainingTime:    durationSec,
	}
	timer.ticker = time.NewTicker(1 * time.Second)
	tm.timers[roomID] = timer
	go func() {
		for range timer.ticker.C {
			tm.mu.Lock()
			timer.RemainingTime--
			if timer.RemainingTime <= 0 {
				timer.Stop()
				delete(tm.timers, roomID)
				tm.mu.Unlock()
				break
			}
			tm.mu.Unlock()
		}
	}()
}
func (tm *TimerManager) GetTimer(roomID string) *RoomTimer {
	tm.mu.RLock()
	defer tm.mu.RUnlock()
	return tm.timers[roomID]
}
func (tm *TimerManager) StopTimer(roomID string) {
	tm.mu.Lock()
	defer tm.mu.Unlock()

	if timer, ok := tm.timers[roomID]; ok {
		timer.Stop()
		delete(tm.timers, roomID)
	}
}
func (rt *RoomTimer) Stop() {
	if rt.ticker != nil {
		rt.ticker.Stop()
	}
}
