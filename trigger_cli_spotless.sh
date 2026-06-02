#!/bin/bash
set -e

# Configuration
API_BASE_URL="http://localhost:8080/api"
SESSION_ID="cli-spotless-session"
WORKFLOW_ID="cli-spotless-workflow"
PROJECT_PATH=$(pwd)

echo "Checking if workflow is already created..."
STATUS_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$API_BASE_URL/sessions/$SESSION_ID")

if [ "$STATUS_CODE" != "200" ]; then
    echo "Workflow not found. Creating workflow..."
    curl -s -X POST "$API_BASE_URL/sessions" \
        -H "Content-Type: application/json" \
        -d "{
            \"sessionId\": \"$SESSION_ID\",
            \"description\": \"Session for running spotlessApply on CLI module\",
            \"initiator\": \"bash-script\",
            \"projectPath\": \"$PROJECT_PATH\",
            \"workflows\": {
                \"$WORKFLOW_ID\": {
                    \"workflowId\": \"$WORKFLOW_ID\",
                    \"description\": \"Runs spotlessApply on the cli module\",
                    \"nodes\": [
                        {
                            \"nodeId\": \"run-spotless\",
                            \"type\": \"PROCESS_EXECUTOR\",
                            \"config\": {
                                \"command\": [\"./gradlew\", \":cli:spotlessApply\"],
                                \"workingDir\": \"$PROJECT_PATH\",
                                \"streamOutput\": true
                            }
                        }
                    ],
                    \"edges\": []
                }
            }
        }" > /dev/null
    echo "Workflow created."
else
    echo "Workflow already exists. Skipping creation."
fi

echo "Triggering workflow execution..."
curl -s -X POST "$API_BASE_URL/workflow/trigger" \
    -H "Content-Type: application/json" \
    -d "{
        \"sessionId\": \"$SESSION_ID\",
        \"workflowId\": \"$WORKFLOW_ID\",
        \"payload\": {}
    }"

echo -e "\n\nWorkflow triggered successfully."
