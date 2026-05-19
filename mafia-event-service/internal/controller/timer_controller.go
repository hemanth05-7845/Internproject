package controller

import (
	"net/http"

	"github.com/example/mafia-event-service/internal/service"
	"github.com/gin-gonic/gin"
)
type TimerController struct {
    timerManager *service.TimerManager
    eventStore   *service.EventStore 
}

func NewTimerController(tm *service.TimerManager, es *service.EventStore) *TimerController {
    return &TimerController{timerManager: tm, eventStore: es}
}

func (tc *TimerController) RegisterTimerRoutes(r *gin.Engine) {
    r.GET("/api/health", tc.Health)
    r.GET("/api/timer/:roomId", tc.GetTimerSnapshot)
    r.GET("/api/events/:roomId", tc.GetEventFeed)
    r.POST("/api/events/:roomId", tc.PushEvent)
}

func (tc *TimerController) Health(c *gin.Context) {
    c.JSON(http.StatusOK, gin.H{"status": "ok", "service": "mafia-event-service"})
}

func (tc *TimerController) GetTimerSnapshot(c *gin.Context) {
    snap := service.BuildTimerSnapshotFromManager(c.Param("roomId"), tc.timerManager)
    c.JSON(http.StatusOK, snap)
}

func (tc *TimerController) GetEventFeed(c *gin.Context) {
    c.JSON(http.StatusOK, service.BuildEventFeed(c.Param("roomId")))
}

func (tc *TimerController) PushEvent(c *gin.Context) {
    var req struct {
        EventType   string `json:"type"        binding:"required"`
        Description string `json:"description"`
    }
    if err := c.ShouldBindJSON(&req); err != nil {
        c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
        return
    }
    tc.eventStore.PushEvent(c.Param("roomId"), req.EventType, req.Description)
    c.JSON(http.StatusOK, gin.H{"status": "event-pushed"})
}