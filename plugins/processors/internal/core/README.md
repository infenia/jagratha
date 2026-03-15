# Processor Plugins

This module contains the core set of EIP-based processors for Yukta workflows.

## 🏗️ Available Processors

- **Branch**: Routes messages based on SpEL predicates.
- **Aggregator**: Combines multiple messages into a single one.
- **Splitter**: Breaks a single message into multiple messages (e.g., from a list).
- **Mapper**: Transforms message payloads using SpEL.
- **Filter**: Drops messages that don't meet a criteria.
- **Enricher**: Adds data to a message from external sources.
- **Content Filter**: Selectively includes or excludes fields from a payload.

## ⚙️ Usage

Processors are configured within the `WorkflowDefinition` in your session configuration.
