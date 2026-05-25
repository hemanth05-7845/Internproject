package service

import (
	"bytes"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os"
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

func engineAdvancePhase(roomID string) {
	engineURL := os.Getenv("SPRING_ENGINE_BASE_URL")

	if engineURL == "" {
		log.Printf("[TimerManager] SPRING_ENGINE_BASE_URL not set; skipping auto-advance for room=%s", roomID)
		return
	}

	url := fmt.Sprintf("%s/advance-phase", engineURL)
	body, _ := json.Marshal(map[string]string{
	"room_id": roomID,
	})
	resp, err := http.Post(url, "application/json", bytes.NewReader(body))
	if err != nil {
		log.Printf("[TimerManager] auto-advance HTTP error room=%s: %v", roomID, err)
		return
	}

	defer resp.Body.Close()

	log.Printf("[TimerManager] auto-advance room=%s status=%d", roomID, resp.StatusCode)
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
				log.Printf(
					"[TimerManager] timer expired room=%s phase=%s",
					roomID,
					timer.CurrentPhase,
				)

				timer.Stop()

				delete(tm.timers, roomID)

				tm.mu.Unlock()

				// Backend is the ONLY source of truth now
				go engineAdvancePhase(roomID)

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