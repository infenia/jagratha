# Yukta Plugin API

This module defines the SPI (Service Provider Interface) and common models used by all Yukta plugins.

## 🏗️ Architecture

The API is designed around the **Enterprise Integration Patterns (EIP)**.

### Key Interfaces

- **WorkflowPlugin**: Base interface for all plugins.
- **TriggerPlugin**: For event sources.
- **ProcessorPlugin**: For message transformation and routing.
- **TerminalPlugin**: For message consumption and output.
- **Message**: The fundamental data carrier.

## 📦 Key Classes

- `com.infenia.yukta.plugin.message.DefaultMessage`: Standard implementation of the `Message` interface.
- `com.infenia.yukta.plugin.exception.WorkflowExecutionException`: Base exception for plugin-related errors.
