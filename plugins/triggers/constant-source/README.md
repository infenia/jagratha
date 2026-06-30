# Constant Source Trigger Plugin

The `CONSTANT_SOURCE` plugin is a utility trigger that initiates a workflow with a fixed, predefined payload at a regular interval or once on startup.

## Configuration

- **payload**: The static data to be sent.
- **repeatCount**: Number of times to fire (-1 for infinite).
- **interval**: Time between firings.

## Use Cases
- Heartbeat signals.
- Periodic polling.
- Testing and debugging.
