# Yukta Core

The `yukta-core` module is the heart of the Yukta server. It handles workflow orchestration, state management, and the execution of message-driven pipelines based on Enterprise Integration Patterns (EIP).

## 🏗️ Architecture

Yukta Core is built around a reactive orchestration engine. It manages the lifecycle of **Sessions**, which contain **Workflows** composed of various **Plugins**.

### Key Components

- **WorkflowOrchestrator**: The central engine that manages the execution of workflows. It uses a `WorkflowRegistry` to find definitions and a `WorkflowGateway` to communicate with plugins.
- **SessionService**: Manages the lifecycle and persistence (currently in-memory) of user sessions.
- **VariableResolver**: Handles dynamic property resolution (e.g., `${env.VAR}`, `${context.key}`) within workflow configurations.
- **AppConfigService**: Manages the global and session-specific configurations.

## ⚙️ Configuration

Yukta Core uses Spring `@ConfigurationProperties` and custom validation. Key configuration areas include:

| Property | Type | Description |
| :--- | :--- | :--- |
| `yukta.async.core-pool-size` | `int` | Core size of the virtual thread pool. |
| `yukta.async.max-pool-size` | `int` | Maximum size of the virtual thread pool. |
| `yukta.storage.base-dir` | `String` | Base directory for session logs and task outputs. |

## 📦 Dependencies & Key Classes

### Internal Dependencies
- `:yukta-plugin-api`: Provides the interfaces for extensibility.

### Key Classes
- `com.infenia.yukta.service.WorkflowOrchestrator`: Entry point for triggering a workflow.
- `com.infenia.yukta.model.WorkflowDefinition`: Represents the DAG structure of a workflow.
- `com.infenia.yukta.util.SpelUtils`: Utility for evaluating Spring Expression Language within processors.

## 🚀 Usage

Typically, `yukta-core` is used by `yukta-web` or `yukta-mcp` to trigger actions.

```java
@Autowired
private WorkflowOrchestrator orchestrator;

public Mono<TriggerResponse> run(String sessionId) {
    return orchestrator.trigger(sessionId, "default-workflow");
}
```
