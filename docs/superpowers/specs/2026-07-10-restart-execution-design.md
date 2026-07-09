# SPDX-License-Identifier: Apache-2.0
# SPDX-FileCopyrightText: 2026 Infenia Private Limited

# Restart Execution — Design

## Context

`ControlBusGateway` declares and `DefaultControlBusGateway` implements `restartWorkflow(executionId)`
and `restartFromNode(executionId, fromNodeId)`. Both compile, run, and have unit tests, but they are
unreachable from outside `core` — no REST endpoint, MCP tool, or other caller invokes them — and their
implementation has two real bugs, confirmed by reading `DefaultControlBusGateway`,
`RestartCommandProcessor`, `RestartFromNodeCommandProcessor`, and `DirectiveDispatcher`:

1. **Fabricated execution ID.** Each gateway method generates `newExecutionId` locally via
   `UUID.randomUUID()` and returns it as soon as the command is emitted onto the control bus — before
   the restart has actually happened. The processor that does the real work
   (`RestartCommandProcessor` / `RestartFromNodeCommandProcessor`) generates its **own separate**
   `UUID.randomUUID()` for the execution it actually starts. The two IDs never match, so a caller
   cannot use the ID `restartWorkflow` returns to `watchExecution`, `getCurrentProgress`, or anything
   else — it refers to nothing.
2. **Silently swallowed failures.** Both processors wrap their work in
   `.onErrorResume(e -> { log...; return Mono.empty(); })`. If `executionId` doesn't exist in
   `ExecutionControlRegistry`, the processor logs a warning and completes empty — the gateway's Mono
   still completes successfully with its fabricated ID, so the caller has no way to know the restart
   never happened.

A third, related finding: `RestartCommandProcessor` / `RestartFromNodeCommandProcessor` implement
`ControlSignalProcessor.process()`, whose contract is to return a `WorkflowDirective` for
`DirectiveDispatcher` to apply. Instead, both processors perform the entire restart inline (unregister,
trip the safe-stop sink, call `orchestrator.execute`/`restartFromNode`) and return
`Mono.<WorkflowDirective>empty()`. This means `DirectiveDispatcher.applyRestart` /
`applyRestartFromNode` (`DirectiveDispatcher.java:166-246`) can never run under current wiring — no
processor ever produces the `WorkflowDirective.Restart` / `RestartFromNode` they match on. This is
dead code today, not a second active implementation.

The checkpoint mechanism `restartFromNode` depends on is real and working:
`StreamTopologyDecorator.getMessageFlux` saves a checkpoint via `NodeCheckpointStore.save(...)` for
every message on every node's stream during normal execution, and
`RestartFromNodeCommandProcessor` reads the restart node's direct parents' checkpoints via
`NodeCheckpointStore.get(...)` to build the replay input. No changes are needed there.

There is no existing correlation-ID / pending-response mechanism anywhere in the control bus to reuse.
`sendCommand` looks like a request/response primitive but is architecturally unrelated — it's a direct
`plugin.onControlSignal(...)` call resolved via `ActivePluginRegistry` lookup, not a bus round-trip.
`RestartCommand` / `RestartFromNodeCommand` are plain in-memory records (no `@Json*` annotations, no
wire serialization anywhere) passed through an in-JVM `Sinks.Many` — free to extend with new fields.

## Goals

- `restartWorkflow(executionId)` and `restartFromNode(executionId, fromNodeId)` return the *real* new
  execution ID, only after the restart has actually started successfully.
- If `executionId` doesn't exist (or isn't currently live), the caller gets a real error instead of a
  fabricated success.
- Delete the dead `DirectiveDispatcher.applyRestart` / `applyRestartFromNode` code path.
- Add REST endpoints so these operations are reachable, following `WorkflowController`'s existing
  conventions exactly.

## Non-goals

- Restarting an execution that has already finished and been unregistered from
  `ExecutionControlRegistry`. Only live executions can be restarted — this matches what's actually
  possible today, since the original trigger payload and compiled `PreparedWorkflow` only live on the
  in-memory `ExecutionControl` record while the execution is registered. Supporting restart of
  finished executions would require a persistent payload store keyed by executionId and is a separate
  effort.
- Any change to `WorkflowOrchestrator.restartFromNode`'s checkpoint-replay/bypass logic — it already
  works and is tested (`WorkflowOrchestratorTest`).
- Any change to `NodeCheckpointStore` or its writer in `StreamTopologyDecorator`.
- MCP tool wiring — out of scope for this change; REST is the only new caller surface.

## Design

### 1. Command records gain the pre-generated new execution ID

`plugin-api/.../ExecutionControlCommand.java`:

```java
record RestartCommand(String executionId, String newExecutionId)
    implements ExecutionControlCommand { ... }

record RestartFromNodeCommand(String executionId, String fromNodeId, String newExecutionId)
    implements ExecutionControlCommand { ... }
```

The gateway generates `newExecutionId` once and passes it in, instead of the processor minting its
own. This follows the existing multi-field record pattern (`StopNodeCommand` already carries 4
fields).

### 2. A per-restart await mechanism in `DefaultControlBusGateway`

Nothing in the codebase today lets a gateway method wait for an async control-bus command to finish
and learn its outcome (`sendCommand` is a different, direct-call mechanism — see Context). This is new
machinery, scoped as narrowly as possible:

```java
private final Map<String, Sinks.One<String>> pendingRestarts = new ConcurrentHashMap<>();
```

Keyed by `newExecutionId` (not the old `executionId`), so concurrent restarts of the same execution
never collide on the same key.

`restartWorkflow` / `restartFromNode`:

1. Generate `newExecutionId`.
2. Register `Sinks.One<String> sink = Sinks.one()` in `pendingRestarts` under `newExecutionId`.
3. Emit the command (as today).
4. `return sink.asMono().timeout(RESTART_TIMEOUT).doFinally(_ -> pendingRestarts.remove(newExecutionId));`

`RESTART_TIMEOUT` is a new `private static final Duration` constant on the class (30 seconds),
consistent with the existing `CONTROL_COMMAND_PRIORITY`-style constants already declared there.

### 3. Processors resolve the sink instead of swallowing errors

`RestartCommandProcessor` / `RestartFromNodeCommandProcessor` keep doing the actual restart work
inline (unregister, trip safe-stop sink, call `orchestrator.execute` / `orchestrator.restartFromNode`)
— that part already works and is tested. What changes:

- On success: resolve the gateway's pending sink with the real `newExecutionId` (the one taken from
  the command, not a freshly minted one) via `emitValue`.
- On failure (`execution not found`, or `orchestrator.execute`/`restartFromNode` erroring): resolve
  the sink with an error via `emitError`, instead of `onErrorResume(...) -> Mono.empty()`.

The processor needs a way to reach the gateway's `pendingRestarts` map. Since both live in `core` and
the processor is already a `@Component` that could take a collaborator, the cleanest seam is a small
new interface the gateway implements and the processors depend on:

```java
public interface RestartCompletionSink {
  void completeRestart(String newExecutionId, @Nullable Throwable error);
}
```

`DefaultControlBusGateway` implements it (resolving the map entry); `RestartCommandProcessor` and
`RestartFromNodeCommandProcessor` take it as a constructor dependency via `@RequiredArgsConstructor`.
This avoids a circular dependency on the full `ControlBusGateway` interface and keeps the processors'
contract narrow and explicit.

### 4. Delete dead code

`DirectiveDispatcher.applyRestart` and `applyRestartFromNode` (`DirectiveDispatcher.java:166-246`) are
removed, along with `WorkflowDirective.Restart` / `RestartFromNode` if nothing else constructs them
(to be confirmed during implementation — a grep across `core` should be the only check needed since
these are sealed-interface records local to `plugin-api`). Corresponding dead tests in
`DirectiveDispatcherTest` (`applyDirective_restartDirective_...`, `applyRestart_...`,
`applyRestartFromNode_...`, and the restart-command dispatch assertions) are removed too.

### 5. REST endpoints in `WorkflowController`

Two new endpoints, modeled directly on the existing `stopExecution` endpoint
(`WorkflowController.java:212-259`) rather than `executeControlSignal`
(`WorkflowController.java:275-320`) — `executeControlSignal`'s `action` supplier returns `Mono<Void>`
and always echoes back the *input* `executionId`, but restart needs to return a *new*, different ID
from a `Mono<String>` action, same shape as `stopExecution`:

```java
@PostMapping("/workflow/executions/{executionId}/restart")
public Mono<ResponseEntity<ApiResponse<WorkflowStartResponse>>> restartWorkflow(
    @PathVariable final String executionId, final ServerWebExchange exchange) {
  return controlBus.restartWorkflow(executionId)
      .map(newId -> ResponseEntity.ok(
          ApiResponse.success(200, "Workflow restart accepted", new WorkflowStartResponse(newId))))
      .onErrorResume(e -> /* same 404 mapping shape as stopExecution */);
}

@PostMapping("/workflow/executions/{executionId}/restart/{fromNodeId}")
public Mono<ResponseEntity<ApiResponse<WorkflowStartResponse>>> restartFromNode(
    @PathVariable final String executionId,
    @PathVariable final String fromNodeId,
    final ServerWebExchange exchange) {
  return controlBus.restartFromNode(executionId, fromNodeId)
      .map(newId -> ResponseEntity.ok(
          ApiResponse.success(200, "Workflow restart from node accepted",
              new WorkflowStartResponse(newId))))
      .onErrorResume(e -> /* same 404 mapping shape as stopExecution */);
}
```

Both return `WorkflowStartResponse(newExecutionId)` — the real new ID, now that the gateway actually
waits for it. No new DTO needed. `IllegalArgumentException` (not-found) and `TimeoutException` both
flow through `onErrorResume` to a 404, matching how other endpoints in this controller collapse
control-bus failures to 404 today.

Like `stopExecution`, these endpoints take only `executionId` in the path (no `sessionId`) and do not
perform a session-ownership check — this matches `stopExecution`'s existing behavior exactly and is
not a new inconsistency introduced by this change.

## Error handling

- **Not found**: processor's existing `registry.findByExecutionId(...).orElseThrow(...)` now feeds
  `completeRestart(newExecutionId, error)` instead of being caught and dropped. Gateway propagates the
  real `IllegalArgumentException("Execution not found: ...")`.
- **Timeout**: if the processor never resolves the sink (e.g., dispatcher never picks up the command),
  `sink.asMono().timeout(RESTART_TIMEOUT)` fails clearly instead of hanging.
- **Cleanup**: `doFinally` removes the map entry on success, error, or timeout — no leaked entries.
- **Concurrent restarts**: keying by `newExecutionId` (unique per call) means two concurrent restarts
  of the same `executionId` never collide.
- **`restartFromNode` unknown node**: unchanged — `WorkflowOrchestrator.restartFromNode`'s existing
  bypass behavior (`Flux.empty()` for nodes without a checkpoint) is out of scope.

## Testing

- `DefaultControlBusGatewayTest`: rewrite the four existing restart tests (lines ~842-908,
  ~1427-1454) to verify the gateway now awaits and returns the ID the *processor* resolves (simulated
  via calling `completeRestart` directly in the test, since `RestartCompletionSink` is the seam), plus
  new cases for not-found propagation and timeout.
- `RestartCommandProcessorTest`, `RestartFromNodeCommandProcessorTest`: update to assert
  `completeRestart(newExecutionId, null)` on success and `completeRestart(newExecutionId, error)` on
  failure, instead of asserting swallowed `Mono.empty()`.
- `DirectiveDispatcherTest`: remove the restart-related cases listed in Design §4.
- `WorkflowControllerTest`: new cases for both endpoints — success path (returns new ID, 200) and
  not-found path (404), matching the existing pattern used for `stopExecution`/step-mode endpoint
  tests.

## Out of scope / explicitly deferred

- Persistent restart of finished executions (see Non-goals).
- MCP tool exposure of restart.
- Fixing `stopExecution`'s lack of session-ownership check — pre-existing behavior, unrelated to this
  change, not touched here.
