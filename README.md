<div style="text-align: center;">
  <img src="ui/src/main/resources/static/images/favicon.svg" alt="Yukta" width="120" height="120" style="border-radius: 64px;" />
  <h1 style="margin-top: 12px;">Yukta</h1>
</div>

**Yukta** is a **reactive DAG orchestrator** with a REST API and MCP integration. Define workflows as JSON (nodes + edges), execute them reactively with millisecond-level feedback, and integrate with AI agents.

[![License: Apache 2.0](https://img.shields.io/badge/license-Apache%202.0-blue?style=flat-square)](LICENSE)
[![Java 25+](https://img.shields.io/badge/java-25%2B-orange?style=flat-square)](https://www.oracle.com/java/)
[![Spring Boot 4.0](https://img.shields.io/badge/spring--boot-4.0-6db33f?style=flat-square)](https://spring.io/projects/spring-boot)
[![Gradle 9.0](https://img.shields.io/badge/gradle-9.0-02303a?style=flat-square)](https://gradle.org/)
[![MCP Native](https://img.shields.io/badge/MCP-native-blueviolet?style=flat-square)](https://modelcontextprotocol.io/)
[![GraalVM](https://img.shields.io/badge/GraalVM-native%20image-red?style=flat-square)](https://www.graalvm.org/)

---

## 🎯 Core Problem

You have **multi-step workflows** (build → test → deploy, format → lint → test, validate → transform → load) and you need:

| Requirement | Shell Scripts | Yukta |
|---|---|---|
| **Define execution order** | Hard-coded in script | JSON DAG (nodes + edges) |
| **Conditional branching** (success/error paths) | `if [ $? -eq 0 ]` (error-prone) | BRANCH plugin (SpEL-based routing) |
| **Real-time status visibility** | Logs only | REST API + Server-Sent Events (SSE) |
| **Reusable workflow definitions** | Copy-paste scripts | Sessions + JSON DAG = reproducible |
| **AI agent integration** | Manual polling/webhooks | MCP-native (instant feedback) |
| **Extensibility** (custom process execution, transformations) | Rewrite script | 16 built-in plugins + custom plugins |
| **Error handling & observability** | Grep logs | Structured logs, session context, control bus |

**The advantage**: Define once, execute anywhere (dev machine, CI/CD, AI agent callback).

---

## ⚙️ Core Architecture

Yukta has **three layers**:

```
┌─────────────────────────────────────────────┐
│ Layer 3: REST API + MCP                     │
│ (@web, @mcp modules)                       │
│ - POST /api/config              (sessions) │
│ - POST /api/workflow/trigger    (execute) │
│ - GET  /api/workflow/status     (monitor)  │
│ - MCP: Instant feedback for AI agents      │
└─────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────┐
│ Layer 2: Orchestrator                       │
│ (@core module)                              │
│ - WorkflowOrchestrator         (DAG exec)  │
│ - WorkflowService              (queueing)  │
│ - SessionService               (lifecycle) │
│ - Reactive streams (Mono/Flux)             │
│ - <100ms feedback loop                     │
└─────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────┐
│ Layer 1: Plugins                            │
│ (@plugin-api, @plugins modules)             │
│ - Trigger, Processor, Terminal types       │
│ - PROCESS_EXECUTOR (execute OS commands)   │
│ - BRANCH (conditional routing)             │
│ - MAPPER, FILTER, SPLITTER, AGGREGATOR     │
│ - 16 built-in plugins                      │
└─────────────────────────────────────────────┘
```

### **Layer 1: Plugins** (Building Blocks)

A **plugin** is a reusable component that processes messages (input → output). Three types:

| Type | Purpose | Example |
|------|---------|---------|
| **Trigger** | Start workflow | API trigger (HTTP POST), constant source |
| **Processor** | Transform/route messages | PROCESS_EXECUTOR, BRANCH, MAPPER |
| **Terminal** | End workflow | Console output, send webhook |

**Plugins are composable**: Chain PROCESS_EXECUTOR (run cmd) → MAPPER (extract output) → BRANCH (route).

### **Layer 2: Orchestrator** (DAG Execution Engine)

The **WorkflowOrchestrator** (`@core`) does the heavy lifting:

1. **DAG Parsing**: Reads nodes (plugins) + edges (connections)
2. **Validation**: 8-step validation (plugins exist, entry points, cycles, configs)
3. **Compilation**: Pre-compiles execution pipeline to optimized lambda
4. **Reactive Execution**: Non-blocking via Project Reactor (Mono/Flux)
5. **Session Context**: Propagates data across nodes (session ID, execution ID, payload)
6. **Error Handling**: Configurable error paths (branch to error handler on failure)
7. **Performance**: <100ms feedback loops, handles concurrent workflow runs

**Core classes**:
- `WorkflowOrchestrator`: DAG validation, compilation, execution
- `WorkflowService`: Queues workflow runs per session
- `SessionService`: Manages session lifecycle
- `TaskTrackerService`: Tracks execution state (O(1) lookup), auto-cleanup
- `ControlBusService`: Multi-cast event bus for monitoring

### **Layer 3: REST API + MCP** (@web, @mcp)

Expose the orchestrator as REST endpoints:

| Endpoint | Purpose | Request | Response |
|----------|---------|---------|----------|
| `POST /api/config` | Init/update session | SessionConfigData (workflows + metadata) | 200 OK |
| `POST /api/workflow/trigger` | Run workflow | {sessionId, workflowId, payload} | 202 + {executionId} |
| `GET /api/workflow/{sessionId}/status/{executionId}` | Check status | - | {status, progress, results} |
| `GET /api/workflow/{sessionId}/logs/{executionId}` | Get logs | - | JSONL logs (streamed) |

**MCP Integration**:
- AI agents (Claude Code) can trigger workflows via MCP
- Instant feedback on failures (no polling)
- Session context passed through MCP messages

---

## 🚀 How It Works: Request Flow

### **Example: Format → Lint → Test Workflow**

**1. Define the DAG (JSON)**:
```json
{
  "sessionId": "my-session",
  "projectPath": "/path/to/project",
  "workflows": {
    "quality-check": {
      "description": "Format, lint, test",
      "nodes": [
        { "nodeId": "format", "type": "PROCESS_EXECUTOR", "config": { "command": ["./gradlew", "spotlessCheck"] } },
        { "nodeId": "lint", "type": "PROCESS_EXECUTOR", "config": { "command": ["./gradlew", "checkstyleMain"] } },
        { "nodeId": "test", "type": "PROCESS_EXECUTOR", "config": { "command": ["./gradlew", "test"] } }
      ],
      "edges": [
        { "source": "format", "target": "lint" },
        { "source": "lint", "target": "test" }
      ]
    }
  }
}
```

**2. Send to Yukta (REST)**:
```bash
# Initialize session
curl -X POST http://localhost:8080/api/config \
  -H "Content-Type: application/json" \
  -d '{ ... above JSON ... }'

# Trigger workflow
curl -X POST http://localhost:8080/api/workflow/trigger \
  -H "Content-Type: application/json" \
  -d '{ "sessionId": "my-session", "workflowId": "quality-check", "payload": {} }'

# Returns: { "executionId": "abc-123" }
```

**3. Yukta Executes (Orchestrator)**:
```
Session: my-session | Execution: abc-123
  │
  ├─ Load DAG from session config
  ├─ Topological sort: format → lint → test
  ├─ Start execution
  │
  ├─ [Node: format] PROCESS_EXECUTOR
  │   Command: ./gradlew spotlessCheck
  │   Output: "All files formatted correctly"
  │   Status: SUCCESS
  │
  ├─ [Node: lint] PROCESS_EXECUTOR
  │   Command: ./gradlew checkstyleMain
  │   Output: "No violations found"
  │   Status: SUCCESS
  │
  ├─ [Node: test] PROCESS_EXECUTOR
  │   Command: ./gradlew test
  │   Output: "10 tests passed, 0 failed"
  │   Status: SUCCESS
  │
  └─ Workflow: SUCCESS (all nodes passed)
     Logs: JSONL format, indexed by sessionId + executionId
```

**4. Check Status (REST)**:
```bash
curl -X GET http://localhost:8080/api/workflow/my-session/status/abc-123

# Returns:
# {
#   "executionId": "abc-123",
#   "workflowId": "quality-check",
#   "status": "SUCCESS",
#   "tasks": [
#     { "nodeId": "format", "status": "SUCCESS", "module": "PROCESS_EXECUTOR", "startTime": "...", "endTime": "..." },
#     { "nodeId": "lint", "status": "SUCCESS", "module": "PROCESS_EXECUTOR", "startTime": "...", "endTime": "..." },
#     { "nodeId": "test", "status": "SUCCESS", "module": "PROCESS_EXECUTOR", "startTime": "...", "endTime": "..." }
#   ],
#   "startTime": "...",
#   "endTime": "..."
# }
```

**5. AI Agent Uses MCP (Optional)**:
```
Claude Code:
  Connect to /sse endpoint via MCP STATELESS protocol
  Call trigger_workflow MCP tool

Yukta (MCP):
  Executes workflow
  Streams heartbeats + status (progress updates)
  On failure: Returns structured error message
  Claude Code parses error, auto-fixes, retries
```

---

## 📋 Core Concepts

### **1. Sessions**
A **session** = config + execution context.
- `sessionId`: Unique identifier (e.g., "claude-code-2026-03-20")
- `workflows`: Map of workflow definitions (reusable DAGs)
- `projectPath`: Project root (for relative paths in commands)
- **Lifetime**: Created on first `/api/config`, persists across workflow runs

**Use case**: Claude Code creates session once, then triggers same DAG multiple times (on each file change).

### **2. Workflows**
A **workflow** = DAG (directed acyclic graph) of plugins.
- `description`: Human-readable purpose
- `nodes`: List of plugins (each with type + config)
- `edges`: Connections defining execution order + conditional routing

**Immutable**: Once defined, orchestrator validates and caches it.

### **3. Execution**
An **execution** = single run of a workflow.
- `executionId`: UUID, unique per run
- `status`: RUNNING, SUCCESS, FAILED
- `nodeResults`: Output from each node
- `logs`: JSONL-formatted, queryable by sessionId + executionId

---

## 🔌 Plugin System

Each **plugin** is a Java bean implementing `WorkflowPlugin` (or subtype).

### **Built-in Plugins**

#### **PROCESS_EXECUTOR** (Most Important)
Execute OS commands (gradle, mvn, npm, bash, custom scripts).
```json
{
  "nodeId": "build",
  "type": "PROCESS_EXECUTOR",
  "config": {
    "command": ["./gradlew", "build"],
    "workingDir": "/path/to/project",
    "timeout": 600,
    "env": { "JAVA_OPTS": "-Xmx2g" },
    "streamOutput": true
  }
}
```

#### **BRANCH** (Conditional Routing)
Route to different nodes based on SpEL expressions.
```json
{
  "nodeId": "check-status",
  "type": "BRANCH",
  "config": {
    "mode": "EXPRESSION",
    "selector": "#status == 'FAILED'",
    "cases": {
      "true": "error",
      "false": "success"
    }
  }
}
```

Edges specify which output port to follow:
```json
[
  { "source": "test", "target": "coverage", "sourcePort": "success" },
  { "source": "test", "target": "notify", "sourcePort": "error" }
]
```

#### **MAPPER** (Transform Data)
Transform payload using SpEL.
```json
{
  "nodeId": "extract-errors",
  "type": "MAPPER",
  "config": {
    "expression": "#payload.errors[?#.severity == 'CRITICAL']"
  }
}
```

#### **Other Plugins**
- **FILTER**: Pass/reject based on condition
- **SPLITTER**: Split 1 message into N (iterate)
- **AGGREGATOR**: Combine N messages into 1
- **LOOP_PREDICATE**: Retry until condition
- **SUB_WORKFLOW**: Nest workflows
- **CONSOLE_TERMINAL**: Log output (debugging)

### **Custom Plugins**
Extend the system by implementing `ProcessorPlugin`:
```java
@Component
public class MyPlugin implements ProcessorPlugin {
  @Override public String getType() { return "MY_PLUGIN"; }
  @Override public Mono<Flux<Message<?>>> process(Message<?> input, Map<String, Object> config) {
    // Your logic: transform input → output
    return Mono.just(Flux.just(output));
  }
}
```

---

## 📊 Key Features

| Feature | How It Works |
|---------|---|
| **DAG Orchestration** | Nodes = plugins, edges = execution order. Topological sort ensures dependencies run first. |
| **Reactive Execution** | Non-blocking streams (Project Reactor) for high concurrency, <100ms latency |
| **Session-Centric Design** | Config once, execute many. Sessions carry context (projectPath, workflows) across runs. |
| **REST API** | POST /api/config to define, POST /api/workflow/trigger to execute, GET status |
| **MCP Integration** | AI agents trigger workflows directly, instant feedback (no polling) |
| **Extensible Plugins** | Add custom processors in minutes (just implement ProcessorPlugin) |
| **Error Handling** | Configurable error ports on edges; branch to error handlers |
| **Observability** | JSONL logs per execution, indexed by sessionId + executionId |
| **Conditional Branching** | BRANCH + MAPPER enable complex decision trees without scripting |

---

## 🚀 Quick Start

### **1. Start Yukta**
```bash
./gradlew bootRun
# Server runs on http://localhost:8080
# Swagger UI: http://localhost:8080/swagger-ui.html
```

### **2. Initialize Session**
```bash
curl -X POST http://localhost:8080/api/config \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "demo",
    "description": "Build project",
    "initiator": "me",
    "projectPath": "/path/to/project",
    "workflows": {
      "build": {
        "description": "Build project",
        "nodes": [
          { "nodeId": "compile", "type": "PROCESS_EXECUTOR", "config": { "command": ["./gradlew", "build"] } }
        ],
        "edges": []
      }
    }
  }'
```

### **3. Trigger Workflow**
```bash
curl -X POST http://localhost:8080/api/workflow/trigger \
  -H "Content-Type: application/json" \
  -d '{ "sessionId": "demo", "workflowId": "build", "payload": {} }'

# Returns: { "executionId": "abc-123" }
```

### **4. Check Status**
```bash
curl -X GET http://localhost:8080/api/workflow/demo/status/abc-123
```

---

## 📈 Advanced: Branching Example

```json
{
  "sessionId": "advanced",
  "projectPath": "/home/user/project",
  "workflows": {
    "ci-pipeline": {
      "description": "Build, test, conditionally deploy",
      "nodes": [
        { "nodeId": "build", "type": "PROCESS_EXECUTOR", "config": { "command": ["./gradlew", "build"] } },
        { "nodeId": "test", "type": "PROCESS_EXECUTOR", "config": { "command": ["./gradlew", "test"] } },
        { "nodeId": "decide", "type": "BRANCH", "config": { "selector": "#status == 'SUCCESS'", "mode": "EXPRESSION" } },
        { "nodeId": "deploy", "type": "PROCESS_EXECUTOR", "config": { "command": ["./deploy.sh", "prod"] } },
        { "nodeId": "alert", "type": "CONSOLE_TERMINAL", "config": {} }
      ],
      "edges": [
        { "source": "build", "target": "test" },
        { "source": "test", "target": "decide" },
        { "source": "decide", "target": "deploy", "port": "success" },
        { "source": "decide", "target": "alert", "port": "error" }
      ]
    }
  }
}
```

**Execution**:
- build → test → decide
- If test succeeds: deploy
- If test fails: alert

---

## 📚 Documentation

- **[Getting Started](docs/getting-started.md)** — 5-minute tutorial
- **[Architecture & Design](docs/architecture.md)** — Deep dive into orchestrator
- **[API Reference](docs/api-reference.md)** — Swagger endpoint details
- **[Plugin Development](docs/plugin-development.md)** — Build custom plugins
- **[Development Setup](docs/development-setup.md)** — Contribute to Yukta

---

## 🛠️ Tech Stack

- **Java 25** (reactive language features)
- **Spring Boot 4.0.2** (WebFlux for non-blocking I/O)
- **Project Reactor** (Mono/Flux for reactive streams)
- **Gradle 9.0** (multi-module build)
- **GraalVM** (native image; 50MB executable)

---

## 📊 Known Limitations & Roadmap

| Feature | Status | Notes |
|---------|--------|-------|
| **DAG Orchestration** | ✅ Stable | Full nodes, edges, branching, sub-workflows |
| **REST API** | ✅ Stable | All core endpoints stable |
| **MCP Integration** | ✅ Stable | AI agents (Claude Code) fully supported |
| **PROCESS_EXECUTOR Plugin** | ✅ Stable | Executes any OS command with timeout, env, streaming |
| **Conditional Branching** | ✅ Stable | BRANCH + MAPPER for complex logic |
| **Session Management** | ✅ Stable | File or in-memory persistence |
| **Parallel Execution** | 🔄 Beta | Parallel nodes in DAG (next release) |
| **Custom Dashboard UI** | ✅ Stable | Web dashboard with live DAG visualization, history, control console |
| **Database Connectors** | 📅 Planned (v0.3) | Direct DB integration plugins |
| **Kubernetes Operator** | 📅 Planned (v0.3) | Deploy Yukta on K8s |

---

## 🤝 Contributing

We welcome contributions!

- **[Contributing Guide](CONTRIBUTING.md)** — Issues, PRs, coding standards
- **[Code of Conduct](CODE_OF_CONDUCT.md)** — Community values
- **[Good First Issues](https://github.com/infenia/yukta/labels/good%20first%20issue)** — Start here

**Ideas for contributions:**
- New plugins (Slack notification, PagerDuty, database connectors)
- Documentation & tutorials
- Testing (integration tests, plugin tests)
- Performance optimizations

---

## 🛡️ Security

- ✅ Local-first (no cloud storage)
- ✅ Apache 2.0 license (transparent, auditable)
- ✅ Sandboxed execution (v0.2 feature)
- ✅ Process isolation via OS-level controls

[Security Policy](SECURITY.md)

---

## 📄 License

Apache License 2.0 — see [LICENSE](LICENSE)

---

## 🙏 Acknowledgments & Call-to-Action

Yukta is developed and maintained by **[Infenia Private Limited](https://infenia.com)**.

- **Creator**: Arun Cherthedath Somanathan ([arun@infenia.com](mailto:arun@infenia.com))

### ⭐ If Yukta helps you, **[star the repo ❤️](https://github.com/infenia/yukta)** and consider:

- **Sharing** your workflow (Twitter, Reddit, DEV.to, Hacker News)
- **Contributing** (issues, PRs, plugins, documentation)
- **Sponsoring** (support development)
- **Reporting bugs** (GitHub Issues)

---

## 📞 Questions?

- **GitHub Issues**: [Ask a question](https://github.com/infenia/yukta/issues/new?labels=question)
- **Discussions**: [Yukta Community](https://github.com/infenia/yukta/discussions)
- **Email**: arun@infenia.com

---

## 🎵 Fun Fact

*Yukta* (युक्त) is a Sanskrit word meaning **"united" or "joined"**—reflecting the philosophy of orchestrating disparate tools, systems, and agents into a cohesive workflow. 🔗

---

Happy orchestrating! 🚀
