# Quick Reference: Cross-Cutting Concerns

## Use Custom Exceptions

### ResourceNotFoundException (404)
```java
// Throw when a resource doesn't exist
throw new ResourceNotFoundException("Session", sessionId);

// Response: 404 with message "Session not found: 'xyz'"
```

### ValidationException (400)
```java
// Throw for business validation failures
throw new ValidationException(
    "Configuration is invalid",
    List.of("Project path must exist", "Session ID required")
);

// Response: 400 with field errors
```

## Access Correlation IDs

Correlation IDs are automatically set in response headers.

### In Logging
```java
@Slf4j
public class MyService {
    public Mono<Result> process(String id) {
        String correlationId = UUID.randomUUID().toString(); // or extract from header
        log.debug("Processing: {} [{}]", id, correlationId);
        // ...
    }
}
```

### In Upstream Calls
```java
webClient.get()
    .uri("/upstream")
    .header("X-Correlation-ID", correlationId)
    .retrieve()
    .bodyToMono(String.class);
```

## Exception Handler Automatic Behavior

| Exception | Status | Message |
|-----------|--------|---------|
| `ResourceNotFoundException` | 404 | "Resource not found: 'id'" |
| `ValidationException` | 400 | Custom message + field errors |
| `ConstraintViolationException` | 400 | "Constraint violation" |
| `WebExchangeBindException` | 400 | "Validation failed" |
| Generic `Exception` | 500 | "An unexpected error occurred" |

## Response Format

All responses follow `ApiResponse`:

```json
{
  "timestamp": "2026-06-14T10:30:45.123456",
  "status": 404,
  "message": "Session not found: 'abc123'",
  "data": null,
  "error": "Not Found",
  "path": "/api/sessions/abc123",
  "errors": [
    {"field": "sessionId", "message": "Session not found: 'abc123'"}
  ]
}
```

## Filter Behavior

### RequestLoggingFilter
- **Logs:** HTTP method, path, status code, duration
- **Level:** DEBUG
- **Example:** `GET /api/sessions/xyz → 200 (42ms)`

### RequestTracingFilter
- **Auto-generates:** `X-Request-ID` (unique per request)
- **Preserves:** `X-Correlation-ID` (for request chains)
- **Forwards:** Both headers in response

## File Locations

| File | Purpose |
|------|---------|
| `web/.../exception/GlobalExceptionHandler.java` | Central exception handler |
| `web/.../exception/ResourceNotFoundException.java` | 404 exceptions |
| `web/.../exception/ValidationException.java` | Validation errors |
| `web/.../filter/RequestLoggingFilter.java` | Request/response logging |
| `web/.../filter/RequestTracingFilter.java` | Correlation ID injection |
| `docs/CROSS_CUTTING_CONCERNS.md` | Full documentation |

## Testing

```bash
# Run all exception handler tests
./gradlew :web:test --tests "com.infenia.yukta.exception.*"

# Run specific test
./gradlew :web:test --tests "GlobalExceptionHandlerTest"
```

## Common Patterns

### In Controllers
```java
@GetMapping("/{sessionId}")
public Mono<ResponseEntity<ApiResponse<SessionDetails>>> getSession(
    @PathVariable String sessionId) {
    return sessionService.getSession(sessionId)
        .switchIfEmpty(
            Mono.error(new ResourceNotFoundException("Session", sessionId)));
}
```

### In Services
```java
public Mono<Config> validateAndLoad(ConfigRequest request) {
    if (request.projectPath() == null) {
        return Mono.error(
            new ValidationException(
                "Invalid configuration",
                List.of("Project path is required")));
    }
    return loadConfig(request);
}
```

### With Correlation ID
```java
public Mono<Result> executeWithTracing(String id, String correlationId) {
    return Mono.defer(() -> {
        log.debug("Starting: {} [{}]", id, correlationId);
        return doWork(id)
            .doOnSuccess(r -> log.debug("Complete: {} [{}]", id, correlationId));
    });
}
```

## Key Takeaways

✅ Throw `ResourceNotFoundException` for 404s
✅ Throw `ValidationException` for validation errors
✅ Use correlation IDs for distributed tracing
✅ All responses automatically formatted with `ApiResponse`
✅ Filters handle logging and tracing transparently
✅ Error handlers catch all exceptions and return structured responses

For full documentation, see `docs/CROSS_CUTTING_CONCERNS.md`.
