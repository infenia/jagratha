# Yukta AI Guidance (Global)

This file provides global guidance for AI agents working on the Yukta project. For module-specific details, refer to the `CLAUDE.md` file within each submodule directory.

## Project Overview

**Yukta** is a high-performance orchestration engine designed for AI-native workflows. It provides a reactive DAG-based execution environment, native MCP support, and a pluggable architecture.

## Global Build & Development Commands

```bash
./gradlew bootRun          # Start the application
./gradlew check            # Run all quality gates (tests + analysis)
./gradlew spotlessApply    # Format code and apply license headers
./gradlew nativeCompile    # Build GraalVM native image
```

## Global Coding Standards

### Reactive by Default
- All orchestration logic MUST use **Project Reactor** (`Mono`, `Flux`).
- Never block the event loop. Use Virtual Threads (Loom) for blocking I/O if necessary.

### Type Safety & Immutability
- Use **Java Records** for data models and configurations.
- Use **Lombok** (`@RequiredArgsConstructor`, `@Slf4j`) to reduce boilerplate in service classes.
- Use `@Valid` and Jakarta Bean Validation for all input boundaries.

### Error Handling
- Use the unified `GlobalExceptionHandler` in the `web` module.
- Define custom exceptions in `plugin-api` or `core` depending on scope.

## Quality Gates

Every commit MUST pass:
1. **Spotless**: Google Java Format + Apache 2.0 headers.
2. **Checkstyle**: Coding style compliance.
3. **PMD & SpotBugs**: Static analysis for bugs and anti-patterns.
4. **JaCoCo**: 100% coverage requirement for core modules (exceptions exist for UI).

## Git & Workflow Conventions

- **Conventional Commits**: `feat:`, `fix:`, `docs:`, `chore:`, etc.
- **Branching**: All changes must go through a Pull Request to `main`.
- **Impact Analysis**: Always run impact analysis using GitNexus tools (if available) before modifying core symbols.

## Module Map

- `boot/`: Application entry point & native config.
- `core/`: DAG engine, variable resolution, & execution logic.
- `plugin-api/`: Interfaces for building custom plugins.
- `messaging/`: Reactive message abstractions.
- `web/`: REST API & SSE streaming.
- `mcp/`: Native AI agent integration.
- `ui/`: Interactive web dashboard.
- `cli/`: Lightweight Go CLI.
- `plugins/`: Built-in plugin implementations.

---
*Refer to submodule `CLAUDE.md` files for deeper technical guidance.*
