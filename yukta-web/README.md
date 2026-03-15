# Yukta Web

The `yukta-web` module provides the RESTful interface for Yukta. It exposes endpoints for session management, file logging, and workflow triggering.

## 🏗️ Architecture

This module follows a standard Spring Boot Web Layer architecture, utilizing **Spring WebFlux** for non-blocking request handling.

### Key Components

- **AppController**: Main controller for file and session operations.
- **WorkflowController**: Handles workflow-specific triggers.
- **GlobalExceptionHandler**: Provides unified error responses across the API.

## ⚙️ Configuration

Exposed via standard Spring Boot properties. Documentation is automatically generated using **Springdoc OpenAPI**.

- **Swagger UI**: `/swagger-ui.html`
- **OpenAPI Docs**: `/v3/api-docs`

## 📦 Dependencies & Key Classes

### Internal Dependencies
- `:yukta-core`: For business logic execution.

### Key Classes
- `com.infenia.yukta.controller.AppController`: Implements the core REST API.
- `com.infenia.yukta.model.ApiResponse`: Unified response wrapper for all API calls.

## 🚀 Usage

Example of a file logging request handled by this module:

```bash
curl -X POST http://localhost:8080/api/files \
  -H "Content-Type: application/json" \
  -d '{"sessionId": "test", "path": "src/main/java/App.java"}'
```
