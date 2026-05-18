package controller

import (
	"net/http"
	"time"

	"github.com/example/mafia-event-service/internal/models"
	"github.com/example/mafia-event-service/internal/service"
	"github.com/gin-gonic/gin"
)

type PhaseTransitionController struct {
	timerManager *service.TimerManager
}

func NewPhaseTransitionController(timerManager *service.TimerManager) *PhaseTransitionController {
	return &PhaseTransitionController{
		timerManager: timerManager,
	}
}

func (ptc *PhaseTransitionController) RegisterPhaseRoutes(r *gin.Engine) {
	r.POST("/api/phase/:roomId/start", ptc.StartPhaseTimer)
	r.POST("/api/phase/:roomId/transition", ptc.TransitionPhase)
	r.GET("/api/phase/:roomId/status", ptc.GetPhaseStatus)
	r.POST("/api/phase/:roomId/cancel", ptc.CancelPhaseTimer)
}

func (ptc *PhaseTransitionController) StartPhaseTimer(c *gin.Context) {
	roomID := c.Param("roomId")

	var req struct {
		Phase       string `json:"phase"`
		DurationSec int    `json:"durationSeconds"`
	}

	if err := c.BindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	ptc.timerManager.StartTimer(roomID, req.Phase, req.DurationSec)

	c.JSON(http.StatusOK, gin.H{
		"roomId":   roomID,
		"phase":    req.Phase,
		"duration": req.DurationSec,
		"message":  "Phase timer started",
	})
}

func (ptc *PhaseTransitionController) TransitionPhase(c *gin.Context) {
	roomID := c.Param("roomId")

	var req struct {
		NextPhase string `json:"nextPhase"`
	}

	if err := c.BindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	// Stop current timer
	ptc.timerManager.StopTimer(roomID)

	// Return phase transition event
	c.JSON(http.StatusOK, models.PhaseTransitionEvent{
		RoomID:    roomID,
		NextPhase: req.NextPhase,
		Timestamp: time.Now().UTC().String(),
	})
}

func (ptc *PhaseTransitionController) GetPhaseStatus(c *gin.Context) {
	roomID := c.Param("roomId")

	timer := ptc.timerManager.GetTimer(roomID)
	if timer == nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "No active timer for room"})
		return
	}

	c.JSON(http.StatusOK, models.TimerSnapshot{
		RoomID:        roomID,
		Phase:         timer.CurrentPhase,
		RemainingTime: timer.RemainingTime,
		UpdatedAt:     time.Now().UTC().String(),
	})
}

func (ptc *PhaseTransitionController) CancelPhaseTimer(c *gin.Context) {
	roomID := c.Param("roomId")
	ptc.timerManager.StopTimer(roomID)

	c.JSON(http.StatusOK, gin.H{
		"roomId":  roomID,
		"message": "Phase timer cancelled",
	})
}
