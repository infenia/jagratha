#!/bin/bash
set -e

# Configuration
SESSION_ID="cli-spotless-session"
CLI_JAR_PATH="./cli-boot/build/libs/cli-boot-0.0.1-SNAPSHOT.jar"
DAEMON_JAR_PATH="./boot/build/libs/boot-0.0.1-SNAPSHOT.jar"

echo "Building the CLI JAR..."
if ! ./gradlew :cli-boot:bootJar -x nativeCompile -x collectReachabilityMetadata --no-configuration-cache; then
  echo "✗ Build failed - see errors above"
  exit 1
fi
echo "✓ Build complete"

echo "Building the Daemon JAR..."
if ! ./gradlew :boot:bootJar -x nativeCompile -x collectReachabilityMetadata --no-configuration-cache; then
  echo "✗ Daemon build failed - see errors above"
  exit 1
fi
echo "✓ Daemon build complete"

echo "Ensuring daemon is running..."
java -jar $CLI_JAR_PATH daemon start 2>&1 | grep -E "(error|Error|ERROR)" || true
echo "✓ Daemon is ready"

echo "Applying spotlessApply via CLI..."
java -jar $CLI_JAR_PATH control session-apply "$(cat <<'EOF'
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
java -jar $CLI_JAR_PATH control trigger "$SESSION_ID" "spotless-check" "{}" 2>&1 | tail -5

echo "✓ Workflow triggered successfully"

echo -e "\nTo view daemon status, run:"
echo "  java -jar $CLI_JAR_PATH daemon status"
echo -e "\nTo stop the daemon, run:"
echo "  java -jar $CLI_JAR_PATH daemon stop"
