# Yukta Core - AI Guidance

The core orchestration engine for Yukta.

## Build & Run Commands
- Run tests: `./gradlew :core:test`
- Run quality checks: `./gradlew :core:check`
- Single test: `./gradlew :core:test --tests com.infenia.yukta.service.WorkflowOrchestratorTest`

## Key Locations
- Orchestrator: `src/main/java/com/infenia/yukta/service/WorkflowOrchestrator.java`
- Registry: `src/main/java/com/infenia/yukta/service/WorkflowRegistry.java`
- Variable Resolution: `src/main/java/com/infenia/yukta/variable/`
- Validation Logic: `src/main/java/com/infenia/yukta/validation/`

## Patterns
- **Reactive DAG Traversal**: Uses Project Reactor (`Flux`, `Mono`) to navigate nodes and edges.
- **Variable Resolution**: Resolve variables from `secrets.`, `env.`, `sys.`, and `context.`.
- **Session Isolation**: All execution state is scoped to a specific `sessionId`.
- **Heartbeats**: The `ControlBus` monitors node health via periodic heartbeats.
