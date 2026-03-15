# Yukta MCP

The `yukta-mcp` module implements the **Model Context Protocol (MCP)** server for Yukta. It allows AI agents to interact with Yukta as a native tool provider.

## 🏗️ Architecture

It uses the Spring AI MCP implementation to expose Yukta's core functionality as AI-callable tools.

### Key Components

- **AppMcpTools**: Defines the tools available to the MCP client.

## ⚙️ Configuration

The MCP server is typically started by the AI agent as a subprocess of `yukta-boot`.

## 📦 Dependencies & Key Classes

### Internal Dependencies
- `:yukta-core`: To delegate tool calls to the orchestration engine.

### Key Classes
- `com.infenia.yukta.mcp.AppMcpTools`: Bridge between MCP tool calls and `WorkflowService`.

## 🚀 Usage

When Yukta is added as an MCP server, the AI agent can call:

- `configure_session(sessionId, projectPath, ...)`
- `log_file_change(sessionId, path)`
- `trigger_workflow(sessionId)`
