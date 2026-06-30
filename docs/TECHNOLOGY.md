# Technology Stack

Yukta is built on a modern, high-performance stack designed for low-latency orchestration and native AI integration.

## Core Language & Runtime

- **Java 25**: We leverage the latest LTS features, including:
    - **Virtual Threads (Project Loom)**: For handling blocking I/O (like Gradle builds) without exhausting thread pools.
    - **Structured Concurrency**: To manage groups of related tasks as a single unit of work.
    - **Scoped Values**: For efficient sharing of immutable data across threads.
    - **Pattern Matching**: For cleaner, type-safe message processing.

- **GraalVM Native Image**: Yukta is compiled ahead-of-time (AOT) into a static executable.
    - **Startup**: < 100ms.
    - **Memory Footprint**: ~50MB vs 250MB+ for a standard JVM app.
    - **Zero Dependencies**: Run on any compatible OS without installing a JDK.

## Frameworks & Libraries

- **Spring Boot 4.0.3**: The backbone of the application, providing dependency injection, configuration management, and robust REST capabilities.
- **Project Reactor (WebFlux)**: Native reactive support for non-blocking orchestration.
    - Used for the core execution loop and SSE streaming.
    - Ensures high concurrency with minimal resource usage.
- **Jackson 3 (`tools.jackson`)**: We use the latest major version of Jackson for JSON processing.
    - Migration to the new namespace `tools.jackson` ensures future-proofing.
    - Optimized for high-performance serialization/deserialization.
- **JTE (Java Template Engine)**: Used for the web UI.
    - Compiled to Java bytecode for maximum performance.
    - Type-safe templates.
- **Alpine.js & Tailwind CSS**: For a lightweight, interactive frontend without the overhead of a heavy SPA framework.

## Integration Protocols

- **Model Context Protocol (MCP)**: Native support for AI agent integration.
    - Allows agents like Claude to "see" and "use" Yukta tools directly.
    - Supports both SSE and StdIO transports.
- **REST & Server-Sent Events (SSE)**: Standard interfaces for human and system interaction.
    - SSE provides real-time, low-latency updates for workflow execution progress.

## Rationale: WebFlux vs. Virtual Threads

Yukta uses a hybrid approach:
1. **Orchestration (WebFlux)**: The core engine uses Project Reactor to manage the DAG and event propagation. This is ideal for managing thousands of concurrent "active" workflows that spend most of their time waiting for events.
2. **Blocking Operations (Loom)**: When a plugin needs to perform a blocking operation (e.g., executing a shell command via `PROCESS_EXECUTOR`), it runs on a Virtual Thread. This prevents blocking the reactive event loop while maintaining a simple, imperative programming model for plugin developers.
