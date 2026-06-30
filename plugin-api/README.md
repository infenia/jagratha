# Yukta Plugin API

This module defines the Service Provider Interface (SPI) for extending Yukta with custom plugins.

## Plugin Types

- **WorkflowPlugin**: The base interface for all plugins.
- **TriggerPlugin**: Used to initiate workflow executions (e.g., from an API call or a schedule).
- **ProcessorPlugin**: Used to transform, route, or act upon messages within a workflow (e.g., executing a process, branching logic, mapping data).
- **TerminalPlugin**: Final nodes in a workflow that consume messages.

## Key Interfaces

- **PluginContext**: Provides plugins with access to system resources, variables, and the messaging gateway.
- **PluginConfig**: Base class for plugin configurations, supporting validation and documentation.

## Creating a Plugin

To create a new plugin:
1. Implement the appropriate interface (e.g., `ProcessorPlugin`).
2. Define a configuration record or class.
3. Annotate the implementation for discovery (or register it in the Spring context).
4. (Optional) Provide a UI template for the workflow designer.
