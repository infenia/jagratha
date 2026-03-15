# Getting Started

This guide will walk you through setting up Yukta and running your first quality check.

## 📋 Prerequisites

- **Java 21/25**
- **Gradle 9.0+**
- An AI Agent or Client (e.g., **Claude Code**) to interact with the server.

---

## 🏃 5-Minute Quick Start

### 1. Start the Yukta Server
Clone the repository and run:
```bash
./gradlew bootRun
```
The server starts on `http://localhost:8080`.

### 2. Configure a Session
Tell Yukta about your project and the tasks you want it to run:
```bash
curl -X POST http://localhost:8080/api/config \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "demo",
    "projectPath": "/path/to/my-project",
    "tasks": ["test", "spotlessCheck"]
  }'
```

### 3. Log a File Change
Notify Yukta when you modify a file:
```bash
curl -X POST http://localhost:8080/api/files \
  -H "Content-Type: application/json" \
  -d '{ "sessionId": "demo", "path": "src/main/java/App.java" }'
```

### 4. Trigger the Quality Gate
Run the checks and get feedback:
```bash
curl -X POST http://localhost:8080/api/workflow/trigger \
  -H "Content-Type: application/json" \
  -d '{ "sessionId": "demo" }'
```

---

## 🤖 AI Agent Integration

Yukta is built to be used by AI agents. For detailed instructions on how to connect Claude or other agents, see **[MCP & ACP Integration](integrations.md)**.

## 🛠️ Advanced Setup

For information on custom plugins and advanced workflow configurations, refer to the **[Architecture](architecture.md)** and **[Plugin Development](plugin-development.md)** guides.
