# Technology Stack & Decisions

This document details the technologies used in Yukta and the rationale behind choosing them. Yukta is built to be a high-performance, scalable, and modern "vigilance server" for AI-driven development.

## 🛠️ Tech Stack Overview

### Core Frameworks & Language
- **Java 25**: The latest LTS-ready version of Java, providing modern features like Pattern Matching, Records, and Virtual Threads (Project Loom).
- **Spring Boot 4.0.2**: The foundation of the application, providing dependency injection, configuration management, and a robust ecosystem.
- **Spring WebFlux**: A reactive, non-blocking web framework used for handling high-concurrency orchestration of asynchronous workflows.
- **Project Loom (Virtual Threads)**: Used to bridge the gap between reactive non-blocking flows and blocking operations (like executing external shell commands for build tools).

### API & Protocols
- **Model Context Protocol (MCP)**: Native support for the MCP server, enabling seamless integration with AI agents like Claude.
- **Agent Client Protocol (ACP)**: Support for tool interactions and lifecycle hooks.
- **OpenAPI 3.0 / Swagger**: Automated API documentation and interactive exploration via Springdoc.

### Build & Infrastructure
- **Gradle 9.0**: A powerful, flexible build system with high performance and support for composite builds.
- **GraalVM**: Support for building native images to reduce startup time and memory footprint.
- **JaCoCo, PMD, Checkstyle, Spotless**: A comprehensive suite of static analysis and quality tools enforced at the build level.

### Frontend (yukta-ui)
- **JTE (Java Templating Engine)**: Fast, type-safe server-side rendering.
- **Tailwind CSS**: A utility-first CSS framework for rapid and consistent UI development.
- **Alpine.js**: A rugged, minimal framework for composing JavaScript behavior in the browser.
- **D3.js & ELK**: Used for rendering complex directed acyclic graphs (DAGs) of workflows.

---

## 🧠 Decision Log & Rationale

### 1. Why Spring WebFlux (Reactive) instead of Spring MVC?
**Context**: Yukta orchestrates multiple asynchronous tasks, including calling AI models (which can have high latency), executing build tools, and handling stream-based logs.
**Decision**: We chose WebFlux to leverage its non-blocking I/O model.
**Rationale**:
- **Efficiency**: A reactive model allows the server to handle a large number of concurrent sessions and workflow executions with a small number of threads.
- **Streaming**: WebFlux natively supports Server-Sent Events (SSE) and streaming responses, which are essential for providing real-time feedback to AI agents and users.
- **Scalability**: It provides better resource utilization during long-running I/O operations (like waiting for an LLM response).

### 2. Why Java 25 & Virtual Threads?
**Context**: While WebFlux is great for I/O, many build tools (like Gradle) are executed as external processes, which is inherently blocking.
**Decision**: Target Java 25 and utilize Project Loom's Virtual Threads.
**Rationale**:
- **Simplified Concurrency**: Virtual threads allow us to write "blocking-style" code for process execution without the overhead of traditional platform threads.
- **Interoperability**: By using a `VirtualThreadScheduler` in Reactor, we can seamlessly mix reactive streams with blocking logic in a way that is both performant and easy to reason about.
- **Future-Proofing**: Targeting the latest Java version ensures access to the most optimized JVM features.

### 3. Why JTE for the UI?
**Context**: We needed a dashboard to monitor workflows but wanted to keep the deployment simple without a complex SPA build process if possible.
**Decision**: Java Templating Engine (JTE).
**Rationale**:
- **Performance**: JTE compiles templates to Java class files, making it incredibly fast—often faster than traditional JSP or Thymeleaf.
- **Type Safety**: Since it's compiled Java, we get compile-time checks for our templates.
- **Low Complexity**: It allows us to build a rich interactive UI (combined with Alpine.js) while keeping the frontend logic close to the backend domain models.

### 4. Why MCP Native?
**Context**: AI agents are the primary users of Yukta.
**Decision**: Native implementation of the Model Context Protocol.
**Rationale**:
- **Standardization**: MCP is becoming the standard for how LLMs interact with local tools and data.
- **Ease of Use**: By being an MCP server, Yukta is "instantly" compatible with any AI client that supports the protocol, requiring zero custom integration code for the user.

### 5. Gradle 9.0 & Build Logic
**Decision**: Use "build-logic" (convention plugins) and Gradle 9.0.
**Rationale**:
- **Maintainability**: Moving build logic into a separate `build-logic` module keeps submodule `build.gradle` files clean and ensures consistent quality gate enforcement across the entire project.
- **Performance**: Gradle 9 offers the latest optimizations for large multi-module projects.

---

## 🏛️ Architecture Philosophy

Yukta follows the **EIP (Enterprise Integration Patterns)** philosophy for its internal workflow engine. Every step in a quality gate check is treated as a "Message" passing through a "Processor" (e.g., Filter, Router, Transformer). This makes the system highly modular and allows users to define complex, conditional workflows via simple configuration.
