<div style="text-align: center;">
  <img src="ui/src/main/resources/static/images/favicon.svg" alt="Yukta" width="120" height="120" style="border-radius: 64px;" />
  <h1 style="margin-top: 12px;">Yukta</h1>
  <p><strong>Orchestrate multi-step workflows with confidence.</strong> Define once, execute anywhere, integrate with AI.</p>
</div>

[![License: Apache 2.0](https://img.shields.io/badge/license-Apache%202.0-blue?style=flat-square)](LICENSE)
[![Java 25+](https://img.shields.io/badge/java-25%2B-orange?style=flat-square)](https://www.oracle.com/java/)
[![Spring Boot 4.0](https://img.shields.io/badge/spring--boot-4.0-6db33f?style=flat-square)](https://spring.io/projects/spring-boot)
[![Gradle 9.0](https://img.shields.io/badge/gradle-9.0-02303a?style=flat-square)](https://gradle.org/)
[![MCP Native](https://img.shields.io/badge/MCP-native-blueviolet?style=flat-square)](https://modelcontextprotocol.io/)
[![GraalVM](https://img.shields.io/badge/GraalVM-native%20image-red?style=flat-square)](https://www.graalvm.org/)

---

## The Problem

You juggle workflows every day:
- **Developers**: "Run tests after formatting, but skip if linting fails"
- **DevOps Engineers**: "Build → Test → Deploy, with rollback on error"
- **Data Pipelines**: "Validate data → Transform → Load, retrying on timeout"
- **AI Teams**: "Run check, get feedback, auto-fix, rerun"

Today, you solve this with **shell scripts** 🐚. Tomorrow, you're debugging them at 3am.

```bash
#!/bin/bash
./gradlew format || exit 1
./gradlew lint || { echo "lint failed"; slack-notify; exit 1; }
./gradlew test || deploy-rollback
```

**The Problem**: Scripts are hard-coded, fragile, error-prone, and don't integrate with modern AI tools.

---

## The Solution: Yukta

**Yukta** is a **workflow orchestrator** that handles the complexity so you don't have to.

### Think of it like this:

| Approach | Problem | Yukta Solution |
|----------|---------|---|
| 🐚 Shell Scripts | Hard to read, error-prone, no reusability | 📋 JSON DAG (visual, reusable, validated) |
| 🔗 Manual Orchestration | "Did that step run? What failed?" | 🎯 Real-time status via REST API + SSE |
| ⚙️ Custom Code | Takes weeks to build, hard to maintain | 🔌 16 built-in plugins, no code needed |
| 🤖 AI Integration | Webhooks, polling, manual retries | 🧠 Native MCP support, instant feedback |
| 🚨 Error Handling | `if [ $? -eq 0 ]` (good luck) | 🌳 Branching, retries, conditional routing |

---

## How It Works (The Pitch)

### For **Developers**:
```json
{
  "sessionId": "my-project",
  "workflows": {
    "quality-check": {
      "description": "Format → Lint → Test",
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

**That's it.** No scripts. No `if` statements. No manual error handling. Just describe the flow, and Yukta runs it.

### For **DevOps Engineers**:
```bash
# Trigger the workflow
curl -X POST http://localhost:8080/api/workflow/trigger \
  -d '{ "sessionId": "my-project", "workflowId": "quality-check", "payload": {} }'

# Returns instantly with execution ID
{ "executionId": "550e8400-e29b-41d4-a716-446655440000" }

# Stream live status (Server-Sent Events)
curl http://localhost:8080/api/workflow/my-project/status/.../stream

# Real-time output:
# { "status": "RUNNING", "tasks": [{ "nodeId": "format", "status": "SUCCESS" }, ...] }
# { "status": "RUNNING", "tasks": [{ "nodeId": "lint", "status": "SUCCESS" }, ...] }
# { "status": "SUCCESS", "tasks": [...] }
```

**No polling. No webhooks. Just REST + SSE.**

### For **AI Teams**:
```python
# Claude Code integration (built-in)
# When file is saved:
#   SessionStart → init_session.py → POST /api/config
#   When task finishes:
#   Stop → trigger_workflow.py → POST /api/workflow/trigger + monitor

# Claude gets instant feedback:
# ✅ Tests passed → commit changes
# ❌ Tests failed → Claude reads error, auto-fixes, retries
```

**The AI doesn't wait. It doesn't poll. It gets instant feedback and self-corrects.**

---

## Three Layers (Architects Will Love This)

```
┌─────────────────────────────────────┐
│ 3. Transport Layer                  │
│ REST API (16 endpoints) + MCP       │
│ for humans and AI agents            │
└──────────────┬──────────────────────┘
               ↓
┌─────────────────────────────────────┐
│ 2. Orchestration Layer              │
│ DAG validator, compiler, executor   │
│ Reactive (Mono/Flux), <100ms loops  │
└──────────────┬──────────────────────┘
               ↓
┌─────────────────────────────────────┐
│ 1. Plugin Layer                     │
│ 16 composable, extensible plugins   │
│ (TriggerPlugin, ProcessorPlugin,    │
│  TerminalPlugin)                    │
└─────────────────────────────────────┘
```

**Each layer is independent.** Swap plugins. Change execution. Integrate anywhere.

---

## What Makes Yukta Different

### 🎯 **Define Once, Execute Anywhere**
- Local dev machine, CI/CD pipeline, AI agent callback
- Same JSON DAG, different execution contexts

### ⚡ **Sub-100ms Feedback**
- Reactive non-blocking (Project Reactor)
- Virtual threads for I/O
- Real-time SSE streaming

### 🧠 **Built for AI**
- MCP-native integration (Claude Code, Claude API, others)
- Instant feedback (no polling)
- Self-correcting workflows

### 🔌 **16 Plugins, Infinite Combinations**
- PROCESS_EXECUTOR (run any OS command)
- BRANCH (conditional routing via SpEL)
- MAPPER, FILTER, SPLITTER, AGGREGATOR, LOOP, SUB_WORKFLOW, and more
- Write custom plugins in minutes

### 🛡️ **Enterprise-Ready**
- Input validation at all boundaries (blocks injection, path traversal)
- Session isolation
- Structured logging (JSONL, searchable)
- Local-first (no cloud vendor lock-in)

### 📊 **Observable**
- Real-time ControlBus (heartbeats, statistics)
- Live DAG visualization (web UI)
- Session-aware logging

---

## Real-World Examples

### Example 1: CI/CD Pipeline
```json
Build → Test → [Decision] → Deploy (if success) OR Alert (if fail)
```
Define once. Use in GitHub Actions, GitLab CI, Jenkins, or anywhere.

### Example 2: Data Processing
```json
Ingest → Validate → [Filter] → Transform → Load → Archive
         ↓ (on error)
         → Log Error → Send Alert
```
Visual, auditable, testable. No spaghetti code.

### Example 3: AI-Driven Development
```json
Format → Lint → Test
  ↑            ↓
  ← (if fail) ←
Claude Code auto-fixes and retries
```
AI never waits. Claude gets instant feedback and self-corrects.

---

## 🚀 Quick Start (5 Minutes)

### **1. Start Yukta**
```bash
./gradlew bootRun
```

### **2. Define Your Workflow (JSON)**
```bash
curl -X POST http://localhost:8080/api/config \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "my-session",
    "description": "My first workflow",
    "initiator": "me",
    "projectPath": "/path/to/project",
    "workflows": {
      "test": {
        "description": "Run tests",
        "nodes": [
          { "nodeId": "test", "type": "PROCESS_EXECUTOR", "config": { "command": ["./gradlew", "test"] } }
        ],
        "edges": []
      }
    }
  }'
```

### **3. Run It**
```bash
curl -X POST http://localhost:8080/api/workflow/trigger \
  -d '{ "sessionId": "my-session", "workflowId": "test", "payload": {} }'

# Get executionId from response
```

### **4. Check Status**
```bash
curl http://localhost:8080/api/workflow/my-session/status/{executionId}
```

**Done.** No shell scripts. No error handling boilerplate. Just results.

---

## Why Yukta?

| Feature | Without Yukta | With Yukta |
|---------|---|---|
| **Define a workflow** | Write shell script (error-prone) | JSON DAG (visual, validated) |
| **Run it** | `./script.sh` in one place | REST API, MCP, web UI, anywhere |
| **See what's running** | Tail logs | Real-time status + DAG visualization |
| **Debug failures** | Grep logs, pray | Structured logs, execution traces |
| **Integrate with AI** | Webhooks + polling | Native MCP, instant feedback |
| **Reuse it** | Copy-paste (bad) | Store once, run everywhere |
| **Handle errors** | `if [ $? -eq 0 ]` | Conditional branching, retries |

---

## Built for Today's Challenges

- **Microservices**: Orchestrate across multiple services
- **AI-Driven Dev**: Claude Code + Yukta = auto-fixing workflows
- **DevOps**: CI/CD without YAML hell
- **Data Pipelines**: ETL with visibility and control
- **Hybrid Workflows**: Mix humans and AI, real-time feedback

---

## Tech Stack (For the Curious)

- **Java 25** (reactive language features)
- **Spring Boot 4.0.2** (enterprise-grade stability)
- **Project Reactor** (non-blocking, sub-100ms latency)
- **GraalVM** (native image, 50MB executable)
- **JTE + Alpine.js** (interactive web UI)
- **MCP** (AI integration)

---

## What's Stable Today

✅ **DAG Orchestration** — Nodes, edges, branching, sub-workflows
✅ **REST API** — All core endpoints battle-tested
✅ **MCP Integration** — Claude Code fully supported
✅ **16 Built-in Plugins** — PROCESS_EXECUTOR, BRANCH, MAPPER, FILTER, SPLITTER, AGGREGATOR, and more
✅ **Session Management** — File or in-memory persistence
✅ **Live UI** — DAG visualization, execution history, control console

---

## Getting Started

- **New here?** Start with **[Getting Started Guide](docs/getting-started.md)** (5 min tutorial)
- **Want the details?** Read **[Architecture & Design](docs/architecture.md)** (deep dive)
- **Need API docs?** Check **[API Reference](docs/api-reference.md)** (all endpoints)
- **Building plugins?** See **[Plugin Development](docs/plugin-development.md)** (extend Yukta)
- **Contributing?** Read **[Development Setup](docs/development-setup.md)** (help us improve)

---

## Community & Support

- **Questions?** [Open an issue](https://github.com/infenia/yukta/issues/new?labels=question) or [start a discussion](https://github.com/infenia/yukta/discussions)
- **Found a bug?** [Report it](https://github.com/infenia/yukta/issues)
- **Want to contribute?** We'd love it! See [Contributing Guide](CONTRIBUTING.md)

### ⭐ Like Yukta? Consider:
- **Starring** the repo ❤️
- **Sharing** your workflow (Twitter, DEV.to, Reddit)
- **Contributing** (code, docs, plugins, feedback)
- **Sponsoring** development

---

## Security & License

- **Local-first**: No cloud vendor, no data exfil
- **Apache 2.0 licensed**: Transparent, auditable, enterprise-friendly
- **Input validated**: Blocks injection, path traversal, malicious input
- [Full Security Policy](SECURITY.md)

---

## Made With ❤️

**Yukta** is developed by **[Infenia Private Limited](https://infenia.com)**.

- **Creator**: Arun Cherthedath Somanathan ([arun@infenia.com](mailto:arun@infenia.com))

**Built on the belief that workflows should be simple, observable, and AI-friendly.**

---

## Fun Fact

*Yukta* (युक्त) is Sanskrit for **"united" or "joined"**—the essence of orchestration: bringing together disparate tools, systems, and agents into a cohesive, harmonious workflow. 🔗

---

**Ready to orchestrate?** ⚡

```bash
./gradlew bootRun
```

Then visit **[http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)** to start defining workflows.

Happy orchestrating! 🚀
