package controller

import (
	"bytes"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/example/mafia-event-service/internal/service"
	"github.com/gin-gonic/gin"
	"github.com/stretchr/testify/assert"
)

func setupTimerRouter() *gin.Engine {
	gin.SetMode(gin.TestMode)
	tm := service.NewTimerManager()
	es := service.GetEventStore()
	r := gin.New()
	controller := NewTimerController(tm, es)
	controller.RegisterTimerRoutes(r)
	return r
}

func TestTimerControllerRoutes(t *testing.T) {
	tests := []struct {
		name           string
		method         string
		url            string
		body           []byte
		contentType    string
		expectedStatus int
	}{
		{
			name:           "health check",
			method:         http.MethodGet,
			url:            "/api/health",
			expectedStatus: http.StatusOK,
		},
		{
			name:           "timer snapshot",
			method:         http.MethodGet,
			url:            "/api/timer/room1",
			expectedStatus: http.StatusOK,
		},
		{
			name:           "get events",
			method:         http.MethodGet,
			url:            "/api/events/room1",
			expectedStatus: http.StatusOK,
		},
		{
			name:           "push event success",
			method:         http.MethodPost,
			url:            "/api/events/room1",
			body:           []byte(`{"type":"VOTE","description":"Bob voted Alice"}`),
			contentType:    "application/json",
			expectedStatus: http.StatusOK,
		},
		{
			name:           "push event bad json",
			method:         http.MethodPost,
			url:            "/api/events/room1",
			body:           []byte(`{bad}`),
			contentType:    "application/json",
			expectedStatus: http.StatusBadRequest,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			r := setupTimerRouter()
			var req *http.Request
			if tt.body != nil {
				req, _ = http.NewRequest(tt.method, tt.url, bytes.NewBuffer(tt.body))
			} else {
				req, _ = http.NewRequest(tt.method, tt.url, nil)
			}
			if tt.contentType != "" {
				req.Header.Set("Content-Type", tt.contentType)
			}
			w := httptest.NewRecorder()
			r.ServeHTTP(w, req)
			assert.Equal(t, tt.expectedStatus, w.Code)
		})
	}
}
