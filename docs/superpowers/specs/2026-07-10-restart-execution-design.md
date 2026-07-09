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

A fourth, independent bug (confirmed by tracing `WorkflowOrchestrator.execute` through
`WorkflowCompiler.compileTemplate` into `ResourceManagementBuilder.buildTerminalMono`, which returns
`Mono.whenDelayError(terminals)` — a Mono that only completes once every node in the DAG reaches a
terminal state): **`orchestrator.execute(...)`'s returned `Mono<Void>` does not complete until the
entire workflow finishes**, not once it starts. `DefaultControlBusGateway.prepareAndExecute` (used by
`startWorkflow`) already knows this and detaches it —
`.subscribeOn(Schedulers.boundedElastic()).subscribe()` — returning the execution ID immediately while
the workflow runs independently in the background. `RestartCommandProcessor.process()` does not: it
chains `orchestrator.execute(...)` directly into its own returned `Mono<WorkflowDirective>`
(`RestartCommandProcessor.java:62-79`), so `taskTracker.emitWorkflowStatusEvent(newExecutionId,
"RUNNING")` in its `doOnSuccess` fires only after the *entire restarted workflow finishes* —
"RUNNING" emitted at the end, not the start. `DirectiveDispatcher`'s own class javadoc says restart
"is fire-and-forget... a new execution is subscribed independently" — confirming this is a bug against
the documented intent, not a deliberate design. `RestartFromNodeCommandProcessor` has the equivalent
issue with `orchestrator.restartFromNode(...)`.

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
  execution ID, as soon as the restarted execution has actually been subscribed/started — not
  fabricated up front, and not delayed until the new execution finishes.
- If `executionId` doesn't exist (or isn't currently live), the caller gets a real error instead of a
  fabricated success.
- Fix `RestartCommandProcessor` / `RestartFromNodeCommandProcessor` to detach `orchestrator.execute` /
  `orchestrator.restartFromNode` the same way `prepareAndExecute` already does, so restart returns
  promptly and `taskTracker.emitWorkflowStatusEvent(..., "RUNNING")` fires at start, not at the end of
  the restarted run.
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
consistent with the existing `CONTROL_COMMAND_PRIORITY`-style constants already declared there. The
command priority itself (`CONTROL_COMMAND_PRIORITY + 20`, already used by both methods today, matching
`stopExecution`'s priority) is unchanged — restart should preempt normal traffic the same way stop
does, and this isn't part of the bug being fixed.

### 3. Processors detach execution and resolve the sink at start, not at finish

`RestartCommandProcessor` / `RestartFromNodeCommandProcessor` keep doing the pre-execute work inline
(look up the live `ExecutionControl`, unregister, trip the safe-stop sink) — that part already works
and is tested. Two things change:

**a. Detach the actual execution**, matching `prepareAndExecute`'s existing pattern exactly, instead
of chaining it into the processor's own returned `Mono`:

```java
registry.unregister(control.executionId());
control.safeStopSink().emitEmpty(Sinks.EmitFailureHandler.FAIL_FAST);

orchestrator
    .execute(control.sessionId(), control.workflowId(), restart.newExecutionId(),
        control.prepared(), control.payload())
    .subscribeOn(Schedulers.boundedElastic())
    .doOnSuccess(ignored -> taskTracker.emitWorkflowStatusEvent(restart.newExecutionId(), "RUNNING"))
    .doOnError(err -> log.atError().setCause(err)...log("Restarted execution failed"))
    .subscribe();

completionSink.completeRestartSuccess(restart.newExecutionId());
return Mono.<WorkflowDirective>empty();
```

`completeRestartSuccess` now fires immediately after the new execution is subscribed (mirroring
`prepareAndExecute` returning the ID immediately after `.subscribe()`), not after the workflow
finishes. `taskTracker`'s "RUNNING" event now genuinely marks the start, fixing the fourth bug from
Context. Node failures during the restarted run still surface exactly as they do for any other
execution (task tracker status events, `watchExecution`) — restart's caller doesn't need to stay
subscribed to know that.

**b. Resolve the sink for failures that happen before detachment** (`execution not found in registry`
is the only such case today) via `completeRestartFailure(newExecutionId, error)`, replacing
`onErrorResume(...) -> Mono.empty()`.

`process()` still returns `Mono<WorkflowDirective>` per the `ControlSignalProcessor` contract, and
still completes empty in both branches — but that's now accurate, not a workaround: there is no
`WorkflowDirective` left for `DirectiveDispatcher` to apply once §4 removes
`applyRestart`/`applyRestartFromNode`.

The processor needs a way to reach the gateway's `pendingRestarts` map. Since both live in `core` and
the processor is already a `@Component` that could take a collaborator, the cleanest seam is a small
new interface the gateway implements and the processors depend on. Two explicit methods rather than a
nullable-`Throwable` flag parameter, matching this codebase's preference for clear, non-ambiguous
signatures over sentinel values:

```java
public interface RestartCompletionSink {
  void completeRestartSuccess(String newExecutionId);

  void completeRestartFailure(String newExecutionId, Throwable error);
}
```

`DefaultControlBusGateway` implements it; `RestartCommandProcessor` and `RestartFromNodeCommandProcessor`
take it as a constructor dependency via `@RequiredArgsConstructor`. This avoids a circular dependency on
the full `ControlBusGateway` interface and keeps the processors' contract narrow and explicit.

**Race with timeout.** The gateway's `pendingRestarts` entry can be removed by the timeout's
`doFinally` before the processor finishes (e.g., dispatcher backlog delays processing past
`RESTART_TIMEOUT`). Both `completeRestartSuccess`/`completeRestartFailure` do a plain
`Map.remove(newExecutionId)` to fetch-and-clear the sink; if it's already gone (`null`), they log at
debug level and return — the caller already received a `TimeoutException` and the late result is
discarded. This is the same "best-effort, caller already moved on" shape as a normal HTTP client
timeout. Resolving the sink itself uses `tryEmitValue`/`tryEmitError` and logs a warning if the emit
doesn't succeed (defensive only — a freshly-removed `Sinks.One` with a single writer shouldn't fail to
emit in practice).

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
  `completeRestartFailure(newExecutionId, error)` instead of being caught and dropped. Gateway
  propagates the real `IllegalArgumentException("Execution not found: ...")`.
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
  via calling `completeRestartSuccess`/`completeRestartFailure` directly in the test, since
  `RestartCompletionSink` is the seam), plus new cases for: not-found propagation, timeout when nothing
  ever resolves the sink, and a late `completeRestart*` call after timeout being a no-op (race case from
  §3).
- `RestartCommandProcessorTest`, `RestartFromNodeCommandProcessorTest`: update to assert
  `completeRestartSuccess(newExecutionId)` on success and `completeRestartFailure(newExecutionId, error)`
  on failure, instead of asserting swallowed `Mono.empty()`. Add a case asserting
  `completeRestartSuccess` fires as soon as `orchestrator.execute`/`restartFromNode` is *subscribed*,
  independent of when that Mono actually completes (e.g. using a `Sinks.One`-backed test double for
  the orchestrator call that's never manually completed during the test, proving `process()` doesn't
  wait on it) — this is the regression test for the fourth bug in Context.
- `DirectiveDispatcherTest`: remove the restart-related cases listed in Design §4.
- `WorkflowControllerTest`: new cases for both endpoints — success path (returns new ID, 200) and
  not-found path (404), matching the existing pattern used for `stopExecution`/step-mode endpoint
  tests.

## Out of scope / explicitly deferred

- Persistent restart of finished executions (see Non-goals).
- MCP tool exposure of restart.
- Fixing `stopExecution`'s lack of session-ownership check — pre-existing behavior, unrelated to this
  change, not touched here.
