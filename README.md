<div align="center">
  <img src="ui/src/main/resources/static/images/favicon.svg" alt="Yukta" width="120" height="120" style="border-radius: 16px;" />
  <h1 style="margin-top: 12px;">Yukta</h1>
</div>

**Yukta** (Sanskrit for *Vigilance*) is a high-performance quality-gate server that enforces code standards autonomously for AI-driven development.

**License**: Apache 2.0 | **Java**: 25+ | **Spring Boot**: 4.0 | **Gradle**: 9.0

It hosts a **Model Context Protocol (MCP)** server, integrates with AI agents (like Claude Code), validates code changes in real-time, enforces quality gates (Spotless, Checkstyle, PMD, SpotBugs, JaCoCo), and provides structured feedback—enabling **AI agents to self-correct instantly** without breaking your build.

**Use case**: You're using Claude Code (or another AI agent) to generate or modify code, and you want Yukta to enforce your project's quality standards *automatically* before changes are committed. If a check fails, Yukta returns the exact error, and the AI fixes it immediately.

---

## 🎯 Why Yukta?

| Problem | Traditional Approach | Yukta |
|---------|---|---|
| AI generates code that breaks your linter/formatter | You discover it post-commit or in CI/CD (slow feedback loop) | Yukta validates *before* the AI commits—instant feedback, AI self-corrects |
| Manual quality checks slow down AI workflows | CI/CD is the first line of defense (delays of minutes to hours) | Real-time MCP integration with AI agents (milliseconds) |
| Hard to trace which checks failed and why | Scattered logs, unclear error messages | Session-aware JSONL logs, structured feedback to the AI |
| Quality gates are tool-specific (Gradle, Maven, etc.) | Every tool needs custom integration | Plugin-based architecture—extend for any build system |

---

## 🚀 Key Features

- **AI Orchestration**: Seamlessly integrates with AI agents like Claude Code via MCP to validate code autonomously.
- **Quality Gates**: Enforces strict coding standards using Spotless, Checkstyle, PMD, SpotBugs, and JaCoCo.
- **Extensible Plugin System**: Support for any build tool (Gradle, Maven, NPM) and custom workflows via DAG-based orchestration.
- **MCP Native**: Native support for Model Context Protocol—AI agents can run quality checks directly without external scripts.
- **Session-Aware Logging**: JSONL-formatted logs per session for easy consumption and tracing.
- **Reactive & High-Performance**: Built on Spring Boot WebFlux for non-blocking operations—handles concurrent workflows efficiently.
- **Real-Time Feedback**: No waiting for CI/CD—feedback loops in milliseconds.

---

## 📋 Prerequisites

- **Java 25+** (Java 25 recommended; Gradle handles toolchain)
- **Gradle 9.0+** (included: `./gradlew` works out-of-the-box)
- **An AI agent** (Claude Code, or any ACP client) — *optional for testing*
- **Linux/macOS/Windows** (tested on Linux; other OSes supported)

---

## 🚀 Quick Start

### 1. Clone & Start the Server

```bash
git clone https://github.com/infenia/yukta.git
cd yukta
./gradlew bootRun
```

The server starts on `http://localhost:8080` and exposes:
- **REST API**: `http://localhost:8080/api/`
- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **MCP Server**: Native MCP integration for AI agents

### 2. Run Your First Quality Check (curl)

Initialize a session for your project:

```bash
curl -X POST http://localhost:8080/api/config \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "my-session",
    "projectPath": "/path/to/your/project",
    "pluginName": "gradle",
    "pluginConfig": { "gradlePath": "./gradlew" },
    "tasks": ["spotlessCheck", "checkstyleMain"],
    "workflows": []
  }'
```

Log a file modification:

```bash
curl -X POST http://localhost:8080/api/files \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "my-session",
    "path": "src/main/java/com/example/App.java"
  }'
```

Trigger quality checks:

```bash
curl -X POST http://localhost:8080/api/workflow/trigger \
  -H "Content-Type: application/json" \
  -d '{ "sessionId": "my-session" }'
```

**Expected output**:
```json
{
  "sessionId": "my-session",
  "status": "SUCCESS",
  "results": [
    {
      "taskName": "spotlessCheck",
      "status": "SUCCESS",
      "output": "All files are formatted correctly."
    },
    {
      "taskName": "checkstyleMain",
      "status": "SUCCESS",
      "output": "No Checkstyle violations found."
    }
  ]
}
```

### 3. Integrate with Claude Code (Optional)

To use Yukta with Claude Code, see **[Integration with Claude Code](docs/getting-started.md#2-integrating-with-claude-code)** in the Getting Started guide.

---

## 📚 Documentation

- **[Getting Started](docs/getting-started.md)** — Run your first check in 5 minutes; integrate with Claude Code.
- **[Architecture & Design](docs/architecture.md)** — How Yukta works: DAG workflows, plugin system, reactive streams.
- **[API Reference](docs/api-reference.md)** — Full endpoint documentation and request/response schemas.
- **[Plugin Development](docs/plugin-development.md)** — Extend Yukta with custom plugins for your build system.
- **[Development Setup](docs/development-setup.md)** — Set up your dev environment; run tests and quality gates.

---

## 💡 Usage Examples

### Example 1: Gradle Project with Spotless + Checkstyle

```bash
# Configure session
curl -X POST http://localhost:8080/api/config \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "gradle-project",
    "projectPath": "/home/user/my-gradle-app",
    "pluginName": "gradle",
    "pluginConfig": { "gradlePath": "./gradlew" },
    "tasks": ["spotlessCheck", "checkstyleMain", "test"],
    "workflows": []
  }'

# Trigger checks
curl -X POST http://localhost:8080/api/workflow/trigger \
  -H "Content-Type: application/json" \
  -d '{ "sessionId": "gradle-project" }'
```

### Example 2: Custom Workflow with DAG

Define a workflow that runs tests *only if* formatting passes:

```bash
curl -X POST http://localhost:8080/api/config \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "custom-workflow",
    "projectPath": "/home/user/my-project",
    "pluginName": "gradle",
    "pluginConfig": { "gradlePath": "./gradlew" },
    "tasks": [],
    "workflows": [
      {
        "name": "quality-gate",
        "nodes": [
          { "id": "format", "pluginName": "gradle", "task": "spotlessCheck" },
          { "id": "style", "pluginName": "gradle", "task": "checkstyleMain" },
          { "id": "test", "pluginName": "gradle", "task": "test" }
        ],
        "edges": [
          { "from": "format", "to": "style" },
          { "from": "style", "to": "test" }
        ]
      }
    ]
  }'
```

---

## 📊 Known Limitations & Roadmap

| Feature | Status | Notes |
|---------|--------|-------|
| **Gradle Plugin** | ✅ Stable | Full support for Gradle 9.0+ |
| **Maven Plugin** | 📅 Planned (v0.2) | In progress; will support Maven 3.8+ |
| **NPM/Node Plugin** | 📅 Planned (v0.3) | Requested by community |
| **Custom Validators** | 🔄 Beta | Works but API may change |
| **Multi-Project DAGs** | ✅ Stable | Tested with 10+ node workflows |
| **Native Image (GraalVM)** | ✅ Stable | ~50MB executable; see `docs/native-image.md` |
| **Real-time Feedback Loop** | ✅ Stable | <100ms latency for MCP calls |
| **Windows Support** | ✅ Stable | Full support (tested on Windows 11) |
| **Docker Support** | 📅 Planned (v0.2) | Dockerfile will be provided |
| **Cloud Deployment** | 📅 Planned (v0.3) | Kubernetes & AWS Lambda guides |

**What we *won't* do**:
- Force specific code styles (plugins are optional)
- Run untrusted code (sandboxed execution in v0.2)
- Store code in the cloud (all processing is local-first)

---

## 🛠️ Tech Stack

- **Java 25** (LTS alternative Java 21; Gradle handles toolchain)
- **Spring Boot 4.0.2** (WebFlux, Actuator, Spring AI)
- **Gradle 9.0** (multi-module with convention plugins)
- **GraalVM** (native image support; 50MB executable)
- **OpenAPI 3.0** (Swagger UI for API exploration)
- **Project Reactor** (Mono/Flux for reactive streams)

---

## 🤝 Contributing

We welcome contributions! Whether it's code, docs, bug reports, or ideas, we'd love to hear from you.

- **[Contributing Guide](CONTRIBUTING.md)** — Issues, PRs, and coding standards.
- **[Code of Conduct](CODE_OF_CONDUCT.md)** — Our community values.
- **[Good First Issues](https://github.com/infenia/yukta/labels/good%20first%20issue)** — Start here if you're new.

---

## 🛡️ Security

Found a security vulnerability? Please see our **[Security Policy](SECURITY.md)** for responsible disclosure.

---

## 📄 License

This project is licensed under the **Apache License 2.0** — see the [LICENSE](LICENSE) file for details.

**Why Apache 2.0?**
- Permissive: You can use, modify, and distribute Yukta freely (even in commercial projects).
- Patent protection: Includes explicit patent grants.
- Industry standard: Used by Spring, Gradle, Kubernetes, and thousands of open-source projects.

---

## 🙏 Acknowledgments & Call-to-Action

Yukta is developed and maintained by **[Infenia Private Limited](https://infenia.com)**.

- **Creator**: Arun Cherthedath Somanathan ([arun@infenia.com](mailto:arun@infenia.com))
- **Community**: Thanks to every contributor who has improved Yukta.

### ⭐ If Yukta helps you, **[star the repo ❤️](https://github.com/infenia/yukta)** and consider:

- **Sharing** your experience (Twitter, Reddit, DEV.to, Hacker News)
- **Contributing** (issues, PRs, plugin development, documentation)
- **Sponsoring** (buy me a coffee — link in repo sidebar)
- **Reporting bugs** (use GitHub Issues with reproduction steps)

---

## 📞 Questions?

- **GitHub Issues**: [Ask a question](https://github.com/infenia/yukta/issues/new?labels=question)
- **Discussions**: [Yukta Community](https://github.com/infenia/yukta/discussions)
- **Email**: arun@infenia.com

Happy quality-checking! 🚀
