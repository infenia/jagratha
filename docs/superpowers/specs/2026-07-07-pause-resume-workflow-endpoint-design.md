# Pause/Resume Workflow REST Endpoints — Design

## Context

`ControlBusGateway` already defines `pauseWorkflow(executionId)` and `resumeWorkflow(executionId)`, implemented in `DefaultControlBusGateway` and fully wired through to `PauseWorkflowCommandProcessor` / `ResumeWorkflowCommandProcessor`, which toggle a per-execution `ReactiveControlValve` (global backpressure gate) and emit a `PAUSED`/`RUNNING` status event. No controller currently exposes these operations over REST — `WorkflowController` only exposes start, stop (bulk and single execution), status, status streaming, and history.

## Problem found during verification

While tracing `pauseWorkflow`/`resumeWorkflow` to verify they are "fully implemented and working," a validation gap was found:

- `pauseWorkflow`/`resumeWorkflow` call `executeCommand` → `emit` → `controlSink.emitNext(...)` and return success as soon as the command is accepted onto the internal control bus (`Mono.create` completes via `sink.success()` immediately).
- The actual check for whether `executionId` refers to a real, tracked execution happens later and asynchronously, inside `PauseWorkflowCommandProcessor.process()` / `ResumeWorkflowCommandProcessor.process()`, via `registry.findByExecutionId(...).orElseThrow(...)`.
- Any exception thrown there is caught by `DirectiveDispatcher.init()`'s `.onErrorResume(e -> { log.atError()...; return Mono.empty(); })`, which logs and swallows it — it never propagates back to the original caller.
- Net effect: calling `pauseWorkflow`/`resumeWorkflow` with a bogus or already-completed `executionId` returns a successful `Mono<Void>` with no indication the operation didn't actually do anything.

This differs from `stopExecution`, which validates synchronously first:
```java
Mono.fromSupplier(() -> executionControlRegistry.findByExecutionId(executionId)
        .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + executionId)))
    .flatMap(control -> executeCommand(...))
```

**Decision:** Fix `pauseWorkflow` and `resumeWorkflow` in `DefaultControlBusGateway` to use the same synchronous-validation pattern as `stopExecution`, so the error surfaces through the Mono's error channel and the new REST endpoints can return a correct 404.

**Known related gap, out of scope:** `pauseNode`, `resumeNode`, `skipNode`, `enableStepMode`, `disableStepMode`, `stepNode` appear to share the same fire-and-forget shape (no pre-validation before `executeCommand`). None of these are currently exposed by any controller, so fixing them is not part of this change. Flagged here for future reference.

## Design

### 1. Gateway fix — `core/src/main/java/com/infenia/yukta/service/control/gateway/DefaultControlBusGateway.java`

Change `pauseWorkflow` and `resumeWorkflow` to validate the execution exists before emitting the command, mirroring `stopExecution`:

```java
@Override
public Mono<Void> pauseWorkflow(final String executionId) {
  return Mono.fromSupplier(
          () -> executionControlRegistry.findByExecutionId(executionId)
              .orElseThrow(() -> new IllegalArgumentException(
                  "Execution not found: " + executionId)))
      .flatMap(control -> executeCommand(
          buildCommand(new PauseWorkflowCommand(executionId), CONTROL_COMMAND_PRIORITY)))
      .doOnSubscribe(_ -> log.atInfo().addKeyValue("executionId", executionId).log("Pausing workflow"))
      .doOnSuccess(_ -> log.atDebug().addKeyValue("executionId", executionId).log("Workflow pause command executed"))
      .doOnError(err -> log.atError().setCause(err).addKeyValue("executionId", executionId).log("Failed to pause workflow"));
}
```

Same shape for `resumeWorkflow`, substituting `ResumeWorkflowCommand` and log messages.

### 2. Controller endpoints — `web/src/main/java/com/infenia/yukta/controller/WorkflowController.java`

Two new endpoints, following the exact structure of the existing `stopExecution` method (structured logging at each reactive stage, `ApiResponse` wrapper, `onErrorResume` → 404 mapping):

- `POST /api/workflow/{sessionId}/{executionId}/pause`
- `POST /api/workflow/{sessionId}/{executionId}/resume`

`sessionId` is accepted in the path for consistency with the rest of the controller's URL conventions but is not passed to the gateway call, since `pauseWorkflow`/`resumeWorkflow` only take `executionId` (same asymmetry already present between `stopWorkflow(sessionId, workflowId, reason)` and `stopExecution(executionId, reason)`).

Response body reuses the existing `WorkflowStartResponse(executionId)` record (already used by `stopExecution` for the same `{executionId}` shape) — no new DTO needed.

Error handling: catch and map to 404 via `ApiResponse.error(404, NOT_FOUND, "Execution not found", path, errors)`, consistent with `stopExecution`.

Swagger annotations (`@Operation`, `@ApiResponse` 200/404) follow the pattern already used throughout the class.

### 3. Tests

- `core/src/test/java/com/infenia/yukta/service/control/gateway/DefaultControlBusGatewayTest.java`: extend `pauseWorkflow`/`resumeWorkflow` test cases to cover (a) valid execution → command emitted, (b) unknown execution → `IllegalArgumentException` propagated without emitting.
- `web/src/test/java/com/infenia/yukta/controller/WorkflowControllerTest.java`: add tests for both new endpoints — 200 success path and 404 not-found path — following the existing `stopExecution` test structure.

## Out of scope

- Fixing the equivalent validation gap in `pauseNode`, `resumeNode`, `skipNode`, `enableStepMode`, `disableStepMode`, `stepNode`.
- Exposing any node-level control endpoints (pause/resume/skip/step a single node) over REST — only workflow-level pause/resume are being added.
