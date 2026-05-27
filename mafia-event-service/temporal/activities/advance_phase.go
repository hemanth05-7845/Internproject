package activities

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"os"

	"go.temporal.io/sdk/activity"
)

type AdvancePhaseInput struct {
	RoomID string
	Phase  string
}

func AdvancePhase(ctx context.Context, input AdvancePhaseInput) error {
	log := activity.GetLogger(ctx)

	gatewayURL := os.Getenv("GATEWAY_BASE_URL")
	if gatewayURL == "" {
		return fmt.Errorf("GATEWAY_BASE_URL not set; cannot auto-advance room=%s", input.RoomID)
	}

	url := fmt.Sprintf("%s/internal/advance-phase", gatewayURL)
	body, _ := json.Marshal(map[string]string{"room_id": input.RoomID})

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, url, bytes.NewReader(body))
	if err != nil {
		return fmt.Errorf("build request: %w", err)
	}
	req.Header.Set("Content-Type", "application/json")

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return fmt.Errorf("POST advance-phase room=%s: %w", input.RoomID, err)
	}
	defer resp.Body.Close()

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return fmt.Errorf("advance-phase room=%s returned HTTP %d", input.RoomID, resp.StatusCode)
	}

	log.Info("AdvancePhase activity succeeded", "roomID", input.RoomID, "phase", input.Phase)
	return nil
}
