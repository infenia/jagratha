# Yukta Project CLAUDE.md

This is the global repository guide for **Yukta**.

## Project Overview

**Yukta** is a high-performance server that enforces code quality gates for AI-driven development. It hosts a Model Context Protocol (MCP) server, integrates with AI agents, and provides a reactive DAG-based workflow engine for orchestration.

## Repository Roadmap & Layout

This is a multi-module Gradle monorepo. Launch Claude from the specific package folder for efficiency.

- **`boot`**: Spring Boot application entry point and GraalVM native config.
- **`build-logic`**: Gradle build conventions (Java, Quality, JaCoCo).
- **`cli`**: Lightweight Go-based CLI for remote server interaction.
- **`core`**: Core services, DAG orchestration engine, and MCP tools.
- **`mcp`**: MCP server implementation.
- **`messaging`**: Shared messaging abstractions.
- **`plugin-api`**: Abstraction interfaces for Yukta plugins.
- **`plugins`**: Implementations of Processors, Triggers, and Terminals.
- **`ui`**: JTE templates and Tailwind CSS frontend.
- **`web`**: REST controllers and web-layer mapping.

## Global Commands

```bash
# Run all quality checks across all modules
./gradlew check

# Format code (must do before commit)
./gradlew spotlessApply

# Clean build the entire project
./gradlew clean build
```

## Testing Strategy
- Unit tests use JUnit 5 + Mockito (for reactive: `reactor.test`).
- Use `StepVerifier` for testing reactive streams.
- Quality gates (Checkstyle, PMD, SpotBugs) are enforced via `build-logic`.
- Minimum coverage thresholds are module-specific.

### Test Skill
Load the below skill for test generation and test improvement.
- **Name**: tests
- **Description**: Use for generating unit tests (Java, JUnit 5, Mockito, AssertJ).

## GitNexus — Code Intelligence
This project is indexed by GitNexus. Use GitNexus MCP tools for impact analysis and navigation.
- **Impact Analysis**: Run `impact` before editing any symbol.
- **Verification**: Run `detect_changes()` before committing.

## Imports
@.claude/rules/coding-standards.md
@.claude/rules/git-workflow.md
