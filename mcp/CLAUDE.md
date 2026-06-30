# Yukta MCP - AI Guidance

Model Context Protocol (MCP) implementation.

## Build & Run Commands
- Run quality checks: `./gradlew :mcp:check`

## Key Locations
- Tool Providers: `src/main/java/com/infenia/yukta/mcp/tool/`
- MCP Server Config: `src/main/java/com/infenia/yukta/mcp/server/`

## Patterns
- **Tool-First Design**: Each Yukta capability (Trigger, Status, Logs) is exposed as an MCP tool.
- **Transports**: Handles both StdIO (local AI) and SSE (remote AI) transports.
- **Context Awareness**: MCP tools should return helpful, structured data for the AI agent to reason about.
