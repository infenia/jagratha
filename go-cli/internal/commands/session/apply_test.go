// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package session

import (
	"os"
	"path/filepath"
	"testing"
)

// TestReadConfigInputFromFile tests reading configuration from a file.
func TestReadConfigInputFromFile(t *testing.T) {
	// Create a temporary file with JSON content
	tmpFile, err := os.CreateTemp("", "config-*.json")
	if err != nil {
		t.Fatalf("failed to create temp file: %v", err)
	}
	defer os.Remove(tmpFile.Name())

	// Write test JSON to the file
	testJSON := `{"sessionId":"test-session","config":"value"}`
	if _, err := tmpFile.WriteString(testJSON); err != nil {
		t.Fatalf("failed to write to temp file: %v", err)
	}
	tmpFile.Close()

	// Test reading from file
	result, err := readConfigInput(tmpFile.Name())
	if err != nil {
		t.Fatalf("readConfigInput failed: %v", err)
	}

	if string(result) != testJSON {
		t.Errorf("expected %s, got %s", testJSON, string(result))
	}
}

// TestReadConfigInputFromInlineJSON tests reading configuration from inline JSON.
func TestReadConfigInputFromInlineJSON(t *testing.T) {
	inlineJSON := `{"sessionId":"inline-session"}`

	result, err := readConfigInput(inlineJSON)
	if err != nil {
		t.Fatalf("readConfigInput failed: %v", err)
	}

	if string(result) != inlineJSON {
		t.Errorf("expected %s, got %s", inlineJSON, string(result))
	}
}

// TestReadConfigInputFromNonexistentFile tests handling of nonexistent file paths.
func TestReadConfigInputFromNonexistentFile(t *testing.T) {
	// Use a path that doesn't exist
	nonexistentPath := "/tmp/this-file-does-not-exist-" + filepath.Base(os.TempDir()) + ".json"

	// The function should treat this as inline JSON since the file doesn't exist
	result, err := readConfigInput(nonexistentPath)
	if err != nil {
		t.Fatalf("readConfigInput failed: %v", err)
	}

	if string(result) != nonexistentPath {
		t.Errorf("expected %s, got %s", nonexistentPath, string(result))
	}
}

// TestReadConfigInputFromDirectory tests handling when input is a directory path.
func TestReadConfigInputFromDirectory(t *testing.T) {
	// Create a temporary directory
	tmpDir, err := os.MkdirTemp("", "test-dir-*")
	if err != nil {
		t.Fatalf("failed to create temp directory: %v", err)
	}
	defer os.RemoveAll(tmpDir)

	// Treat directory path as inline JSON since it's a directory
	result, err := readConfigInput(tmpDir)
	if err != nil {
		t.Fatalf("readConfigInput failed: %v", err)
	}

	if string(result) != tmpDir {
		t.Errorf("expected %s, got %s", tmpDir, string(result))
	}
}
