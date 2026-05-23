package controller

import (
	"log"
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
		Phase       string `json:"phase" binding:"required"`
		DurationSec int    `json:"durationSeconds" binding:"required,min=1"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		log.Printf("StartPhaseTimer bad request room=%s: %v", roomID, err)
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}
	ptc.timerManager.StartTimer(roomID, req.Phase, req.DurationSec)
	log.Printf("StartPhaseTimer timer started room=%s phase=%s duration=%ds", roomID, req.Phase, req.DurationSec)
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
		NextPhase string `json:"nextPhase" binding:"required"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		log.Printf("TransitionPhase bad request room=%s: %v", roomID, err)
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}
	if ptc.timerManager.GetTimer(roomID) == nil {
    c.JSON(http.StatusBadRequest, gin.H{"error": "no active timer to transition"})
    return
	}
	ptc.timerManager.StopTimer(roomID)
	log.Printf("TransitionPhase transition room=%s nextPhase=%s", roomID, req.NextPhase)
	c.JSON(http.StatusOK, models.PhaseTransitionEvent{
		RoomID:    roomID,
		NextPhase: req.NextPhase,
		Timestamp: time.Now().UTC(),
	})
}

func (ptc *PhaseTransitionController) GetPhaseStatus(c *gin.Context) {
	roomID := c.Param("roomId")
	timer := ptc.timerManager.GetTimer(roomID)
	if timer == nil {
		log.Printf("GetPhaseStatus no active timer room=%s", roomID)
		c.JSON(http.StatusNotFound, gin.H{"error": "no active timer for room"})
		return
	}
	c.JSON(http.StatusOK, models.TimerSnapshot{
		RoomID:        roomID,
		Phase:         timer.CurrentPhase,
		RemainingTime: timer.RemainingTime,
		UpdatedAt:     time.Now().UTC(),
	})
}

func (ptc *PhaseTransitionController) CancelPhaseTimer(c *gin.Context) {
	roomID := c.Param("roomId")
	if ptc.timerManager.GetTimer(roomID) == nil {
    c.JSON(http.StatusBadRequest, gin.H{"error": "no active timer to transition"})
    return
	}
	ptc.timerManager.StopTimer(roomID)
	
	log.Printf("CancelPhaseTimer timer cancelled room=%s", roomID)
	c.JSON(http.StatusOK, gin.H{
		"roomId":  roomID,
		"message": "Phase timer cancelled",
	})
}
