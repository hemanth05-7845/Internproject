package server

import (
	"os"

	"github.com/example/mafia-event-service/internal/controller"
	"github.com/example/mafia-event-service/internal/service"
	"github.com/gin-gonic/gin"
)

func Run() {
	r := gin.Default()

	// Add CORS headers so the gateway can reach this service
	r.Use(func(c *gin.Context) {
		c.Header("Access-Control-Allow-Origin", "*")
		c.Header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
		c.Header("Access-Control-Allow-Headers", "Content-Type, Authorization")
		if c.Request.Method == "OPTIONS" {
			c.AbortWithStatus(204)
			return
		}
		c.Next()
	})

	timerManager := service.NewTimerManager()
	controller.RegisterTimerRoutes(r, timerManager)
	phaseController := controller.NewPhaseTransitionController(timerManager)
	phaseController.RegisterPhaseRoutes(r)

	// Railway sets PORT; fall back to 8081 for local docker-compose
	port := os.Getenv("PORT")
	if port == "" {
		port = os.Getenv("EVENT_SERVICE_PORT")
	}
	if port == "" {
		port = "8081"
	}

	_ = r.Run(":" + port)
}
