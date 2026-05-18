package controller

import (
	"bytes"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/example/mafia-event-service/internal/service"
	"github.com/gin-gonic/gin"
)

func setupTimerRouter() *gin.Engine {
	gin.SetMode(gin.TestMode)
	tm := service.NewTimerManager()
	r := gin.Default()
	RegisterTimerRoutes(r, tm)
	return r
}

func TestHealthCheck(t *testing.T) {
	r := setupTimerRouter()

	req, _ := http.NewRequest("GET", "/api/health", nil)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Errorf("Expected 200 OK, got %d", w.Code)
	}
}

func TestTimerSnapshot(t *testing.T) {
	r := setupTimerRouter()

	req, _ := http.NewRequest("GET", "/api/timer/room1", nil)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Errorf("Expected 200 OK, got %d", w.Code)
	}
}

func TestGetEvents(t *testing.T) {
	r := setupTimerRouter()

	req, _ := http.NewRequest("GET", "/api/events/room1", nil)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Errorf("Expected 200 OK, got %d", w.Code)
	}
}

func TestPushEvent(t *testing.T) {
	r := setupTimerRouter()

	body := []byte(`{"type": "VOTE", "description": "Bob voted Alice"}`)
	req, _ := http.NewRequest("POST", "/api/events/room1", bytes.NewBuffer(body))
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Errorf("Expected 200 OK, got %d", w.Code)
	}

	// Bad Request
	reqBad, _ := http.NewRequest("POST", "/api/events/room1", bytes.NewBuffer([]byte(`{bad}`)))
	wBad := httptest.NewRecorder()
	r.ServeHTTP(wBad, reqBad)
	if wBad.Code != http.StatusBadRequest {
		t.Errorf("Expected 400 Bad Request, got %d", wBad.Code)
	}
}
