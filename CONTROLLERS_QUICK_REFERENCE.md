# Controller Best Practices Quick Reference

**Project:** Yukta  
**Spring Boot:** 4.0.3 + WebFlux  
**Created:** June 14, 2026

---

## 🚀 Quick Wins (Easy Fixes)

### 1. Remove Method Entry Logging (5 min each controller)
```java
// ❌ DELETE THIS FROM ALL CONTROLLERS
log.atInfo().log("methodName reached: param1={}, param2={}", param1, param2);

// ✅ Let Spring handle request logging via configuration
logging:
  level:
    org.springframework.web: DEBUG
```

**Controllers:** All 5  
**Time:** ~30 minutes total  
**Benefit:** Cleaner code, better performance

---

### 2. Add Missing Response Status Codes (10 min each endpoint)
```java
// ❌ BEFORE - Missing error codes
@GetMapping
@Operation(summary = "Get data")
@ApiResponse(responseCode = "200", description = "Success")
public Mono<ResponseEntity<...>> getData() { }

// ✅ AFTER - Complete documentation
@GetMapping
@Operation(summary = "Get data")
@ApiResponse(responseCode = "200", description = "Success")
@ApiResponse(responseCode = "500", description = "Internal server error")
@ApiResponse(responseCode = "503", description = "Service unavailable")
public Mono<ResponseEntity<...>> getData() { }
```

**Controllers:** All 5 (approximately 20 endpoints)  
**Time:** ~2-3 hours  
**Benefit:** Better Swagger docs, clearer API contract

---

### 3. Fix SSE Response Wrapping in ControlBusController (15 min)
```java
// ❌ BEFORE
@GetMapping(value = "/control/executions/{executionId}/progress/stream", 
    produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<WorkflowProgress> streamProgress(@PathVariable String executionId) {
    return controlBus.watchExecution(executionId);
}

// ✅ AFTER
@GetMapping(value = "/control/executions/{executionId}/progress/stream", 
    produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<WorkflowProgress>> streamProgress(
        @PathVariable String executionId) {
    return controlBus.watchExecution(executionId)
        .map(progress -> ServerSentEvent.<WorkflowProgress>builder()
            .id(executionId)
            .event("progress")
            .data(progress)
            .build());
}
```

**Controllers:** ControlBusController (2 methods)  
**Time:** ~15 minutes  
**Benefit:** Proper SSE support, standard client compatibility

---

## ⚠️ Medium Priority (1-2 hours each)

### 4. Create Global Exception Handler
**File:** `web/src/main/java/com/infenia/yukta/handler/GlobalExceptionHandler.java`

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    @ExceptionHandler(ResourceNotFoundException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleNotFound(
            ResourceNotFoundException ex, ServerWebExchange exchange) {
        log.atWarn().addKeyValue("message", ex.getMessage()).log("Resource not found");
        return Mono.fromSupplier(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(404, "Not Found", ex.getMessage(), 
                exchange.getRequest().getPath().value(), List.of())));
    }
    
    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleGeneric(
            Exception ex, ServerWebExchange exchange) {
        log.atError().log("Unexpected error", ex);
        return Mono.fromSupplier(() -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error(500, "Internal Server Error", "An error occurred",
                exchange.getRequest().getPath().value(), List.of())));
    }
}
```

**Benefits:**
- Eliminates error handling duplication across all 5 controllers
- Consistent error responses
- Centralized logging
- Easy to add new error types

**Impact:** HIGHEST - Affects all 5 controllers

---

### 5. Replace Mono.fromCallable() with Reactive Types

**Pattern to eliminate (found in all controllers):**
```java
// ❌ REMOVE
Mono.fromCallable(() -> service.getBlocking())

// ✅ USE (after service is updated)
service.getMono()
```

**Services to update:**
- ControlBusGateway - 6 methods
- LogRetrievalService - 2 methods
- WorkflowRegistry - 2 methods
- SessionService - 3 methods
- WorkflowService - 1 method

**Total blocking calls to fix:** ~14

**Time:** 2-3 hours (service layer) + 1 hour (controllers)

---

## 🔥 Critical Items (2-3 hours each)

### 6. Type-Safe DTOs Instead of Raw Maps

**Current Issue:**
```java
// ❌ IN SessionConfigController
@SuppressWarnings("unchecked")
final Map<String, Object> workflows = 
    (Map<String, Object>) config.getOrDefault("workflows", Map.of());
```

**Solution:**
Create `SessionConfig` record:
```java
public record SessionConfig(
    String sessionId,
    Map<String, WorkflowDefinition> workflows,
    // ...
) {}
```

Update service to return `Mono<SessionConfig>` instead of `Mono<Map<String, Object>>`

Update controller to use typed object:
```java
return sessionService.getSessionConfig(sessionId)
    .map(config -> new SessionDetails(sessionId, config.workflows().keySet().stream().toList()))
```

**Impact:** Removes unsafe casts, improves type safety

---

## 📊 Priority Matrix

```
┌─────────────────────────────────────────┐
│ Impact  │                               │
│  HIGH   │ Global Handler │ Reactive     │
│         │                │ Types        │
├─────────────────────────────────────────┤
│ MEDIUM  │ SSE Wrapping   │ Type-Safe    │
│         │ Type Safety    │ DTOs         │
├─────────────────────────────────────────┤
│  LOW    │ Logging        │ Status Codes │
├─────────────────────────────────────────┤
│         │  EASY  │      │  HARD        │
│         │        │      │              │
└─────────────────────────────────────────┘
```

### Implementation Order

1. **Phase 1 (Day 1):** Remove logging + Status codes → Quick wins
2. **Phase 2 (Day 2):** Global exception handler → Eliminates duplication
3. **Phase 3 (Day 3-4):** Reactive service types → Requires service updates
4. **Phase 4 (Day 5):** Type-safe DTOs → Final cleanup
5. **Phase 5 (Day 6):** Testing + validation → Ensure quality

---

## 🔍 Checklist for Each Controller

### For Each Controller File

- [ ] Remove all `log.atInfo().log("methodName reached: ...")` lines
- [ ] Add missing `@ApiResponse` annotations for 500/503 status codes
- [ ] Remove inline error handling (will be in GlobalExceptionHandler)
- [ ] Remove `Mono.fromCallable()` wrappers (service layer should return Mono)
- [ ] Update SSE endpoints to wrap in `ServerSentEvent`
- [ ] Remove `@SuppressWarnings("unchecked")` if present
- [ ] Verify all methods use `ServerWebExchange` only for error path extraction
- [ ] Update Javadoc if changed parameter types

### ControlBusController Specific
- [ ] Fix `streamProgress()` SSE wrapping
- [ ] Fix `streamLogs()` SSE wrapping
- [ ] Remove `Mono.fromCallable()` from all methods

### WorkflowController Specific
- [ ] Add 500/503 responses to `triggerWorkflow()`
- [ ] Already has correct SSE pattern - verify consistency

### LogManagementController Specific
- [ ] Add error response annotations to `getRawLogContent()`
- [ ] Verify `LogRetrievalService` returns `Mono<T>` types

### PluginController Specific
- [ ] No SSE handling needed
- [ ] Verify `WorkflowRegistry` returns `Mono<T>` types
- [ ] Error handler already good pattern - keep as is

### SessionConfigController Specific
- [ ] Replace `Map<String, Object>` casting with `SessionConfig` DTO
- [ ] Remove `@SuppressWarnings("unchecked")`
- [ ] Verify `SessionService` returns typed objects

---

## 🛠️ Code Templates

### Template 1: Clean Controller Method
```java
@GetMapping("/{id}")
@Operation(summary = "Get resource", description = "...")
@ApiResponse(responseCode = "200", description = "Success")
@ApiResponse(responseCode = "404", description = "Not found")
@ApiResponse(responseCode = "500", description = "Server error")
public Mono<ResponseEntity<ApiResponse<ResourceData>>> getResource(
        @PathVariable final String id) {
    
    // ✅ No method entry logging
    // ✅ No error handling (delegated to GlobalExceptionHandler)
    // ✅ Direct reactive call (no Mono.fromCallable)
    
    return service.getResource(id)
        .map(resource -> ResponseEntity.ok(
            ApiResponse.success(200, "Resource retrieved", resource)));
}
```

### Template 2: SSE Endpoint
```java
@GetMapping(value = "/stream/{id}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
@Operation(summary = "Stream events", description = "...")
public Flux<ServerSentEvent<EventData>> streamEvents(@PathVariable final String id) {
    
    return service.watchEvents(id)
        .map(event -> ServerSentEvent.<EventData>builder()
            .id(UUID.randomUUID().toString())
            .event("update")
            .data(event)
            .build());
}
```

### Template 3: Exception Handler
```java
@ExceptionHandler(MyException.class)
public Mono<ResponseEntity<ApiResponse<Void>>> handleMyException(
        final MyException ex,
        final ServerWebExchange exchange) {
    
    log.atWarn().addKeyValue("message", ex.getMessage()).log("Custom error occurred");
    
    return Mono.fromSupplier(() ->
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(400, "Bad Request", ex.getMessage(),
                exchange.getRequest().getPath().value(), List.of())));
}
```

---

## 🧪 Testing Checklist

After making changes, test:

```java
// ✅ Test 1: Error handling consistency
void testAllErrorResponses_SameFormat() {
    // Call multiple endpoints with invalid input
    // Verify all return same error format
}

// ✅ Test 2: No blocking behavior
void testNonBlockingBehavior() {
    // Measure thread pool usage
    // Verify no threads blocked
}

// ✅ Test 3: SSE events
void testSSEFormat() {
    // Call SSE endpoint
    // Verify ServerSentEvent wrapper present
    // Verify id, event, data fields
}

// ✅ Test 4: Response codes
void testResponseCodes() {
    // Verify 200 for success
    // Verify 404 for not found
    // Verify 500 for error
}
```

---

## 📚 Key Files to Modify

| Priority | File | Changes | Time |
|----------|------|---------|------|
| 1 | ControlBusController | SSE wrapping, logging, status codes | 1h |
| 1 | WorkflowController | Status codes, logging | 30m |
| 1 | LogManagementController | Status codes, logging | 30m |
| 1 | PluginController | Status codes, logging | 30m |
| 1 | SessionConfigController | DTOs, logging, status codes | 1h |
| 2 | GlobalExceptionHandler | Create new file | 1-2h |
| 2 | ControlBusGateway | Update return types | 1-2h |
| 2 | LogRetrievalService | Update return types | 30m |
| 2 | WorkflowRegistry | Update return types | 30m |
| 2 | SessionService | Update return types | 1h |

---

## 📖 Quick Reference Links

- [Spring WebFlux Docs](https://spring.io/projects/spring-webflux)
- [Reactive Streams](https://www.reactive-streams.org/)
- [Global Error Handling](https://spring.io/blog/2022/05/02/spring-framework-5-3-spring-boot-2-7-spring-security-5-7-spring-data-2022-0-released)
- [Server-Sent Events](https://www.baeldung.com/spring-server-sent-events)

---

## ✅ Success Criteria

- [ ] All method entry/exit logs removed from controllers
- [ ] Global exception handler implemented and tested
- [ ] All SSE endpoints wrap in ServerSentEvent
- [ ] All response status codes documented in Swagger
- [ ] All service methods return Mono<T>/Flux<T>
- [ ] No Mono.fromCallable() wrappers in controllers
- [ ] All unsafe casts removed (use type-safe DTOs)
- [ ] All tests passing
- [ ] Code review approved

---

## 🎯 Estimated Total Time

| Phase | Time |
|-------|------|
| Quick Wins (logging, status codes) | 3 hours |
| Global Exception Handler | 2 hours |
| Service Layer Updates | 3 hours |
| Controller Updates | 2 hours |
| Testing & Validation | 2 hours |
| **TOTAL** | **12 hours** |

**Recommended spread:** 3 days (4 hours/day) or 2 days (6 hours/day)

