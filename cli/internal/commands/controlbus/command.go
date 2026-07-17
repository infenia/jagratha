// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Infenia Private Limited

package controlbus

import (
	"encoding/json"
	"fmt"
	"os"

	"com.infenia.yukta/go-cli/internal/client"
	"com.infenia.yukta/go-cli/internal/commands"
	"com.infenia.yukta/go-cli/internal/output"
	"github.com/spf13/cobra"
)

// CommandCmd creates and returns the "controlbus command" subcommand.
// It sends a command to a specific node in a workflow.
// The command payload can be provided as either a file path or inline JSON.
func CommandCmd(c client.ClientInterface) *cobra.Command {
	return &cobra.Command{
		Use:   "command <workflow-id> <node-id> <json-or-file>",
		Short: "Send command to a node",
		Long: `Send a command to a specific node in a workflow.

The command payload can be provided in two ways:
  1. As a file path (e.g., command.json)
  2. As an inline JSON string (e.g., '{"action":"pause"}')

The command will automatically detect whether the input is a file or JSON string.`,
		Args: cobra.ExactArgs(3),
		RunE: func(cmd *cobra.Command, args []string) error {
			workflowID := args[0]
			nodeID := args[1]
			input := args[2]

			// Read command payload from file or inline JSON
			commandPayload, err := readCommandInput(input)
			if err != nil {
				return fmt.Errorf("failed to read command: %w", err)
			}

			// Validate JSON before sending
			var commandData interface{}
			if err := json.Unmarshal(commandPayload, &commandData); err != nil {
				return fmt.Errorf("invalid JSON: %w", err)
			}

			// Send the command to the server
			response, err := c.SendCommand(workflowID, nodeID, commandPayload)
			if err != nil {
				return fmt.Errorf("failed to send command: %w", err)
			}

			// Format and print the output
			format := commands.GetOutputFormat()

			if format == "json" {
				output.PrintJSON(response)
			} else {
				// Table format: key-value pairs
				rows := make([][]string, 0)
				for key, value := range response {
					rows = append(rows, []string{key, fmt.Sprintf("%v", value)})
				}
				output.PrintTable([]string{"Field", "Value"}, rows)
			}

			return nil
		},
	}
}

// readCommandInput reads command payload from either a file or an inline JSON string.
// It first attempts to check if the input is a file path using os.Stat.
// If the file exists, it reads and returns the file contents.
// If the file doesn't exist, it treats the input as an inline JSON string.
func readCommandInput(input string) ([]byte, error) {
	// Check if the input is a file
	if fileInfo, err := os.Stat(input); err == nil && !fileInfo.IsDir() {
		// File exists and is not a directory, so read it
		return os.ReadFile(input)
	}

	// File doesn't exist or is a directory, treat as inline JSON
	return []byte(input), nil
}
