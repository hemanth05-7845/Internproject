package server

import (
	"log"
	"os"

	"github.com/gin-gonic/gin"

	"github.com/example/mafia-event-service/internal/controller"
	"github.com/example/mafia-event-service/internal/service"
	temporalworker "github.com/example/mafia-event-service/temporal/worker"
)

func Run() {
	temporalClient, err := temporalworker.Start()
	if err != nil {
		log.Fatalf("[Server] failed to connect to Temporal: %v", err)
	}

	r := gin.Default()

	es := service.GetEventStore()
	tm := service.NewTimerManager(temporalClient) // ← Temporal-backed now

	tc := controller.NewTimerController(tm, es)
	tc.RegisterTimerRoutes(r)

	phaseController := controller.NewPhaseTransitionController(tm)
	phaseController.RegisterPhaseRoutes(r)

	port := os.Getenv("EVENT_SERVICE_PORT")
	log.Printf("[Server] Event Service starting on port: %s", port)
	if err := r.Run(":" + port); err != nil {
		log.Fatalf("[Server] failed to start: %v", err)
	}
}
