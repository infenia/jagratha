# Yukta Web

The `web` module provides the REST API and streaming endpoints for Yukta.

## Key Controllers

- **SessionConfigController**: Manage session configurations and workflow definitions.
- **WorkflowController**: Trigger workflows and check execution status.
- **LogManagementController**: Retrieve and stream execution logs.
- **ControlBusController**: Access the Control Bus for node health and administration.
- **PluginController**: Discover and get details about registered plugins.

## Features

- **Reactive Endpoints**: Controllers use WebFlux for non-blocking I/O.
- **SSE Support**: Native streaming of progress and logs via Server-Sent Events.
- **Standardized Responses**: Unified response and error formats.
- **Swagger Documentation**: API documentation is automatically generated and available at `/swagger-ui.html`.
