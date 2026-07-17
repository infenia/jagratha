// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Infenia Private Limited

package executions

import (
	"bytes"
	"io"
	"os"
	"strings"
	"testing"

	"com.infenia.yukta/go-cli/internal/client"
)

func TestStopCmd_successful(t *testing.T) {
	mockClient := &client.MockClient{
		StopExecutionFunc: func(executionID string) (client.WorkflowStartResponse, error) {
			return client.WorkflowStartResponse{ExecutionID: "exec-123"}, nil
		},
	}

	cmd := StopCmd(mockClient)

	r, w, _ := os.Pipe()
	oldStdout := os.Stdout
	os.Stdout = w

	err := cmd.RunE(cmd, []string{"exec-123"})

	w.Close()
	os.Stdout = oldStdout

	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if mockClient.StopExecutionCalls != 1 {
		t.Errorf("expected 1 call, got %d", mockClient.StopExecutionCalls)
	}

	var buf bytes.Buffer
	_, _ = io.Copy(&buf, r)
	output := buf.String()
	if !strings.Contains(output, "stopped") {
		t.Errorf("expected 'stopped' in output, got: %s", output)
	}
}

func TestRestartCmd_restartExecution(t *testing.T) {
	mockClient := &client.MockClient{
		RestartExecutionFunc: func(executionID string) (client.WorkflowStartResponse, error) {
			return client.WorkflowStartResponse{ExecutionID: "exec-456"}, nil
		},
	}

	cmd := RestartCmd(mockClient)

	r, w, _ := os.Pipe()
	oldStdout := os.Stdout
	os.Stdout = w

	err := cmd.RunE(cmd, []string{"exec-123"})

	w.Close()
	os.Stdout = oldStdout

	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if mockClient.RestartExecutionCalls != 1 {
		t.Errorf("expected RestartExecution call, got %d", mockClient.RestartExecutionCalls)
	}

	var buf bytes.Buffer
	_, _ = io.Copy(&buf, r)
	output := buf.String()
	if !strings.Contains(output, "restarted") {
		t.Errorf("expected 'restarted' in output, got: %s", output)
	}
}

func TestRestartCmd_restartFromNode(t *testing.T) {
	mockClient := &client.MockClient{
		RestartFromNodeFunc: func(executionID, fromNodeID string) (client.WorkflowStartResponse, error) {
			return client.WorkflowStartResponse{ExecutionID: "exec-456"}, nil
		},
	}

	cmd := RestartCmd(mockClient)

	r, w, _ := os.Pipe()
	oldStdout := os.Stdout
	os.Stdout = w

	err := cmd.RunE(cmd, []string{"exec-123", "node-5"})

	w.Close()
	os.Stdout = oldStdout

	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if mockClient.RestartFromNodeCalls != 1 {
		t.Errorf("expected RestartFromNode call, got %d", mockClient.RestartFromNodeCalls)
	}

	var buf bytes.Buffer
	_, _ = io.Copy(&buf, r)
	output := buf.String()
	if !strings.Contains(output, "restarted") {
		t.Errorf("expected 'restarted' in output, got: %s", output)
	}
}
