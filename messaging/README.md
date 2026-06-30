# Yukta Messaging

This module provides the core messaging abstractions used throughout Yukta.

## Key Abstractions

- **Message**: The fundamental unit of data transfer. It contains a payload and technical headers (e.g., `traceId`, `timestamp`, `priority`).
- **Messaging Gateway**: Interfaces for sending and receiving messages asynchronously.
- **Message Store**: SPI for persisting messages during workflow execution.

## Features

- **Immutability**: `Message` objects are designed to be immutable, supporting "wither" methods for creating modified copies.
- **Reactive Design**: All interfaces are designed to work seamlessly with Project Reactor (`Mono`, `Flux`).
- **Standardized Headers**: Consistent propagation of technical metadata across the DAG.
