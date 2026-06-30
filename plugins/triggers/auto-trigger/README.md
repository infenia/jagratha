# Auto Trigger Plugin

The `AUTO_TRIGGER` plugin automatically starts a workflow when a session is initialized or when certain system events occur.

## Configuration

- **delay**: (Duration, optional) Delay before starting the workflow.
- **payload**: (Map, optional) The initial payload to use.
- **condition**: (SpEL, optional) Expression that must evaluate to true for the trigger to fire.

## Use Cases
- Initializing environment variables.
- Starting background monitoring tasks.
- Pre-heating caches.
