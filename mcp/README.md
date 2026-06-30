# Yukta MCP

This module implements the Model Context Protocol (MCP) for Yukta, enabling native integration with AI agents.

## Features

- **MCP Tools**: Exposes Yukta's core functionality (session management, workflow triggering, logs) as tools that AI agents can use.
- **SSE & StdIO Transports**: Supports both remote and local connection methods.
- **Reactive Implementation**: Built on Project Reactor to ensure high performance and low latency.
- **Contextual Feedback**: Provides AI agents with real-time feedback from workflow executions.

## Usage

### Run with StdIO Transport
```bash
./yukta --mcp
```

### Run with SSE Transport
The MCP SSE endpoint is available at `/sse`.
