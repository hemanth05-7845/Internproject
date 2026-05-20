package controller

import (
	"bytes"
	"encoding/json"
	"github.com/example/mafia-event-service/internal/service"
	"github.com/gin-gonic/gin"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"net/http"
	"net/http/httptest"
	"testing"
)

func setupPhaseRouter() (*gin.Engine, *service.TimerManager) {
	gin.SetMode(gin.TestMode)
	tm := service.NewTimerManager()
	ptc := NewPhaseTransitionController(tm)
	r := gin.New()
	ptc.RegisterPhaseRoutes(r)
	return r, tm
}
func postJSON(r *gin.Engine, url string, body []byte) *httptest.ResponseRecorder {
	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)
	return w
}
func getRequest(r *gin.Engine, url string) *httptest.ResponseRecorder {
	req, _ := http.NewRequest("GET", url, nil)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)
	return w
}
func TestStartPhaseTimer(t *testing.T) {
	tests := []struct {
		name             string
		body             []byte
		expectedStatus   int
		expectedPhase    string
		expectedTimer    bool
		expectedDuration int
	}{
		{
			name:             "success",
			body:             []byte(`{"phase":"VOTING","durationSeconds":30}`),
			expectedStatus:   http.StatusOK,
			expectedPhase:    "VOTING",
			expectedTimer:    true,
			expectedDuration: 30,
		},
		{
			name:           "missing phase",
			body:           []byte(`{"durationSeconds":30}`),
			expectedStatus: http.StatusBadRequest,
		},
		{
			name:           "missing duration",
			body:           []byte(`{"phase":"VOTING"}`),
			expectedStatus: http.StatusBadRequest,
		},
		{
			name:           "zero duration",
			body:           []byte(`{"phase":"VOTING","durationSeconds":0}`),
			expectedStatus: http.StatusBadRequest,
		},
		{
			name:           "malformed json",
			body:           []byte(`{bad json`),
			expectedStatus: http.StatusBadRequest,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			r, tm := setupPhaseRouter()
			w := postJSON(r, "/api/phase/room1/start", tt.body)
			assert.Equal(t, tt.expectedStatus, w.Code)
			if tt.expectedStatus == http.StatusOK {
				var res map[string]interface{}
				require.NoError(t, json.Unmarshal(w.Body.Bytes(), &res))
				assert.Equal(t, "room1", res["roomId"])
				assert.Equal(t, tt.expectedPhase, res["phase"])
				assert.Equal(t, float64(tt.expectedDuration), res["duration"])
				timer := tm.GetTimer("room1")
				if tt.expectedTimer {
					require.NotNil(t, timer)
					assert.Equal(t, tt.expectedPhase, timer.CurrentPhase)
				} else {
					assert.Nil(t, timer)
				}
			}
		})
	}
}
func TestTransitionPhase(t *testing.T) {
	tests := []struct {
		name           string
		setupTimer     bool
		body           []byte
		expectedStatus int
		expectedPhase  string
		stopTimer      bool
	}{
		{
			name:           "success",
			setupTimer:     true,
			body:           []byte(`{"nextPhase":"NIGHT"}`),
			expectedStatus: http.StatusOK,
			expectedPhase:  "NIGHT",
			stopTimer:      true,
		},
		{
			name:           "missing next phase",
			setupTimer:     true,
			body:           []byte(`{}`),
			expectedStatus: http.StatusBadRequest,
		},
		{
			name:           "malformed json",
			setupTimer:     true,
			body:           []byte(`{bad json`),
			expectedStatus: http.StatusBadRequest,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			r, tm := setupPhaseRouter()
			if tt.setupTimer {
				tm.StartTimer("room1", "DAY", 30)
			}
			w := postJSON(r, "/api/phase/room1/transition", tt.body)
			assert.Equal(t, tt.expectedStatus, w.Code)
			if tt.expectedStatus == http.StatusOK {
				var res map[string]interface{}
				require.NoError(t, json.Unmarshal(w.Body.Bytes(), &res))
				assert.Equal(t, "room1", res["roomId"])
				assert.Equal(t, tt.expectedPhase, res["nextPhase"])
				assert.NotEmpty(t, res["timestamp"])
			}
			if tt.stopTimer {
				assert.Nil(t, tm.GetTimer("room1"), "timer should be stopped after transition")
			}
		})
	}
}
func TestGetPhaseStatus(t *testing.T) {
	tests := []struct {
		name           string
		setupTimer     bool
		phase          string
		expectedStatus int
	}{
		{
			name:           "no active timer",
			expectedStatus: http.StatusNotFound,
		},
		{
			name:           "with active timer",
			setupTimer:     true,
			phase:          "NIGHT",
			expectedStatus: http.StatusOK,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			r, tm := setupPhaseRouter()
			if tt.setupTimer {
				tm.StartTimer("room1", tt.phase, 30)
			}
			w := getRequest(r, "/api/phase/room1/status")
			assert.Equal(t, tt.expectedStatus, w.Code)
			var res map[string]interface{}
			require.NoError(t, json.Unmarshal(w.Body.Bytes(), &res))
			if tt.expectedStatus == http.StatusOK {
				assert.Equal(t, "room1", res["roomId"])
				assert.Equal(t, tt.phase, res["phase"])
				assert.NotEmpty(t, res["updatedAt"])
			} else {
				assert.Contains(t, res["error"].(string), "no active timer")
			}
		})
	}
}
func TestCancelPhaseTimer(t *testing.T) {
	tests := []struct {
		name           string
		setupTimer     bool
		expectedStatus int
	}{
		{
			name:           "with active timer",
			setupTimer:     true,
			expectedStatus: http.StatusOK,
		},
		{
			name:           "no active timer",
			expectedStatus: http.StatusOK,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			r, tm := setupPhaseRouter()
			if tt.setupTimer {
				tm.StartTimer("room1", "NIGHT", 30)
			}
			w := postJSON(r, "/api/phase/room1/cancel", nil)
			assert.Equal(t, tt.expectedStatus, w.Code)
			assert.Nil(t, tm.GetTimer("room1"), "timer should be cancelled")
		})
	}
}
