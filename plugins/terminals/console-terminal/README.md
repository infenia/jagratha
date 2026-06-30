# Console Terminal Plugin

The `CONSOLE_TERMINAL` is a simple sink plugin that prints the received message payload and headers to the system console (stdout).

## Configuration

- **prefix**: (String, optional) A prefix to add to each log message.
- **includeHeaders**: (Boolean, default: false) Whether to also print technical headers.
- **format**: (String, default: "JSON") Output format (e.g., JSON, PRETTY_JSON).

## Use Cases
- Debugging workflows.
- Simple logging of final results.
- Monitoring data flow in real-time during development.
