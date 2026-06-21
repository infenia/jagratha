# List Sessions Endpoint Design

**Date:** 2026-06-21  
**Author:** Claude Code  
**Status:** Approved  

## Overview

Add a new REST endpoint to list all available session identifiers. This allows clients to discover sessions without requiring prior knowledge of session IDs. Clients can then use the existing `GET /api/sessions/{sessionId}` endpoint to fetch detailed information about specific sessions.

## Requirements

- List all available session IDs in the system
- Return data in a wrapper object for consistency with existing API patterns
- Non-blocking, reactive implementation using Spring WebFlux
- Follow existing code conventions (Swagger annotations, logging, error handling)

## Design

### 1. New Response DTO: `SessionList`

**Location:** `web/src/main/java/com/infenia/yukta/dto/response/SessionList.java`

A simple record DTO that wraps a list of session IDs:

```java
public record SessionList(
    @Schema(description = "The list of session identifiers") 
    List<String> sessionIds) {
  
  public SessionList {
    sessionIds = sessionIds != null ? List.copyOf(sessionIds) : List.of();
  }
}
```

**Rationale:** 
- Consistent with existing `SessionDetails` pattern
- Immutable via compact constructor
- Extensible for future metadata (pagination, timestamp, etc.)
- Clear Swagger documentation

### 2. New Endpoint: `GET /api/sessions`

**Location:** `SessionConfigController.listSessions()`

**Method Signature:**
```java
@GetMapping
@Operation(summary = "List all sessions", description = "...")
public Mono<ResponseEntity<ApiResponse<SessionList>>> listSessions()
```

**Behavior:**
- Calls `sessionService.getSessionIds()` which returns `Flux<String>`
- Collects the flux into a `List<String>` using `.collectList()`
- Maps result to `SessionList` wrapper
- Returns wrapped in `ApiResponse.success(HttpStatus.OK.value(), message, sessionList)`
- Includes logging at key points (start, success, error)
- Returns HTTP 200 even if no sessions exist (empty list)
- Returns HTTP 500 on retrieval errors

**Response Examples:**

Success with sessions:
```json
{
  "timestamp": "2026-06-21T10:30:00",
  "status": 200,
  "message": "Sessions retrieved successfully",
  "data": {
    "sessionIds": ["session-1", "session-2", "session-3"]
  }
}
```

Success with no sessions:
```json
{
  "timestamp": "2026-06-21T10:30:00",
  "status": 200,
  "message": "Sessions retrieved successfully",
  "data": {
    "sessionIds": []
  }
}
```

### 3. Testing

**Location:** `web/src/test/java/com/infenia/yukta/controller/SessionConfigControllerTest.java`

Test cases to add:
- **List sessions successfully:** Verify endpoint returns paginated list of session IDs
- **Empty sessions:** Verify endpoint returns empty list when no sessions exist
- **Error handling:** Verify proper error response when service fails

## Implementation Details

### Flow
1. HTTP GET `/api/sessions` received
2. `listSessions()` method invoked
3. Calls `sessionService.getSessionIds()` → returns `Flux<String>`
4. Collects flux to `List<String>` via `.collectList()`
5. Maps to `SessionList(sessionIds)`
6. Wraps in `ApiResponse.success()` with HTTP 200
7. Returns `Mono<ResponseEntity<ApiResponse<SessionList>>>`

### Reactive Pattern
- Uses Project Reactor's `Flux.collectList()` to gather all IDs before responding
- Appropriate for session discovery (expected finite list size)
- Alternative (streaming) deferred in favor of simplicity

### Logging
- **Debug:** Start of retrieval
- **Trace:** Each session ID found (inherited from SessionService)
- **Info:** Completion and response sent
- **Error:** Failure details with cause

### Error Handling
- Service errors propagate to `GlobalExceptionHandler`
- Empty flux results in empty list (not an error)
- HTTP 500 on underlying service failures

## Consistency with Existing Patterns

✓ **Response wrapper:** Uses `ApiResponse<T>` like all other endpoints  
✓ **DTO style:** Record with compact constructor, consistent with `SessionDetails`  
✓ **HTTP method:** `@GetMapping` without variables for list operations  
✓ **Reactive:** `Mono<ResponseEntity<T>>` return type, `.doOnNext()`, `.doOnError()` chains  
✓ **Swagger:** `@Operation`, `@Parameter`, `@Content` annotations  
✓ **Logging:** `log.atInfo()`, `log.atError()` with contextual info  

## No Breaking Changes

- New endpoint only; no modifications to existing endpoints
- No database migrations
- No configuration changes required
- Fully backward compatible

## Deliverables

1. `SessionList.java` — Response DTO
2. `SessionConfigController.listSessions()` — New endpoint method
3. Test methods in `SessionConfigControllerTest.java`

## Future Extensions

This design is extensible for:
- Pagination (add `page`, `size` query params, update `SessionList` with metadata)
- Filtering (add query params for session status, creation date, etc.)
- Sorting (add `sortBy` query param)
- Session metadata (enhance `SessionList` or return individual `SessionInfo` objects)
