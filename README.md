<p align="center">
  <img src="yukta-ui/src/main/resources/static/images/favicon.svg" width="120" alt="Yukta Logo">
</p>

<h1 align="center">Yukta 🛡️</h1>

<p align="center">
  <strong>The High-Performance DAG Workflow Orchestrator Optimized for AI</strong>
</p>

<p align="center">
  <a href="#-key-features">Features</a> •
  <a href="#-why-yukta">Why Yukta?</a> •
  <a href="#-core-concepts">Core Concepts</a> •
  <a href="#-quick-start">Quick Start</a> •
  <a href="#-documentation">Docs</a>
</p>

---

**Yukta** (Sanskrit for *Vigilance*) is a modern, reactive server designed to orchestrate complex task sequences using **Directed Acyclic Graphs (DAG)** and **Enterprise Integration Patterns (EIP)**.

While it excels as a "vigilance server" for AI-driven development—enforcing quality gates and providing structured feedback to LLMs—its plugin-based architecture makes it a powerful engine for **any** automated workflow.

## 🚀 Key Features

- **AI-Native Orchestration**: Built specifically to bridge the gap between AI agents (like Claude) and local development tools.
- **Reactive & High-Performance**: Powered by **Spring WebFlux** and **Project Loom (Virtual Threads)** for non-blocking, scalable execution.
- **DAG-Based Workflows**: Define complex task dependencies and conditional execution paths using a flexible DAG structure.
- **EIP Powered**: Leverage proven Enterprise Integration Patterns (Splitter, Aggregator, Router, etc.) to handle data flow.
- **MCP & ACP Native**: Native support for **Model Context Protocol** and **Agent Client Protocol**, allowing AI to "see" and "interact" with your system.
- **Extensible Plugin System**: Easily add support for new build tools, AI models, or custom processors.

---

## 🤔 Why Yukta?

### 1. Built for the AI Era
Traditional CI/CD tools are designed for humans. Yukta is designed for **AI agents**. It provides session-aware, JSONL-formatted logs and structured feedback that LLMs can parse and act upon autonomously.

### 2. Flexible & Extensible
Yukta isn't just for code quality. By swapping or adding plugins, you can use it for:
- Data processing pipelines.
- Automated system monitoring.
- Multi-step AI agent task coordination.

### 3. Modern Tech Stack
- **Java 25**: Leveraging the latest JVM performance and features.
- **WebFlux**: Handles thousands of concurrent sessions with minimal resource footprint.
- **GraalVM**: Native image support for near-instant startup.

---

## 🧩 Core Concepts

Yukta's architecture is inspired by Enterprise Integration Patterns (EIP):

- **Message**: The fundamental unit of data (Payload + Technical Headers).
- **Exchange**: A container for a message as it flows through the system.
- **Processor**: A functional node in the DAG that transforms, routes, or filters messages (e.g., *Branch*, *Mapper*, *Filter*).
- **Trigger**: The starting point of a workflow (e.g., *API Call*, *Timed Event*).
- **Terminal**: The final destination for workflow results (e.g., *Console*, *Webhook*).

---

## 🚦 Quick Start

### 1. Spin up the server
```bash
./gradlew bootRun
```
The server will be live at `http://localhost:8080`.

### 2. Configure your first session
Initialize Yukta for your project:
```bash
curl -X POST http://localhost:8080/api/config \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "my-session",
    "projectPath": "/path/to/project",
    "tasks": ["test", "checkstyleMain"]
  }'
```

### 3. Trigger the workflow
```bash
curl -X POST http://localhost:8080/api/workflow/trigger \
  -H "Content-Type: application/json" \
  -d '{ "sessionId": "my-session" }'
```

Access the **Dashboard** at [http://localhost:8080](http://localhost:8080) to see your DAG in action!

---

## 🏗️ Project Structure

- **[yukta-boot](yukta-boot/)**: Main application entry point.
- **[yukta-web](yukta-web/)**: REST API layer.
- **[yukta-mcp](yukta-mcp/)**: Native MCP server implementation.
- **[yukta-core](yukta-core/)**: Core orchestration and EIP engine.
- **[yukta-ui](yukta-ui/)**: Interactive DAG dashboard.
- **[plugins](plugins/)**: The heart of Yukta's extensibility (Triggers, Processors, Terminals).

---

## 📖 Documentation

- **[Getting Started Guide](docs/getting-started.md)**
- **[Architecture & Design Deep Dive](docs/architecture.md)**
- **[Technology Stack & Rationale](docs/TECHNOLOGY.md)**
- **[AI Integration (MCP/ACP)](docs/integrations.md)**
- **[API Reference](docs/api-reference.md)**

---

## 🛡️ Security & Attribution

Maintained by **[Infenia Private Limited](https://infenia.com)**.
Licensed under the **Apache License, Version 2.0**.
