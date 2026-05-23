package server

import (
	"github.com/example/mafia-event-service/internal/controller"
	"github.com/example/mafia-event-service/internal/service"
	"github.com/gin-gonic/gin"
	"log"
	"os"
)

func Run() {
	r := gin.Default()
	es := service.GetEventStore()
	tm := service.NewTimerManager()
	tc := controller.NewTimerController(tm, es)
	tc.RegisterTimerRoutes(r)
	phaseController := controller.NewPhaseTransitionController(tm)
	phaseController.RegisterPhaseRoutes(r)
	port := os.Getenv("EVENT_SERVICE_PORT")
	log.Printf("Event Service is Starting on the port: %s", port)
	if err := r.Run(":" + port); err != nil {
		log.Fatalf("Event Service has failed to start: %v", err)
	}
}

