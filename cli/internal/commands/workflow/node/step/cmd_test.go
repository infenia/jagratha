// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Infenia Private Limited

package step

import (
	"bytes"
	"io"
	"os"
	"strings"
	"testing"

	"com.infenia.yukta/go-cli/internal/client"
)

func TestEnableCmd_successful(t *testing.T) {
	mockClient := &client.MockClient{
		EnableStepModeFunc: func(sessionID, executionID, nodeID string) (client.WorkflowStartResponse, error) {
			return client.WorkflowStartResponse{ExecutionID: "exec-123"}, nil
		},
	}

	cmd := EnableCmd(mockClient)

	r, w, _ := os.Pipe()
	oldStdout := os.Stdout
	os.Stdout = w

	err := cmd.RunE(cmd, []string{"session-1", "exec-123", "node-1"})

	w.Close()
	os.Stdout = oldStdout

	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if mockClient.EnableStepModeCalls != 1 {
		t.Errorf("expected 1 call, got %d", mockClient.EnableStepModeCalls)
	}

	var buf bytes.Buffer
	_, _ = io.Copy(&buf, r)
	output := buf.String()
	if !strings.Contains(output, "enabled") {
		t.Errorf("expected 'enabled' in output, got: %s", output)
	}
}

func TestDisableCmd_successful(t *testing.T) {
	mockClient := &client.MockClient{
		DisableStepModeFunc: func(sessionID, executionID, nodeID string) (client.WorkflowStartResponse, error) {
			return client.WorkflowStartResponse{ExecutionID: "exec-123"}, nil
		},
	}

	cmd := DisableCmd(mockClient)

	r, w, _ := os.Pipe()
	oldStdout := os.Stdout
	os.Stdout = w

	err := cmd.RunE(cmd, []string{"session-1", "exec-123", "node-1"})

	w.Close()
	os.Stdout = oldStdout

	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if mockClient.DisableStepModeCalls != 1 {
		t.Errorf("expected 1 call, got %d", mockClient.DisableStepModeCalls)
	}

	var buf bytes.Buffer
	_, _ = io.Copy(&buf, r)
	output := buf.String()
	if !strings.Contains(output, "disabled") {
		t.Errorf("expected 'disabled' in output, got: %s", output)
	}
}

func TestNextCmd_successful(t *testing.T) {
	mockClient := &client.MockClient{
		StepNodeFunc: func(sessionID, executionID, nodeID string) (client.WorkflowStartResponse, error) {
			return client.WorkflowStartResponse{ExecutionID: "exec-123"}, nil
		},
	}

	cmd := NextCmd(mockClient)

	r, w, _ := os.Pipe()
	oldStdout := os.Stdout
	os.Stdout = w

	err := cmd.RunE(cmd, []string{"session-1", "exec-123", "node-1"})

	w.Close()
	os.Stdout = oldStdout

	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if mockClient.StepNodeCalls != 1 {
		t.Errorf("expected 1 call, got %d", mockClient.StepNodeCalls)
	}

	var buf bytes.Buffer
	_, _ = io.Copy(&buf, r)
	output := buf.String()
	if !strings.Contains(output, "Stepped") {
		t.Errorf("expected 'Stepped' in output, got: %s", output)
	}
}
