# Controller Review - Spring Boot 4 + WebFlux Best Practices

**Project:** Yukta  
**Spring Boot Version:** 4.0.3  
**Framework:** Reactive WebFlux  
**Review Date:** June 14, 2026

---

## Executive Summary

The codebase contains 5 REST controllers that are generally well-structured with good patterns for reactive Spring Boot. However, there are **several areas for improvement** to fully align with Spring Boot 4 and WebFlux best practices. This review identifies issues and provides actionable recommendations.

**Overall Assessment:** ✅ **Good Foundation** | ⚠️ **Improvement Opportunities**

---

## Controllers Reviewed

1. ✅ `WorkflowController.java` (217 lines)
2. ✅ `ControlBusController.java` (194 lines)
3. ✅ `LogManagementController.java` (127 lines)
4. ✅ `PluginController.java` (157 lines)
5. ✅ `SessionConfigController.java` (237 lines)

---

## Issues & Improvements

### 1. 🔴 **Critical Issue: Mono.fromCallable() Blocking Operations**

**Severity:** HIGH  
**Affected Controllers:** All 5 controllers  
**Issue:**

Using `Mono.fromCallable()` with blocking operations defeats the purpose of reactive programming:

```java
// ❌ BAD PATTERN - Multiple occurrences
Mono.fromCallable(() -> controlBus.getCurrentProgress(executionId))
    .flatMap(Mono::justOrEmpty)

Mono.fromCallable(() -> controlBus.getActiveNodes(workflowId))
    .map(nodes -> ApiResponse.success(200, "Active nodes retrieved", nodes))

Mono.fromCallable(registry::listPlugins)
    .map(...)

Mono.fromCallable(() -> sessionService.getSessionConfig(sessionId))
    .map(...)
```

**Why it's problematic:**
- These services should return `Mono<T>` or `Flux<T>`, not blocking `T`
- `fromCallable()` wraps blocking code, preventing real async benefits
- Could cause thread pool exhaustion

**Recommendation:**
Ensure all service methods return reactive types (`Mono<T>`, `Flux<T>`) instead of blocking types.

**Example Fix:**
```java
// ✅ GOOD PATTERN
return controlBus.getCurrentProgress(executionId)  // Returns Mono<WorkflowProgress>
    .map(progress -> ApiResponse.success(200, "Status retrieved", progress))
```

---

### 2. 🔴 **Critical Issue: Inconsistent SSE Response Wrapping**

**Severity:** HIGH  
**Affected Controllers:** `ControlBusController`, `WorkflowController`  
**Issue:**

Inconsistent handling of Server-Sent Events responses:

```java
// ControlBusController.java - INCONSISTENT
@GetMapping(value = "/control/executions/{executionId}/progress/stream", ...)
public Flux<WorkflowProgress> streamProgress(@PathVariable final String executionId) {
    // Returns raw Flux<WorkflowProgress> - NO ServerSentEvent wrapping
    return controlBus.watchExecution(executionId);
}

@GetMapping(value = "/control/executions/{executionId}/logs/stream", ...)
public Flux<String> streamLogs(@PathVariable final String executionId) {
    // Returns raw Flux<String> - NO ServerSentEvent wrapping
    return controlBus.watchLogs(executionId);
}

// WorkflowController.java - CONSISTENT (CORRECT PATTERN)
public Flux<ServerSentEvent<WorkflowProgress>> streamWorkflowStatus(...) {
    return controlBus
        .watchExecution(executionId)
        .map(progress -> ServerSentEvent.<WorkflowProgress>builder().data(progress).build());
}
```

**Why it's problematic:**
- Clients expect proper SSE event structure with metadata (id, retry, event type)
- Raw data may not work with standard SSE clients
- WorkflowController has the correct pattern; ControlBusController doesn't

**Recommendation:**
Standardize all SSE endpoints to wrap responses in `ServerSentEvent`:

```java
// ✅ CORRECT
public Flux<ServerSentEvent<WorkflowProgress>> streamProgress(@PathVariable final String executionId) {
    return controlBus.watchExecution(executionId)
        .map(progress -> ServerSentEvent.<WorkflowProgress>builder()
            .data(progress)
            .build());
}

public Flux<ServerSentEvent<String>> streamLogs(@PathVariable final String executionId) {
    return controlBus.watchLogs(executionId)
        .map(log -> ServerSentEvent.<String>builder()
            .data(log)
            .build());
}
```

---

### 3. ⚠️ **Error Handling: Missing ExceptionHandler Advice**

**Severity:** MEDIUM  
**Affected:** All controllers  
**Issue:**

Controllers handle errors locally with manual `onErrorResume()` blocks, creating code duplication:

```java
// ❌ REPEATED across multiple endpoints
.onErrorResume(e -> {
    final String path = exchange.getRequest().getPath().value();
    final List<ApiResponse.FieldError> errors = List.of(...);
    return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)...);
})
```

**Recommendation:**
Create a `@RestControllerAdvice` for centralized error handling (Spring Boot 4 best practice):

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    @ExceptionHandler(ResourceNotFoundException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleNotFound(
            ResourceNotFoundException ex, ServerWebExchange exchange) {
        log.atWarn().log("Resource not found: {}", ex.getMessage());
        return Mono.fromSupplier(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(404, "Not Found", ex.getMessage(), 
                exchange.getRequest().getPath().value(), List.of())));
    }
    
    @ExceptionHandler(ValidationException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleValidation(
            ValidationException ex, ServerWebExchange exchange) {
        // Handle validation errors...
    }
}
```

**Benefits:**
- ✅ Eliminates code duplication
- ✅ Consistent error responses
- ✅ Easier maintenance
- ✅ Centralized logging

---

### 4. ⚠️ **Missing Response Status Annotations**

**Severity:** MEDIUM  
**Affected:** `WorkflowController`, `SessionConfigController`  
**Issue:**

Some endpoints don't declare response status codes in annotations:

```java
// ❌ INCOMPLETE - WorkflowController.triggerWorkflow()
@PostMapping("/workflow/trigger")
@Operation(summary = "Trigger a workflow", ...)
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", ...)
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", ...)
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", ...)
// Missing: 500, 503, etc.
public Mono<ResponseEntity<ApiResponse<TriggerResponse>>> triggerWorkflow(...) {
    // Actual code may throw exceptions not documented
}
```

**Recommendation:**
Document all possible response codes:

```java
// ✅ COMPLETE
@PostMapping("/workflow/trigger")
@Operation(summary = "Trigger a workflow", ...)
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "Workflow trigger accepted")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid session ID or workflow ID")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Session or workflow not found")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "Service unavailable")
public Mono<ResponseEntity<ApiResponse<TriggerResponse>>> triggerWorkflow(...) {}
```

---

### 5. ⚠️ **Logging Best Practices - Structured Logging**

**Severity:** LOW-MEDIUM  
**Affected:** All controllers  
**Issue:**

Using `log.atInfo().log()` with method entry/exit logs adds noise:

```java
// ⚠️ NOT RECOMMENDED
log.atInfo().log("getActiveNodes reached: workflowId={}", workflowId);
log.atInfo().log("getWorkflowStatus reached: sessionId={}, executionId={}", sessionId, executionId);
```

**Why:**
- Excessive logging reduces performance
- Method entry/exit logs clutter logs
- Better to log at business logic level (service layer)

**Recommendation:**

```java
// ✅ BETTER APPROACH
// Remove method entry logs from controllers
// Controllers should not log request details - let Spring's logging handle it

// Instead, log in service layer for actual operations:
@Service
public class WorkflowService {
    public Mono<WorkflowExecution> validateAndTriggerWorkflow(
            String sessionId, String workflowId, Map<String, Object> payload) {
        log.atInfo()
            .addKeyValue("sessionId", sessionId)
            .addKeyValue("workflowId", workflowId)
            .log("Validating and triggering workflow");
        // ...actual work...
    }
}
```

Use Spring's request logging filter instead:
```yaml
# application.yml
logging:
  level:
    org.springframework.web: DEBUG
  pattern:
      console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
```

---

### 6. ⚠️ **Reactive Chain Optimization**

**Severity:** LOW  
**Affected:** `SessionConfigController.getSessionDetails()`  
**Issue:**

Unnecessary type casting with unsafe cast operations:

```java
// ⚠️ RISKY
@SuppressWarnings("unchecked")
public Mono<ResponseEntity<ApiResponse<SessionDetails>>> getSessionDetails(...) {
    return sessionService
        .getSessionConfig(sessionId)
        .map(config -> {
            final Map<String, Object> workflows =
                (Map<String, Object>) config.getOrDefault("workflows", Map.of());
            // ...
        })
}
```

**Better approach:**
Create a proper model/DTO instead of using raw `Map<String, Object>`:

```java
// ✅ BETTER
public record SessionConfig(
    String sessionId,
    Map<String, WorkflowDefinition> workflows,
    // ... other fields
) {}

// In service:
public Mono<SessionConfig> getSessionConfig(String sessionId) {
    return // ... returns strongly typed object
}

// In controller:
return sessionService.getSessionConfig(sessionId)
    .map(config -> new SessionDetails(sessionId, config.workflows().keySet().stream().toList()))
```

---

### 7. ✅ **Positive: Good Practices Found**

The codebase also demonstrates several good patterns:

✅ **Proper use of `switchIfEmpty()`** for handling optional responses
```java
return controlBus.getProgress(executionId)
    .map(...)
    .switchIfEmpty(Mono.fromSupplier(() -> ...))  // Good!
```

✅ **Validation annotation** on request bodies
```java
@PostMapping("/workflow/trigger")
public Mono<ResponseEntity<...>> triggerWorkflow(
    @Valid @RequestBody final WorkflowTriggerRequest request,  // Good!
    ...
)
```

✅ **Proper use of @RestController and @RequestMapping** for routing

✅ **Comprehensive OpenAPI/Swagger documentation** with @Operation and @ApiResponse

✅ **Immutable request/response objects** (records likely used)

✅ **@RequiredArgsConstructor** for dependency injection (constructor-based, best practice)

---

## Priority Action Items

| Priority | Issue | Controller(s) | Effort | Impact |
|----------|-------|---------------|--------|--------|
| 🔴 HIGH | Blocking `Mono.fromCallable()` | All 5 | Medium | HIGH |
| 🔴 HIGH | Inconsistent SSE wrapping | ControlBusController | Low | MEDIUM |
| 🟡 MEDIUM | Missing global error handler | All 5 | Medium | HIGH |
| 🟡 MEDIUM | Missing response status codes | 3 | Low | LOW |
| 🔵 LOW | Excessive logging | All 5 | Low | LOW |
| 🔵 LOW | Unsafe type casting | SessionConfigController | Medium | LOW |

---

## Recommended Fixes by Controller

### WorkflowController.java
1. **Ensure** `WorkflowService.validateAndTriggerWorkflow()` returns `Mono<WorkflowExecution>`
2. **Remove** method entry logs
3. **Add** 500/503 response codes to @ApiResponse annotations
4. **Consider** wrapping SSE response in `ServerSentEvent` for consistency (currently correct, but keep aligned)

### ControlBusController.java
1. 🔴 **CRITICAL:** Wrap `streamProgress()` and `streamLogs()` responses in `ServerSentEvent`
2. **Ensure** `ControlBusGateway` methods return `Flux<T>` or `Mono<T>`, not blocking types
3. **Remove** all `Mono.fromCallable()` wrappers
4. **Remove** method entry logs

### LogManagementController.java
1. **Ensure** `LogRetrievalService` returns `Mono<List<String>>` and `Mono<String>`, not blocking types
2. **Remove** `Mono.fromCallable()` wrappers
3. **Remove** method entry logs
4. **Add** missing error response annotations

### PluginController.java
1. **Ensure** `WorkflowRegistry` methods return `Mono<T>`, not blocking types
2. **Remove** `Mono.fromCallable()` wrappers
3. **Remove** method entry logs
4. **Consider** extracting error response creation to a shared utility method

### SessionConfigController.java
1. **Replace** `Map<String, Object>` casting with proper `SessionConfig` DTO
2. **Remove** `@SuppressWarnings("unchecked")` by using type-safe approach
3. **Ensure** service methods return `Mono<SessionConfig>`, not blocking types
4. **Remove** method entry logs
5. **Add** error response annotations where missing

---

## Implementation Guide

### Step 1: Create Global Exception Handler
Create `com/infenia/yukta/handler/GlobalExceptionHandler.java`:
```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    // Implement handlers for various exceptions
}
```

### Step 2: Review Service Layer
Audit all service methods to return `Mono<T>` or `Flux<T>`:
- `WorkflowService`
- `ControlBusGateway`
- `LogRetrievalService`
- `WorkflowRegistry`
- `SessionService`

### Step 3: Update Controllers
1. Remove `Mono.fromCallable()` patterns
2. Update SSE endpoints to wrap in `ServerSentEvent`
3. Remove method entry/exit logging
4. Add missing response status annotations

### Step 4: Create DTOs
Replace raw `Map<String, Object>` with proper records:
```java
public record SessionConfig(String sessionId, Map<String, WorkflowDefinition> workflows) {}
```

### Step 5: Testing
- Verify SSE clients receive proper event structure
- Test error scenarios with new exception handler
- Load test to ensure non-blocking behavior
- Verify Swagger/OpenAPI documentation accuracy

---

## Spring Boot 4 + WebFlux Checklist

- [ ] ✅ Using Spring Boot 4.0.3
- [ ] ⚠️ All service methods return `Mono<T>` or `Flux<T>` (need verification)
- [ ] ⚠️ No blocking operations in controllers (found `Mono.fromCallable()` pattern)
- [ ] ⚠️ Global exception handler implemented (missing)
- [ ] ✅ Request validation with @Valid
- [ ] ⚠️ Proper SSE response wrapping (partially inconsistent)
- [ ] ✅ OpenAPI/Swagger documentation
- [ ] ⚠️ Comprehensive response status codes (mostly missing)
- [ ] ✅ Constructor-based dependency injection
- [ ] ⚠️ Structured logging in service layer (currently in controllers)

---

## Additional Resources

- [Spring Boot 4 WebFlux Documentation](https://spring.io/projects/spring-framework#reactive)
- [Reactive Programming Best Practices](https://www.baeldung.com/spring-webflux)
- [Global Error Handling in WebFlux](https://spring.io/blog/2022/05/02/spring-framework-5-3-spring-boot-2-7-spring-security-5-7-spring-data-2022-0-released#spring-webflux-improvements)
- [Server-Sent Events Best Practices](https://www.baeldung.com/spring-mvc-sse-jsp)

---

## Conclusion

The controller layer provides a solid foundation with good structure and documentation. The main improvements needed focus on:

1. **Ensuring full reactivity** by removing blocking `Mono.fromCallable()` patterns
2. **Centralizing error handling** with a global exception handler
3. **Standardizing SSE responses** across all controllers
4. **Improving type safety** by replacing raw Maps with proper DTOs

These changes will bring the codebase fully in line with Spring Boot 4 and WebFlux best practices while improving maintainability and performance.

