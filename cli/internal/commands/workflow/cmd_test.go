// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Infenia Private Limited

package workflow

import (
	"bytes"
	"io"
	"os"
	"strings"
	"testing"

	"com.infenia.yukta/go-cli/internal/client"
	"com.infenia.yukta/go-cli/internal/commands"
)

func TestStartCmd_successful(t *testing.T) {
	mockClient := &client.MockClient{
		StartWorkflowFunc: func(sessionID, workflowID string) (client.WorkflowStartResponse, error) {
			return client.WorkflowStartResponse{ExecutionID: "exec-123"}, nil
		},
	}

	cmd := StartCmd(mockClient)

	r, w, _ := os.Pipe()
	oldStdout := os.Stdout
	os.Stdout = w

	err := cmd.RunE(cmd, []string{"session-1", "workflow-1"})

	w.Close()
	os.Stdout = oldStdout

	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if mockClient.StartWorkflowCalls != 1 {
		t.Errorf("expected 1 call, got %d", mockClient.StartWorkflowCalls)
	}

	var buf bytes.Buffer
	_, _ = io.Copy(&buf, r)
	output := buf.String()
	if !strings.Contains(output, "exec-123") {
		t.Errorf("expected execution ID in output, got: %s", output)
	}
}

func TestStopCmd_successful(t *testing.T) {
	mockClient := &client.MockClient{
		StopWorkflowFunc: func(sessionID, workflowID string) (client.WorkflowStopResponse, error) {
			return client.WorkflowStopResponse{ExecutionIDs: []string{"exec-1", "exec-2"}}, nil
		},
	}

	cmd := StopCmd(mockClient)

	r, w, _ := os.Pipe()
	oldStdout := os.Stdout
	os.Stdout = w

	err := cmd.RunE(cmd, []string{"session-1", "workflow-1"})

	w.Close()
	os.Stdout = oldStdout

	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if mockClient.StopWorkflowCalls != 1 {
		t.Errorf("expected 1 call, got %d", mockClient.StopWorkflowCalls)
	}

	var buf bytes.Buffer
	_, _ = io.Copy(&buf, r)
	output := buf.String()
	if !strings.Contains(output, "2") {
		t.Errorf("expected execution count in output, got: %s", output)
	}
}

func TestPauseCmd_successful(t *testing.T) {
	mockClient := &client.MockClient{
		PauseWorkflowFunc: func(sessionID, executionID string) (client.WorkflowStartResponse, error) {
			return client.WorkflowStartResponse{ExecutionID: "exec-123"}, nil
		},
	}

	cmd := PauseCmd(mockClient)

	r, w, _ := os.Pipe()
	oldStdout := os.Stdout
	os.Stdout = w

	err := cmd.RunE(cmd, []string{"session-1", "exec-123"})

	w.Close()
	os.Stdout = oldStdout

	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if mockClient.PauseWorkflowCalls != 1 {
		t.Errorf("expected 1 call, got %d", mockClient.PauseWorkflowCalls)
	}

	var buf bytes.Buffer
	_, _ = io.Copy(&buf, r)
	output := buf.String()
	if !strings.Contains(output, "paused") {
		t.Errorf("expected 'paused' in output, got: %s", output)
	}
}

func TestResumeCmd_successful(t *testing.T) {
	mockClient := &client.MockClient{
		ResumeWorkflowFunc: func(sessionID, executionID string) (client.WorkflowStartResponse, error) {
			return client.WorkflowStartResponse{ExecutionID: "exec-123"}, nil
		},
	}

	cmd := ResumeCmd(mockClient)

	r, w, _ := os.Pipe()
	oldStdout := os.Stdout
	os.Stdout = w

	err := cmd.RunE(cmd, []string{"session-1", "exec-123"})

	w.Close()
	os.Stdout = oldStdout

	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if mockClient.ResumeWorkflowCalls != 1 {
		t.Errorf("expected 1 call, got %d", mockClient.ResumeWorkflowCalls)
	}

	var buf bytes.Buffer
	_, _ = io.Copy(&buf, r)
	output := buf.String()
	if !strings.Contains(output, "resumed") {
		t.Errorf("expected 'resumed' in output, got: %s", output)
	}
}

func TestStatusCmd_successful(t *testing.T) {
	mockClient := &client.MockClient{
		GetWorkflowStatusFunc: func(sessionID, executionID string) (client.WorkflowProgress, error) {
			return client.WorkflowProgress{
				ExecutionID: "exec-123",
				Status:      "RUNNING",
				Tasks:       []client.TaskProgress{},
			}, nil
		},
	}

	cmd := StatusCmd(mockClient)

	r, w, _ := os.Pipe()
	oldStdout := os.Stdout
	os.Stdout = w

	err := cmd.RunE(cmd, []string{"session-1", "exec-123"})

	w.Close()
	os.Stdout = oldStdout

	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if mockClient.GetWorkflowStatusCalls != 1 {
		t.Errorf("expected 1 call, got %d", mockClient.GetWorkflowStatusCalls)
	}

	var buf bytes.Buffer
	_, _ = io.Copy(&buf, r)
	output := buf.String()
	if !strings.Contains(output, "RUNNING") {
		t.Errorf("expected status in output, got: %s", output)
	}
}

func TestHistoryCmd_successful(t *testing.T) {
	mockClient := &client.MockClient{
		GetWorkflowHistoryFunc: func(sessionID string) ([]client.WorkflowExecutionSummary, error) {
			return []client.WorkflowExecutionSummary{
				{ExecutionID: "exec-1", WorkflowID: "wf-1", Status: "COMPLETED"},
			}, nil
		},
	}

	cmd := HistoryCmd(mockClient)

	r, w, _ := os.Pipe()
	oldStdout := os.Stdout
	os.Stdout = w

	err := cmd.RunE(cmd, []string{"session-1"})

	w.Close()
	os.Stdout = oldStdout

	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if mockClient.GetWorkflowHistoryCalls != 1 {
		t.Errorf("expected 1 call, got %d", mockClient.GetWorkflowHistoryCalls)
	}

	var buf bytes.Buffer
	_, _ = io.Copy(&buf, r)
	output := buf.String()
	if !strings.Contains(output, "exec-1") {
		t.Errorf("expected execution in output, got: %s", output)
	}
}

func TestStartCmd_jsonOutput(t *testing.T) {
	mockClient := &client.MockClient{
		StartWorkflowFunc: func(sessionID, workflowID string) (client.WorkflowStartResponse, error) {
			return client.WorkflowStartResponse{ExecutionID: "exec-123"}, nil
		},
	}

	// Set output format to JSON
	oldFormat := commands.GetOutputFormat()
	commands.SetTestOutputFormat("json")
	defer commands.SetTestOutputFormat(oldFormat)

	cmd := StartCmd(mockClient)

	r, w, _ := os.Pipe()
	oldStdout := os.Stdout
	os.Stdout = w

	err := cmd.RunE(cmd, []string{"session-1", "workflow-1"})

	w.Close()
	os.Stdout = oldStdout

	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	var buf bytes.Buffer
	_, _ = io.Copy(&buf, r)
	output := buf.String()
	if !strings.Contains(output, "executionId") {
		t.Errorf("expected JSON output, got: %s", output)
	}
}
