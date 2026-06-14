# Workflow Definition Store — Design Spec

**Date:** 2026-06-14
**Branch:** orchestrator-revamp

## Problem

`ControlBusService.prepareWorkflow()` compiles a `WorkflowDefinition` into a `PreparedWorkflow` but stores neither. `WorkflowService.runWorkflow()` recompiles from `SessionConfigStore` on every execution. After a server restart, nothing survives. `WorkflowDefinition` is redundantly stored in `SessionConfigStore` (mixed into session config) with no clean interface boundary for future persistence backends.

## Goals

- Single source of truth for `WorkflowDefinition`, keyed by `sessionId + workflowId`
- Survive server restarts (in-memory now; file/DB impl slots in later via interface)
- Cache compiled `PreparedWorkflow` in memory with TTL eviction to avoid recompilation on every run
- Remove `WorkflowDefinition` storage from `SessionConfigStore` — eliminate redundancy

## Out of Scope

- File or database persistence (future impl, interface designed for it)
- Cross-session workflow sharing
- Distributed cache

---

## New Components — `service/workflow/store/`

### `WorkflowDefinitionStore` (interface)

```java
Mono<Void> save(String sessionId, WorkflowDefinition definition);
Mono<WorkflowDefinition> find(String sessionId, String workflowId);
Mono<Map<String, WorkflowDefinition>> findAll(String sessionId);
Mono<Void> remove(String sessionId, String workflowId);
Mono<Void> removeAll(String sessionId);
```

Reactive contract. Mirrors the shape of the removed `SessionConfigStore` workflow methods. Future file/DB implementations slot in without touching callers.

### `InMemoryWorkflowDefinitionStore` (`@Component`)

- `ConcurrentHashMap<String, Map<String, WorkflowDefinition>>` — outer key: `sessionId`, inner key: `workflowId`
- All methods return `Mono` wrapping synchronous map operations (no I/O)
- `find()` on unknown key returns `Mono.empty()`

### `PreparedWorkflowCache` (`@Component`)

```java
void put(String sessionId, String workflowId, PreparedWorkflow prepared);
Optional<PreparedWorkflow> get(String sessionId, String workflowId);
void invalidate(String sessionId, String workflowId);
void invalidateAll(String sessionId);
```

- Backed by `ConcurrentHashMap<String, CacheEntry>` where key = `sessionId + "\0" + workflowId`
- `CacheEntry` holds `PreparedWorkflow` + `lastAccessTime` (milliseconds)
- `get()` updates `lastAccessTime` (TTL resets on active use)
- `@PostConstruct` starts a `ScheduledExecutorService` (daemon thread, same pattern as `InMemoryAggregateStore`) that runs every 60s and evicts entries where `now - lastAccessTime > workflow.cache.ttl.ms`
- Config property: `workflow.cache.ttl.ms` (default: 600000 — 10 minutes)
- Not reactive — synchronous access, no I/O

---

## Wiring Changes

### `PrepareWorkflowCommand`

```java
public record PrepareWorkflowCommand(String sessionId, WorkflowDefinition workflowDefinition) {}
```

### `ControlBusGateway` / `DefaultControlBusGateway`

`prepareWorkflow(WorkflowDefinition)` → `prepareWorkflow(String sessionId, WorkflowDefinition)`

### `ControlBusService.prepareWorkflow()`

Orchestration point (sequential, fail-fast):

1. `WorkflowDefinitionStore.save(sessionId, definition)` — if this fails, stop
2. `PreparedWorkflowCache.invalidate(sessionId, workflowId)` — evict stale compiled form
3. `WorkflowOrchestrator.prepareWorkflow(definition)` — compile
4. `PreparedWorkflowCache.put(sessionId, workflowId, result)` — warm cache
5. Return `Mono<Void>`

### `WorkflowService.runWorkflow()`

Replace `configService.getWorkflow(sessionId, workflowId)` + recompile every time with:

1. `PreparedWorkflowCache.get(sessionId, workflowId)`
   - **Hit**: update `lastAccessTime`, execute directly
   - **Miss**: `WorkflowDefinitionStore.find(sessionId, workflowId)` → compile → cache → execute
   - **Miss + store miss**: `Mono.error(new IllegalArgumentException("Workflow not found: sessionId/workflowId"))`

### `SessionConfigStore` (interface + both impls)

Remove:
- `getWorkflow(sessionId, workflowId)`
- `getWorkflows(sessionId)`
- `setWorkflows(sessionId, workflows)`

`InMemorySessionConfigStore`:
- Drop `workflowsMap` field
- `applySessionConfig()` delegates workflow saving: `workflowDefinitionStore.save(sessionId, def)` per entry in `data.workflows()`

`FileSessionConfigStore`:
- `SessionConfig` inner class retains `workflows` field for JSON compatibility (existing files still parse)
- `applySessionConfig()` saves definition data to `WorkflowDefinitionStore`, not to local map
- `getWorkflows()` / `getWorkflow()` / `setWorkflows()` removed from impl

### `DefaultWorkflowGateway`

```java
// before
configService.getWorkflows(parentSessionId)
    .flatMap(wfs -> configService.setWorkflows(childSessionId, wfs))

// after
workflowDefinitionStore.findAll(parentSessionId)
    .flatMapMany(wfs -> Flux.fromIterable(wfs.values()))
    .flatMap(def -> workflowDefinitionStore.save(childSessionId, def))
    .then()
```

### `SessionService.getSessionWorkflow()`

`configService.getWorkflow(sessionId, workflowId)` → `workflowDefinitionStore.find(sessionId, workflowId)`

---

## Data Flow

### Prepare path

```
caller
  → ControlBusGateway.prepareWorkflow(sessionId, definition)
    → ControlBusService.prepareWorkflow(PrepareWorkflowCommand(sessionId, definition))
      → WorkflowDefinitionStore.save(sessionId, definition)
      → PreparedWorkflowCache.invalidate(sessionId, workflowId)
      → WorkflowOrchestrator.prepareWorkflow(definition)
      → PreparedWorkflowCache.put(sessionId, workflowId, result)
```

### Execute path

```
WorkflowService.runWorkflow(sessionId, workflowId, payload)
  → PreparedWorkflowCache.get(sessionId, workflowId)
      hit  → update lastAccessTime → execute
      miss → WorkflowDefinitionStore.find(sessionId, workflowId)
               hit  → compile → cache → execute
               miss → Mono.error(IllegalArgumentException)
```

### Session config apply path

```
SessionConfigStore.applySessionConfig(data)
  → store projectPath, description, initiator, tags, initiatedTime (unchanged)
  → WorkflowDefinitionStore.save(sessionId, def) per workflow in data.workflows()
```

### TTL eviction

```
ScheduledExecutorService (every 60s)
  → scan PreparedWorkflowCache entries
  → evict where now - lastAccessTime > workflow.cache.ttl.ms
```

Cache miss after eviction triggers transparent recompilation — no error path needed.

---

## Error Handling

| Scenario | Behaviour |
|---|---|
| `WorkflowDefinitionStore.save()` fails in `prepareWorkflow` | Propagated to caller; cache invalidation and compilation do not proceed |
| `WorkflowDefinitionStore.find()` returns empty during execute | `Mono.error(IllegalArgumentException("Workflow not found: sessionId/workflowId"))` |
| Compilation fails on cache-miss recompile | Propagated as-is; cache not populated; next run retries |
| TTL eviction removes entry mid-execution | Execution holds reference to `PreparedWorkflow` already — eviction has no effect on in-flight runs |

---

## Testing Strategy

### `WorkflowDefinitionStoreTest`

- `save` then `find` returns definition
- `find` on unknown key → `Mono.empty()`
- `remove` then `find` → `Mono.empty()`
- `removeAll` clears session, leaves other sessions intact
- `findAll` returns all workflows for session
- All verified with `StepVerifier`

### `PreparedWorkflowCacheTest`

- `put` then `get` returns prepared workflow
- `invalidate` then `get` returns empty
- TTL eviction: put entry, advance clock past TTL, verify eviction removes it
- `lastAccessTime` resets on `get` — entry survives TTL if accessed within window

### `ControlBusServiceTest`

- `prepareWorkflow()` calls `save()` with correct `sessionId` + definition
- Cache invalidated before compilation
- Cache populated after successful compilation
- Failure in `save()` short-circuits — compilation not called

### `WorkflowServiceTest`

- Cache hit: `WorkflowDefinitionStore.find()` not called, `WorkflowOrchestrator.prepareWorkflow()` not called
- Cache miss: definition loaded, compiled, cached, executed
- Cache miss + store miss: `Mono.error(IllegalArgumentException)` propagated

### `SessionConfigStore` impls (updated)

- Remove workflow assertions from existing `InMemorySessionConfigStoreTest` and `FileSessionConfigStoreTest`
- Add assertion: `applySessionConfig()` delegates workflow saving to `WorkflowDefinitionStore` (via mock)

---

## Files Created

| File | Type |
|---|---|
| `service/workflow/store/WorkflowDefinitionStore.java` | Interface |
| `service/workflow/store/InMemoryWorkflowDefinitionStore.java` | Component |
| `service/workflow/store/PreparedWorkflowCache.java` | Component |

## Files Modified

| File | Change |
|---|---|
| `service/control/command/PrepareWorkflowCommand.java` | Add `sessionId` field |
| `service/control/gateway/ControlBusGateway.java` | Add `sessionId` param to `prepareWorkflow` |
| `service/control/gateway/DefaultControlBusGateway.java` | Add `sessionId` param, wire `WorkflowDefinitionStore` |
| `service/control/ControlBusService.java` | Orchestrate save → invalidate → compile → cache |
| `service/WorkflowService.java` | Cache-first lookup in `runWorkflow()`, remove `configService` workflow calls |
| `service/session/SessionConfigStore.java` | Remove 3 workflow methods |
| `service/session/InMemorySessionConfigStore.java` | Remove `workflowsMap`, delegate to store in `applySessionConfig` |
| `service/session/FileSessionConfigStore.java` | Remove workflow methods, delegate in `applySessionConfig` |
| `service/DefaultWorkflowGateway.java` | Use `WorkflowDefinitionStore` for session workflow copy |
| `service/session/SessionService.java` | Switch `getSessionWorkflow` to `WorkflowDefinitionStore` |
