# Integrations

Yukta is designed to be highly integrative, providing native support for AI agents and standard protocols for system-to-system communication.

## Model Context Protocol (MCP)

Yukta is an MCP-native server. This allows AI agents (like Claude Desktop or Claude Code) to interact with Yukta as if it were a local tool.

### Capabilities Exposed via MCP
- **Session Management**: Create, list, and update sessions.
- **Workflow Orchestration**: Trigger workflows and monitor their status.
- **Observability**: Retrieve execution logs and plugin details.
- **Control**: Interact with the Control Bus.

### Transport Modes
1. **StdIO**: Used when Yukta is started by an AI agent as a subprocess (e.g., `yukta --mcp`).
2. **SSE (Server-Sent Events)**: Used for remote connection via the `/sse` endpoint.

## REST API & SSE

For traditional integrations, Yukta provides a comprehensive REST API.

- **Synchronous Operations**: Configuration management and status checks.
- **Asynchronous Operations**: Workflow triggering (returns an `executionId` immediately).
- **Real-time Streaming**: SSE endpoints for live progress updates and log streaming.

## Agent Context Protocol (ACP)

(Future/In-Progress) Yukta is working towards full ACP compatibility to further enhance its "AI-native" capabilities, allowing for even deeper integration with autonomous agentic workflows.

## CI/CD Integration

Yukta fits perfectly into CI/CD pipelines as the "inner orchestrator".

- **GitHub Actions / GitLab CI**: Use the Yukta native executable or Go CLI to trigger complex multi-step workflows.
- **Local Consistency**: Run the exact same workflow locally that runs in your CI pipeline, ensuring "works on my machine" translates to "works in CI".

## Custom Integrations

Since Yukta is built on Spring Boot and Project Reactor, it is easy to add new integration points:
- **Custom Triggers**: Write a plugin to trigger workflows from Kafka, RabbitMQ, or SQS.
- **Custom Terminals**: Send workflow results to Slack, Microsoft Teams, or an external database.
