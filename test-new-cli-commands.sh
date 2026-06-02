#!/bin/bash
# Test script for new CLI commands: session-apply and trigger

set -e

echo "=========================================="
echo "Testing new CLI commands"
echo "=========================================="
echo ""

# Test 1: Help for new commands
echo "Test 1: Display help for session-apply"
echo "Command: ./gradlew :boot:bootRun --args='control session-apply --help'"
echo ""

echo "Test 2: Display help for trigger"
echo "Command: ./gradlew :boot:bootRun --args='control trigger --help'"
echo ""

# Test 3: Test with sample JSON
echo "Test 3: Sample session-apply with JSON file"
cat > /tmp/test-session.json << 'EOF'
{
  "sessionId": "cli-test-session",
  "description": "Test session for CLI",
  "initiator": "cli-test",
  "projectPath": "/test/project",
  "workflows": {
    "test-workflow": {
      "workflowId": "test-workflow",
      "description": "A test workflow",
      "nodes": [
        {"nodeId": "start", "type": "trigger", "config": {}}
      ],
      "edges": []
    }
  }
}
EOF

echo "Created /tmp/test-session.json"
echo ""
echo "To test session-apply with a file:"
echo "  ./gradlew :boot:bootRun --args='control session-apply /tmp/test-session.json'"
echo ""
echo "To test session-apply with inline JSON:"
echo "  ./gradlew :boot:bootRun --args='control session-apply \"$(cat /tmp/test-session.json)\"'"
echo ""

echo "Test 4: Test workflow trigger"
echo "Once a session is applied, trigger with:"
echo "  ./gradlew :boot:bootRun --args='control trigger cli-test-session test-workflow \"{}\"'"
echo ""

echo "=========================================="
echo "Implementation Summary"
echo "=========================================="
echo "✓ SessionApplyCommand.java - Apply session config from JSON file or string"
echo "✓ WorkflowTriggerCommand.java - Trigger workflow execution"
echo "✓ Updated CliRunner.java - Registered both commands"
echo "✓ Updated CliRunnerTest.java - Added mocks for new commands"
echo ""
echo "Files created:"
echo "  - cli/src/main/java/com/infenia/yukta/cli/command/control/SessionApplyCommand.java"
echo "  - cli/src/main/java/com/infenia/yukta/cli/command/control/WorkflowTriggerCommand.java"
echo ""
echo "Files modified:"
echo "  - cli/src/main/java/com/infenia/yukta/cli/CliRunner.java"
echo "  - cli/src/test/java/com/infenia/yukta/cli/CliRunnerTest.java"
echo ""
