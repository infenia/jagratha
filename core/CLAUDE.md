# Core Module CLAUDE.md

## Overview
Core service layer and workflow orchestration engine.

## Build & Development Commands
```bash
# Run tests for core module
./gradlew :core:test

# Run a single test class
./gradlew :core:test --tests com.infenia.yukta.service.WorkflowOrchestratorTest
```

## Key Architecture & Patterns

### Reactive Streams (Core Pattern)
All service logic uses **Project Reactor** (`Mono`, `Flux`).
- Services use `Mono` for single operations, `Flux` for streams.
- Avoid blocking operations.

### Workflow Orchestration
- `com.infenia.yukta.service.WorkflowOrchestrator`: DAG orchestration logic.
- `com.infenia.yukta.service.WorkflowRegistry`: Plugin registration.
- Executes a Directed Acyclic Graph (DAG) of nodes and edges.

### Immutability & Records
Use **Java records** for data models (preferred) or **Lombok** (`@RequiredArgsConstructor`, `@Getter`) for immutable objects.

### Validation
- Custom validators in `com.infenia.yukta.validation`.
- Use Jakarta Bean Validation.

## Key File Locations
- **Services**: `src/main/java/com/infenia/yukta/service/`
- **MCP Tools**: `src/main/java/com/infenia/yukta/mcp/`
- **Validation**: `src/main/java/com/infenia/yukta/validation/`

## Imports
@.claude/rules/coding-standards.md
@.claude/rules/git-workflow.md
