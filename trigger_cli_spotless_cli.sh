#!/bin/bash
set -e

# Configuration
SESSION_ID="cli-spotless-session"
WORKFLOW_ID="cli-spotless-workflow"
PROJECT_PATH=$(pwd)

# Function to run Yukta CLI
# Properly escape arguments for gradle --args parameter
yukta_cli() {
    local args=("$@")
    local escaped_args=""
    for arg in "${args[@]}"; do
        # Escape single quotes and wrap in single quotes for gradle
        escaped_args="$escaped_args '$(echo "$arg" | sed "s/'/'\\\\''/g")'"
    done
    ./gradlew :boot:bootRun --args="control$escaped_args" -q
}

echo "Checking if workflow is already created (checking history)..."
# We check if history is empty. Note: this might trigger application if not exists, 
# but history command in Yukta returns [] for non-existent sessions too.
HISTORY=$(yukta_cli history "$SESSION_ID" --output json)

if [ "$HISTORY" == "[]" ] || [ -z "$HISTORY" ]; then
    echo "Workflow not found or no history. Applying session configuration..."
    
    CONFIG_JSON=$(cat <<EOF
{
  "sessionId": "$SESSION_ID",
  "description": "Session for running spotlessApply on CLI module",
  "initiator": "bash-script",
  "projectPath": "$PROJECT_PATH",
  "workflows": {
    "$WORKFLOW_ID": {
      "workflowId": "$WORKFLOW_ID",
      "description": "Runs spotlessApply on the cli module",
      "nodes": [
        {
          "nodeId": "run-spotless",
          "type": "PROCESS_EXECUTOR",
          "config": {
            "command": ["./gradlew", ":cli:spotlessApply"],
            "workingDir": "$PROJECT_PATH",
            "streamOutput": true
          }
        }
      ],
      "edges": []
    }
  }
}
EOF
)

    yukta_cli session-apply "$CONFIG_JSON"
    echo "Session applied."
else
    echo "Workflow already exists (history found). Skipping configuration."
fi

echo "Triggering workflow execution..."
# Call trigger with properly escaped JSON - use single quotes to prevent shell expansion
yukta_cli trigger "$SESSION_ID" "$WORKFLOW_ID" '{"action":"run"}' --output json
echo -e "\nWorkflow triggered successfully via CLI."
