<p align="center">
  <img src="yukta-ui/src/main/resources/static/images/favicon.svg" width="120" alt="Yukta Logo" style="border-radius: 50%;">
</p>

<h1 align="center">Yukta</h1>

<p align="center">
  <strong>The Intelligent Workflow Engine Bridging AI Agents and High-Performance Engineering</strong>
</p>

<p align="center">
  <a href="https://github.com/infenia/yukta/actions/workflows/ci.yml">
    <img src="https://github.com/infenia/yukta/actions/workflows/ci.yml/badge.svg" alt="Java CI with Gradle">
  </a>
  <img src="https://img.shields.io/badge/Java-25-orange.svg" alt="Java 25">
  <img src="https://img.shields.io/badge/Code%20Style-Google-blue.svg" alt="Code Style">
  <img src="https://img.shields.io/badge/Coverage-80%25-green.svg" alt="Code Coverage">
  <img src="https://img.shields.io/badge/PRs-Welcome-brightgreen.svg" alt="PRs Welcome">
  <img src="https://img.shields.io/badge/License-Apache%202.0-blue.svg" alt="License">
  <img src="https://img.shields.io/badge/Security-Snyk%20Passed-blueviolet.svg" alt="Security">
</p>

<p align="center">
  <a href="#-key-features">Features</a> •
  <a href="#-why-yukta">Why Yukta?</a> •
  <a href="#-core-concepts">Core Concepts</a> •
  <a href="#-quick-start">Quick Start</a> •
  <a href="#-documentation">Docs</a> •
  <a href="CONTRIBUTING.md">Contributing</a>
</p>

---

**Yukta** is a high-performance, reactive **DAG (Directed Acyclic Graph) Workflow Orchestrator**. 

At its core, Yukta is designed to manage complex task sequences with precision and speed. While it serves as a powerful bridge for AI agents, it is a multi-utility engine capable of orchestrating everything from AI agent tool-use to enterprise CI/CD pipelines and generic automated workflows.

## 🔌 Seamless Integration

Yukta is built to live at the center of your ecosystem:
- **MCP Native**: Direct, deep integration with AI agents (like Claude) via the **Model Context Protocol**, allowing agents to trigger and monitor workflows autonomously.
- **REST API First**: A robust API layer that enables integration with any external system, dashboard, or custom toolchain.

## 🧩 The Plugin Ecosystem

Workflows in Yukta are composed of **Nodes**, which are powered by a modular plugin system:
- **Core Plugins**: Essential nodes for basic DAG operations, routing, and data transformation.
- **Domain-Specific Plugins**: Specialized modules that allow Yukta to:
    - Invoke **Build Tools** (Gradle, Maven, etc.).
    - Coordinate with **AI Agents** and LLMs.
    - Execute **Shell** or **Python** scripts.
    - Interact with cloud services and local file systems.

---

## 🚀 Key Features

- **Multi-Utility Orchestration**: One engine for AI agent orchestration, CI/CD, data processing, and more.
- **Reactive Performance**: Powered by **Spring WebFlux** and **Project Loom** for non-blocking, massively scalable execution.
- **Visual DAG Control**: Define, monitor, and debug complex task dependencies through an intuitive interactive dashboard.
- **EIP Powered**: Employs proven Enterprise Integration Patterns (Splitter, Aggregator, Router) for robust data flow.
- **Extensible Architecture**: Easily build and swap plugins to extend Yukta's capabilities to any domain.

---

## 🤔 Why Yukta?

### 1. The Bridge to AI
Yukta provides the "hands" and "eyes" for AI agents. By exposing complex workflows through MCP, agents can perform sophisticated multi-step tasks with structured feedback.

### 2. Unified Workflow Language
Stop juggling multiple orchestrators. Use Yukta for your local dev quality gates, your remote CI/CD pipelines, and your AI-driven automation.

### 3. Modern & Future-Proof
Built on **Java 25**, Yukta leverages the latest JVM features to deliver high throughput with minimal overhead.

---

## 🧩 Core Concepts

The name **Yukta** (Sanskrit: युक्त) literally means "joined" or "skillfully yoked"—reflecting how we harmoniously connect disparate tools, agents, and processes into a single, unified workflow.

- **Message**: The fundamental unit of data (Payload + Technical Headers).
- **Exchange**: A container for a message as it flows through the DAG.
- **Processor**: A functional node (Plugin) that transforms, routes, or filters messages.
- **Trigger**: The entry point of a workflow (API, Timer, File Watcher, etc.).
- **Terminal**: The final destination for workflow results.

---

## 🏗️ Project Structure

- **[yukta-boot](yukta-boot/)**: Main application entry point and configuration.
- **[yukta-web](yukta-web/)**: Reactive REST API layer.
- **[yukta-mcp](yukta-mcp/)**: Native MCP server implementation.
- **[yukta-core](yukta-core/)**: Core orchestration and EIP engine.
- **[yukta-ui](yukta-ui/)**: Interactive JTE-based dashboard and DAG visualizer.
- **[plugins](plugins/)**: The heart of Yukta's extensibility (Build tools, AI, Shell, etc.).

---

## 🤝 Contributing

We welcome contributions! Whether you're building a new domain plugin or improving the core engine, check out our **[Contributing Guide](CONTRIBUTING.md)** to get started.

---

## 🛡️ License & Attribution

Maintained by **[Infenia Private Limited](https://infenia.com)**.
Released under the **Apache License, Version 2.0**.
