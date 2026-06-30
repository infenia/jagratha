# Process Executor Plugin

The `PROCESS_EXECUTOR` plugin allows you to run any OS command or script as part of your workflow.

## Configuration

- **command**: (List<String>) The command and its arguments.
- **workingDir**: (String, optional) The directory in which to execute the command.
- **env**: (Map<String, String>, optional) Environment variables for the process.
- **timeout**: (Duration, optional) Maximum time allowed for execution.
- **captureOutput**: (Boolean, default: true) Whether to capture stdout and stderr into the message payload.

## Output

The plugin emits a message with the following payload structure:
- **exitCode**: The process exit code.
- **stdout**: Captured standard output.
- **stderr**: Captured standard error.

## Example

```json
{
  "nodeId": "run-tests",
  "type": "PROCESS_EXECUTOR",
  "config": {
    "command": ["./gradlew", "test"],
    "timeout": "10m"
  }
}
```
