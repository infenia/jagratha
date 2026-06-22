# Controller Improvements - Implementation Plan

**Date:** June 14, 2026  
**Project:** Yukta  
**Spring Boot Version:** 4.0.3 + WebFlux

---

## Overview

This document provides step-by-step guidance to implement the improvements identified in the controller review.

---

## Priority 1: Create Global Exception Handler (CRITICAL)

### File: `web/src/main/java/com/infenia/yukta/handler/GlobalExceptionHandler.java`

**Purpose:** Centralize error handling and eliminate code duplication across controllers.

**Implementation Steps:**

1. Create the exception handler class
2. Define handlers for each exception type
3. Return consistent `ApiResponse` format
4. Update controllers to remove inline error handling

**Key Benefits:**
- Eliminates duplicate error handling code across 5 controllers
- Consistent error responses across the application
- Easier to add new error scenarios
- Single point of logging for errors

**Code Template:**
```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    @ExceptionHandler(ResourceNotFoundException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleResourceNotFound(
            ResourceNotFoundException ex, ServerWebExchange exchange) {
        log.atWarn()
            .addKeyValue("errorMessage", ex.getMessage())
            .addKeyValue("path", exchange.getRequest().getPath().value())
            .log("Resource not found");
        
        return Mono.fromSupplier(() -> 
            ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(
                    404, 
                    "Not Found", 
                    ex.getMessage(),
                    exchange.getRequest().getPath().value(),
                    ex.getFieldErrors()
                ))
        );
    }
    
    @ExceptionHandler(ValidationException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleValidation(
            ValidationException ex, ServerWebExchange exchange) {
        // Implementation...
    }
    
    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleGenericError(
            Exception ex, ServerWebExchange exchange) {
        log.atError()
            .addKeyValue("errorMessage", ex.getMessage())
            .addKeyValue("path", exchange.getRequest().getPath().value())
            .log("Unexpected error", ex);
        
        return Mono.fromSupplier(() ->
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(
                    500,
                    "Internal Server Error",
                    "An unexpected error occurred",
                    exchange.getRequest().getPath().value(),
                    List.of()
                ))
        );
    }
}
```

---

## Priority 2: Fix SSE Response Wrapping in ControlBusController

### Issue
`ControlBusController` has inconsistent SSE response handling compared to `WorkflowController`.

### Changes Required

**Current (❌ WRONG):**
```java
@GetMapping(value = "/control/executions/{executionId}/progress/stream", ...)
public Flux<WorkflowProgress> streamProgress(@PathVariable final String executionId) {
    return controlBus.watchExecution(executionId);
}
```

**Fixed (✅ CORRECT):**
```java
@GetMapping(value = "/control/executions/{executionId}/progress/stream", 
    produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<WorkflowProgress>> streamProgress(
        @PathVariable final String executionId) {
    log.atDebug().addKeyValue("executionId", executionId).log("Streaming execution progress");
    return controlBus.watchExecution(executionId)
        .map(progress -> ServerSentEvent.<WorkflowProgress>builder()
            .id(executionId)
            .event("progress")
            .data(progress)
            .build());
}
```

**Changes Summary:**
1. Add `ServerSentEvent<T>` wrapper
2. Add SSE-specific headers (already has `produces = MediaType.TEXT_EVENT_STREAM_VALUE`)
3. Include event metadata (id, event type)
4. Apply to both `streamProgress()` and `streamLogs()` methods

---

## Priority 3: Replace Mono.fromCallable() with Reactive Types

### Issue
Services are blocking, wrapped in `Mono.fromCallable()`. This defeats reactive benefits.

### Analysis Required

For each service, verify if methods should return `Mono<T>` or `Flux<T>`:

1. **ControlBusGateway Methods:**
   - `getActiveNodes()` → Should return `Mono<List<String>>`
   - `getLastHeartbeat()` → Should return `Mono<Message<?>>`
   - `sendCommand()` → Should return `Mono<Message<?>>`
   - `getCurrentProgress()` → Should return `Mono<WorkflowProgress>`
   - `watchExecution()` → Already returns `Flux<WorkflowProgress>` ✅
   - `watchLogs()` → Already returns `Flux<String>` ✅
   - `getHistory()` → Should return `Mono<List<WorkflowExecutionSummary>>`

2. **WorkflowService Methods:**
   - `validateAndTriggerWorkflow()` → Check return type

3. **LogRetrievalService Methods:**
   - `listLogs()` → Check return type
   - `getLogContent()` → Check return type

4. **WorkflowRegistry Methods:**
   - `listPlugins()` → Should return `Mono<List<Plugin>>`
   - `get()` → Should return `Mono<Optional<Plugin>>` or `Mono<Plugin>`

5. **SessionService Methods:**
   - `getSessionConfig()` → Should return `Mono<SessionConfig>`
   - `getSessionWorkflow()` → Should return `Mono<WorkflowDefinition>`
   - `applyConfig()` → Already returns `Mono<Void>` ✅

### Controller Changes After Service Updates

**Before (❌):**
```java
Mono.fromCallable(() -> controlBus.getActiveNodes(workflowId))
    .map(nodes -> ApiResponse.success(200, "Active nodes retrieved", nodes))
```

**After (✅):**
```java
controlBus.getActiveNodes(workflowId)
    .map(nodes -> ApiResponse.success(200, "Active nodes retrieved", nodes))
```

---

## Priority 4: Remove Method Entry/Exit Logging from Controllers

### Issue
Controllers log every method entry, creating noise and reducing performance.

### Pattern to Remove

```java
// ❌ REMOVE THESE FROM ALL CONTROLLERS
log.atInfo().log("getActiveNodes reached: workflowId={}", workflowId);
log.atInfo().log("getWorkflowStatus reached: sessionId={}, executionId={}", sessionId, executionId);
log.atDebug().log("listPlugins reached");
```

### Best Practice

- Let Spring's request logging handle HTTP-level logging
- Log at **service layer** for business operations
- Configure Spring's logging filter for request/response details

**Application Configuration (application.yml):**
```yaml
logging:
  level:
    org.springframework.web: DEBUG
    com.infenia.yukta.service: DEBUG
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
```

---

## Priority 5: Type-Safe DTOs Instead of Raw Maps

### Issue
`SessionConfigController.getSessionDetails()` uses unsafe casting:

```java
@SuppressWarnings("unchecked")
final Map<String, Object> workflows = 
    (Map<String, Object>) config.getOrDefault("workflows", Map.of());
```

### Solution: Create Proper DTOs

**File: `web/src/main/java/com/infenia/yukta/model/session/SessionConfig.java`**

```java
public record SessionConfig(
    String sessionId,
    String projectPath,
    Map<String, WorkflowDefinition> workflows,
    Map<String, Object> metadata
) {}
```

**Update SessionService:**
```java
// Before (returns Map<String, Object>)
Mono<Map<String, Object>> getSessionConfig(String sessionId)

// After (returns typed object)
Mono<SessionConfig> getSessionConfig(String sessionId)
```

**Update Controller:**
```java
// Before (unsafe casting)
return sessionService.getSessionConfig(sessionId)
    .map(config -> {
        final Map<String, Object> workflows = 
            (Map<String, Object>) config.getOrDefault("workflows", Map.of());
        final List<String> workflowIds = List.copyOf(workflows.keySet());
        return new SessionDetails(sessionId, workflowIds);
    })

// After (type-safe)
return sessionService.getSessionConfig(sessionId)
    .map(config -> new SessionDetails(
        sessionId, 
        config.workflows().keySet().stream().toList()
    ))
```

---

## Priority 6: Add Missing Response Status Codes

### Affected Endpoints

#### 1. WorkflowController.triggerWorkflow()
**Missing:** 500, 503

```java
@io.swagger.v3.oas.annotations.responses.ApiResponse(
    responseCode = "500",
    description = "Internal server error")
@io.swagger.v3.oas.annotations.responses.ApiResponse(
    responseCode = "503",
    description = "Service unavailable")
```

#### 2. All other POST endpoints
**Add:** 400 (validation errors), 500, 503

#### 3. All GET endpoints  
**Add:** 500, 503

---

## Implementation Checklist

### Phase 1: Preparation (1-2 hours)
- [ ] Review all service layer methods for return types
- [ ] Identify which services return blocking types
- [ ] Create list of required service modifications
- [ ] Review API response format expectations

### Phase 2: Service Layer Updates (2-4 hours)
- [ ] Update `ControlBusGateway` to return `Mono<T>`/`Flux<T>`
- [ ] Update `LogRetrievalService` to return `Mono<T>`/`Flux<T>`
- [ ] Update `WorkflowRegistry` to return `Mono<T>`/`Flux<T>`
- [ ] Update `SessionService` to return typed objects
- [ ] Create `SessionConfig` DTO
- [ ] Add unit tests for service changes

### Phase 3: Global Exception Handler (1-2 hours)
- [ ] Create `GlobalExceptionHandler` class
- [ ] Implement handlers for all exception types
- [ ] Add comprehensive logging
- [ ] Test error scenarios

### Phase 4: Controller Updates (3-4 hours)
- [ ] Remove `Mono.fromCallable()` patterns (5 controllers)
- [ ] Fix SSE response wrapping in `ControlBusController`
- [ ] Remove method entry/exit logging (all 5 controllers)
- [ ] Add missing response status codes
- [ ] Remove inline error handling (use global handler)
- [ ] Remove `@SuppressWarnings("unchecked")` annotations
- [ ] Update controllers to use type-safe DTOs

### Phase 5: Testing & Validation (2-3 hours)
- [ ] Unit tests for controllers
- [ ] Integration tests for error scenarios
- [ ] Load testing for non-blocking behavior
- [ ] SSE client testing (verify event structure)
- [ ] Swagger/OpenAPI documentation validation

### Phase 6: Documentation (1 hour)
- [ ] Update architecture documentation
- [ ] Add controller best practices guide
- [ ] Document exception types and handlers

---

## File Modification Summary

### Files to Create
1. `web/src/main/java/com/infenia/yukta/handler/GlobalExceptionHandler.java`
2. `web/src/main/java/com/infenia/yukta/model/session/SessionConfig.java` (if not exists)

### Files to Modify
1. `web/src/main/java/com/infenia/yukta/controller/ControlBusController.java`
2. `web/src/main/java/com/infenia/yukta/controller/WorkflowController.java`
3. `web/src/main/java/com/infenia/yukta/controller/LogManagementController.java`
4. `web/src/main/java/com/infenia/yukta/controller/PluginController.java`
5. `web/src/main/java/com/infenia/yukta/controller/SessionConfigController.java`

### Files to Review (Service Layer)
1. `web/src/main/java/com/infenia/yukta/service/WorkflowService.java`
2. `web/src/main/java/com/infenia/yukta/service/control/gateway/ControlBusGateway.java`
3. `web/src/main/java/com/infenia/yukta/service/LogRetrievalService.java`
4. `web/src/main/java/com/infenia/yukta/service/registry/WorkflowRegistry.java`
5. `web/src/main/java/com/infenia/yukta/service/session/SessionService.java`

---

## Estimated Effort

| Phase | Task | Effort | Complexity |
|-------|------|--------|-----------|
| 1 | Preparation | 1-2 hrs | Low |
| 2 | Service Layer | 2-4 hrs | Medium |
| 3 | Global Handler | 1-2 hrs | Low |
| 4 | Controllers | 3-4 hrs | Medium |
| 5 | Testing | 2-3 hrs | Medium |
| 6 | Documentation | 1 hr | Low |
| **Total** | **Complete Refactor** | **10-16 hrs** | **Medium** |

---

## Rollout Strategy

### Option A: Big Bang (Not Recommended)
- Do all changes at once
- Risk: Breaking changes, harder to debug

### Option B: Phased Rollout (Recommended)
1. **Week 1:** Global exception handler + service layer review
2. **Week 2:** Update service layer return types
3. **Week 3:** Update controllers (one at a time with testing)
4. **Week 4:** Testing, documentation, deploy

### Option C: Incremental (Most Cautious)
- Update one controller at a time
- Test thoroughly between each update
- Estimated timeline: 4-5 weeks

---

## Testing Strategy

### Unit Tests
```java
@SpringBootTest
class ControllerTests {
    @Test
    void testErrorHandling_ResourceNotFound() {
        // Test that 404 errors are properly handled
    }
    
    @Test
    void testSSE_StreamFormat() {
        // Test that SSE events have proper structure
    }
    
    @Test
    void testNonBlocking_Behavior() {
        // Test that no blocking operations occur
    }
}
```

### Integration Tests
```java
@SpringBootTest
class IntegrationTests {
    @Test
    void testWorkflowTrigger_EndToEnd() {
        // Full workflow trigger flow
    }
    
    @Test
    void testGlobalErrorHandler_ConsistentResponses() {
        // Test error response format across endpoints
    }
}
```

### Load Testing
- Verify no thread pool exhaustion
- Verify request throughput improvements
- Monitor reactive stream behavior

---

## Success Criteria

✅ All service methods return `Mono<T>` or `Flux<T>`  
✅ No blocking operations in controller methods  
✅ Global exception handler implemented and used  
✅ All SSE endpoints wrap responses in `ServerSentEvent`  
✅ No method entry/exit logging in controllers  
✅ All response status codes documented in Swagger  
✅ Type-safe DTOs instead of raw `Map<String, Object>`  
✅ All tests passing (unit + integration + load)  
✅ Documentation updated  
✅ Code review approved  

---

## Resources & References

- [Spring WebFlux Best Practices](https://spring.io/projects/spring-webflux)
- [Reactive Streams Specification](https://www.reactive-streams.org/)
- [Spring Framework 6.0 WebFlux Guide](https://docs.spring.io/spring-framework/reference/web/webflux.html)
- [GlobalExceptionHandler in WebFlux](https://spring.io/blog/2022/05/02/spring-framework-5-3-spring-boot-2-7-spring-security-5-7-spring-data-2022-0-released#spring-webflux-improvements)
- [Server-Sent Events with Spring](https://www.baeldung.com/spring-server-sent-events)

