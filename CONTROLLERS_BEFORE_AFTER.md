# Controller Best Practices - Before & After Code Examples

**Project:** Yukta  
**Framework:** Spring Boot 4.0.3 + WebFlux  
**Date:** June 14, 2026

---

## Table of Contents

1. [Global Exception Handler](#global-exception-handler)
2. [SSE Response Wrapping](#sse-response-wrapping)
3. [Mono.fromCallable() Pattern](#monocallable-pattern)
4. [Method Entry Logging](#method-entry-logging)
5. [Type-Safe DTOs](#type-safe-dtos)
6. [Response Status Codes](#response-status-codes)

---

## Global Exception Handler

### ❌ BEFORE: Duplicated Error Handling

**Current Pattern in Controllers:**

```java
// WorkflowController.java
@PostMapping("/workflow/trigger")
public Mono<ResponseEntity<ApiResponse<TriggerResponse>>> triggerWorkflow(
        @Valid @RequestBody final WorkflowTriggerRequest request,
        final ServerWebExchange exchange) {
    return workflowService
        .validateAndTriggerWorkflow(request.sessionId(), request.workflowId(), request.payload())
        .map(execution ->
            ResponseEntity.accepted()
                .body(ApiResponse.success(
                    202,
                    "Workflow trigger accepted",
                    new TriggerResponse(execution.executionId()))))
        .onErrorResume(e -> {
            // ❌ ERROR HANDLING DUPLICATED ACROSS CONTROLLERS
            final String path = exchange.getRequest().getPath().value();
            final List<ApiResponse.FieldError> errors =
                List.of(new ApiResponse.FieldError("workflow", e.getMessage()));
            return Mono.just(
                ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(404, "Not Found", "Workflow not found", path, errors)));
        });
}

// PluginController.java
@GetMapping("/{type}")
public Mono<ResponseEntity<ApiResponse<PluginDetails>>> getPluginDetails(
        @PathVariable final String type,
        final ServerWebExchange exchange) {
    return Mono.fromCallable(() -> registry.get(type))
        .flatMap(Mono::justOrEmpty)
        .map(p -> ResponseEntity.ok(
            ApiResponse.success(200, "Plugin details retrieved", ...)))
        .switchIfEmpty(Mono.fromSupplier(() -> {
            // ❌ SAME ERROR HANDLING PATTERN REPEATED
            final String path = exchange.getRequest().getPath().value();
            final List<ApiResponse.FieldError> errors =
                List.of(new ApiResponse.FieldError("type", "Plugin not found: '" + type + "'"));
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(
                    HttpStatus.NOT_FOUND.value(),
                    "Not Found",
                    "Plugin not found",
                    path,
                    errors));
        }));
}

// SessionConfigController.java
@GetMapping("/{sessionId}")
public Mono<ResponseEntity<ApiResponse<SessionDetails>>> getSessionDetails(
        @PathVariable final String sessionId,
        final ServerWebExchange exchange) {
    return sessionService
        .getSessionConfig(sessionId)
        .map(config -> ResponseEntity.ok(
            ApiResponse.success(200, "Session details retrieved", ...)))
        .switchIfEmpty(Mono.fromSupplier(() -> {
            // ❌ SAME ERROR HANDLING PATTERN REPEATED AGAIN
            final String path = exchange.getRequest().getPath().value();
            final List<ApiResponse.FieldError> errors =
                List.of(new ApiResponse.FieldError("sessionId", "Session not found: '" + sessionId + "'"));
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(
                    HttpStatus.NOT_FOUND.value(),
                    "Not Found",
                    "Session not found",
                    path,
                    errors));
        }));
}
```

**Problems:**
- ❌ Error handling logic duplicated across 5 controllers
- ❌ Hard to maintain consistency
- ❌ Difficult to add new error types
- ❌ Controllers are cluttered with error handling code
- ❌ No centralized logging for errors

### ✅ AFTER: Centralized Exception Handler

**File: `web/src/main/java/com/infenia/yukta/handler/GlobalExceptionHandler.java`**

```java
package com.infenia.yukta.handler;

import com.infenia.yukta.exception.ResourceNotFoundException;
import com.infenia.yukta.exception.ValidationException;
import com.infenia.yukta.model.api.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Global exception handler for REST API.
 * Centralizes error handling and ensures consistent error responses across all controllers.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handle ResourceNotFoundException (404).
     * Logged at WARN level with context information.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleResourceNotFound(
            final ResourceNotFoundException ex,
            final ServerWebExchange exchange) {
        
        final String path = exchange.getRequest().getPath().value();
        
        log.atWarn()
            .addKeyValue("errorType", "ResourceNotFound")
            .addKeyValue("message", ex.getMessage())
            .addKeyValue("path", path)
            .addKeyValue("resourceType", ex.getResourceType())
            .log("Resource not found");
        
        return Mono.fromSupplier(() ->
            ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(
                    404,
                    "Not Found",
                    ex.getMessage(),
                    path,
                    ex.getFieldErrors()
                ))
        );
    }

    /**
     * Handle ValidationException (400).
     * Includes detailed field-level error information.
     */
    @ExceptionHandler(ValidationException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleValidation(
            final ValidationException ex,
            final ServerWebExchange exchange) {
        
        final String path = exchange.getRequest().getPath().value();
        
        log.atWarn()
            .addKeyValue("errorType", "ValidationError")
            .addKeyValue("message", ex.getMessage())
            .addKeyValue("path", path)
            .addKeyValue("fieldCount", ex.getFieldErrors().size())
            .log("Validation failed");
        
        return Mono.fromSupplier(() ->
            ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(
                    400,
                    "Bad Request",
                    ex.getMessage(),
                    path,
                    ex.getFieldErrors()
                ))
        );
    }

    /**
     * Handle generic RuntimeException (500).
     * Logged at ERROR level with full stack trace.
     */
    @ExceptionHandler(RuntimeException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleRuntimeException(
            final RuntimeException ex,
            final ServerWebExchange exchange) {
        
        final String path = exchange.getRequest().getPath().value();
        
        log.atError()
            .addKeyValue("errorType", "InternalServerError")
            .addKeyValue("message", ex.getMessage())
            .addKeyValue("path", path)
            .log("Unexpected runtime error", ex);
        
        return Mono.fromSupplier(() ->
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(
                    500,
                    "Internal Server Error",
                    "An unexpected error occurred. Please try again later.",
                    path,
                    List.of()
                ))
        );
    }

    /**
     * Handle any other uncaught exceptions (500).
     * Last resort error handler.
     */
    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleGenericException(
            final Exception ex,
            final ServerWebExchange exchange) {
        
        final String path = exchange.getRequest().getPath().value();
        
        log.atError()
            .addKeyValue("errorType", "UnexpectedException")
            .addKeyValue("exceptionClass", ex.getClass().getSimpleName())
            .addKeyValue("message", ex.getMessage())
            .addKeyValue("path", path)
            .log("Unexpected error", ex);
        
        return Mono.fromSupplier(() ->
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(
                    500,
                    "Internal Server Error",
                    "An unexpected error occurred",
                    path,
                    List.of()
                ))
        );
    }
}
```

**Custom Exception Classes:**

```java
// ResourceNotFoundException.java
public class ResourceNotFoundException extends RuntimeException {
    private final String resourceType;
    private final List<ApiResponse.FieldError> fieldErrors;

    public ResourceNotFoundException(
            final String message,
            final String resourceType) {
        super(message);
        this.resourceType = resourceType;
        this.fieldErrors = List.of();
    }

    public ResourceNotFoundException(
            final String message,
            final String resourceType,
            final List<ApiResponse.FieldError> fieldErrors) {
        super(message);
        this.resourceType = resourceType;
        this.fieldErrors = fieldErrors;
    }

    public String getResourceType() {
        return resourceType;
    }

    public List<ApiResponse.FieldError> getFieldErrors() {
        return fieldErrors;
    }
}

// ValidationException.java
public class ValidationException extends RuntimeException {
    private final List<ApiResponse.FieldError> fieldErrors;

    public ValidationException(
            final String message,
            final List<ApiResponse.FieldError> fieldErrors) {
        super(message);
        this.fieldErrors = fieldErrors;
    }

    public List<ApiResponse.FieldError> getFieldErrors() {
        return fieldErrors;
    }
}
```

**Cleaned Up Controller:**

```java
// WorkflowController.java - AFTER
@PostMapping("/workflow/trigger")
@Operation(summary = "Trigger a workflow", ...)
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", ...)
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", ...)
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", ...)
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ...)
public Mono<ResponseEntity<ApiResponse<TriggerResponse>>> triggerWorkflow(
        @Valid @RequestBody final WorkflowTriggerRequest request,
        final ServerWebExchange exchange) {
    
    // ✅ CLEAN - No error handling code!
    return workflowService
        .validateAndTriggerWorkflow(
            request.sessionId(),
            request.workflowId(),
            request.payload())
        .map(execution ->
            ResponseEntity.accepted()
                .body(ApiResponse.success(
                    202,
                    "Workflow trigger accepted",
                    new TriggerResponse(execution.executionId())
                )));
    // Error handling is now in GlobalExceptionHandler
}
```

**Benefits:**
- ✅ Single source of truth for error handling
- ✅ Controllers focus on happy path
- ✅ Easy to add new error types
- ✅ Consistent error responses
- ✅ Centralized logging

---

## SSE Response Wrapping

### ❌ BEFORE: Inconsistent SSE Responses

```java
// ControlBusController.java - WRONG PATTERN
@GetMapping(
    value = "/control/executions/{executionId}/progress/stream",
    produces = MediaType.TEXT_EVENT_STREAM_VALUE)
@Operation(
    summary = "Stream execution progress",
    description = "Streams progress updates for an execution in real-time")
public Flux<WorkflowProgress> streamProgress(@PathVariable final String executionId) {
    log.atInfo().log("streamProgress reached: executionId={}", executionId);
    return controlBus.watchExecution(executionId);  // ❌ Returns raw data, not wrapped
}

@GetMapping(
    value = "/control/executions/{executionId}/logs/stream",
    produces = MediaType.TEXT_EVENT_STREAM_VALUE)
@Operation(
    summary = "Stream execution logs",
    description = "Streams log lines for an execution in real-time")
public Flux<String> streamLogs(@PathVariable final String executionId) {
    log.atInfo().log("streamLogs reached: executionId={}", executionId);
    return controlBus.watchLogs(executionId);  // ❌ Returns raw data, not wrapped
}

// WorkflowController.java - CORRECT PATTERN
@GetMapping(
    value = "/workflow/{sessionId}/status/{executionId}/stream",
    produces = MediaType.TEXT_EVENT_STREAM_VALUE)
@Operation(
    summary = "Stream workflow execution status",
    description = "Streams the status and progress of a workflow execution via SSE")
public Flux<ServerSentEvent<WorkflowProgress>> streamWorkflowStatus(
        @PathVariable final String sessionId,
        @PathVariable final String executionId) {
    log.atInfo().log("streamWorkflowStatus reached: sessionId={}, executionId={}", sessionId, executionId);
    return controlBus
        .watchExecution(executionId)
        .map(progress -> ServerSentEvent.<WorkflowProgress>builder()
            .data(progress)
            .build());  // ✅ Properly wrapped
}
```

**Problems:**
- ❌ ControlBus returns raw data without SSE wrapping
- ❌ Clients can't use standard SSE libraries
- ❌ No event metadata (id, retry, event type)
- ❌ Inconsistency between controllers

### ✅ AFTER: Consistent SSE Responses

```java
// ControlBusController.java - CORRECTED
@GetMapping(
    value = "/control/executions/{executionId}/progress/stream",
    produces = MediaType.TEXT_EVENT_STREAM_VALUE)
@Operation(
    summary = "Stream execution progress",
    description = "Streams progress updates for an execution in real-time via Server-Sent Events")
public Flux<ServerSentEvent<WorkflowProgress>> streamProgress(
        @PathVariable final String executionId) {
    
    // Removed method entry logging
    return controlBus.watchExecution(executionId)
        .map(progress -> ServerSentEvent.<WorkflowProgress>builder()
            .id(executionId)
            .event("progress")
            .data(progress)
            .build());  // ✅ Now properly wrapped with metadata
}

@GetMapping(
    value = "/control/executions/{executionId}/logs/stream",
    produces = MediaType.TEXT_EVENT_STREAM_VALUE)
@Operation(
    summary = "Stream execution logs",
    description = "Streams log lines for an execution in real-time via Server-Sent Events")
public Flux<ServerSentEvent<String>> streamLogs(
        @PathVariable final String executionId) {
    
    // Removed method entry logging
    return controlBus.watchLogs(executionId)
        .map(logLine -> ServerSentEvent.<String>builder()
            .id(UUID.randomUUID().toString())
            .event("log")
            .data(logLine)
            .build());  // ✅ Now properly wrapped with metadata
}

// WorkflowController.java - Already correct, no changes needed
```

**Client-Side Usage:**

```javascript
// ✅ Now works with standard EventSource API
const eventSource = new EventSource('/api/control/executions/exec-123/progress/stream');

eventSource.addEventListener('progress', (event) => {
    const progress = JSON.parse(event.data);
    console.log('Progress:', progress);
});

eventSource.addEventListener('error', () => {
    console.log('Stream error');
    eventSource.close();
});
```

---

## Mono.fromCallable() Pattern

### ❌ BEFORE: Blocking Operations Wrapped

```java
// ControlBusController.java
@GetMapping("/control/workflows/{workflowId}/nodes")
public Mono<ApiResponse<List<String>>> getActiveNodes(
        @PathVariable final String workflowId) {
    log.atInfo().log("getActiveNodes reached: workflowId={}", workflowId);
    return Mono.fromCallable(() -> controlBus.getActiveNodes(workflowId))  // ❌ BLOCKING!
        .map(nodes -> ApiResponse.success(200, "Active nodes retrieved", nodes));
}

// PluginController.java
@GetMapping
public Mono<ResponseEntity<ApiResponse<List<PluginSummary>>>> listPlugins() {
    log.atInfo().log("listPlugins reached");
    return Mono.fromCallable(registry::listPlugins)  // ❌ BLOCKING!
        .map(plugins ->
            plugins.stream()
                .map(p -> new PluginSummary(p.getType(), p.getCategory()))
                .toList())
        .map(summaries ->
            ResponseEntity.ok(
                ApiResponse.success(200, "Plugins retrieved successfully", summaries)))
        .onErrorResume(e ->
            Mono.just(
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(500, "Internal Server Error", ..., "/api/plugins", List.of()))));
}

// SessionConfigController.java
@PostMapping
public Mono<ResponseEntity<ApiResponse<Void>>> applyConfig(
        @Valid @RequestBody final ConfigRequest request) {
    log.atInfo().log("applyConfig reached: sessionId={}", request.sessionId());
    final SessionConfigData configData = configMapper.toData(request);
    return sessionService
        .applyConfig(configData)
        .thenReturn(
            ResponseEntity.ok(
                ApiResponse.success(200, "Configuration applied successfully", null)));
}
```

**Problems:**
- ❌ `Mono.fromCallable()` wraps blocking code
- ❌ Defeats reactive benefits
- ❌ Can cause thread pool exhaustion under load
- ❌ Services should return `Mono<T>` natively

### ✅ AFTER: Native Reactive Types

**Step 1: Service Layer Returns Reactive Types**

```java
// ControlBusGateway.java - UPDATED
@Service
public class ControlBusGateway {
    
    // ✅ Returns Mono<List<String>> instead of List<String>
    public Mono<List<String>> getActiveNodes(final String workflowId) {
        return Mono.defer(() ->
            // Actual implementation that returns Mono
            Mono.just(actuallyGetActiveNodes(workflowId))
        );
    }
    
    // ✅ Returns Mono<Message<?>> instead of Message<?>
    public Mono<Message<?>> getLastHeartbeat(
            final String workflowId,
            final String nodeId) {
        return Mono.defer(() ->
            Mono.justOrEmpty(actuallyGetLastHeartbeat(workflowId, nodeId))
        );
    }
    
    // ✅ Already returns Flux<WorkflowProgress> - GOOD!
    public Flux<WorkflowProgress> watchExecution(final String executionId) {
        return Flux.create(emitter -> {
            // Emit progress updates
        });
    }
}

// PluginRegistry.java - UPDATED
@Service
public class PluginRegistry {
    
    // ✅ Returns Mono<List<Plugin>> instead of List<Plugin>
    public Mono<List<Plugin>> listPlugins() {
        return Mono.defer(() ->
            Mono.just(actuallyListPlugins())
        );
    }
    
    // ✅ Returns Mono<Optional<Plugin>> instead of Optional<Plugin>
    public Mono<Optional<Plugin>> get(final String type) {
        return Mono.defer(() ->
            Mono.just(actuallyGet(type))
        );
    }
}
```

**Step 2: Controller Uses Native Reactive Types**

```java
// ControlBusController.java - UPDATED
@GetMapping("/control/workflows/{workflowId}/nodes")
@Operation(summary = "Get active nodes in workflow", ...)
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", ...)
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ...)
public Mono<ResponseEntity<ApiResponse<List<String>>>> getActiveNodes(
        @PathVariable final String workflowId) {
    
    // ✅ NO Mono.fromCallable() - Direct reactive call!
    return controlBus.getActiveNodes(workflowId)
        .map(nodes -> ResponseEntity.ok(
            ApiResponse.success(200, "Active nodes retrieved", nodes)));
    // Error handling delegated to GlobalExceptionHandler
}

// PluginController.java - UPDATED
@GetMapping
@Operation(summary = "List plugins", ...)
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", ...)
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ...)
public Mono<ResponseEntity<ApiResponse<List<PluginSummary>>>> listPlugins() {
    
    // ✅ NO Mono.fromCallable() - Direct reactive call!
    return registry.listPlugins()
        .map(plugins ->
            plugins.stream()
                .map(p -> new PluginSummary(p.getType(), p.getCategory()))
                .toList())
        .map(summaries ->
            ResponseEntity.ok(
                ApiResponse.success(200, "Plugins retrieved successfully", summaries)));
    // Error handling delegated to GlobalExceptionHandler
}
```

**Benefits:**
- ✅ Truly non-blocking and reactive
- ✅ Better thread utilization
- ✅ Improved throughput under load
- ✅ Cleaner controller code
- ✅ True async all the way down

---

## Method Entry Logging

### ❌ BEFORE: Verbose Method Entry Logs

```java
// Every controller has method entry logs
@GetMapping("/control/workflows/{workflowId}/nodes")
public Mono<ApiResponse<List<String>>> getActiveNodes(@PathVariable final String workflowId) {
    log.atInfo().log("getActiveNodes reached: workflowId={}", workflowId);  // ❌ NOISY
    return controlBus.getActiveNodes(workflowId)
        .map(nodes -> ApiResponse.success(200, "Active nodes retrieved", nodes));
}

@GetMapping("/control/workflows/{workflowId}/nodes/{nodeId}/heartbeat")
public Mono<ApiResponse<Message<?>>> getLastHeartbeat(
        @PathVariable final String workflowId,
        @PathVariable final String nodeId) {
    log.atInfo().log("getLastHeartbeat reached: workflowId={}, nodeId={}", workflowId, nodeId);  // ❌ NOISY
    return Mono.fromCallable(() -> controlBus.getLastHeartbeat(workflowId, nodeId))
        .map(hb -> ApiResponse.success(200, "Node heartbeat retrieved", hb));
}

@PostMapping("/control/workflows/{workflowId}/nodes/{nodeId}/command")
public Mono<ApiResponse<Message<?>>> sendCommand(
        @PathVariable final String workflowId,
        @PathVariable final String nodeId,
        @RequestBody final Map<String, Object> payload) {
    log.atInfo().log("sendCommand reached: workflowId={}, nodeId={}", workflowId, nodeId);  // ❌ NOISY
    // ...
}

@GetMapping("/logs/{sessionId}")
public Mono<ApiResponse<List<String>>> listLogs(@PathVariable final String sessionId) {
    log.atInfo().log("listLogs reached: sessionId={}", sessionId);  // ❌ NOISY
    return logs.listLogs(sessionId)
        .map(logList -> ApiResponse.success(200, "List of log filenames", logList));
}

@GetMapping("/logs/{sessionId}/{filename}")
public Mono<ResponseEntity<ApiResponse<String>>> getLogContent(
        @PathVariable final String sessionId,
        @PathVariable final String filename) {
    log.atInfo().log("getLogContent reached: sessionId={}, filename={}", sessionId, filename);  // ❌ NOISY
    return logs.getLogContent(sessionId, filename)
        .map(content -> ResponseEntity.ok(
            ApiResponse.success(200, "Log content retrieved successfully", content)))
        .onErrorResume(e ->
            Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(404, "Not Found", "Log file not found", null, List.of()))));
}
```

**Problems:**
- ❌ Excessive logging reduces performance
- ❌ Clutters logs with method entry/exit noise
- ❌ Better handled by Spring's request logging
- ❌ Business-level logging should be in service layer

### ✅ AFTER: Structured Logging at Service Layer

**Controllers - Clean, No Method Entry Logs:**

```java
// ControlBusController.java - UPDATED
@GetMapping("/control/workflows/{workflowId}/nodes")
@Operation(summary = "Get active nodes in workflow", ...)
public Mono<ResponseEntity<ApiResponse<List<String>>>> getActiveNodes(
        @PathVariable final String workflowId) {
    
    // ✅ NO method entry log - Clean code!
    return controlBus.getActiveNodes(workflowId)
        .map(nodes -> ResponseEntity.ok(
            ApiResponse.success(200, "Active nodes retrieved", nodes)));
}

@GetMapping("/control/workflows/{workflowId}/nodes/{nodeId}/heartbeat")
@Operation(summary = "Get node heartbeat in workflow", ...)
public Mono<ResponseEntity<ApiResponse<Message<?>>>> getLastHeartbeat(
        @PathVariable final String workflowId,
        @PathVariable final String nodeId) {
    
    // ✅ NO method entry log - Clean code!
    return controlBus.getLastHeartbeat(workflowId, nodeId)
        .map(hb -> ResponseEntity.ok(
            ApiResponse.success(200, "Node heartbeat retrieved", hb)));
}

@GetMapping("/logs/{sessionId}")
@Operation(summary = "List logs", ...)
public Mono<ResponseEntity<ApiResponse<List<String>>>> listLogs(
        @PathVariable final String sessionId) {
    
    // ✅ NO method entry log - Clean code!
    return logs.listLogs(sessionId)
        .map(logList -> ResponseEntity.ok(
            ApiResponse.success(200, "List of log filenames", logList)));
}
```

**Service Layer - Structured Logging:**

```java
// ControlBusGateway.java - Service Layer Logging
@Service
@Slf4j
public class ControlBusGateway {
    
    public Mono<List<String>> getActiveNodes(final String workflowId) {
        log.atDebug()
            .addKeyValue("workflowId", workflowId)
            .log("Retrieving active nodes");  // ✅ Business-level logging
        
        return Mono.defer(() -> {
            final List<String> nodes = actuallyGetActiveNodes(workflowId);
            
            log.atDebug()
                .addKeyValue("workflowId", workflowId)
                .addKeyValue("nodeCount", nodes.size())
                .log("Successfully retrieved active nodes");  // ✅ Result logging
            
            return Mono.just(nodes);
        });
    }
    
    public Mono<Message<?>> getLastHeartbeat(
            final String workflowId,
            final String nodeId) {
        log.atDebug()
            .addKeyValue("workflowId", workflowId)
            .addKeyValue("nodeId", nodeId)
            .log("Retrieving last heartbeat");  // ✅ Business-level logging
        
        return Mono.defer(() ->
            Mono.justOrEmpty(actuallyGetLastHeartbeat(workflowId, nodeId))
        );
    }
}

// LogRetrievalService.java - Service Layer Logging
@Service
@Slf4j
public class LogRetrievalService {
    
    public Mono<List<String>> listLogs(final String sessionId) {
        log.atDebug()
            .addKeyValue("sessionId", sessionId)
            .log("Listing logs for session");  // ✅ Business-level logging
        
        return Mono.defer(() -> {
            final List<String> logFiles = actuallyListLogs(sessionId);
            
            log.atDebug()
                .addKeyValue("sessionId", sessionId)
                .addKeyValue("fileCount", logFiles.size())
                .log("Successfully listed logs");  // ✅ Result logging
            
            return Mono.just(logFiles);
        });
    }
}
```

**Spring Configuration for Request Logging:**

```yaml
# application.yml
logging:
  level:
    root: INFO
    com.infenia.yukta.service: DEBUG
    org.springframework.web: DEBUG
    org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping: TRACE
  
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"

server:
  servlet:
    context-path: /
  tomcat:
    accesslog:
      enabled: true
      pattern: "%h %l %u %t \"%r\" %s %b"
```

**Benefits:**
- ✅ Cleaner controller code
- ✅ Business logic logged at service layer
- ✅ Spring handles HTTP-level logging
- ✅ Reduced performance overhead
- ✅ Better separation of concerns

---

## Type-Safe DTOs

### ❌ BEFORE: Unsafe Casting

```java
// SessionConfigController.java
@GetMapping("/{sessionId}")
@SuppressWarnings("unchecked")  // ❌ BAD SMELL - Indicates unsafe code
public Mono<ResponseEntity<ApiResponse<SessionDetails>>> getSessionDetails(
        @PathVariable final String sessionId,
        final ServerWebExchange exchange) {
    log.atInfo().log("getSessionDetails reached: sessionId={}", sessionId);
    
    return sessionService
        .getSessionConfig(sessionId)
        .map(config -> {
            // ❌ UNSAFE - Casting raw Map<String, Object>
            final Map<String, Object> workflows =
                (Map<String, Object>) config.getOrDefault("workflows", Map.of());
            final List<String> workflowIds = List.copyOf(workflows.keySet());
            
            return ResponseEntity.ok(
                ApiResponse.success(200, "Session details retrieved",
                    new SessionDetails(sessionId, workflowIds)));
        })
        .switchIfEmpty(Mono.fromSupplier(() -> {
            final String path = exchange.getRequest().getPath().value();
            final List<ApiResponse.FieldError> errors = List.of(
                new ApiResponse.FieldError("sessionId", "Session not found: '" + sessionId + "'"));
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(404, "Not Found", "Session not found", path, errors));
        }));
}

@GetMapping("/{sessionId}/workflows/{workflowId}")
public Mono<ResponseEntity<ApiResponse<WorkflowDefinition>>> getWorkflow(
        @PathVariable final String sessionId,
        @PathVariable final String workflowId,
        final ServerWebExchange exchange) {
    log.atInfo().log("getWorkflow reached: sessionId={}, workflowId={}", sessionId, workflowId);
    
    return sessionService
        .getSessionWorkflow(sessionId, workflowId)
        .map(def -> ResponseEntity.ok(
            ApiResponse.success(200, "Workflow retrieved", def)))
        .switchIfEmpty(Mono.fromSupplier(() -> {
            final String path = exchange.getRequest().getPath().value();
            final List<ApiResponse.FieldError> errors = List.of(
                new ApiResponse.FieldError("workflowId",
                    "Workflow not found: '" + workflowId + "' in session: '" + sessionId + "'"));
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(404, "Not Found", "Workflow not found", path, errors));
        }));
}
```

**Problems:**
- ❌ Requires `@SuppressWarnings("unchecked")`
- ❌ Type safety lost
- ❌ Potential ClassCastException at runtime
- ❌ Hard to understand data structure
- ❌ Difficult to maintain

### ✅ AFTER: Type-Safe DTOs

**Step 1: Create SessionConfig DTO**

```java
// web/src/main/java/com/infenia/yukta/model/session/SessionConfig.java
package com.infenia.yukta.model.session;

import com.infenia.yukta.model.workflow.WorkflowDefinition;
import java.util.Map;

/**
 * Strongly typed session configuration.
 * Replaces raw Map<String, Object> for type safety.
 */
public record SessionConfig(
    String sessionId,
    String projectPath,
    Map<String, WorkflowDefinition> workflows,
    Map<String, Object> metadata
) {
    /**
     * Convenience method to get workflow IDs.
     */
    public java.util.List<String> getWorkflowIds() {
        return workflows.keySet().stream().toList();
    }
}
```

**Step 2: Update Service Layer**

```java
// web/src/main/java/com/infenia/yukta/service/session/SessionService.java
@Service
@Slf4j
public class SessionService {
    
    // ❌ OLD - Returns Map<String, Object>
    // public Mono<Map<String, Object>> getSessionConfig(String sessionId) { ... }
    
    // ✅ NEW - Returns typed SessionConfig
    public Mono<SessionConfig> getSessionConfig(final String sessionId) {
        log.atDebug()
            .addKeyValue("sessionId", sessionId)
            .log("Retrieving session configuration");
        
        return Mono.defer(() -> {
            // Retrieve configuration and construct SessionConfig
            final SessionConfig config = actuallyLoadSessionConfig(sessionId);
            
            log.atDebug()
                .addKeyValue("sessionId", sessionId)
                .addKeyValue("workflowCount", config.workflows().size())
                .log("Successfully retrieved session configuration");
            
            return Mono.just(config);
        });
    }
    
    public Mono<WorkflowDefinition> getSessionWorkflow(
            final String sessionId,
            final String workflowId) {
        log.atDebug()
            .addKeyValue("sessionId", sessionId)
            .addKeyValue("workflowId", workflowId)
            .log("Retrieving workflow definition");
        
        return this.getSessionConfig(sessionId)
            .flatMap(config ->
                Mono.justOrEmpty(config.workflows().get(workflowId))
            );
    }
}
```

**Step 3: Clean Up Controller**

```java
// SessionConfigController.java - UPDATED
@GetMapping("/{sessionId}")
@Operation(summary = "Get session details", ...)
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", ...)
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", ...)
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ...)
public Mono<ResponseEntity<ApiResponse<SessionDetails>>> getSessionDetails(
        @Parameter(description = "The unique identifier of the session")
        @PathVariable final String sessionId) {
    
    // ✅ Clean, type-safe code - No casting!
    return sessionService
        .getSessionConfig(sessionId)
        .map(config -> new SessionDetails(sessionId, config.getWorkflowIds()))
        .map(details -> ResponseEntity.ok(
            ApiResponse.success(200, "Session details retrieved", details)));
    // Error handling delegated to GlobalExceptionHandler
}

@GetMapping("/{sessionId}/workflows/{workflowId}")
@Operation(summary = "Get workflow", ...)
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", ...)
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", ...)
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ...)
public Mono<ResponseEntity<ApiResponse<WorkflowDefinition>>> getWorkflow(
        @Parameter(description = "The unique identifier of the session")
        @PathVariable final String sessionId,
        @Parameter(description = "The unique identifier of the workflow")
        @PathVariable final String workflowId) {
    
    // ✅ Clean, type-safe code - No casting!
    return sessionService
        .getSessionWorkflow(sessionId, workflowId)
        .map(def -> ResponseEntity.ok(
            ApiResponse.success(200, "Workflow retrieved", def)));
    // Error handling delegated to GlobalExceptionHandler
}
```

**Benefits:**
- ✅ Type-safe - compiler catches errors
- ✅ No unsafe casting
- ✅ Clear data structure
- ✅ Better IDE support and autocomplete
- ✅ Easier to maintain and refactor
- ✅ Self-documenting code

---

## Response Status Codes

### ❌ BEFORE: Incomplete Documentation

```java
// WorkflowController.java
@PostMapping("/workflow/trigger")
@Operation(
    summary = "Trigger a workflow",
    description = "Triggers the execution of a specific DAG workflow for a session")
@io.swagger.v3.oas.annotations.responses.ApiResponse(
    responseCode = "202",
    description = "Workflow trigger accepted")
@io.swagger.v3.oas.annotations.responses.ApiResponse(
    responseCode = "400",
    description = "Invalid session ID or workflow ID")
@io.swagger.v3.oas.annotations.responses.ApiResponse(
    responseCode = "404",
    description = "Session or workflow not found")
// ❌ Missing: 500, 503, 429, etc.
public Mono<ResponseEntity<ApiResponse<TriggerResponse>>> triggerWorkflow(
    @Valid @RequestBody final WorkflowTriggerRequest request,
    final ServerWebExchange exchange) {
    // ...
}

// PluginController.java
@GetMapping
@Operation(
    summary = "List plugins",
    description = "Lists all registered workflow plugins...")
@io.swagger.v3.oas.annotations.responses.ApiResponse(
    responseCode = "200",
    description = "Plugins retrieved successfully",
    content = @Content(mediaType = APPLICATION_JSON))
@io.swagger.v3.oas.annotations.responses.ApiResponse(
    responseCode = "500",
    description = "Internal server error",
    content = @Content(mediaType = APPLICATION_JSON))
// ❌ Missing other possible status codes
public Mono<ResponseEntity<ApiResponse<List<PluginSummary>>>> listPlugins() {
    // ...
}
```

**Problems:**
- ❌ Swagger docs incomplete
- ❌ Clients don't know all possible responses
- ❌ Hard to test error scenarios
- ❌ Inconsistent across endpoints

### ✅ AFTER: Complete Response Documentation

```java
// WorkflowController.java - UPDATED
@PostMapping("/workflow/trigger")
@Operation(
    summary = "Trigger a workflow",
    description = "Triggers the execution of a specific DAG workflow for a session")
@io.swagger.v3.oas.annotations.responses.ApiResponse(
    responseCode = "202",
    description = "Workflow trigger accepted",
    content = @Content(mediaType = "application/json"))
@io.swagger.v3.oas.annotations.responses.ApiResponse(
    responseCode = "400",
    description = "Invalid request payload - validation failed",
    content = @Content(mediaType = "application/json"))
@io.swagger.v3.oas.annotations.responses.ApiResponse(
    responseCode = "404",
    description = "Session or workflow not found",
    content = @Content(mediaType = "application/json"))
@io.swagger.v3.oas.annotations.responses.ApiResponse(
    responseCode = "500",
    description = "Internal server error",
    content = @Content(mediaType = "application/json"))
@io.swagger.v3.oas.annotations.responses.ApiResponse(
    responseCode = "503",
    description = "Service unavailable - control bus unreachable",
    content = @Content(mediaType = "application/json"))
// ✅ Complete documentation
public Mono<ResponseEntity<ApiResponse<TriggerResponse>>> triggerWorkflow(
    @Valid @RequestBody final WorkflowTriggerRequest request,
    final ServerWebExchange exchange) {
    
    return workflowService
        .validateAndTriggerWorkflow(
            request.sessionId(),
            request.workflowId(),
            request.payload())
        .map(execution ->
            ResponseEntity.accepted()
                .body(ApiResponse.success(
                    202,
                    "Workflow trigger accepted",
                    new TriggerResponse(execution.executionId())
                )));
}

@GetMapping
@Operation(
    summary = "List plugins",
    description = "Lists all registered workflow plugins. Response is non-blocking and " +
        "returned asynchronously via Mono.")
@io.swagger.v3.oas.annotations.responses.ApiResponse(
    responseCode = "200",
    description = "Plugins retrieved successfully",
    content = @Content(mediaType = "application/json"))
@io.swagger.v3.oas.annotations.responses.ApiResponse(
    responseCode = "500",
    description = "Internal server error - registry unavailable",
    content = @Content(mediaType = "application/json"))
// ✅ Complete documentation
public Mono<ResponseEntity<ApiResponse<List<PluginSummary>>>> listPlugins() {
    
    return registry.listPlugins()
        .map(plugins ->
            plugins.stream()
                .map(p -> new PluginSummary(p.getType(), p.getCategory()))
                .toList())
        .map(summaries ->
            ResponseEntity.ok(
                ApiResponse.success(
                    200,
                    "Plugins retrieved successfully",
                    summaries
                )));
}
```

**Standard HTTP Status Codes Table:**

| Code | Usage | Example |
|------|-------|---------|
| 200 | Success (GET, PUT) | Plugin details retrieved |
| 201 | Created (POST) | Session created |
| 202 | Accepted (async) | Workflow trigger accepted |
| 204 | No Content | Delete successful, no response body |
| 400 | Bad Request | Invalid validation |
| 401 | Unauthorized | Missing authentication |
| 403 | Forbidden | Insufficient permissions |
| 404 | Not Found | Session not found |
| 409 | Conflict | Resource already exists |
| 500 | Internal Error | Unexpected exception |
| 503 | Unavailable | Service temporarily down |

**Benefits:**
- ✅ Clear API documentation
- ✅ Better client error handling
- ✅ Easier testing and QA
- ✅ Proper Swagger/OpenAPI docs
- ✅ Client developers know what to expect

---

## Summary of All Changes

| Aspect | Before | After | Impact |
|--------|--------|-------|--------|
| Error Handling | Duplicated in controllers | Global exception handler | HIGH |
| SSE Responses | Inconsistent | Consistent ServerSentEvent | MEDIUM |
| Reactivity | Mono.fromCallable() blocking | Native Mono<T>/Flux<T> | HIGH |
| Logging | Method entry/exit in controllers | Service layer logging | MEDIUM |
| Type Safety | Raw Map casting | Type-safe DTOs | MEDIUM |
| Documentation | Incomplete status codes | Complete documentation | LOW |

---

## Next Steps

1. Review and approve these code patterns
2. Create issues for each improvement area
3. Implement changes in priority order
4. Thorough testing at each phase
5. Deploy with confidence

