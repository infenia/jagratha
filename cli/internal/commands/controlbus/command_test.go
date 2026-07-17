// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Infenia Private Limited

package controlbus

import (
	"bytes"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"strings"
	"testing"

	"com.infenia.yukta/go-cli/internal/client"
	"com.infenia.yukta/go-cli/internal/commands"
)

func TestCommandCmd_createsCommand(t *testing.T) {
	c := client.NewClient("http://localhost:8080")
	cmd := CommandCmd(c)

	if !strings.HasPrefix(cmd.Use, "command") {
		t.Errorf("expected Use starting with 'command', got %q", cmd.Use)
	}
}

func TestCommandCmd_executesSuccessfully_inlineJSON_tableFormat(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "POST" {
			t.Errorf("expected POST, got %s", r.Method)
		}
		if r.URL.Path != "/api/control/workflows/workflow-123/nodes/node-1/command" {
			t.Errorf("expected /api/control/workflows/workflow-123/nodes/node-1/command, got %s", r.URL.Path)
		}

		w.Header().Set("Content-Type", "application/json")
		response := map[string]interface{}{
			"data": map[string]interface{}{
				"status":  "success",
				"message": "Command executed",
			},
		}
		_ = json.NewEncoder(w).Encode(response)
	}))
	defer server.Close()

	c := client.NewClient(server.URL)
	cmd := CommandCmd(c)

	commands.SetTestOutputFormat("table")
	defer commands.SetTestOutputFormat("table")

	r, w, _ := os.Pipe()
	oldStdout := os.Stdout
	os.Stdout = w

	err := cmd.RunE(cmd, []string{"workflow-123", "node-1", `{"action":"pause"}`})

	w.Close()
	os.Stdout = oldStdout

	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	var buf bytes.Buffer
	_, _ = io.Copy(&buf, r)
	output := buf.String()

	if !strings.Contains(output, "success") {
		t.Errorf("expected 'success' in output, got: %s", output)
	}
}

func TestCommandCmd_executesSuccessfully_inlineJSON_jsonFormat(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		response := map[string]interface{}{
			"data": map[string]interface{}{
				"status":  "success",
				"message": "Command executed",
			},
		}
		_ = json.NewEncoder(w).Encode(response)
	}))
	defer server.Close()

	c := client.NewClient(server.URL)
	cmd := CommandCmd(c)

	commands.SetTestOutputFormat("json")
	defer commands.SetTestOutputFormat("table")

	r, w, _ := os.Pipe()
	oldStdout := os.Stdout
	os.Stdout = w

	err := cmd.RunE(cmd, []string{"workflow-123", "node-1", `{"action":"pause"}`})

	w.Close()
	os.Stdout = oldStdout

	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	var buf bytes.Buffer
	_, _ = io.Copy(&buf, r)
	output := buf.String()

	if !strings.Contains(output, "success") {
		t.Errorf("expected 'success' in JSON output, got: %s", output)
	}
}

func TestCommandCmd_executesSuccessfully_fromFile(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		response := map[string]interface{}{
			"data": map[string]interface{}{
				"status": "success",
			},
		}
		_ = json.NewEncoder(w).Encode(response)
	}))
	defer server.Close()

	// Create a temporary file with JSON content
	tmpFile, err := os.CreateTemp("", "command-*.json")
	if err != nil {
		t.Fatalf("failed to create temp file: %v", err)
	}
	defer os.Remove(tmpFile.Name())

	if _, err := tmpFile.Write([]byte(`{"action":"resume"}`)); err != nil {
		t.Fatalf("failed to write to temp file: %v", err)
	}
	tmpFile.Close()

	c := client.NewClient(server.URL)
	cmd := CommandCmd(c)

	commands.SetTestOutputFormat("json")
	defer commands.SetTestOutputFormat("table")

	r, w, _ := os.Pipe()
	oldStdout := os.Stdout
	os.Stdout = w

	err = cmd.RunE(cmd, []string{"workflow-123", "node-1", tmpFile.Name()})

	w.Close()
	os.Stdout = oldStdout

	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	var buf bytes.Buffer
	_, _ = io.Copy(&buf, r)
	output := buf.String()

	if !strings.Contains(output, "success") {
		t.Errorf("expected 'success' in output, got: %s", output)
	}
}

func TestCommandCmd_invalidJSON(t *testing.T) {
	c := client.NewClient("http://localhost:8080")
	cmd := CommandCmd(c)

	err := cmd.RunE(cmd, []string{"workflow-123", "node-1", `invalid json {`})

	if err == nil {
		t.Error("expected error for invalid JSON, got nil")
	}
	if !strings.Contains(err.Error(), "invalid JSON") {
		t.Errorf("expected 'invalid JSON' error, got: %v", err)
	}
}

func TestCommandCmd_handlesApiError(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusNotFound)
		_, _ = w.Write([]byte("Node not found"))
	}))
	defer server.Close()

	c := client.NewClient(server.URL)
	cmd := CommandCmd(c)

	err := cmd.RunE(cmd, []string{"workflow-123", "nonexistent", `{"action":"pause"}`})

	if err == nil {
		t.Error("expected error, got nil")
	}
	if !strings.Contains(err.Error(), "failed to send command") {
		t.Errorf("expected wrapped error, got: %v", err)
	}
}

func TestCommandCmd_emptyResponseMap(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		response := map[string]interface{}{
			"data": map[string]interface{}{},
		}
		_ = json.NewEncoder(w).Encode(response)
	}))
	defer server.Close()

	c := client.NewClient(server.URL)
	cmd := CommandCmd(c)

	commands.SetTestOutputFormat("table")
	defer commands.SetTestOutputFormat("table")

	r, w, _ := os.Pipe()
	oldStdout := os.Stdout
	os.Stdout = w

	err := cmd.RunE(cmd, []string{"workflow-123", "node-1", `{"action":"pause"}`})

	w.Close()
	os.Stdout = oldStdout

	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	var buf bytes.Buffer
	_, _ = io.Copy(&buf, r)
}

func TestCommandCmd_multipleResponseFields_tableFormat(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		response := map[string]interface{}{
			"data": map[string]interface{}{
				"status":    "executed",
				"message":   "Command successfully sent",
				"timestamp": "2026-07-17T00:00:00Z",
			},
		}
		_ = json.NewEncoder(w).Encode(response)
	}))
	defer server.Close()

	c := client.NewClient(server.URL)
	cmd := CommandCmd(c)

	commands.SetTestOutputFormat("table")
	defer commands.SetTestOutputFormat("table")

	r, w, _ := os.Pipe()
	oldStdout := os.Stdout
	os.Stdout = w

	err := cmd.RunE(cmd, []string{"workflow-123", "node-1", `{"action":"pause"}`})

	w.Close()
	os.Stdout = oldStdout

	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	var buf bytes.Buffer
	_, _ = io.Copy(&buf, r)
	output := buf.String()

	if !strings.Contains(output, "executed") {
		t.Errorf("expected 'executed' in output, got: %s", output)
	}
}

func TestCommandCmd_formatCycleThroughAllPaths(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		response := map[string]interface{}{
			"data": map[string]interface{}{
				"field1": "value1",
				"field2": "value2",
			},
		}
		_ = json.NewEncoder(w).Encode(response)
	}))
	defer server.Close()

	c := client.NewClient(server.URL)
	cmd := CommandCmd(c)

	testCases := []string{"table", "json", "table", "json"}

	for _, format := range testCases {
		commands.SetTestOutputFormat(format)

		r, w, _ := os.Pipe()
		oldStdout := os.Stdout
		os.Stdout = w

		err := cmd.RunE(cmd, []string{"wf-123", "node-x", `{"cmd":"test"}`})

		w.Close()
		os.Stdout = oldStdout

		if err != nil {
			t.Fatalf("unexpected error with format %s: %v", format, err)
		}

		var buf bytes.Buffer
		_, _ = io.Copy(&buf, r)
		output := buf.String()

		if !strings.Contains(output, "value1") {
			t.Errorf("expected 'value1' in output with format %s, got: %s", format, output)
		}
	}

	commands.SetTestOutputFormat("table")
}

func TestCommandCmd_responseSingleField(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		response := map[string]interface{}{
			"data": map[string]interface{}{
				"result": "ok",
			},
		}
		_ = json.NewEncoder(w).Encode(response)
	}))
	defer server.Close()

	c := client.NewClient(server.URL)
	cmd := CommandCmd(c)

	commands.SetTestOutputFormat("json")
	defer commands.SetTestOutputFormat("table")

	r, w, _ := os.Pipe()
	oldStdout := os.Stdout
	os.Stdout = w

	err := cmd.RunE(cmd, []string{"wf", "node", `{"action":"test"}`})

	w.Close()
	os.Stdout = oldStdout

	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	var buf bytes.Buffer
	_, _ = io.Copy(&buf, r)
	output := buf.String()

	if !strings.Contains(output, "ok") {
		t.Errorf("expected 'ok' in JSON output, got: %s", output)
	}
}

func TestCommandCmd_usingMockClient(t *testing.T) {
	testCases := []struct {
		name       string
		format     string
		response   map[string]interface{}
		shouldHave string
	}{
		{
			"json_single_field",
			"json",
			map[string]interface{}{"status": "ok"},
			"ok",
		},
		{
			"table_single_field",
			"table",
			map[string]interface{}{"status": "ok"},
			"ok",
		},
		{
			"json_multiple_fields",
			"json",
			map[string]interface{}{"field1": "val1", "field2": "val2"},
			"val1",
		},
		{
			"table_multiple_fields",
			"table",
			map[string]interface{}{"field1": "val1", "field2": "val2"},
			"val1",
		},
		{
			"json_empty_response",
			"json",
			map[string]interface{}{},
			"{}",
		},
		{
			"table_empty_response",
			"table",
			map[string]interface{}{},
			"Field",
		},
	}

	for _, tc := range testCases {
		t.Run(tc.name, func(t *testing.T) {
			responseData := tc.response
			mockClient := &client.MockClient{
				SendCommandFunc: func(workflowID, nodeID string, commandPayloadJSON []byte) (map[string]interface{}, error) {
					return responseData, nil
				},
			}

			cmd := CommandCmd(mockClient)
			commands.SetTestOutputFormat(tc.format)

			r, w, _ := os.Pipe()
			oldStdout := os.Stdout
			os.Stdout = w

			err := cmd.RunE(cmd, []string{"wf-123", "node-1", `{"cmd":"test"}`})

			w.Close()
			os.Stdout = oldStdout

			if err != nil {
				t.Fatalf("unexpected error: %v", err)
			}

			var buf bytes.Buffer
			_, _ = io.Copy(&buf, r)
			output := buf.String()

			if !strings.Contains(output, tc.shouldHave) {
				t.Errorf("expected %q in output, got: %s", tc.shouldHave, output)
			}
		})
	}

	commands.SetTestOutputFormat("table")
}

func TestCommandCmd_complexResponseStructures(t *testing.T) {
	testCases := []struct {
		name       string
		format     string
		response   map[string]interface{}
		testName   string
	}{
		{
			"many_fields_json",
			"json",
			map[string]interface{}{
				"field1": "value1", "field2": "value2", "field3": "value3",
				"field4": "value4", "field5": "value5", "field6": "value6",
			},
			"many_fields_json",
		},
		{
			"many_fields_table",
			"table",
			map[string]interface{}{
				"field1": "value1", "field2": "value2", "field3": "value3",
				"field4": "value4", "field5": "value5", "field6": "value6",
			},
			"many_fields_table",
		},
		{
			"special_chars_json",
			"json",
			map[string]interface{}{"special": "value\nwith\nnewlines\tand\ttabs"},
			"special_chars_json",
		},
		{
			"special_chars_table",
			"table",
			map[string]interface{}{"special": "value\nwith\nnewlines\tand\ttabs"},
			"special_chars_table",
		},
	}

	for _, tc := range testCases {
		t.Run(tc.testName, func(t *testing.T) {
			responseData := tc.response
			mockClient := &client.MockClient{
				SendCommandFunc: func(workflowID, nodeID string, commandPayloadJSON []byte) (map[string]interface{}, error) {
					return responseData, nil
				},
			}

			cmd := CommandCmd(mockClient)
			commands.SetTestOutputFormat(tc.format)

			_, w, _ := os.Pipe()
			oldStdout := os.Stdout
			os.Stdout = w

			err := cmd.RunE(cmd, []string{"wf-1", "n-1", `{}`})

			w.Close()
			os.Stdout = oldStdout

			if err != nil {
				t.Fatalf("unexpected error: %v", err)
			}
		})
	}

	commands.SetTestOutputFormat("table")
}

func TestCommandCmd_exhaustiveFormatAndResponseCombinations(t *testing.T) {
	responses := []map[string]interface{}{
		{},
		{"a": "1"},
		{"x": "10", "y": "20"},
		{"field1": "val1", "field2": "val2", "field3": "val3", "field4": "val4"},
	}

	formats := []string{"json", "table"}

	for _, resp := range responses {
		for _, format := range formats {
			responseData := resp
			mockClient := &client.MockClient{
				SendCommandFunc: func(workflowID, nodeID string, commandPayloadJSON []byte) (map[string]interface{}, error) {
					return responseData, nil
				},
			}

			cmd := CommandCmd(mockClient)
			commands.SetTestOutputFormat(format)

			_, w, _ := os.Pipe()
			oldStdout := os.Stdout
			os.Stdout = w

			err := cmd.RunE(cmd, []string{"w", "n", `{}`})

			w.Close()
			os.Stdout = oldStdout

			if err != nil {
				t.Fatalf("unexpected error with format %s: %v", format, err)
			}
		}
	}

	commands.SetTestOutputFormat("table")
}

func TestBuildTableRows(t *testing.T) {
	testCases := []struct {
		name     string
		response map[string]interface{}
		expected int
	}{
		{"empty", map[string]interface{}{}, 0},
		{"single", map[string]interface{}{"a": "1"}, 1},
		{"multiple", map[string]interface{}{"a": "1", "b": "2", "c": "3"}, 3},
		{"values", map[string]interface{}{"status": "ok", "count": 42, "active": true}, 3},
	}

	for _, tc := range testCases {
		t.Run(tc.name, func(t *testing.T) {
			rows := buildTableRows(tc.response)
			if len(rows) != tc.expected {
				t.Errorf("expected %d rows, got %d", tc.expected, len(rows))
			}
			for _, row := range rows {
				if len(row) != 2 {
					t.Errorf("expected each row to have 2 columns, got %d", len(row))
				}
			}
		})
	}
}

func TestFormatAndPrintResponse(t *testing.T) {
	testCases := []struct {
		name     string
		format   string
		response map[string]interface{}
	}{
		{"json_empty", "json", map[string]interface{}{}},
		{"json_single", "json", map[string]interface{}{"result": "ok"}},
		{"json_multiple", "json", map[string]interface{}{"a": "1", "b": "2"}},
		{"table_empty", "table", map[string]interface{}{}},
		{"table_single", "table", map[string]interface{}{"result": "ok"}},
		{"table_multiple", "table", map[string]interface{}{"a": "1", "b": "2"}},
	}

	for _, tc := range testCases {
		t.Run(tc.name, func(t *testing.T) {
			commands.SetTestOutputFormat(tc.format)

			_, w, _ := os.Pipe()
			oldStdout := os.Stdout
			os.Stdout = w

			err := formatAndPrintResponse(tc.response)

			w.Close()
			os.Stdout = oldStdout

			if err != nil {
				t.Fatalf("unexpected error: %v", err)
			}
		})
	}

	commands.SetTestOutputFormat("table")
}

func TestReadCommandInput_inlineJSON(t *testing.T) {
	input := `{"action":"pause","reason":"testing"}`
	result, err := readCommandInput(input)

	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if string(result) != input {
		t.Errorf("expected %q, got %q", input, string(result))
	}
}

func TestReadCommandInput_various(t *testing.T) {
	testCases := []string{
		`{}`,
		`{"field":"value"}`,
		`{"a":"1","b":"2","c":"3"}`,
		`{"nested":{"inner":"value"}}`,
	}

	for _, input := range testCases {
		result, err := readCommandInput(input)
		if err != nil {
			t.Fatalf("unexpected error for input %q: %v", input, err)
		}
		if string(result) != input {
			t.Errorf("for input %q: expected %q, got %q", input, input, string(result))
		}
	}
}
