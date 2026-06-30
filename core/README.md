# Yukta Core

The `core` module is the heart of Yukta, responsible for workflow orchestration, DAG management, and reactive execution.

## Key Components

- **Workflow Orchestrator**: The central engine that manages the lifecycle of workflow executions.
- **DAG Engine**: Handles the compilation, validation, and traversal of the Directed Acyclic Graph (DAG) that defines a workflow.
- **Variable Resolver**: Evaluates expressions and resolves variables from various sources (secrets, env, system, context).
- **Execution Manager**: Tracks the state of active executions and manages persistence.
- **Control Bus**: Facilitates communication between the orchestrator and individual nodes (heartbeats, commands).

## Key Features

- **Non-blocking Execution**: Built on Project Reactor for high concurrency.
- **Type Safety**: Strong typing for message payloads and configurations.
- **Isolation**: Workflows are isolated within sessions to prevent interference.
- **Observability**: Built-in hooks for metrics, logging, and status tracking.

## Architecture
This module implements the middle layer of the 3-layer architecture. It depends on `plugin-api` and `messaging` but is independent of the transport layer (`web`, `mcp`).
