package controller

import (
	"net/http"

	"github.com/example/mafia-event-service/internal/service"
	"github.com/gin-gonic/gin"
)

// RegisterTimerRoutes wires the timer controller routes.
// timerManager is passed in so the handler can read live state.
func RegisterTimerRoutes(router *gin.Engine, timerManager *service.TimerManager) {
	router.GET("/api/health", func(c *gin.Context) {
		c.JSON(http.StatusOK, gin.H{"status": "ok", "service": "mafia-event-service"})
	})

	// Timer snapshot — reads live TimerManager state
	router.GET("/api/timer/:roomId", func(c *gin.Context) {
		snap := service.BuildTimerSnapshotFromManager(c.Param("roomId"), timerManager)
		c.JSON(http.StatusOK, snap)
	})

	// Event feed — reads in-memory EventStore
	router.GET("/api/events/:roomId", func(c *gin.Context) {
		c.JSON(http.StatusOK, service.BuildEventFeed(c.Param("roomId")))
	})

	// Push event — called by external services (engine, gateway) to log an event
	router.POST("/api/events/:roomId", func(c *gin.Context) {
		var req struct {
			EventType   string `json:"type" binding:"required"`
			Description string `json:"description"`
		}
		if err := c.ShouldBindJSON(&req); err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
			return
		}
		service.GetEventStore().PushEvent(c.Param("roomId"), req.EventType, req.Description)
		c.JSON(http.StatusOK, gin.H{"status": "event-pushed"})
	})
}
