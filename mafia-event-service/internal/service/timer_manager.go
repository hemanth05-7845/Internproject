package service

import (
	"context"
	"fmt"
	"log"
	"sync"
	"time"

	"go.temporal.io/sdk/client"

	temporalworker "github.com/example/mafia-event-service/temporal/worker"
	"github.com/example/mafia-event-service/temporal/workflows"
)


type TimerManager struct {
	temporalClient client.Client
	snapshots      map[string]*RoomTimer
	mu             sync.RWMutex
}

type RoomTimer struct {
	RoomID           string
	CurrentPhase     string
	PhaseStartTime   time.Time
	PhaseDurationSec int
	RemainingTime    int
}

func NewTimerManager(temporalClient client.Client) *TimerManager {
	return &TimerManager{
		temporalClient: temporalClient,
		snapshots:      make(map[string]*RoomTimer),
	}
}

// workflowID returns the deterministic Temporal workflow ID for a room.
func workflowID(roomID string) string {
	return fmt.Sprintf("phase-timer-%s", roomID)
}

func (tm *TimerManager) StartTimer(roomID string, phase string, durationSec int) {
	ctx := context.Background()
	wfID := workflowID(roomID)

	_ = tm.temporalClient.TerminateWorkflow(ctx, wfID, "", "new phase starting")

	_, err := tm.temporalClient.ExecuteWorkflow(
		ctx,
		client.StartWorkflowOptions{
			ID:        wfID,
			TaskQueue: temporalworker.TaskQueue,
		},
		workflows.PhaseTimerWorkflow,
		workflows.PhaseTimerInput{
			RoomID:      roomID,
			Phase:       phase,
			DurationSec: durationSec,
		},
	)
	if err != nil {
		log.Printf("[TimerManager] failed to start workflow room=%s phase=%s: %v", roomID, phase, err)
		return
	}

	tm.mu.Lock()
	tm.snapshots[roomID] = &RoomTimer{
		RoomID:           roomID,
		CurrentPhase:     phase,
		PhaseStartTime:   time.Now(),
		PhaseDurationSec: durationSec,
		RemainingTime:    durationSec,
	}
	tm.mu.Unlock()

	go tm.tickSnapshot(roomID, durationSec)

	log.Printf("[TimerManager] workflow started room=%s phase=%s duration=%ds wfID=%s",
		roomID, phase, durationSec, wfID)
}


func (tm *TimerManager) tickSnapshot(roomID string, durationSec int) {
	ticker := time.NewTicker(1 * time.Second)
	defer ticker.Stop()

	for range ticker.C {
		tm.mu.Lock()
		snap, ok := tm.snapshots[roomID]
		if !ok {
			tm.mu.Unlock()
			return 
		}
		snap.RemainingTime--
		if snap.RemainingTime <= 0 {
			delete(tm.snapshots, roomID)
			tm.mu.Unlock()
			return
		}
		tm.mu.Unlock()
	}
}

func (tm *TimerManager) GetTimer(roomID string) *RoomTimer {
	tm.mu.RLock()
	defer tm.mu.RUnlock()
	return tm.snapshots[roomID]
}

func (tm *TimerManager) StopTimer(roomID string) {
	ctx := context.Background()
	if err := tm.temporalClient.CancelWorkflow(ctx, workflowID(roomID), ""); err != nil {
		log.Printf("[TimerManager] cancel workflow room=%s: %v (may already be done)", roomID, err)
	}

	tm.mu.Lock()
	delete(tm.snapshots, roomID)
	tm.mu.Unlock()

	log.Printf("[TimerManager] timer stopped room=%s", roomID)
}
