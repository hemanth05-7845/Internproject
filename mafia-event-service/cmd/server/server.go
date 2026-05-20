package server

import (
	"github.com/example/mafia-event-service/internal/controller"
	"github.com/example/mafia-event-service/internal/service"
	"github.com/gin-contrib/cors"
	"github.com/gin-gonic/gin"
	"log"
	"os"
)

func Run() {
	r := gin.Default()
	r.Use(cors.New(cors.Config{
		AllowOrigins: []string{"*"},
		AllowMethods: []string{"GET", "POST", "OPTIONS"},
		AllowHeaders: []string{"Content-Type", "Authorization"},
	}))
	es := service.GetEventStore()
	tc := controller.NewTimerController(service.NewTimerManager(), es)
	tc.RegisterTimerRoutes(r)
	phaseController := controller.NewPhaseTransitionController(service.NewTimerManager())
	phaseController.RegisterPhaseRoutes(r)
	port := startPort()
	log.Printf("Event Service is Starting on the port: %s", port)
	if err := r.Run(":" + port); err != nil {
		log.Fatalf("Event Service has failed to start: %v", err)
	}
}

func startPort() string {
	if port := os.Getenv("PORT"); port != "" {
		return port
	}
	if port := os.Getenv("EVENT_SERVICE_PORT"); port != "" {
		return port
	}
	return "8081"
}
