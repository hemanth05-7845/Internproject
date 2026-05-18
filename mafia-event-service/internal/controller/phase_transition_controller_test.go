package controller

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/example/mafia-event-service/internal/service"
	"github.com/gin-gonic/gin"
)

func setupPhaseRouter() (*gin.Engine, *service.TimerManager) {
	gin.SetMode(gin.TestMode)
	tm := service.NewTimerManager()
	ptc := NewPhaseTransitionController(tm)
	r := gin.Default()
	ptc.RegisterPhaseRoutes(r)
	return r, tm
}

func TestStartPhaseTimer(t *testing.T) {
	r, tm := setupPhaseRouter()

	body := []byte(`{"phase": "VOTING", "durationSeconds": 30}`)
	req, _ := http.NewRequest("POST", "/api/phase/room1/start", bytes.NewBuffer(body))
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Errorf("Expected 200 OK, got %d", w.Code)
	}

	timer := tm.GetTimer("room1")
	if timer == nil || timer.CurrentPhase != "VOTING" {
		t.Errorf("Timer not started correctly")
	}

	// Bad Request
	reqBad, _ := http.NewRequest("POST", "/api/phase/room1/start", bytes.NewBuffer([]byte(`{bad json`)))
	wBad := httptest.NewRecorder()
	r.ServeHTTP(wBad, reqBad)
	if wBad.Code != http.StatusBadRequest {
		t.Errorf("Expected 400 Bad Request, got %d", wBad.Code)
	}
}

func TestTransitionPhase(t *testing.T) {
	r, tm := setupPhaseRouter()
	tm.StartTimer("room1", "DAY", 30)

	body := []byte(`{"nextPhase": "NIGHT"}`)
	req, _ := http.NewRequest("POST", "/api/phase/room1/transition", bytes.NewBuffer(body))
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Errorf("Expected 200 OK, got %d", w.Code)
	}

	if tm.GetTimer("room1") != nil {
		t.Errorf("Timer should be stopped after transition")
	}

	// Bad Request
	reqBad, _ := http.NewRequest("POST", "/api/phase/room1/transition", bytes.NewBuffer([]byte(`{bad json`)))
	wBad := httptest.NewRecorder()
	r.ServeHTTP(wBad, reqBad)
	if wBad.Code != http.StatusBadRequest {
		t.Errorf("Expected 400 Bad Request, got %d", wBad.Code)
	}
}

func TestGetPhaseStatus(t *testing.T) {
	r, tm := setupPhaseRouter()

	// No timer
	req, _ := http.NewRequest("GET", "/api/phase/room1/status", nil)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)
	if w.Code != http.StatusNotFound {
		t.Errorf("Expected 404 Not Found, got %d", w.Code)
	}

	// With timer
	tm.StartTimer("room1", "NIGHT", 30)
	req2, _ := http.NewRequest("GET", "/api/phase/room1/status", nil)
	w2 := httptest.NewRecorder()
	r.ServeHTTP(w2, req2)
	if w2.Code != http.StatusOK {
		t.Errorf("Expected 200 OK, got %d", w2.Code)
	}
	
	var res map[string]interface{}
	json.Unmarshal(w2.Body.Bytes(), &res)
	if res["phase"] != "NIGHT" {
		t.Errorf("Expected NIGHT, got %v", res["phase"])
	}
}

func TestCancelPhaseTimer(t *testing.T) {
	r, tm := setupPhaseRouter()
	tm.StartTimer("room1", "NIGHT", 30)

	req, _ := http.NewRequest("POST", "/api/phase/room1/cancel", nil)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Errorf("Expected 200 OK, got %d", w.Code)
	}

	if tm.GetTimer("room1") != nil {
		t.Errorf("Timer should be cancelled")
	}
}
