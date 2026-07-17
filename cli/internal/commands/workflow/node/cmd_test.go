// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Infenia Private Limited

package node

import (
	"bytes"
	"io"
	"os"
	"strings"
	"testing"

	"com.infenia.yukta/go-cli/internal/client"
)

func TestPauseCmd_successful(t *testing.T) {
	mockClient := &client.MockClient{
		PauseNodeFunc: func(sessionID, executionID, nodeID string) (client.WorkflowStartResponse, error) {
			return client.WorkflowStartResponse{ExecutionID: "exec-123"}, nil
		},
	}

	cmd := PauseCmd(mockClient)

	r, w, _ := os.Pipe()
	oldStdout := os.Stdout
	os.Stdout = w

	err := cmd.RunE(cmd, []string{"session-1", "exec-123", "node-1"})

	w.Close()
	os.Stdout = oldStdout

	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if mockClient.PauseNodeCalls != 1 {
		t.Errorf("expected 1 call, got %d", mockClient.PauseNodeCalls)
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
		ResumeNodeFunc: func(sessionID, executionID, nodeID string) (client.WorkflowStartResponse, error) {
			return client.WorkflowStartResponse{ExecutionID: "exec-123"}, nil
		},
	}

	cmd := ResumeCmd(mockClient)

	r, w, _ := os.Pipe()
	oldStdout := os.Stdout
	os.Stdout = w

	err := cmd.RunE(cmd, []string{"session-1", "exec-123", "node-1"})

	w.Close()
	os.Stdout = oldStdout

	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if mockClient.ResumeNodeCalls != 1 {
		t.Errorf("expected 1 call, got %d", mockClient.ResumeNodeCalls)
	}

	var buf bytes.Buffer
	_, _ = io.Copy(&buf, r)
	output := buf.String()
	if !strings.Contains(output, "resumed") {
		t.Errorf("expected 'resumed' in output, got: %s", output)
	}
}

func TestStopCmd_successful(t *testing.T) {
	mockClient := &client.MockClient{
		StopNodeFunc: func(sessionID, executionID, nodeID string, immediate bool, reason string) (client.WorkflowStartResponse, error) {
			return client.WorkflowStartResponse{ExecutionID: "exec-123"}, nil
		},
	}

	cmd := StopCmd(mockClient)

	r, w, _ := os.Pipe()
	oldStdout := os.Stdout
	os.Stdout = w

	err := cmd.RunE(cmd, []string{"session-1", "exec-123", "node-1"})

	w.Close()
	os.Stdout = oldStdout

	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if mockClient.StopNodeCalls != 1 {
		t.Errorf("expected 1 call, got %d", mockClient.StopNodeCalls)
	}

	var buf bytes.Buffer
	_, _ = io.Copy(&buf, r)
	output := buf.String()
	if !strings.Contains(output, "stopped") {
		t.Errorf("expected 'stopped' in output, got: %s", output)
	}
}

func TestSkipCmd_skipTrue(t *testing.T) {
	mockClient := &client.MockClient{
		SkipNodeFunc: func(sessionID, executionID, nodeID string, skip bool) (client.WorkflowStartResponse, error) {
			return client.WorkflowStartResponse{ExecutionID: "exec-123"}, nil
		},
	}

	cmd := SkipCmd(mockClient)

	r, w, _ := os.Pipe()
	oldStdout := os.Stdout
	os.Stdout = w

	err := cmd.RunE(cmd, []string{"session-1", "exec-123", "node-1"})

	w.Close()
	os.Stdout = oldStdout

	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if mockClient.SkipNodeCalls != 1 {
		t.Errorf("expected 1 call, got %d", mockClient.SkipNodeCalls)
	}

	var buf bytes.Buffer
	_, _ = io.Copy(&buf, r)
	output := buf.String()
	if !strings.Contains(output, "skipped") {
		t.Errorf("expected 'skipped' in output, got: %s", output)
	}
}
