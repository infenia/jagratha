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

package commands

import (
	"github.com/spf13/cobra"
)

var outputFormat string

// GetOutputFormat returns the current output format setting.
// The default format is "table" if not explicitly set.
func GetOutputFormat() string {
	if outputFormat == "" {
		return "table"
	}
	return outputFormat
}

// NewRootCommand creates and returns the root Cobra command.
// It sets up the global flags including the output format.
func NewRootCommand() *cobra.Command {
	rootCmd := &cobra.Command{
		Use:   "yukta",
		Short: "Yukta CLI for managing sessions and workflows",
		Long: `Yukta is a CLI tool for interacting with the Yukta API.
It provides commands to manage sessions, workflows, and execute tasks.`,
	}

	// Add persistent flags
	rootCmd.PersistentFlags().StringVarP(&outputFormat, "output", "o", "table",
		"Output format (table or json)")

	return rootCmd
}
