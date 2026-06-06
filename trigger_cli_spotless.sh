#!/bin/bash
set -e

# Configuration
SESSION_ID="cli-spotless-session"
JAR_PATH="./boot/build/libs/boot-0.0.1-SNAPSHOT.jar"

echo "Building the fat JAR..."
./gradlew :boot:bootJar > /dev/null 2>&1
echo "✓ Build complete"

echo "Ensuring daemon is running..."
java -jar $JAR_PATH daemon start > /dev/null 2>&1
echo "✓ Daemon is ready"

echo "Applying spotlessApply via CLI..."
java -jar $JAR_PATH control session-apply "$(cat <<'EOF'
{
  "sessionId": "cli-spotless-session",
  "description": "CLI Spotless formatting workflow",
  "initiator": "spotless-cli",
  "projectPath": "$(pwd)",
  "workflows": {
    "spotless-check": {
      "workflowId": "spotless-check",
      "description": "Apply spotless code formatting",
      "nodes": [
        {
          "nodeId": "spotless",
          "type": "PROCESS_EXECUTOR",
          "config": {
            "command": ["./gradlew", "spotlessApply"],
            "workingDirectory": "$(pwd)"
          }
        }
      ],
      "edges": []
    }
  }
}
EOF
)" 2>&1 | tail -5

echo "✓ Session configured successfully"

echo "Triggering spotlessApply workflow..."
java -jar $JAR_PATH control trigger "$SESSION_ID" "spotless-check" "{}" 2>&1 | tail -5

echo "✓ Workflow triggered successfully"

echo -e "\nTo view daemon status, run:"
echo "  java -jar $JAR_PATH daemon status"
echo -e "\nTo stop the daemon, run:"
echo "  java -jar $JAR_PATH daemon stop"
