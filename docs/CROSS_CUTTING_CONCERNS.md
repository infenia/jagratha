# Cross-Cutting Concerns Architecture

This document describes the cross-cutting concerns implemented in the web module using `@ControllerAdvice` and web filters.

## Overview

Cross-cutting concerns are aspects of an application that span multiple layers and functionalities. The Yukta web module implements three key concerns:

1. **Error Handling** — Unified exception handling via `@RestControllerAdvice`
2. **Request Logging** — HTTP request/response logging with timing info
3. **Request Tracing** — Distributed tracing via correlation IDs

## Error Handling (`GlobalExceptionHandler`)

Located in: `web/src/main/java/com/infenia/yukta/exception/GlobalExceptionHandler.java`

### Features

- **Unified Error Response Format** — All errors return structured `ApiResponse` objects with timestamp, status, message, path, and field errors
- **Domain-Specific Exception Handling** — Custom handlers for business logic exceptions
- **Standard Exception Handlers** — Handles Spring validation exceptions, JSON parsing errors, and general exceptions
- **Reactive Support** — Built on Spring WebFlux for non-blocking error handling

### Exception Types

#### `ResourceNotFoundException`
Thrown when a requested resource (Session, Workflow, Plugin) is not found.

```java
throw new ResourceNotFoundException("Session", "session-id-123");
// → Response: "Session not found: 'session-id-123'" (404)
```

#### `ValidationException`
Thrown for business logic validation failures.

```java
throw new ValidationException("Invalid configuration", List.of(
    "Project path must exist",
    "Session ID cannot be null"
));
// → Response: 400 with structured field errors
```

#### Spring Exceptions
Automatically handled:
- `WebExchangeBindException` — Request binding failures (400)
- `ConstraintViolationException` — Bean validation failures (400)
- `UnrecognizedPropertyException` — Unknown JSON fields (400)
- `IllegalArgumentException` — Invalid arguments (400)
- `IllegalStateException` — Invalid state (500)
- Generic `Exception` — Catch-all for unhandled exceptions (500)

### Error Response Format

```json
{
  "timestamp": "2026-06-14T10:30:45",
  "status": 404,
  "message": "Session not found: 'unknown-session'",
  "error": "Not Found",
  "path": "/api/sessions/unknown-session",
  "errors": [
    {
      "field": "sessionId",
      "message": "Session not found: 'unknown-session'"
    }
  ]
}
```

## Request Logging (`RequestLoggingFilter`)

Located in: `web/src/main/java/com/infenia/yukta/filter/RequestLoggingFilter.java`

### Features

- **HTTP Method & Path Logging** — Logs incoming request details
- **Response Status & Timing** — Logs outgoing response with execution time
- **Debug Level** — Information logged at DEBUG level to avoid production verbosity

### Sample Log Output

```
DEBUG: Incoming request - method: GET, path: /api/sessions/session-1
DEBUG: Outgoing response - method: GET, path: /api/sessions/session-1, statusCode: 200, duration: 45ms
```

### Configuration

The filter is auto-registered as a Spring `@Component`. To disable:

```yaml
# application.yaml
spring:
  webflux:
    filter:
      logging:
        enabled: false  # Note: Requires custom property binding
```

## Request Tracing (`RequestTracingFilter`)

Located in: `web/src/main/java/com/infenia/yukta/filter/RequestTracingFilter.java`

### Features

- **Correlation ID Generation** — Auto-generates correlation IDs for request tracking
- **Header Propagation** — Preserves existing correlation IDs across requests
- **Distributed Tracing Support** — Enables request tracing across microservices

### Headers

#### `X-Request-ID`
Unique identifier for this HTTP request. Generated if not provided.

```
X-Request-ID: 550e8400-e29b-41d4-a716-446655440000
```

#### `X-Correlation-ID`
Correlation ID for tracking related requests. Defaults to the request ID if not provided, allowing chains of requests to be traced together.

```
X-Correlation-ID: 550e8400-e29b-41d4-a716-446655440000
```

### Usage in Logging

Integrate correlation IDs into structured logging:

```java
@Slf4j
public class SessionService {
    public Mono<SessionConfig> getSession(String sessionId, String correlationId) {
        return Mono.defer(() -> {
            log.debug("Fetching session: {} (correlation: {})", sessionId, correlationId);
            return loadSession(sessionId);
        });
    }
}
```

### Upstream Service Communication

Forward correlation IDs when calling upstream services:

```java
return webClient
    .get()
    .uri("/api/upstream")
    .header("X-Correlation-ID", correlationId)
    .retrieve()
    .bodyToMono(String.class);
```

## File Organization

```
web/src/main/java/com/infenia/yukta/
├── exception/
│   ├── GlobalExceptionHandler.java      # Central exception handler
│   ├── ResourceNotFoundException.java    # 404 exceptions
│   └── ValidationException.java          # Validation failures
└── filter/
    ├── RequestLoggingFilter.java         # Request/response logging
    └── RequestTracingFilter.java         # Correlation ID injection

web/src/test/java/com/infenia/yukta/
├── exception/
│   ├── GlobalExceptionHandlerTest.java
│   ├── ResourceNotFoundExceptionTest.java
│   └── ValidationExceptionTest.java
```

## Testing

All exception handlers and custom exceptions have comprehensive test coverage in `web/src/test/java/com/infenia/yukta/exception/`.

Run exception handler tests:

```bash
./gradlew :web:test --tests "com.infenia.yukta.exception.*"
```

## Best Practices

### 1. Use Domain-Specific Exceptions
Throw `ResourceNotFoundException` instead of generic exceptions for clearer semantics and better error messages.

```java
// ✅ Good
if (session == null) {
    throw new ResourceNotFoundException("Session", sessionId);
}

// ❌ Avoid
if (session == null) {
    throw new IllegalArgumentException("Session not found");
}
```

### 2. Preserve Correlation IDs
Pass correlation IDs through reactive chains:

```java
return sessionService.getSession(sessionId)
    .doOnNext(session -> 
        log.debug("Session loaded: {} [correlation: {}]", 
            sessionId, correlationId))
    .switchIfEmpty(Mono.error(new ResourceNotFoundException("Session", sessionId)));
```

### 3. Use Structured Logging
Leverage Spring's structured logging for better observability:

```java
log.debug("Operation completed", 
    builder -> builder
        .addKeyValue("sessionId", sessionId)
        .addKeyValue("duration", duration)
        .addKeyValue("correlationId", correlationId));
```

### 4. Custom Exception Handlers
Add handlers for domain-specific exceptions by extending the handler:

```java
@ExceptionHandler(MyCustomException.class)
public ResponseEntity<ApiResponse<Object>> handleCustomException(
    MyCustomException exception, ServerHttpRequest request) {
    // Custom handling logic
}
```

## Migration Guide

### From Core to Web Module

The `GlobalExceptionHandler` has been moved from `core/src/main/java/com/infenia/yukta/exception/` to `web/src/main/java/com/infenia/yukta/exception/` as it is web-layer-specific.

**Old Location:** `core/src/main/java/com/infenia/yukta/exception/GlobalExceptionHandler.java`
**New Location:** `web/src/main/java/com/infenia/yukta/exception/GlobalExceptionHandler.java`

No code changes required—Spring auto-discovers `@RestControllerAdvice` classes.

## See Also

- [API Response Format](../MODEL_API.md) — `ApiResponse` structure
- [Exception Handling Guide](../../CLAUDE.md#exception-handling) — Project conventions
- [Spring WebFlux Documentation](https://docs.spring.io/spring-framework/docs/current/reference/html/web-reactive.html#webflux)
