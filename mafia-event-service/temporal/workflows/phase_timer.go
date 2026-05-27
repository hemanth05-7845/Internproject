package workflows

import (
	"time"
	"go.temporal.io/sdk/temporal"
	"go.temporal.io/sdk/workflow"
	"github.com/example/mafia-event-service/temporal/activities"
)

type PhaseTimerInput struct {
	RoomID      string
	Phase       string
	DurationSec int
}


var retryPolicy = &temporal.RetryPolicy{
	InitialInterval:    2 * time.Second,
	BackoffCoefficient: 2.0,
	MaximumInterval:    30 * time.Second,
	MaximumAttempts:    3,
}

func PhaseTimerWorkflow(ctx workflow.Context, input PhaseTimerInput) error {
	log := workflow.GetLogger(ctx)
	log.Info("PhaseTimerWorkflow started","roomID", input.RoomID,"phase", input.Phase,"duration", input.DurationSec,)

	if err := workflow.Sleep(ctx, time.Duration(input.DurationSec)*time.Second); err != nil {
		log.Info("PhaseTimerWorkflow cancelled (manual advance)",
			"roomID", input.RoomID, "phase", input.Phase)
		return nil
	}

	log.Info("PhaseTimerWorkflow timer expired, advancing phase","roomID", input.RoomID, "phase", input.Phase)

	actCtx := workflow.WithActivityOptions(ctx, workflow.ActivityOptions{
		StartToCloseTimeout: 30 * time.Second,
		RetryPolicy:         retryPolicy,
	})

	return workflow.ExecuteActivity(actCtx, activities.AdvancePhase, activities.AdvancePhaseInput{
		RoomID: input.RoomID,
		Phase:  input.Phase,
	}).Get(actCtx, nil)
}
