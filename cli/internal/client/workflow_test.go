// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Infenia Private Limited

package client

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestStartWorkflow(t *testing.T) {
	tests := []struct {
		name              string
		sessionID         string
		workflowID        string
		responseBody      string
		expectError       bool
		expectExecutionID string
	}{
		{
			name:       "valid start",
			sessionID:  "session-1",
			workflowID: "workflow-1",
			responseBody: `{"data":{"executionId":"exec-123"}}`,
			expectError: false,
			expectExecutionID: "exec-123",
		},
		{
			name:       "missing sessionID",
			sessionID:  "",
			workflowID: "workflow-1",
			expectError: true,
		},
		{
			name:       "missing workflowID",
			sessionID:  "session-1",
			workflowID: "",
			expectError: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				if r.URL.Path != "/api/workflow/start" {
					t.Errorf("expected path /api/workflow/start, got %s", r.URL.Path)
				}
				if r.Method != "POST" {
					t.Errorf("expected POST, got %s", r.Method)
				}
				w.Header().Set("Content-Type", "application/json")
				w.WriteHeader(http.StatusOK)
				w.Write([]byte(tt.responseBody))
			}))
			defer server.Close()

			c := NewClient(server.URL)
			resp, err := c.StartWorkflow(tt.sessionID, tt.workflowID)

			if tt.expectError && err == nil {
				t.Fatal("expected error, got nil")
			}
			if !tt.expectError && err != nil {
				t.Fatalf("unexpected error: %v", err)
			}
			if !tt.expectError && resp.ExecutionID != tt.expectExecutionID {
				t.Errorf("expected executionID %s, got %s", tt.expectExecutionID, resp.ExecutionID)
			}
		})
	}
}

func TestStopWorkflow(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "POST" {
			t.Errorf("expected POST, got %s", r.Method)
		}
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		w.Write([]byte(`{"data":{"executionIds":["exec-1","exec-2"]}}`))
	}))
	defer server.Close()

	c := NewClient(server.URL)
	resp, err := c.StopWorkflow("session-1", "workflow-1")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if len(resp.ExecutionIDs) != 2 {
		t.Errorf("expected 2 execution IDs, got %d", len(resp.ExecutionIDs))
	}
}

func TestStopExecution(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "POST" {
			t.Errorf("expected POST, got %s", r.Method)
		}
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		w.Write([]byte(`{"data":{"executionId":"exec-123"}}`))
	}))
	defer server.Close()

	c := NewClient(server.URL)
	resp, err := c.StopExecution("exec-123")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if resp.ExecutionID != "exec-123" {
		t.Errorf("expected execution ID exec-123, got %s", resp.ExecutionID)
	}
}

func TestRestartExecution(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "POST" {
			t.Errorf("expected POST, got %s", r.Method)
		}
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		w.Write([]byte(`{"data":{"executionId":"exec-456"}}`))
	}))
	defer server.Close()

	c := NewClient(server.URL)
	resp, err := c.RestartExecution("exec-123")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if resp.ExecutionID != "exec-456" {
		t.Errorf("expected execution ID exec-456, got %s", resp.ExecutionID)
	}
}

func TestRestartFromNode(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "POST" {
			t.Errorf("expected POST, got %s", r.Method)
		}
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		w.Write([]byte(`{"data":{"executionId":"exec-456"}}`))
	}))
	defer server.Close()

	c := NewClient(server.URL)
	resp, err := c.RestartFromNode("exec-123", "node-1")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if resp.ExecutionID != "exec-456" {
		t.Errorf("expected execution ID exec-456, got %s", resp.ExecutionID)
	}
}

func TestPauseWorkflow(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "POST" {
			t.Errorf("expected POST, got %s", r.Method)
		}
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		w.Write([]byte(`{"data":{"executionId":"exec-123"}}`))
	}))
	defer server.Close()

	c := NewClient(server.URL)
	resp, err := c.PauseWorkflow("session-1", "exec-123")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if resp.ExecutionID != "exec-123" {
		t.Errorf("expected execution ID exec-123, got %s", resp.ExecutionID)
	}
}

func TestResumeWorkflow(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "POST" {
			t.Errorf("expected POST, got %s", r.Method)
		}
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		w.Write([]byte(`{"data":{"executionId":"exec-123"}}`))
	}))
	defer server.Close()

	c := NewClient(server.URL)
	resp, err := c.ResumeWorkflow("session-1", "exec-123")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if resp.ExecutionID != "exec-123" {
		t.Errorf("expected execution ID exec-123, got %s", resp.ExecutionID)
	}
}

func TestGetWorkflowStatus(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "GET" {
			t.Errorf("expected GET, got %s", r.Method)
		}
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		w.Write([]byte(`{"data":{"executionId":"exec-123","sessionId":"session-1","workflowId":"wf-1","status":"RUNNING","tasks":[],"startTime":"2026-07-17T10:00:00Z","endTime":"0001-01-01T00:00:00Z"}}`))
	}))
	defer server.Close()

	c := NewClient(server.URL)
	resp, err := c.GetWorkflowStatus("session-1", "exec-123")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if resp.ExecutionID != "exec-123" {
		t.Errorf("expected execution ID exec-123, got %s", resp.ExecutionID)
	}
	if resp.Status != "RUNNING" {
		t.Errorf("expected status RUNNING, got %s", resp.Status)
	}
}

func TestGetWorkflowHistory(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "GET" {
			t.Errorf("expected GET, got %s", r.Method)
		}
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		w.Write([]byte(`{"data":[{"executionId":"exec-1","workflowId":"wf-1","status":"COMPLETED","startTime":"2026-07-17T10:00:00Z","endTime":"2026-07-17T10:05:00Z"}]}`))
	}))
	defer server.Close()

	c := NewClient(server.URL)
	resp, err := c.GetWorkflowHistory("session-1")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if len(resp) != 1 {
		t.Errorf("expected 1 summary, got %d", len(resp))
	}
	if resp[0].ExecutionID != "exec-1" {
		t.Errorf("expected execution ID exec-1, got %s", resp[0].ExecutionID)
	}
}

func TestPauseNode(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "POST" {
			t.Errorf("expected POST, got %s", r.Method)
		}
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		w.Write([]byte(`{"data":{"executionId":"exec-123"}}`))
	}))
	defer server.Close()

	c := NewClient(server.URL)
	resp, err := c.PauseNode("session-1", "exec-123", "node-1")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if resp.ExecutionID != "exec-123" {
		t.Errorf("expected execution ID exec-123, got %s", resp.ExecutionID)
	}
}

func TestResumeNode(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "POST" {
			t.Errorf("expected POST, got %s", r.Method)
		}
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		w.Write([]byte(`{"data":{"executionId":"exec-123"}}`))
	}))
	defer server.Close()

	c := NewClient(server.URL)
	resp, err := c.ResumeNode("session-1", "exec-123", "node-1")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if resp.ExecutionID != "exec-123" {
		t.Errorf("expected execution ID exec-123, got %s", resp.ExecutionID)
	}
}

func TestEnableStepMode(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "POST" {
			t.Errorf("expected POST, got %s", r.Method)
		}
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		w.Write([]byte(`{"data":{"executionId":"exec-123"}}`))
	}))
	defer server.Close()

	c := NewClient(server.URL)
	resp, err := c.EnableStepMode("session-1", "exec-123", "node-1")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if resp.ExecutionID != "exec-123" {
		t.Errorf("expected execution ID exec-123, got %s", resp.ExecutionID)
	}
}

func TestDisableStepMode(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "POST" {
			t.Errorf("expected POST, got %s", r.Method)
		}
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		w.Write([]byte(`{"data":{"executionId":"exec-123"}}`))
	}))
	defer server.Close()

	c := NewClient(server.URL)
	resp, err := c.DisableStepMode("session-1", "exec-123", "node-1")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if resp.ExecutionID != "exec-123" {
		t.Errorf("expected execution ID exec-123, got %s", resp.ExecutionID)
	}
}

func TestStepNode(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "POST" {
			t.Errorf("expected POST, got %s", r.Method)
		}
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		w.Write([]byte(`{"data":{"executionId":"exec-123"}}`))
	}))
	defer server.Close()

	c := NewClient(server.URL)
	resp, err := c.StepNode("session-1", "exec-123", "node-1")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if resp.ExecutionID != "exec-123" {
		t.Errorf("expected execution ID exec-123, got %s", resp.ExecutionID)
	}
}

func TestStopNode(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "POST" {
			t.Errorf("expected POST, got %s", r.Method)
		}
		if r.URL.RawQuery == "" {
			t.Error("expected query parameters")
		}
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		w.Write([]byte(`{"data":{"executionId":"exec-123"}}`))
	}))
	defer server.Close()

	c := NewClient(server.URL)
	resp, err := c.StopNode("session-1", "exec-123", "node-1", true, "test reason")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if resp.ExecutionID != "exec-123" {
		t.Errorf("expected execution ID exec-123, got %s", resp.ExecutionID)
	}
}

func TestSkipNode(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "POST" {
			t.Errorf("expected POST, got %s", r.Method)
		}
		if r.URL.RawQuery == "" {
			t.Error("expected query parameters")
		}
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		w.Write([]byte(`{"data":{"executionId":"exec-123"}}`))
	}))
	defer server.Close()

	c := NewClient(server.URL)
	resp, err := c.SkipNode("session-1", "exec-123", "node-1", true)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if resp.ExecutionID != "exec-123" {
		t.Errorf("expected execution ID exec-123, got %s", resp.ExecutionID)
	}
}

func TestStreamWorkflowStatus(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "GET" {
			t.Errorf("expected GET, got %s", r.Method)
		}
		w.Header().Set("Content-Type", "text/event-stream")
		w.WriteHeader(http.StatusOK)

		// Send a mock SSE event
		progress := WorkflowProgress{
			ExecutionID: "exec-123",
			SessionID:   "session-1",
			Status:      "RUNNING",
			Tasks:       []TaskProgress{},
		}
		data, _ := json.Marshal(progress)
		fmt.Fprintf(w, "data: %s\n\n", string(data))

		if f, ok := w.(http.Flusher); ok {
			f.Flush()
		}
	}))
	defer server.Close()

	c := NewClient(server.URL)
	called := false
	err := c.StreamWorkflowStatus(context.Background(), "session-1", "exec-123", false, func(progress WorkflowProgress) error {
		called = true
		if progress.ExecutionID != "exec-123" {
			t.Errorf("expected execution ID exec-123, got %s", progress.ExecutionID)
		}
		return nil
	})

	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !called {
		t.Error("onProgress callback was not called")
	}
}
