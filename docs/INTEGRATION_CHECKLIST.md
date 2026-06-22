# Cross-Cutting Concerns Integration Checklist

## ✅ Implementation Complete

This checklist helps you integrate the new cross-cutting concerns into existing controllers and services.

## Phase 1: Review & Understand (5 min)

- [ ] Read `docs/CROSS_CUTTING_CONCERNS.md` for architecture overview
- [ ] Review `docs/QUICK_REFERENCE_CROSS_CUTTING.md` for quick lookup
- [ ] Verify tests pass: `./gradlew :web:test --tests "com.infenia.yukta.exception.*"`

## Phase 2: Update Controllers (Per Controller)

### Replace Manual Error Handling

**Before:**
```java
return sessionService.getSession(sessionId)
    .switchIfEmpty(
        Mono.fromSupplier(() -> {
            final String path = exchange.getRequest().getPath().value();
            final List<ApiResponse.FieldError> errors = List.of(
                new ApiResponse.FieldError("sessionId", "Session not found"));
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(...));
        }));
```

**After:**
```java
return sessionService.getSession(sessionId)
    .switchIfEmpty(Mono.error(
        new ResourceNotFoundException("Session", sessionId)));
```

### Update Not-Found Scenarios

- [ ] `SessionConfigController.getSessionDetails()` — Replace `switchIfEmpty` with `ResourceNotFoundException`
- [ ] `SessionConfigController.getWorkflow()` — Replace `switchIfEmpty` with `ResourceNotFoundException`
- [ ] `PluginController.getPluginDetails()` — Replace `switchIfEmpty` with `ResourceNotFoundException`

### Remove Boilerplate

- [ ] Remove manual `ServerWebExchange` parameters (used only for error path extraction)
- [ ] Remove manual `ApiResponse.error()` construction in `switchIfEmpty`
- [ ] Remove duplicate `buildErrorResponse()` patterns

## Phase 3: Add Semantic Exception Throwing

### In Services

**Validation Errors:**
```java
public Mono<Config> validateConfig(ConfigRequest request) {
    final List<String> violations = new ArrayList<>();
    
    if (request.projectPath() == null) {
        violations.add("Project path is required");
    }
    if (request.sessionId() == null) {
        violations.add("Session ID is required");
    }
    
    if (!violations.isEmpty()) {
        return Mono.error(new ValidationException(
            "Configuration validation failed",
            violations));
    }
    
    return loadConfig(request);
}
```

**Resource Not Found:**
```java
public Mono<Session> getSession(String sessionId) {
    return sessionStore.find(sessionId)
        .switchIfEmpty(Mono.error(
            new ResourceNotFoundException("Session", sessionId)));
}
```

## Phase 4: Add Correlation ID Support

### In Logging

```java
@Slf4j
public class MyService {
    public Mono<Result> process(String id, String correlationId) {
        return Mono.defer(() -> {
            log.debug("Starting process: {} [correlation: {}]", 
                id, correlationId);
            
            return doWork(id)
                .doOnNext(result -> 
                    log.debug("Process complete: {} [correlation: {}]", 
                        id, correlationId));
        });
    }
}
```

### In Upstream Calls

```java
private Mono<ExternalData> callUpstream(String id, String correlationId) {
    return webClient.get()
        .uri("/api/upstream/" + id)
        .header("X-Correlation-ID", correlationId)
        .retrieve()
        .bodyToMono(ExternalData.class);
}
```

## Phase 5: Testing

### Unit Tests

- [ ] Add exception handler tests for new exception types
- [ ] Verify error response format for new handlers
- [ ] Test correlation ID propagation

### Integration Tests

- [ ] Test end-to-end with actual HTTP requests
- [ ] Verify response headers include `X-Request-ID` and `X-Correlation-ID`
- [ ] Check logs include timing and request details

### Example Test Pattern

```java
@Test
void testResourceNotFoundReturns404() {
    webClient.get()
        .uri("/api/sessions/unknown")
        .exchange()
        .expectStatus().isNotFound()
        .expectBody(ApiResponse.class)
        .consumeWith(response -> {
            ApiResponse<?> body = response.getResponseBody();
            assert body != null;
            assert body.status() == 404;
            assert body.message().contains("Session not found");
        });
}

@Test
void testCorrelationIdPropagated() {
    webClient.get()
        .uri("/api/sessions/123")
        .header("X-Correlation-ID", "test-corr-123")
        .exchange()
        .expectHeader().exists("X-Correlation-ID")
        .expectHeader().valueEquals("X-Correlation-ID", "test-corr-123");
}
```

## Phase 6: Documentation

### Code Comments

- [ ] Add JavaDoc for custom exception usage in controllers
- [ ] Document correlation ID usage in services
- [ ] Update README if relevant

### API Documentation

- [ ] Verify Swagger annotations include error response bodies
- [ ] Check that 404 and 400 responses are documented
- [ ] Ensure correlation ID headers are documented

## Phase 7: Verification

### Build & Tests
```bash
# Compile
./gradlew :web:compileJava

# Run all tests
./gradlew :web:test

# Run specific tests
./gradlew :web:test --tests "GlobalExceptionHandlerTest"

# Run all quality checks
./gradlew check
```

- [ ] All tests pass
- [ ] No compilation errors
- [ ] No quality gate violations
- [ ] Coverage thresholds met

### Manual Testing

- [ ] Start the application: `./gradlew bootRun`
- [ ] Call a not-found endpoint and verify 404 response
- [ ] Call an invalid request and verify 400 response
- [ ] Check response headers for correlation IDs
- [ ] Review logs for request/response timing

## Phase 8: Deployment

- [ ] Code reviewed and approved
- [ ] All tests passing on CI
- [ ] Documentation updated
- [ ] Release notes prepared
- [ ] Monitoring/alerting configured for error rates

## Quick Conversion Reference

### Exception Patterns

| Scenario | Before | After |
|----------|--------|-------|
| Resource not found | `ResponseEntity.notFound()` | `throw new ResourceNotFoundException(...)` |
| Invalid input | Manual validation + `ResponseEntity.badRequest()` | `throw new ValidationException(...)` |
| Generic error | Custom error response | Caught by `GlobalExceptionHandler` |

### Controller Simplification Example

**Before:** ~30 lines of error handling per endpoint
**After:** Single-line exception throw

```java
// Before: 30+ lines with manual ResponseEntity construction
// After: 1 line
.switchIfEmpty(Mono.error(
    new ResourceNotFoundException("Session", sessionId)));
```

## Support & Questions

For implementation questions, refer to:
- **Full Docs:** `docs/CROSS_CUTTING_CONCERNS.md`
- **Quick Ref:** `docs/QUICK_REFERENCE_CROSS_CUTTING.md`
- **Code:** `web/src/main/java/com/infenia/yukta/exception/`
- **Tests:** `web/src/test/java/com/infenia/yukta/exception/`

## Success Metrics

After complete integration:

✅ All controllers use semantic exceptions (ResourceNotFoundException, ValidationException)
✅ No manual `ApiResponse.error()` construction in controllers
✅ Request/response logging shows timing information
✅ Correlation IDs flow through request chains
✅ All error responses follow unified format
✅ 100% of 404/400/500 scenarios have tests
✅ Documentation complete and up-to-date

---

**Status:** Ready for integration
**Estimated Integration Time:** 2-3 hours per module
**Recommendation:** Integrate one controller at a time, test after each
