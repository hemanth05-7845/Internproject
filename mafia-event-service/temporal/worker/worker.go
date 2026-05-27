package worker

import (
	"log"
	"os"

	"go.temporal.io/sdk/client"
	"go.temporal.io/sdk/worker"

	"github.com/example/mafia-event-service/temporal/activities"
	"github.com/example/mafia-event-service/temporal/workflows"
)

const TaskQueue = "mafia-phase-timers"

func Start() (client.Client, error) {
	temporalHost := os.Getenv("TEMPORAL_HOST")
	if temporalHost == "" {
		temporalHost = "temporal:7233"
	}

	c, err := client.Dial(client.Options{HostPort: temporalHost})
	if err != nil {
		return nil, err
	}

	w := worker.New(c, TaskQueue, worker.Options{})
	w.RegisterWorkflow(workflows.PhaseTimerWorkflow)
	w.RegisterActivity(activities.AdvancePhase)

	go func() {
		if err := w.Run(worker.InterruptCh()); err != nil {
			log.Fatalf("[TemporalWorker] worker stopped: %v", err)
		}
	}()

	log.Printf("[TemporalWorker] started on task_queue=%s host=%s", TaskQueue, temporalHost)
	return c, nil
}
