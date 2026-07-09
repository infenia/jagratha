# Restart Execution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `ControlBusGateway.restartWorkflow`/`restartFromNode` return the real new execution ID (not a fabricated one), propagate real failures instead of swallowing them, return promptly instead of blocking until the restarted workflow finishes, delete the dead `DirectiveDispatcher` restart code path, and expose both operations as REST endpoints.

**Architecture:** Thread a gateway-generated `newExecutionId` through the command records into the processors. Add a `RestartCompletionSink` interface (implemented by the gateway, consumed by the processors) backed by a `ConcurrentHashMap<String, Sinks.One<String>>` keyed by `newExecutionId`, so the gateway can synchronously await the processor's real outcome instead of returning immediately after emit. Processors detach the actual `orchestrator.execute`/`restartFromNode` call (`subscribeOn(Schedulers.boundedElastic()).subscribe()`, mirroring `DefaultControlBusGateway.prepareAndExecute`) so the completion signal fires right after subscription, not after the whole workflow finishes. `WorkflowController` gets two new endpoints modeled on the existing `stopExecution` endpoint.

**Tech Stack:** Java 25, Spring Boot WebFlux, Project Reactor (`Mono`, `Sinks.One`), JUnit 5, Mockito, `reactor-test` (`StepVerifier`), `WebTestClient`.

## Global Constraints

- Every Java file must carry the Apache License 2.0 header (Spotless-managed) — copy it verbatim from an existing file in the same module (`// SPDX-License-Identifier: Apache-2.0` / `// SPDX-FileCopyrightText: 2026 Infenia Private Limited`).
- Google Java Style: 2-space indent, 100-char line limit.
- Run `./gradlew spotlessApply` before considering any task done, and the relevant `./gradlew :<module>:test` to verify.
- Follow Conventional Commits (`fix:`, `feat:`, `test:`, `refactor:`) per `.claude/rules/git-workflow.md`.
- Design spec: `docs/superpowers/specs/2026-07-10-restart-execution-design.md` — every task below implements a section of it.
- Work happens on branch `feat/restart-execution`, created from `main`.

---

### Task 0: Create the feature branch

- [ ] **Step 1: Create and switch to the branch**

```bash
git checkout main
git pull
git checkout -b feat/restart-execution
```

---

### Task 1: Add `newExecutionId` to the restart command records

**Files:**
- Modify: `plugin-api/src/main/java/com/infenia/yukta/plugin/control/ExecutionControlCommand.java:184-206`
- Test: none directly (records have no independent test file; covered by downstream tasks)

**Interfaces:**
- Produces: `RestartCommand(String executionId, String newExecutionId)` and `RestartFromNodeCommand(String executionId, String fromNodeId, String newExecutionId)`, both still implementing `ExecutionControlCommand` (`executionId()` returns the *old* execution ID, matching the interface contract used by `DirectiveDispatcher.dispatch`/`registry.findByExecutionId`).

This is a pure data-shape change with no new behavior to test in isolation — its effects are verified by the tests in Tasks 3, 4, and 5, which will not compile until this change lands. Do it first so every other task's code samples type-check against the final shape.

- [ ] **Step 1: Change the two record declarations**

Replace lines 179–206 of `ExecutionControlCommand.java`:

```java
  /**
   * Stop the current execution and restart from the beginning with the original payload.
   *
   * <p>A new {@code executionId} is generated for the restarted execution.
   */
  record RestartCommand(String executionId) implements ExecutionControlCommand {
    @Override
    public String commandType() {
      return "execution.restart";
    }
  }

  /**
   * Stop the current execution and restart from a specific node.
   *
   * <p>The restart uses the last checkpoint messages from the target node's direct parents,
   * allowing partial replay. A new {@code executionId} is generated.
   *
   * @param executionId the execution to restart
   * @param fromNodeId the node from which to resume execution
   */
  record RestartFromNodeCommand(String executionId, String fromNodeId)
      implements ExecutionControlCommand {
    @Override
    public String commandType() {
      return "execution.restart-from-node";
    }
  }
```

with:

```java
  /**
   * Stop the current execution and restart from the beginning with the original payload.
   *
   * @param executionId the execution to restart
   * @param newExecutionId the pre-generated identifier for the restarted execution
   */
  record RestartCommand(String executionId, String newExecutionId)
      implements ExecutionControlCommand {
    @Override
    public String commandType() {
      return "execution.restart";
    }
  }

  /**
   * Stop the current execution and restart from a specific node.
   *
   * <p>The restart uses the last checkpoint messages from the target node's direct parents,
   * allowing partial replay.
   *
   * @param executionId the execution to restart
   * @param fromNodeId the node from which to resume execution
   * @param newExecutionId the pre-generated identifier for the restarted execution
   */
  record RestartFromNodeCommand(String executionId, String fromNodeId, String newExecutionId)
      implements ExecutionControlCommand {
    @Override
    public String commandType() {
      return "execution.restart-from-node";
    }
  }
```

- [ ] **Step 2: Confirm the module compiles in isolation (it won't yet — downstream call sites are fixed in later tasks)**

Run: `./gradlew :plugin-api:compileJava`
Expected: BUILD SUCCESSFUL (this module only declares the record; it has no call sites of the constructors).

- [ ] **Step 3: Commit**

```bash
git add plugin-api/src/main/java/com/infenia/yukta/plugin/control/ExecutionControlCommand.java
git commit -m "feat: add newExecutionId field to restart command records"
```

---

### Task 2: Delete the dead `DirectiveDispatcher` restart path

**Files:**
- Modify: `plugin-api/src/main/java/com/infenia/yukta/plugin/control/WorkflowDirective.java` (entire file)
- Modify: `core/src/main/java/com/infenia/yukta/service/control/directive/DirectiveDispatcher.java:143-246`
- Modify: `core/src/test/java/com/infenia/yukta/service/control/directive/DirectiveDispatcherTest.java`

**Interfaces:**
- Consumes: nothing new.
- Produces: `WorkflowDirective` is now `sealed interface WorkflowDirective permits WorkflowDirective.Stop` with only the `Stop` record. `DirectiveDispatcher.applyDirective` has only the `Stop` case. `applyRestart`/`applyRestartFromNode` methods are removed.

This is safe to do now, independent of the gateway/processor rewrite in Tasks 3–4: `applyRestart`/`applyRestartFromNode` are unreachable today (confirmed in the design spec — no processor ever returns `WorkflowDirective.Restart`/`RestartFromNode`), and after Task 4 the processors still won't return them. Doing the deletion first shrinks the surface area for the remaining tasks.

- [ ] **Step 1: Shrink `WorkflowDirective`'s sealed permits to just `Stop`**

Replace the full content of `plugin-api/src/main/java/com/infenia/yukta/plugin/control/WorkflowDirective.java`:

```java
// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Infenia Private Limited
package com.infenia.yukta.plugin.control;

/**
 * A typed action to be applied to an active workflow execution.
 *
 * <p>{@code WorkflowDirective} is the output side of the control-bus plugin pipeline. A {@link
 * ControlSignalProcessor} converts an inbound {@link
 * com.infenia.yukta.plugin.message.control.ControlCommand} into a concrete directive type. The
 * {@code DirectiveDispatcher} then pattern-matches on the sealed hierarchy to apply the correct
 * runtime behaviour.
 *
 * <p>Sealed permits list is exhaustive: the dispatcher can switch without a default branch.
 */
public sealed interface WorkflowDirective permits WorkflowDirective.Stop {

  /**
   * Terminates the active execution immediately.
   *
   * @param reason human-readable explanation, included in logs
   */
  record Stop(String reason) implements WorkflowDirective {}
}
```

(Restart is now performed entirely inside `RestartCommandProcessor`/`RestartFromNodeCommandProcessor` — see Tasks 3–4 — with no `WorkflowDirective` round-trip.)

- [ ] **Step 2: Remove `applyRestart`/`applyRestartFromNode` and their switch cases from `DirectiveDispatcher`**

In `DirectiveDispatcher.java`, replace the `applyDirective` method (lines 143–150):

```java
  private Mono<Void> applyDirective(
      final ExecutionControl control, final WorkflowDirective directive) {
    return switch (directive) {
      case WorkflowDirective.Stop stop -> applyStop(control, stop);
      case WorkflowDirective.Restart _ -> applyRestart(control);
      case WorkflowDirective.RestartFromNode rfn -> applyRestartFromNode(control, rfn);
    };
  }
```

with:

```java
  private Mono<Void> applyDirective(
      final ExecutionControl control, final WorkflowDirective directive) {
    return switch (directive) {
      case WorkflowDirective.Stop stop -> applyStop(control, stop);
    };
  }
```

Then delete the entire `applyRestart` method (lines 166–192) and the entire `applyRestartFromNode` method (lines 194–246) — i.e. everything from immediately after `applyStop`'s closing brace (line 164) through the end of `applyRestartFromNode` (line 246), leaving only the final class-closing brace.

- [ ] **Step 3: Remove now-unused imports from `DirectiveDispatcher.java`**

After Step 2, `WorkflowOrchestrator`, `NodeCheckpointStore`, `WorkflowNode`, `Message`, `Flux`, `ConcurrentHashMap`, `UUID`, and the constructor parameters `orchestrator`/`checkpointStore` become unused (they were only used by the two deleted methods). Remove:
- Constructor parameters `orchestrator` and `checkpointStore`, and their field declarations.
- Imports: `com.infenia.yukta.message.Message`, `com.infenia.yukta.model.workflow.WorkflowNode`, `com.infenia.yukta.plugin.store.NodeCheckpointStore`, `com.infenia.yukta.service.orchestrator.WorkflowOrchestrator`, `java.util.Map`, `java.util.UUID`, `java.util.concurrent.ConcurrentHashMap`, `reactor.core.publisher.Flux`.

The resulting constructor:

```java
  /**
   * Constructs a DirectiveDispatcher.
   *
   * @param processors the registered signal processors, ordered by priority
   * @param registry the live execution registry
   * @param controlBusService the control bus to subscribe to
   */
  @SuppressFBWarnings("EI_EXPOSE_REP2")
  public DirectiveDispatcher(
      final List<ControlSignalProcessor> processors,
      final ExecutionControlRegistry registry,
      final ControlBusService controlBusService) {
    this.processors = List.copyOf(processors);
    this.registry = registry;
    this.controlBusService = controlBusService;
  }
```

Also update the class javadoc (lines 30–39) to drop the now-inaccurate restart-related sentence:

```java
/**
 * Bridges the Control Bus to the workflow execution layer.
 *
 * <p>On startup, subscribes to the raw control stream from {@link ControlBusService}. For every
 * message whose payload is a {@link ControlCommand}, finds the first matching {@link
 * ControlSignalProcessor} (by priority), obtains a {@link WorkflowDirective}, and applies it to the
 * active execution found in {@link ExecutionControlRegistry}.
 */
```

- [ ] **Step 4: Update `DirectiveDispatcherTest` — fix the constructor call sites and remove dead-code tests**

The test file's `@BeforeEach setUp()` (lines 76–92) and one test-local `new DirectiveDispatcher(...)` call in `dispatch_multipleProcessors_selectsByHighestPriority` (lines 184–190), plus two more in `init_subscribesToControlStream_filtersExecutionControlCommands` (lines 570–572) and `init_dispatchErrorHandling_logsAndContinues` (lines 593–595), pass 5 args including `orchestrator`/`checkpointStore`. Update all four to the new 3-arg constructor `(processors, registry, controlBusService)`.

Remove these tests entirely (they test the now-deleted `applyRestart`/`applyRestartFromNode`):
- `applyDirective_restartDirective_callsApplyRestart` (lines 223–243)
- `applyDirective_restartFromNodeDirective_callsApplyRestartFromNode` (lines 245–285)
- `applyRestart_validExecution_unregistersAndStartsNewExecution` (lines 309–339)
- `applyRestart_orchestratorExecuteFails_logsError` (lines 341–363)
- `applyRestartFromNode_withParentCheckpoints_loadsAndApplies` (lines 365–430)
- `applyRestartFromNode_parentCheckpointNotFound_continuesWithEmptyCheckpoints` (lines 432–494)
- `applyRestartFromNode_noParents_executeWithEmptyCheckpointMap` (lines 496–552)
- `testDispatchRestartCommand` (lines 615–631)

Rewrite `dispatch_multipleProcessors_selectsByHighestPriority` (lines 161–200) to use `StopNodeCommand`/`WorkflowDirective.Stop` instead of the now-removed `RestartCommand`/`WorkflowDirective.Restart` — priority selection doesn't care which command/directive type is used, it was only incidental:

```java
  @Test
  void dispatch_multipleProcessors_selectsByHighestPriority() {
    verifyDispatchWithMultipleProcessorsSelectsByHighestPriority();
  }

  private void verifyDispatchWithMultipleProcessorsSelectsByHighestPriority() {
    final String executionId = "exec-1";

    final ExecutionControl control = createControl("session-1", "workflow-1", executionId);
    registry.register(control);

    final ControlSignalProcessor lowPriorityProcessor = mock(ControlSignalProcessor.class);
    final ControlSignalProcessor highPriorityProcessor = mock(ControlSignalProcessor.class);

    when(lowPriorityProcessor.canProcess(any())).thenReturn(true);
    when(lowPriorityProcessor.getPriority()).thenReturn(0);

    when(highPriorityProcessor.canProcess(any())).thenReturn(true);
    when(highPriorityProcessor.getPriority()).thenReturn(10);

    when(highPriorityProcessor.process(any()))
        .thenReturn(Mono.just(new WorkflowDirective.Stop("test")));

    final DirectiveDispatcher multiDispatcher =
        new DirectiveDispatcher(
            List.of(lowPriorityProcessor, highPriorityProcessor), registry, controlBusService);

    final ExecutionControlCommand command =
        new ExecutionControlCommand.StopNodeCommand(executionId, "node-1", false, "test");

    // When
    StepVerifier.create(multiDispatcher.dispatch(command)).verifyComplete();

    // Then
    verify(highPriorityProcessor, times(1)).process(command);
    verify(lowPriorityProcessor, never()).process(command);
  }
```

Also update `testDispatchMultipleProcessorsPriority` (lines 638–641) — it just delegates to `verifyDispatchWithMultipleProcessorsSelectsByHighestPriority()`, so it needs no code change, only recompiles cleanly once the helper above is fixed.

Fix `init_subscribesToControlStream_filtersExecutionControlCommands` (line 561) and `init_dispatchErrorHandling_logsAndContinues` (line 584), which construct a `RestartCommand("exec-1")` / `RestartCommand("exec-no-exist")` purely as a stand-in `ExecutionControlCommand` — update both call sites to the new 2-arg constructor:

```java
new ExecutionControlCommand.RestartCommand("exec-1", "new-exec-1")
```
and
```java
new ExecutionControlCommand.RestartCommand("exec-no-exist", "new-exec-no-exist")
```

Also remove the now-unused `orchestrator`/`checkpointStore` mock setup from `@BeforeEach setUp()` (lines 79, 82, 85–87 — the `orchestrator = mock(...)`, `checkpointStore = new InMemoryNodeCheckpointStore()`, and the two `when(orchestrator...)` stubs), and their field declarations (lines 51, 54), and the now-unused imports this leaves behind (`NodeCheckpointStore`, `WorkflowOrchestrator`, `InMemoryNodeCheckpointStore`, `WorkflowNode`, `PreparedWorkflow` if no longer referenced by any remaining test — check after deletion since `applyDirective_stopDirective_callsApplyStop` and the `dispatch_*`/`testDispatch*` tests remaining don't use `PreparedWorkflow`).

- [ ] **Step 5: Run the dispatcher tests**

Run: `./gradlew :core:test --tests com.infenia.yukta.service.control.directive.DirectiveDispatcherTest`
Expected: BUILD SUCCESSFUL, all remaining tests pass.

- [ ] **Step 6: Run Checkstyle/PMD/SpotBugs on the touched files to catch unused-import/field violations**

Run: `./gradlew :core:checkstyleMain :core:pmdMain :core:spotbugsMain :plugin-api:checkstyleMain :plugin-api:pmdMain`
Expected: BUILD SUCCESSFUL (no unused-import or unused-field violations).

- [ ] **Step 7: Commit**

```bash
git add plugin-api/src/main/java/com/infenia/yukta/plugin/control/WorkflowDirective.java \
        core/src/main/java/com/infenia/yukta/service/control/directive/DirectiveDispatcher.java \
        core/src/test/java/com/infenia/yukta/service/control/directive/DirectiveDispatcherTest.java
git commit -m "refactor: delete dead restart directive path from DirectiveDispatcher"
```

---

### Task 3: Add `RestartCompletionSink` and the await mechanism to `DefaultControlBusGateway`

**Files:**
- Modify: `core/src/main/java/com/infenia/yukta/service/control/gateway/DefaultControlBusGateway.java`
- Create: `core/src/main/java/com/infenia/yukta/service/control/gateway/RestartCompletionSink.java`
- Modify: `core/src/test/java/com/infenia/yukta/service/control/gateway/DefaultControlBusGatewayTest.java`

**Interfaces:**
- Produces: `public interface RestartCompletionSink { void completeRestartSuccess(String newExecutionId); void completeRestartFailure(String newExecutionId, Throwable error); }`, implemented by `DefaultControlBusGateway`. `restartWorkflow`/`restartFromNode` now return a `Mono<String>` that completes only once `completeRestartSuccess`/`completeRestartFailure` is called for the `newExecutionId` they generated, or after a 30-second timeout.
- Consumes (by Task 4): `RestartCompletionSink` is injected into `RestartCommandProcessor`/`RestartFromNodeCommandProcessor` as a constructor dependency.

- [ ] **Step 1: Create the `RestartCompletionSink` interface**

```java
// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Infenia Private Limited
package com.infenia.yukta.service.control.gateway;

/**
 * Callback used by restart command processors to report the outcome of an in-flight restart back
 * to the {@link ControlBusGateway} caller awaiting it.
 *
 * <p>{@link #completeRestartSuccess} and {@link #completeRestartFailure} are best-effort: if the
 * caller has already timed out and the pending entry was removed, both are no-ops.
 */
public interface RestartCompletionSink {

  /**
   * Reports that a restart succeeded and the new execution has been subscribed.
   *
   * @param newExecutionId the identifier of the restarted execution
   */
  void completeRestartSuccess(String newExecutionId);

  /**
   * Reports that a restart failed before the new execution could be started.
   *
   * @param newExecutionId the identifier that was reserved for the restarted execution
   * @param error the failure
   */
  void completeRestartFailure(String newExecutionId, Throwable error);
}
```

- [ ] **Step 2: Write failing gateway tests for the new await behavior**

Add these tests to `DefaultControlBusGatewayTest.java`, replacing the four existing tests `restartWorkflow_validExecutionId_generatesNewExecutionId` (lines 842–873), `restartFromNode_validInputs_generatesNewExecutionId` (lines 875–908), `restartWorkflow_emitError_logsErrorAndPropagates` (lines 1427–1439), and `restartFromNode_emitError_logsErrorAndPropagates` (lines 1441–1454) with:

```java
  @Test
  void restartWorkflow_processorCompletesSuccess_returnsRealNewExecutionId() {
    // Given
    final String executionId = "exec-12";
    when(controlBusService.emit(any())).thenReturn(Mono.empty());

    // When
    final Mono<String> result = gateway.restartWorkflow(executionId);

    // Then — the Mono does not complete until the processor reports success
    final ArgumentCaptor<Message<?>> captor = ArgumentCaptor.forClass(Message.class);
    StepVerifier.create(result)
        .then(
            () -> {
              verify(controlBusService).emit(captor.capture());
              final RestartCommand cmd = (RestartCommand) captor.getValue().getPayload();
              assertThat(cmd.executionId()).isEqualTo(executionId);
              gateway.completeRestartSuccess(cmd.newExecutionId());
            })
        .assertNext(
            newExecId -> {
              assertThat(newExecId).isNotNull().isNotEqualTo(executionId);
              UUID.fromString(newExecId);
            })
        .verifyComplete();
  }

  @Test
  void restartFromNode_processorCompletesSuccess_returnsRealNewExecutionId() {
    // Given
    final String executionId = "exec-13";
    final String fromNodeId = "node-11";
    when(controlBusService.emit(any())).thenReturn(Mono.empty());

    // When
    final Mono<String> result = gateway.restartFromNode(executionId, fromNodeId);

    // Then
    final ArgumentCaptor<Message<?>> captor = ArgumentCaptor.forClass(Message.class);
    StepVerifier.create(result)
        .then(
            () -> {
              verify(controlBusService).emit(captor.capture());
              final RestartFromNodeCommand cmd =
                  (RestartFromNodeCommand) captor.getValue().getPayload();
              assertThat(cmd.executionId()).isEqualTo(executionId);
              assertThat(cmd.fromNodeId()).isEqualTo(fromNodeId);
              gateway.completeRestartSuccess(cmd.newExecutionId());
            })
        .assertNext(newExecId -> assertThat(newExecId).isEqualTo(newExecId))
        .verifyComplete();
  }

  @Test
  void restartWorkflow_processorCompletesFailure_propagatesRealError() {
    // Given
    final String executionId = "exec-not-found";
    final RuntimeException notFound = new IllegalArgumentException("Execution not found: " + executionId);
    when(controlBusService.emit(any())).thenReturn(Mono.empty());

    // When
    final Mono<String> result = gateway.restartWorkflow(executionId);

    // Then
    final ArgumentCaptor<Message<?>> captor = ArgumentCaptor.forClass(Message.class);
    StepVerifier.create(result)
        .then(
            () -> {
              verify(controlBusService).emit(captor.capture());
              final RestartCommand cmd = (RestartCommand) captor.getValue().getPayload();
              gateway.completeRestartFailure(cmd.newExecutionId(), notFound);
            })
        .expectErrorMatches(err -> err == notFound)
        .verify();
  }

  @Test
  void restartWorkflow_emitError_propagatesImmediately() {
    // Given
    final String executionId = "exec-error";
    final RuntimeException testError = new RuntimeException("Emit failed");
    when(controlBusService.emit(any())).thenReturn(Mono.error(testError));

    // When
    final Mono<String> result = gateway.restartWorkflow(executionId);

    // Then
    StepVerifier.create(result).expectErrorMatches(err -> err == testError).verify();
  }

  @Test
  void restartFromNode_emitError_propagatesImmediately() {
    // Given
    final String executionId = "exec-error";
    final String fromNodeId = "node-error";
    final RuntimeException testError = new RuntimeException("Emit failed");
    when(controlBusService.emit(any())).thenReturn(Mono.error(testError));

    // When
    final Mono<String> result = gateway.restartFromNode(executionId, fromNodeId);

    // Then
    StepVerifier.create(result).expectErrorMatches(err -> err == testError).verify();
  }

  @Test
  void restartWorkflow_processorNeverCompletes_timesOut() {
    // Given
    final String executionId = "exec-timeout";
    when(controlBusService.emit(any())).thenReturn(Mono.empty());

    // When
    final Mono<String> result = gateway.restartWorkflow(executionId);

    // Then — nothing ever calls completeRestartSuccess/Failure
    StepVerifier.withVirtualTime(() -> result)
        .thenAwait(java.time.Duration.ofSeconds(31))
        .expectError(java.util.concurrent.TimeoutException.class)
        .verify();
  }

  @Test
  void completeRestartSuccess_unknownNewExecutionId_isNoOp() {
    // No pending sink registered for this ID — must not throw
    gateway.completeRestartSuccess("never-registered");
  }

  @Test
  void completeRestartFailure_unknownNewExecutionId_isNoOp() {
    // No pending sink registered for this ID — must not throw
    gateway.completeRestartFailure("never-registered", new RuntimeException("late"));
  }
```

Add the missing import to the test file's static-import block: `import java.time.Duration;` is used fully-qualified above to avoid an extra import-ordering diff; leave as `java.time.Duration.ofSeconds(31)` / `java.util.concurrent.TimeoutException.class` inline as shown.

- [ ] **Step 3: Run the new tests to verify they fail to compile / fail**

Run: `./gradlew :core:test --tests com.infenia.yukta.service.control.gateway.DefaultControlBusGatewayTest`
Expected: COMPILATION FAILED — `gateway.completeRestartSuccess(...)` doesn't exist yet, `RestartCommand`/`RestartFromNodeCommand` payload has no `newExecutionId()` accessor mismatch is fine (Task 1 already added it), but `restartWorkflow` still returns a `Mono<String>` that completes immediately today, so once compilation is fixed by Step 4 these tests will fail at runtime (timeout never happens, sink never awaited) until Step 4 lands.

- [ ] **Step 4: Implement the await mechanism in `DefaultControlBusGateway`**

Add to the imports block (after line 34, alphabetically among existing `java.util.*` imports):

```java
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
```

Add `reactor.core.scheduler.Schedulers` as a real import (replacing the fully-qualified inline usage) — add after `import reactor.core.publisher.Mono;`:

```java
import reactor.core.scheduler.Schedulers;
```

And update the one existing fully-qualified use in `prepareAndExecute` (line 291) from `reactor.core.scheduler.Schedulers.boundedElastic()` to `Schedulers.boundedElastic()`.

Change the class declaration to implement the new interface:

```java
public class DefaultControlBusGateway implements ControlBusGateway, RestartCompletionSink {
```

Add two new constants and a field, immediately after the existing `CONTROL_COMMAND_PRIORITY` constant (line 64):

```java
  /** Timeout for awaiting a restart's real outcome from its command processor. */
  private static final Duration RESTART_TIMEOUT = Duration.ofSeconds(30);

  /** Pending restart completions, keyed by the pre-generated new execution ID. */
  private final Map<String, Sinks.One<String>> pendingRestarts = new ConcurrentHashMap<>();
```

Add `import reactor.core.publisher.Sinks;` to the imports block (after `import reactor.core.publisher.Mono;`, before the new `Schedulers` import).

Replace `restartWorkflow` (lines 630–654):

```java
  @Override
  public Mono<String> restartWorkflow(final String executionId) {
    final String newExecutionId = UUID.randomUUID().toString();
    final Sinks.One<String> sink = Sinks.one();
    pendingRestarts.put(newExecutionId, sink);
    return executeCommand(
            buildCommand(
                new RestartCommand(executionId, newExecutionId), CONTROL_COMMAND_PRIORITY + 20))
        .doOnSubscribe(
            _ ->
                log.atInfo()
                    .addKeyValue("executionId", executionId)
                    .addKeyValue("newExecutionId", newExecutionId)
                    .log("Restarting workflow"))
        .then(sink.asMono())
        .timeout(RESTART_TIMEOUT)
        .doFinally(_ -> pendingRestarts.remove(newExecutionId))
        .doOnSuccess(
            newId ->
                log.atInfo()
                    .addKeyValue("oldExecutionId", executionId)
                    .addKeyValue("newExecutionId", newId)
                    .log("Workflow restarted successfully"))
        .doOnError(
            err ->
                log.atError()
                    .setCause(err)
                    .addKeyValue("executionId", executionId)
                    .log("Failed to restart workflow"));
  }
```

Replace `restartFromNode` (lines 656–684):

```java
  @Override
  public Mono<String> restartFromNode(final String executionId, final String fromNodeId) {
    final String newExecutionId = UUID.randomUUID().toString();
    final Sinks.One<String> sink = Sinks.one();
    pendingRestarts.put(newExecutionId, sink);
    return executeCommand(
            buildCommand(
                new RestartFromNodeCommand(executionId, fromNodeId, newExecutionId),
                CONTROL_COMMAND_PRIORITY + 20))
        .doOnSubscribe(
            _ ->
                log.atInfo()
                    .addKeyValue("executionId", executionId)
                    .addKeyValue("fromNodeId", fromNodeId)
                    .addKeyValue("newExecutionId", newExecutionId)
                    .log("Restarting workflow from node"))
        .then(sink.asMono())
        .timeout(RESTART_TIMEOUT)
        .doFinally(_ -> pendingRestarts.remove(newExecutionId))
        .doOnSuccess(
            newId ->
                log.atInfo()
                    .addKeyValue("oldExecutionId", executionId)
                    .addKeyValue("fromNodeId", fromNodeId)
                    .addKeyValue("newExecutionId", newId)
                    .log("Workflow restarted from node successfully"))
        .doOnError(
            err ->
                log.atError()
                    .setCause(err)
                    .addKeyValue("executionId", executionId)
                    .addKeyValue("fromNodeId", fromNodeId)
                    .log("Failed to restart workflow from node"));
  }
```

Add the two `RestartCompletionSink` implementation methods, immediately after `restartFromNode`:

```java
  @Override
  public void completeRestartSuccess(final String newExecutionId) {
    final Sinks.One<String> sink = pendingRestarts.remove(newExecutionId);
    if (sink == null) {
      log.atDebug()
          .addKeyValue("newExecutionId", newExecutionId)
          .log("Restart completion arrived after caller already timed out");
      return;
    }
    sink.tryEmitValue(newExecutionId);
  }

  @Override
  public void completeRestartFailure(final String newExecutionId, final Throwable error) {
    final Sinks.One<String> sink = pendingRestarts.remove(newExecutionId);
    if (sink == null) {
      log.atDebug()
          .addKeyValue("newExecutionId", newExecutionId)
          .log("Restart failure arrived after caller already timed out");
      return;
    }
    sink.tryEmitError(error);
  }
```

Note `emit()`/`executeCommand()` still return `Mono<Void>`, and `.then(sink.asMono())` sequences: wait for the emit to complete (bus accepted the command), then wait for the sink. If `emit()` itself errors (e.g. `restartWorkflow_emitError_propagatesImmediately`), `.then(...)` short-circuits and the sink is never awaited — but the sink is still in `pendingRestarts` at that point. Add `.doFinally(_ -> pendingRestarts.remove(newExecutionId))` (already present above) so this leaks nothing: `doFinally` runs on every terminal signal (`onComplete`, `onError`, or cancel), covering the emit-error path too.

- [ ] **Step 5: Run the gateway tests**

Run: `./gradlew :core:test --tests com.infenia.yukta.service.control.gateway.DefaultControlBusGatewayTest`
Expected: BUILD SUCCESSFUL, all tests pass including the 8 new/rewritten ones from Step 2.

- [ ] **Step 6: Run Checkstyle/PMD/SpotBugs**

Run: `./gradlew :core:checkstyleMain :core:pmdMain :core:spotbugsMain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/com/infenia/yukta/service/control/gateway/RestartCompletionSink.java \
        core/src/main/java/com/infenia/yukta/service/control/gateway/DefaultControlBusGateway.java \
        core/src/test/java/com/infenia/yukta/service/control/gateway/DefaultControlBusGatewayTest.java
git commit -m "fix: make restartWorkflow/restartFromNode await the real outcome"
```

---

### Task 4: Rewrite the restart processors to thread the real ID, detach execution, and report completion

**Files:**
- Modify: `core/src/main/java/com/infenia/yukta/service/control/processor/RestartCommandProcessor.java`
- Modify: `core/src/main/java/com/infenia/yukta/service/control/processor/RestartFromNodeCommandProcessor.java`
- Modify: `core/src/test/java/com/infenia/yukta/service/control/processor/RestartCommandProcessorTest.java`
- Modify: `core/src/test/java/com/infenia/yukta/service/control/processor/RestartFromNodeCommandProcessorTest.java`

**Interfaces:**
- Consumes: `RestartCompletionSink.completeRestartSuccess(String)` / `completeRestartFailure(String, Throwable)` (Task 3), `RestartCommand.newExecutionId()` / `RestartFromNodeCommand.newExecutionId()` (Task 1).
- Produces: no change to `ControlSignalProcessor` contract — both processors still return `Mono<WorkflowDirective>`, always empty.

- [ ] **Step 1: Write failing tests for `RestartCommandProcessor`**

Replace the full content of `RestartCommandProcessorTest.java`:

```java
// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Infenia Private Limited
package com.infenia.yukta.service.control.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infenia.yukta.model.workflow.PreparedWorkflow;
import com.infenia.yukta.plugin.control.ExecutionControlCommand;
import com.infenia.yukta.plugin.control.ExecutionControlCommand.PauseWorkflowCommand;
import com.infenia.yukta.plugin.control.ExecutionControlCommand.RestartCommand;
import com.infenia.yukta.service.control.ExecutionControl;
import com.infenia.yukta.service.control.gateway.RestartCompletionSink;
import com.infenia.yukta.service.control.store.ExecutionControlRegistry;
import com.infenia.yukta.service.orchestrator.WorkflowOrchestrator;
import java.util.Map;
import java.util.Optional;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@NoArgsConstructor
@SuppressWarnings({"PMD.TooManyStaticImports", "PMD.CommentRequired", "PMD.LinguisticNaming"})
class RestartCommandProcessorTest {

  @Mock private ExecutionControlRegistry registry;
  @Mock private WorkflowOrchestrator orchestrator;
  @Mock private RestartCompletionSink completionSink;
  @Mock private ExecutionControl executionControl;
  @Mock private PreparedWorkflow preparedWorkflow;

  @InjectMocks private RestartCommandProcessor processor;

  @Test
  void canProcess_restartCommand_returnsTrue() {
    final ExecutionControlCommand command = new RestartCommand("exec-1", "new-1");
    assertThat(processor.canProcess(command)).isTrue();
  }

  @Test
  void canProcess_otherCommand_returnsFalse() {
    final ExecutionControlCommand command = new PauseWorkflowCommand("exec-1");
    assertThat(processor.canProcess(command)).isFalse();
  }

  @Test
  void process_executionFound_detachesExecutionAndReportsSuccessImmediately() {
    // Given
    final String executionId = "exec-restart";
    final String newExecutionId = "new-exec-restart";
    final String sessionId = "session-1";
    final String workflowId = "workflow-1";
    final Map<String, Object> payload = Map.of("key", "value");
    final Sinks.One<Void> safeStopSink = Sinks.one();
    final RestartCommand command = new RestartCommand(executionId, newExecutionId);

    when(registry.findByExecutionId(executionId)).thenReturn(Optional.of(executionControl));
    when(executionControl.executionId()).thenReturn(executionId);
    when(executionControl.sessionId()).thenReturn(sessionId);
    when(executionControl.workflowId()).thenReturn(workflowId);
    when(executionControl.prepared()).thenReturn(preparedWorkflow);
    when(executionControl.payload()).thenReturn(payload);
    when(executionControl.safeStopSink()).thenReturn(safeStopSink);
    // Never completes — proves the processor does not wait for it.
    when(orchestrator.execute(
            eq(sessionId), eq(workflowId), eq(newExecutionId), eq(preparedWorkflow), eq(payload)))
        .thenReturn(Mono.never());

    // When
    final var result = processor.process(command);

    // Then — process() completes without waiting for orchestrator.execute()
    StepVerifier.create(result).verifyComplete();
    verify(registry).unregister(executionId);
    verify(completionSink).completeRestartSuccess(newExecutionId);
  }

  @Test
  void process_executionNotFound_reportsFailureAndCompletesEmpty() {
    // Given
    final String executionId = "exec-not-found";
    final String newExecutionId = "new-exec-not-found";
    final RestartCommand command = new RestartCommand(executionId, newExecutionId);
    when(registry.findByExecutionId(executionId)).thenReturn(Optional.empty());

    // When
    final var result = processor.process(command);

    // Then
    StepVerifier.create(result).verifyComplete();
    verify(completionSink)
        .completeRestartFailure(eq(newExecutionId), any(IllegalArgumentException.class));
    verify(orchestrator, never()).execute(any(), any(), any(), any(), any());
  }

  @Test
  void process_orchestratorFailsAsynchronously_doesNotReportFailureViaCompletionSink() {
    // Given: detachment means an async orchestrator failure is not observable by process()'s
    // own Mono — it surfaces via normal task-tracker/watchExecution channels instead, same as
    // any other execution failure. completeRestartSuccess was already called at subscribe time.
    final String executionId = "exec-orch-fail";
    final String newExecutionId = "new-exec-orch-fail";
    final String sessionId = "session-1";
    final String workflowId = "workflow-1";
    final Sinks.One<Void> safeStopSink = Sinks.one();
    final RestartCommand command = new RestartCommand(executionId, newExecutionId);

    when(registry.findByExecutionId(executionId)).thenReturn(Optional.of(executionControl));
    when(executionControl.executionId()).thenReturn(executionId);
    when(executionControl.sessionId()).thenReturn(sessionId);
    when(executionControl.workflowId()).thenReturn(workflowId);
    when(executionControl.prepared()).thenReturn(preparedWorkflow);
    when(executionControl.payload()).thenReturn(Map.of());
    when(executionControl.safeStopSink()).thenReturn(safeStopSink);
    when(orchestrator.execute(
            eq(sessionId), eq(workflowId), eq(newExecutionId), eq(preparedWorkflow), eq(Map.of())))
        .thenReturn(Mono.error(new RuntimeException("Orchestrator failure")));

    // When
    final var result = processor.process(command);

    // Then
    StepVerifier.create(result).verifyComplete();
    verify(completionSink, timeout(1000)).completeRestartSuccess(newExecutionId);
    verify(completionSink, never()).completeRestartFailure(eq(newExecutionId), any());
  }

  @Test
  void process_safeStopSinkEmitFails_reportsFailure() {
    // Given: safeStopSink already completed, so emitEmpty(FAIL_FAST) throws synchronously inside
    // the doOnNext block — proving that path converts to onError and reports completeRestartFailure,
    // not completeRestartSuccess.
    final String executionId = "exec-sink-emit-fail";
    final String newExecutionId = "new-exec-sink-emit-fail";
    final String sessionId = "session-1";
    final String workflowId = "workflow-1";
    final Sinks.One<Void> failingSink = Sinks.one();
    failingSink.emitEmpty(Sinks.EmitFailureHandler.FAIL_FAST);
    final RestartCommand command = new RestartCommand(executionId, newExecutionId);

    when(registry.findByExecutionId(executionId)).thenReturn(Optional.of(executionControl));
    when(executionControl.executionId()).thenReturn(executionId);
    when(executionControl.sessionId()).thenReturn(sessionId);
    when(executionControl.workflowId()).thenReturn(workflowId);
    when(executionControl.prepared()).thenReturn(preparedWorkflow);
    when(executionControl.payload()).thenReturn(Map.of());
    when(executionControl.safeStopSink()).thenReturn(failingSink);

    // When
    final var result = processor.process(command);

    // Then
    StepVerifier.create(result).verifyComplete();
    verify(completionSink)
        .completeRestartFailure(eq(newExecutionId), any(RuntimeException.class));
    verify(completionSink, never()).completeRestartSuccess(newExecutionId);
    verify(orchestrator, never()).execute(any(), any(), any(), any(), any());
  }

  @Test
  void getPriority_returnsCorrectValue() {
    assertThat(processor.getPriority()).isEqualTo(20);
  }
}
```

- [ ] **Step 2: Run to verify tests fail (compilation error — production code not yet updated)**

Run: `./gradlew :core:test --tests com.infenia.yukta.service.control.processor.RestartCommandProcessorTest`
Expected: COMPILATION FAILED (`RestartCompletionSink` not yet a constructor dependency of `RestartCommandProcessor`; `DefaultTaskTrackerService taskTracker` field removed from the test but still required by `@InjectMocks` target — will resolve once Step 3 changes the production constructor shape to match).

- [ ] **Step 3: Rewrite `RestartCommandProcessor`**

Replace the full content of `RestartCommandProcessor.java`:

```java
// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Infenia Private Limited
package com.infenia.yukta.service.control.processor;

import com.infenia.yukta.plugin.control.ControlSignalProcessor;
import com.infenia.yukta.plugin.control.ExecutionControlCommand;
import com.infenia.yukta.plugin.control.ExecutionControlCommand.RestartCommand;
import com.infenia.yukta.plugin.control.WorkflowDirective;
import com.infenia.yukta.service.control.gateway.RestartCompletionSink;
import com.infenia.yukta.service.control.store.ExecutionControlRegistry;
import com.infenia.yukta.service.orchestrator.WorkflowOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

/**
 * Processor for restart commands.
 *
 * <p>Stops the current execution and restarts the entire workflow from the beginning with the
 * original payload. The new execution is detached ({@code subscribeOn(boundedElastic()).subscribe()})
 * so this processor reports completion as soon as the new execution is subscribed, not once it
 * finishes — matching how {@code DefaultControlBusGateway.prepareAndExecute} starts a normal
 * (non-restart) execution.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RestartCommandProcessor implements ControlSignalProcessor {

  /** The execution control registry for accessing execution control state. */
  private final ExecutionControlRegistry registry;

  /** The workflow orchestrator for restarting workflows. */
  private final WorkflowOrchestrator orchestrator;

  /** Reports the outcome of a restart back to the awaiting gateway caller. */
  private final RestartCompletionSink completionSink;

  @Override
  public boolean canProcess(final ExecutionControlCommand command) {
    return command instanceof RestartCommand;
  }

  @Override
  public Mono<WorkflowDirective> process(final ExecutionControlCommand command) {
    final RestartCommand restart = (RestartCommand) command;

    return Mono.fromSupplier(
            () ->
                registry
                    .findByExecutionId(restart.executionId())
                    .orElseThrow(
                        () ->
                            new IllegalArgumentException(
                                "Execution not found: " + restart.executionId())))
        .doOnNext(
            control -> {
              registry.unregister(control.executionId());
              control.safeStopSink().emitEmpty(Sinks.EmitFailureHandler.FAIL_FAST);

              orchestrator
                  .execute(
                      control.sessionId(),
                      control.workflowId(),
                      restart.newExecutionId(),
                      control.prepared(),
                      control.payload())
                  .subscribeOn(Schedulers.boundedElastic())
                  .doOnError(
                      err ->
                          log.atError()
                              .setCause(err)
                              .addKeyValue("oldExecutionId", restart.executionId())
                              .addKeyValue("newExecutionId", restart.newExecutionId())
                              .log("Restarted execution failed"))
                  .subscribe();

              log.atInfo()
                  .addKeyValue("oldExecutionId", restart.executionId())
                  .addKeyValue("newExecutionId", restart.newExecutionId())
                  .addKeyValue("workflowId", control.workflowId())
                  .log("Restarted execution");
              completionSink.completeRestartSuccess(restart.newExecutionId());
            })
        .then(Mono.<WorkflowDirective>empty())
        .onErrorResume(
            e -> {
              log.atError()
                  .addKeyValue("executionId", restart.executionId())
                  .setCause(e)
                  .log("Restart failed");
              completionSink.completeRestartFailure(restart.newExecutionId(), e);
              return Mono.empty();
            });
  }

  @Override
  public int getPriority() {
    return 20;
  }
}
```

Note `taskTracker.emitWorkflowStatusEvent(newExecutionId, "RUNNING")` is intentionally **removed**, not moved: `WorkflowOrchestrator.execute()` already calls `tracker.startWorkflow(executionId, sessionId, workflowId, nodeIds)` as the first step of its own chain (`WorkflowOrchestrator.java:138-139`), which sets `WorkflowState.status = "RUNNING"` synchronously in its constructor (`DefaultTaskTrackerService.startWorkflow`, lines 194-227) — before any node runs. This mirrors `prepareAndExecute`, which also never calls `emitWorkflowStatusEvent` after detaching.

Keeping the call would be worse than redundant: `emitWorkflowStatusEvent` writes onto `wfStatusSink`, a `bufferTimeout(100, 50ms)`-batched pipeline (`DefaultTaskTrackerService.java:144-159`) that also carries the terminal `SUCCESS`/`FAILURE` event from `finishWorkflow`, and `handleWorkflowStatusEvents` applies `state.setStatus(...)` **unconditionally** (line 592) — no guard against a "RUNNING" event overwriting a terminal one. If a restarted workflow finishes fast enough, a manually-emitted "RUNNING" call fired right after `.subscribe()` could land in a batch processed after the real terminal event, regressing `WorkflowState.status` back to "RUNNING" for an execution that has already finished. Removing the call avoids this race entirely, not just the duplication.

- [ ] **Step 4: Run `RestartCommandProcessorTest`**

Run: `./gradlew :core:test --tests com.infenia.yukta.service.control.processor.RestartCommandProcessorTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Write failing tests for `RestartFromNodeCommandProcessor`**

Replace the full content of `RestartFromNodeCommandProcessorTest.java`:

```java
// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Infenia Private Limited
package com.infenia.yukta.service.control.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infenia.yukta.message.Message;
import com.infenia.yukta.model.workflow.PreparedWorkflow;
import com.infenia.yukta.model.workflow.WorkflowNode;
import com.infenia.yukta.plugin.control.ExecutionControlCommand;
import com.infenia.yukta.plugin.control.ExecutionControlCommand.PauseWorkflowCommand;
import com.infenia.yukta.plugin.control.ExecutionControlCommand.RestartFromNodeCommand;
import com.infenia.yukta.plugin.store.NodeCheckpointStore;
import com.infenia.yukta.service.control.ExecutionControl;
import com.infenia.yukta.service.control.gateway.RestartCompletionSink;
import com.infenia.yukta.service.control.store.ExecutionControlRegistry;
import com.infenia.yukta.service.orchestrator.WorkflowOrchestrator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@NoArgsConstructor
@SuppressWarnings({
  "PMD.CommentRequired",
  "PMD.AvoidDuplicateLiterals",
  "PMD.LinguisticNaming",
  "PMD.TooManyMethods",
  "PMD.TooManyStaticImports"
})
class RestartFromNodeCommandProcessorTest {

  @Mock private ExecutionControlRegistry registry;
  @Mock private WorkflowOrchestrator orchestrator;
  @Mock private NodeCheckpointStore checkpointStore;
  @Mock private RestartCompletionSink completionSink;
  @Mock private ExecutionControl executionControl;
  @Mock private PreparedWorkflow preparedWorkflow;
  @Mock private Message<?> checkpointMessage;

  @InjectMocks private RestartFromNodeCommandProcessor processor;

  @Test
  void canProcess_restartFromNodeCommand_returnsTrue() {
    final ExecutionControlCommand command =
        new RestartFromNodeCommand("exec-1", "node-1", "new-1");
    assertThat(processor.canProcess(command)).isTrue();
  }

  @Test
  void canProcess_otherCommand_returnsFalse() {
    final ExecutionControlCommand command = new PauseWorkflowCommand("exec-1");
    assertThat(processor.canProcess(command)).isFalse();
  }

  @Test
  void process_noParentNodes_detachesRestartAndReportsSuccessImmediately() {
    // Given
    final String executionId = "exec-restart-from-no-parents";
    final String newExecutionId = "new-exec-restart-from-no-parents";
    final String sessionId = "session-1";
    final String workflowId = "workflow-1";
    final String fromNodeId = "node-target";
    final Sinks.One<Void> safeStopSink = Sinks.one();
    final RestartFromNodeCommand command =
        new RestartFromNodeCommand(executionId, fromNodeId, newExecutionId);

    when(registry.findByExecutionId(executionId)).thenReturn(Optional.of(executionControl));
    when(executionControl.executionId()).thenReturn(executionId);
    when(executionControl.sessionId()).thenReturn(sessionId);
    when(executionControl.workflowId()).thenReturn(workflowId);
    when(executionControl.prepared()).thenReturn(preparedWorkflow);
    when(executionControl.safeStopSink()).thenReturn(safeStopSink);
    when(preparedWorkflow.parentsList()).thenReturn(Map.of());
    // Never completes — proves the processor does not wait for it.
    when(orchestrator.restartFromNode(
            eq(sessionId),
            eq(workflowId),
            eq(executionId),
            eq(newExecutionId),
            eq(preparedWorkflow),
            eq(fromNodeId),
            eq(Map.of())))
        .thenReturn(Mono.never());

    // When
    final var result = processor.process(command);

    // Then
    StepVerifier.create(result).verifyComplete();
    verify(registry).unregister(executionId);
    verify(checkpointStore).clear(executionId);
    verify(completionSink).completeRestartSuccess(newExecutionId);
  }

  @Test
  void process_withParentNodes_loadsCheckpointsAndDetachesRestart() {
    // Given
    final String executionId = "exec-restart-from-parents";
    final String newExecutionId = "new-exec-restart-from-parents";
    final String sessionId = "session-1";
    final String workflowId = "workflow-1";
    final String fromNodeId = "node-target";
    final String parentNodeId = "parent-node";
    final Sinks.One<Void> safeStopSink = Sinks.one();
    final WorkflowNode parentNode = new WorkflowNode(parentNodeId, "processor", Map.of());
    final RestartFromNodeCommand command =
        new RestartFromNodeCommand(executionId, fromNodeId, newExecutionId);

    when(registry.findByExecutionId(executionId)).thenReturn(Optional.of(executionControl));
    when(executionControl.executionId()).thenReturn(executionId);
    when(executionControl.sessionId()).thenReturn(sessionId);
    when(executionControl.workflowId()).thenReturn(workflowId);
    when(executionControl.prepared()).thenReturn(preparedWorkflow);
    when(executionControl.safeStopSink()).thenReturn(safeStopSink);
    when(preparedWorkflow.parentsList()).thenReturn(Map.of(fromNodeId, List.of(parentNode)));
    when(checkpointStore.get(executionId, parentNodeId)).thenReturn(Mono.just(checkpointMessage));
    when(checkpointMessage.getSourceNodeId()).thenReturn(parentNodeId);
    when(orchestrator.restartFromNode(
            eq(sessionId),
            eq(workflowId),
            eq(executionId),
            eq(newExecutionId),
            eq(preparedWorkflow),
            eq(fromNodeId),
            any()))
        .thenReturn(Mono.never());

    // When
    final var result = processor.process(command);

    // Then
    StepVerifier.create(result).verifyComplete();
    verify(checkpointStore).get(executionId, parentNodeId);
    verify(registry).unregister(executionId);
    verify(checkpointStore).clear(executionId);
    verify(completionSink).completeRestartSuccess(newExecutionId);
  }

  @Test
  void process_checkpointStoreFails_continuesWithAvailableCheckpoints() {
    // Given
    final String executionId = "exec-checkpoint-fail";
    final String newExecutionId = "new-exec-checkpoint-fail";
    final String sessionId = "session-1";
    final String workflowId = "workflow-1";
    final String fromNodeId = "node-target";
    final String parentNodeId = "parent-node";
    final Sinks.One<Void> safeStopSink = Sinks.one();
    final WorkflowNode parentNode = new WorkflowNode(parentNodeId, "processor", Map.of());
    final RestartFromNodeCommand command =
        new RestartFromNodeCommand(executionId, fromNodeId, newExecutionId);

    when(registry.findByExecutionId(executionId)).thenReturn(Optional.of(executionControl));
    when(executionControl.executionId()).thenReturn(executionId);
    when(executionControl.sessionId()).thenReturn(sessionId);
    when(executionControl.workflowId()).thenReturn(workflowId);
    when(executionControl.prepared()).thenReturn(preparedWorkflow);
    when(executionControl.safeStopSink()).thenReturn(safeStopSink);
    when(preparedWorkflow.parentsList()).thenReturn(Map.of(fromNodeId, List.of(parentNode)));
    when(checkpointStore.get(executionId, parentNodeId))
        .thenReturn(Mono.error(new RuntimeException("Checkpoint not found")));
    when(orchestrator.restartFromNode(
            eq(sessionId),
            eq(workflowId),
            eq(executionId),
            eq(newExecutionId),
            eq(preparedWorkflow),
            eq(fromNodeId),
            eq(Map.of())))
        .thenReturn(Mono.never());

    // When
    final var result = processor.process(command);

    // Then
    StepVerifier.create(result).verifyComplete();
    verify(completionSink).completeRestartSuccess(newExecutionId);
  }

  @Test
  void process_executionNotFound_reportsFailureAndCompletesEmpty() {
    // Given
    final String executionId = "exec-not-found";
    final String newExecutionId = "new-exec-not-found";
    final RestartFromNodeCommand command =
        new RestartFromNodeCommand(executionId, "node-1", newExecutionId);
    when(registry.findByExecutionId(executionId)).thenReturn(Optional.empty());

    // When
    final var result = processor.process(command);

    // Then
    StepVerifier.create(result).verifyComplete();
    verify(completionSink)
        .completeRestartFailure(eq(newExecutionId), any(IllegalArgumentException.class));
  }

  @Test
  void getPriority_returnsCorrectValue() {
    assertThat(processor.getPriority()).isEqualTo(20);
  }

  @Test
  void process_orchestratorFailsAsynchronously_doesNotReportFailureViaCompletionSink() {
    // Given: detachment means an async orchestrator failure is not observable by process()'s
    // own Mono — completeRestartSuccess was already called at subscribe time.
    final String executionId = "exec-orch-fails";
    final String newExecutionId = "new-exec-orch-fails";
    final String sessionId = "session-1";
    final String workflowId = "workflow-1";
    final String fromNodeId = "node-target";
    final String parentNodeId = "parent-node";
    final Sinks.One<Void> safeStopSink = Sinks.one();
    final WorkflowNode parentNode = new WorkflowNode(parentNodeId, "processor", Map.of());
    final RestartFromNodeCommand command =
        new RestartFromNodeCommand(executionId, fromNodeId, newExecutionId);

    when(registry.findByExecutionId(executionId)).thenReturn(Optional.of(executionControl));
    when(executionControl.executionId()).thenReturn(executionId);
    when(executionControl.sessionId()).thenReturn(sessionId);
    when(executionControl.workflowId()).thenReturn(workflowId);
    when(executionControl.prepared()).thenReturn(preparedWorkflow);
    when(executionControl.safeStopSink()).thenReturn(safeStopSink);
    when(preparedWorkflow.parentsList()).thenReturn(Map.of(fromNodeId, List.of(parentNode)));
    when(checkpointStore.get(executionId, parentNodeId)).thenReturn(Mono.just(checkpointMessage));
    when(checkpointMessage.getSourceNodeId()).thenReturn(parentNodeId);
    when(orchestrator.restartFromNode(
            eq(sessionId),
            eq(workflowId),
            eq(executionId),
            eq(newExecutionId),
            eq(preparedWorkflow),
            eq(fromNodeId),
            any()))
        .thenReturn(Mono.error(new RuntimeException("Orchestrator restart failed")));

    // When
    final var result = processor.process(command);

    // Then
    StepVerifier.create(result).verifyComplete();
    verify(registry).unregister(executionId);
    verify(checkpointStore).clear(executionId);
    verify(completionSink, timeout(1000)).completeRestartSuccess(newExecutionId);
    verify(completionSink, never()).completeRestartFailure(eq(newExecutionId), any());
  }

  @Test
  void process_multipleParentNodes_oneCheckpointFailsPartial() {
    // Given
    final String executionId = "exec-partial-checkpoints";
    final String newExecutionId = "new-exec-partial-checkpoints";
    final String sessionId = "session-1";
    final String workflowId = "workflow-1";
    final String fromNodeId = "node-target";
    final String parentNode1Id = "parent-1";
    final String parentNode2Id = "parent-2";
    final Sinks.One<Void> safeStopSink = Sinks.one();
    final WorkflowNode parentNode1 = new WorkflowNode(parentNode1Id, "processor", Map.of());
    final WorkflowNode parentNode2 = new WorkflowNode(parentNode2Id, "processor", Map.of());
    final RestartFromNodeCommand command =
        new RestartFromNodeCommand(executionId, fromNodeId, newExecutionId);

    when(registry.findByExecutionId(executionId)).thenReturn(Optional.of(executionControl));
    when(executionControl.executionId()).thenReturn(executionId);
    when(executionControl.sessionId()).thenReturn(sessionId);
    when(executionControl.workflowId()).thenReturn(workflowId);
    when(executionControl.prepared()).thenReturn(preparedWorkflow);
    when(executionControl.safeStopSink()).thenReturn(safeStopSink);
    when(preparedWorkflow.parentsList())
        .thenReturn(Map.of(fromNodeId, List.of(parentNode1, parentNode2)));
    when(checkpointStore.get(executionId, parentNode1Id)).thenReturn(Mono.just(checkpointMessage));
    when(checkpointMessage.getSourceNodeId()).thenReturn(parentNode1Id);
    when(checkpointStore.get(executionId, parentNode2Id))
        .thenReturn(Mono.error(new RuntimeException("Checkpoint not found")));
    when(orchestrator.restartFromNode(
            eq(sessionId),
            eq(workflowId),
            eq(executionId),
            eq(newExecutionId),
            eq(preparedWorkflow),
            eq(fromNodeId),
            any()))
        .thenReturn(Mono.never());

    // When
    final var result = processor.process(command);

    // Then
    StepVerifier.create(result).verifyComplete();
    verify(registry).unregister(executionId);
    verify(checkpointStore).clear(executionId);
    verify(completionSink).completeRestartSuccess(newExecutionId);
  }

  @Test
  void process_safeStopSinkEmitFails_continuesWithRestart() {
    // Given
    final String executionId = "exec-sink-emit-fail";
    final String newExecutionId = "new-exec-sink-emit-fail";
    final String sessionId = "session-1";
    final String workflowId = "workflow-1";
    final String fromNodeId = "node-target";
    final String parentNodeId = "parent-node";
    final WorkflowNode parentNode = new WorkflowNode(parentNodeId, "processor", Map.of());
    final RestartFromNodeCommand command =
        new RestartFromNodeCommand(executionId, fromNodeId, newExecutionId);

    when(registry.findByExecutionId(executionId)).thenReturn(Optional.of(executionControl));
    when(executionControl.executionId()).thenReturn(executionId);
    when(executionControl.sessionId()).thenReturn(sessionId);
    when(executionControl.workflowId()).thenReturn(workflowId);
    when(executionControl.prepared()).thenReturn(preparedWorkflow);
    when(preparedWorkflow.parentsList()).thenReturn(Map.of(fromNodeId, List.of(parentNode)));
    when(checkpointStore.get(executionId, parentNodeId)).thenReturn(Mono.just(checkpointMessage));
    when(checkpointMessage.getSourceNodeId()).thenReturn(parentNodeId);
    final Sinks.One<Void> failingSink = Sinks.one();
    failingSink.emitEmpty(Sinks.EmitFailureHandler.FAIL_FAST);
    when(executionControl.safeStopSink()).thenReturn(failingSink);
    when(orchestrator.restartFromNode(
            eq(sessionId),
            eq(workflowId),
            eq(executionId),
            eq(newExecutionId),
            eq(preparedWorkflow),
            eq(fromNodeId),
            any()))
        .thenReturn(Mono.never());

    // When
    final var result = processor.process(command);

    // Then — the outer onErrorResume catches the sink emit failure and reports it
    StepVerifier.create(result).verifyComplete();
    verify(completionSink)
        .completeRestartFailure(eq(newExecutionId), any(RuntimeException.class));
  }
}
```

- [ ] **Step 6: Rewrite `RestartFromNodeCommandProcessor`**

Replace the full content of `RestartFromNodeCommandProcessor.java`:

```java
// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Infenia Private Limited
package com.infenia.yukta.service.control.processor;

import com.infenia.yukta.message.Message;
import com.infenia.yukta.model.workflow.WorkflowNode;
import com.infenia.yukta.plugin.control.ControlSignalProcessor;
import com.infenia.yukta.plugin.control.ExecutionControlCommand;
import com.infenia.yukta.plugin.control.ExecutionControlCommand.RestartFromNodeCommand;
import com.infenia.yukta.plugin.control.WorkflowDirective;
import com.infenia.yukta.plugin.store.NodeCheckpointStore;
import com.infenia.yukta.service.control.gateway.RestartCompletionSink;
import com.infenia.yukta.service.control.store.ExecutionControlRegistry;
import com.infenia.yukta.service.orchestrator.WorkflowOrchestrator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

/**
 * Processor for restart from node commands.
 *
 * <p>Stops the current execution and restarts from a specific node, using the last checkpoint
 * messages from parent nodes. The new execution is detached ({@code
 * subscribeOn(boundedElastic()).subscribe()}) so this processor reports completion as soon as the
 * new execution is subscribed, not once it finishes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RestartFromNodeCommandProcessor implements ControlSignalProcessor {

  /** The execution control registry for accessing execution control state. */
  private final ExecutionControlRegistry registry;

  /** The workflow orchestrator for restarting workflows. */
  private final WorkflowOrchestrator orchestrator;

  /** The node checkpoint store for accessing node state checkpoints. */
  private final NodeCheckpointStore checkpointStore;

  /** Reports the outcome of a restart back to the awaiting gateway caller. */
  private final RestartCompletionSink completionSink;

  @Override
  public boolean canProcess(final ExecutionControlCommand command) {
    return command instanceof RestartFromNodeCommand;
  }

  @Override
  public Mono<WorkflowDirective> process(final ExecutionControlCommand command) {
    final RestartFromNodeCommand restart = (RestartFromNodeCommand) command;

    return Mono.fromSupplier(
            () ->
                registry
                    .findByExecutionId(restart.executionId())
                    .orElseThrow(
                        () ->
                            new IllegalArgumentException(
                                "Execution not found: " + restart.executionId())))
        .flatMap(
            control -> {
              final List<String> parentNodeIds =
                  control
                      .prepared()
                      .parentsList()
                      .getOrDefault(restart.fromNodeId(), List.of())
                      .stream()
                      .map(WorkflowNode::nodeId)
                      .toList();

              return Flux.fromIterable(parentNodeIds)
                  .flatMap(
                      parentNodeId ->
                          checkpointStore
                              .get(control.executionId(), parentNodeId)
                              .doOnNext(
                                  v ->
                                      log.atDebug()
                                          .addKeyValue("parentNodeId", parentNodeId)
                                          .log("Loaded checkpoint"))
                              .onErrorResume(
                                  e -> {
                                    log.atWarn()
                                        .addKeyValue("parentNodeId", parentNodeId)
                                        .log("No checkpoint for parent node");
                                    return Mono.empty();
                                  }))
                  .collectMap(Message::getSourceNodeId, m -> m)
                  .doOnNext(
                      parentCheckpoints -> {
                        registry.unregister(control.executionId());
                        control.safeStopSink().emitEmpty(Sinks.EmitFailureHandler.FAIL_FAST);
                        checkpointStore.clear(control.executionId());

                        @SuppressWarnings("unchecked")
                        final Map<String, Message<?>> checkpoints =
                            (Map<String, Message<?>>) (Map<?, ?>) parentCheckpoints;
                        orchestrator
                            .restartFromNode(
                                control.sessionId(),
                                control.workflowId(),
                                control.executionId(),
                                restart.newExecutionId(),
                                control.prepared(),
                                restart.fromNodeId(),
                                checkpoints)
                            .subscribeOn(Schedulers.boundedElastic())
                            .doOnError(
                                err ->
                                    log.atError()
                                        .setCause(err)
                                        .addKeyValue("oldExecutionId", restart.executionId())
                                        .addKeyValue("newExecutionId", restart.newExecutionId())
                                        .log("Restarted execution from node failed"))
                            .subscribe();

                        log.atInfo()
                            .addKeyValue("oldExecutionId", restart.executionId())
                            .addKeyValue("newExecutionId", restart.newExecutionId())
                            .addKeyValue("fromNodeId", restart.fromNodeId())
                            .log("Restarted execution from node");
                        completionSink.completeRestartSuccess(restart.newExecutionId());
                      });
            })
        .then(Mono.<WorkflowDirective>empty())
        .onErrorResume(
            e -> {
              log.atError()
                  .addKeyValue("executionId", restart.executionId())
                  .addKeyValue("fromNodeId", restart.fromNodeId())
                  .setCause(e)
                  .log("Restart from node failed");
              completionSink.completeRestartFailure(restart.newExecutionId(), e);
              return Mono.empty();
            });
  }

  @Override
  public int getPriority() {
    return 20;
  }
}
```

Note the two separate `onErrorResume` layers in the original (one wrapping the `flatMap` body, one wrapping the whole chain) are consolidated into a single outer `onErrorResume`, since detaching the orchestrator call removes the only reason the inner one existed (to catch an orchestrator failure that could no longer occur synchronously in this chain — it's now fully async and unrelated to `process()`'s completion). `taskTracker` is removed as a dependency entirely, along with its `emitWorkflowStatusEvent` call, for the same reason as Task 4 Step 3.

- [ ] **Step 7: Run `RestartFromNodeCommandProcessorTest`**

Run: `./gradlew :core:test --tests com.infenia.yukta.service.control.processor.RestartFromNodeCommandProcessorTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Run the full core test suite and static analysis**

Run: `./gradlew :core:test :core:checkstyleMain :core:checkstyleTest :core:pmdMain :core:pmdTest :core:spotbugsMain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
git add core/src/main/java/com/infenia/yukta/service/control/processor/RestartCommandProcessor.java \
        core/src/main/java/com/infenia/yukta/service/control/processor/RestartFromNodeCommandProcessor.java \
        core/src/test/java/com/infenia/yukta/service/control/processor/RestartCommandProcessorTest.java \
        core/src/test/java/com/infenia/yukta/service/control/processor/RestartFromNodeCommandProcessorTest.java
git commit -m "fix: detach restarted execution and report real completion to gateway"
```

---

### Task 5: Add REST endpoints for restart

**Files:**
- Modify: `web/src/main/java/com/infenia/yukta/controller/WorkflowController.java`
- Modify: `web/src/test/java/com/infenia/yukta/controller/WorkflowControllerTest.java`

**Interfaces:**
- Consumes: `ControlBusGateway.restartWorkflow(String executionId): Mono<String>`, `ControlBusGateway.restartFromNode(String executionId, String fromNodeId): Mono<String>` (both already declared on the interface; behavior fixed by Tasks 3–4).
- Produces: `POST /api/workflow/executions/{executionId}/restart` and `POST /api/workflow/executions/{executionId}/restart/{fromNodeId}`, both returning `ApiResponse<WorkflowStartResponse>`.

- [ ] **Step 1: Write failing controller tests**

Add these tests to `WorkflowControllerTest.java`, immediately after the existing `testStopExecutionNotFoundLogging` test (currently ending at line 747, right before the `// --- Pause Workflow Tests ---` section):

First add two new constants near the existing `API_WORKFLOW_EXECUTIONS`/`STOP` constants (after line 100):

```java
  /** Restart endpoint suffix. */
  private static final String RESTART = "/restart";
```

Then the tests:

```java
  // --- Restart Workflow Tests ---

  @Test
  void testRestartWorkflowSuccess() {
    final String newExecId = "new-exec-1";
    when(controlBusGateway.restartWorkflow(EXEC_ID_1)).thenReturn(Mono.just(newExecId));

    final var result =
        webClient
            .post()
            .uri(API_WORKFLOW_EXECUTIONS + EXEC_ID_1 + RESTART)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath(DOLLAR_STATUS)
            .isEqualTo(200)
            .jsonPath(DOLLAR_MESSAGE)
            .isEqualTo("Workflow restart accepted")
            .jsonPath(DOLLAR_DATA_EXECUTION_ID)
            .isEqualTo(newExecId)
            .returnResult();
    assertThat(result.getStatus().value()).isEqualTo(200);
  }

  @Test
  void testRestartWorkflowNotFound() {
    when(controlBusGateway.restartWorkflow(EXEC_ID_1))
        .thenReturn(Mono.error(new IllegalArgumentException(EXECUTION_NOT_FOUND)));

    final var result =
        webClient
            .post()
            .uri(API_WORKFLOW_EXECUTIONS + EXEC_ID_1 + RESTART)
            .exchange()
            .expectStatus()
            .isNotFound()
            .expectBody()
            .jsonPath(DOLLAR_STATUS)
            .isEqualTo(404)
            .jsonPath(DOLLAR_MESSAGE)
            .isEqualTo(EXECUTION_NOT_FOUND)
            .returnResult();
    assertThat(result.getStatus().value()).isEqualTo(404);
  }

  @Test
  void testRestartFromNodeSuccess() {
    final String newExecId = "new-exec-2";
    when(controlBusGateway.restartFromNode(EXEC_ID_1, NODE_ID_1)).thenReturn(Mono.just(newExecId));

    final var result =
        webClient
            .post()
            .uri(API_WORKFLOW_EXECUTIONS + EXEC_ID_1 + RESTART + "/" + NODE_ID_1)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath(DOLLAR_STATUS)
            .isEqualTo(200)
            .jsonPath(DOLLAR_MESSAGE)
            .isEqualTo("Workflow restart from node accepted")
            .jsonPath(DOLLAR_DATA_EXECUTION_ID)
            .isEqualTo(newExecId)
            .returnResult();
    assertThat(result.getStatus().value()).isEqualTo(200);
  }

  @Test
  void testRestartFromNodeNotFound() {
    when(controlBusGateway.restartFromNode(EXEC_ID_1, NODE_ID_1))
        .thenReturn(Mono.error(new IllegalArgumentException(EXECUTION_NOT_FOUND)));

    final var result =
        webClient
            .post()
            .uri(API_WORKFLOW_EXECUTIONS + EXEC_ID_1 + RESTART + "/" + NODE_ID_1)
            .exchange()
            .expectStatus()
            .isNotFound()
            .expectBody()
            .jsonPath(DOLLAR_STATUS)
            .isEqualTo(404)
            .jsonPath(DOLLAR_MESSAGE)
            .isEqualTo(EXECUTION_NOT_FOUND)
            .returnResult();
    assertThat(result.getStatus().value()).isEqualTo(404);
  }
```

- [ ] **Step 2: Run to verify tests fail**

Run: `./gradlew :web:test --tests com.infenia.yukta.controller.WorkflowControllerTest`
Expected: FAIL — `controlBusGateway.restartWorkflow(...)`/`restartFromNode(...)` stubs are set up but no endpoint exists yet to call them, so `webClient.post()...` returns 404 (no route matched) instead of the expected body/status.

- [ ] **Step 3: Add the two endpoints to `WorkflowController`**

Add immediately after `stopExecution` (after line 259, before the `executeControlSignal` helper):

```java
  /**
   * Safely stop the current execution and restart the entire workflow from the beginning using
   * the original trigger payload.
   *
   * @param executionId the execution to restart
   * @return response entity with the new execution ID
   */
  @PostMapping("/workflow/executions/{executionId}/restart")
  @Operation(
      summary = "Restart a workflow execution",
      description =
          "Stops the current execution and restarts the entire workflow from the beginning using"
              + " the original trigger payload")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = HTTP_200,
      description = "Workflow restart accepted")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "404",
      description = "Execution not found")
  public Mono<ResponseEntity<ApiResponse<WorkflowStartResponse>>> restartWorkflow(
      @Parameter(description = "Execution ID") @PathVariable final String executionId,
      final ServerWebExchange exchange) {
    log.atInfo().log("restartWorkflow: executionId={}", executionId);
    return controlBus
        .restartWorkflow(executionId)
        .doOnNext(
            newExecId ->
                log.atInfo().log(
                    "restartWorkflow command accepted: executionId={}, newExecutionId={}",
                    executionId,
                    newExecId))
        .map(
            newExecId ->
                ResponseEntity.ok(
                    ApiResponse.success(
                        200, "Workflow restart accepted", new WorkflowStartResponse(newExecId))))
        .doOnSuccess(
            _ ->
                log.atInfo().log(
                    "restartWorkflow response sent successfully: executionId={}", executionId))
        .onErrorResume(
            e -> {
              log.atError()
                  .log(
                      "restartWorkflow error occurred: executionId={}, error={}",
                      executionId,
                      e.getMessage());
              @SuppressWarnings("PMD.LawOfDemeter")
              final var req = exchange.getRequest();
              final String path = req.getPath().value();
              final List<ApiResponse.FieldError> errors =
                  List.of(new ApiResponse.FieldError("execution", e.getMessage()));
              return Mono.just(
                  ResponseEntity.status(HttpStatus.NOT_FOUND)
                      .body(
                          ApiResponse.error(404, NOT_FOUND, "Execution not found", path, errors)));
            });
  }

  /**
   * Safely stop the current execution and restart the workflow from a specific node, replaying
   * the last known checkpoints for its parent nodes.
   *
   * @param executionId the execution to restart
   * @param fromNodeId the node from which to resume execution
   * @return response entity with the new execution ID
   */
  @PostMapping("/workflow/executions/{executionId}/restart/{fromNodeId}")
  @Operation(
      summary = "Restart a workflow execution from a node",
      description =
          "Stops the current execution and restarts from a specific node, replaying the last"
              + " known checkpoints for its parent nodes")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = HTTP_200,
      description = "Workflow restart from node accepted")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "404",
      description = "Execution not found")
  public Mono<ResponseEntity<ApiResponse<WorkflowStartResponse>>> restartFromNode(
      @Parameter(description = "Execution ID") @PathVariable final String executionId,
      @Parameter(description = "Node ID to restart from") @PathVariable final String fromNodeId,
      final ServerWebExchange exchange) {
    log.atInfo().log(
        "restartFromNode: executionId={}, fromNodeId={}", executionId, fromNodeId);
    return controlBus
        .restartFromNode(executionId, fromNodeId)
        .doOnNext(
            newExecId ->
                log.atInfo().log(
                    "restartFromNode command accepted: executionId={}, fromNodeId={},"
                        + " newExecutionId={}",
                    executionId,
                    fromNodeId,
                    newExecId))
        .map(
            newExecId ->
                ResponseEntity.ok(
                    ApiResponse.success(
                        200,
                        "Workflow restart from node accepted",
                        new WorkflowStartResponse(newExecId))))
        .doOnSuccess(
            _ ->
                log.atInfo().log(
                    "restartFromNode response sent successfully: executionId={}, fromNodeId={}",
                    executionId,
                    fromNodeId))
        .onErrorResume(
            e -> {
              log.atError()
                  .log(
                      "restartFromNode error occurred: executionId={}, fromNodeId={}, error={}",
                      executionId,
                      fromNodeId,
                      e.getMessage());
              @SuppressWarnings("PMD.LawOfDemeter")
              final var req = exchange.getRequest();
              final String path = req.getPath().value();
              final List<ApiResponse.FieldError> errors =
                  List.of(new ApiResponse.FieldError("execution", e.getMessage()));
              return Mono.just(
                  ResponseEntity.status(HttpStatus.NOT_FOUND)
                      .body(
                          ApiResponse.error(404, NOT_FOUND, "Execution not found", path, errors)));
            });
  }
```

- [ ] **Step 4: Run the controller tests**

Run: `./gradlew :web:test --tests com.infenia.yukta.controller.WorkflowControllerTest`
Expected: BUILD SUCCESSFUL, all 4 new tests pass plus all pre-existing tests still pass.

- [ ] **Step 5: Run Checkstyle/PMD on the web module**

Run: `./gradlew :web:checkstyleMain :web:checkstyleTest :web:pmdMain :web:pmdTest`
Expected: BUILD SUCCESSFUL. If PMD flags `WorkflowController` for excessive method count (it already carries `@SuppressWarnings("PMD.TooManyMethods")` at the class level per the existing file, so this should already be covered) or `WorkflowControllerTest` for the same, add `PMD.CyclomaticComplexity`/`PMD.TooManyMethods` to that test class's existing `@SuppressWarnings` list only if the build actually fails on it — don't add preemptively.

- [ ] **Step 6: Commit**

```bash
git add web/src/main/java/com/infenia/yukta/controller/WorkflowController.java \
        web/src/test/java/com/infenia/yukta/controller/WorkflowControllerTest.java
git commit -m "feat: add REST endpoints for restarting workflow executions"
```

---

### Task 6: Full-repo verification

**Files:** none (verification only)

- [ ] **Step 1: Run Spotless**

Run: `./gradlew spotlessApply`
Expected: BUILD SUCCESSFUL. If it reformats anything, review the diff before proceeding.

- [ ] **Step 2: Run the full quality gate**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL — all tests, Checkstyle, PMD, SpotBugs, JaCoCo across every module.

- [ ] **Step 3: Grep for any remaining old-arity restart command construction**

Run: `grep -rn "new RestartCommand(\|new RestartFromNodeCommand(" --include="*.java" core plugin-api web mcp`
Expected: every match shows the new 2-arg/3-arg constructor form. If any 1-arg/2-arg call site remains, fix it — it indicates a missed call site from Task 1–5.

- [ ] **Step 4: If Spotless reformatted anything in Step 1, commit that separately**

```bash
git add -A
git commit -m "style: apply spotless formatting"
```

(Skip this step if Step 1 made no changes.)

---

## Self-Review Notes

- **Spec coverage**: Design §1 (command records) → Task 1. Design §2 (await mechanism) → Task 3. Design §3 (processor detach + completion) → Task 4. Design §4 (delete dead code) → Task 2. Design §5 (REST endpoints) → Task 5. Error handling (not-found, timeout, cleanup, concurrent restarts, race with timeout) → covered across Task 3 Steps 2/4 and Task 4 Step 1/5 test cases. Non-goals (finished executions, checkpoint/orchestrator changes, MCP) are untouched by every task above — confirmed no task modifies `WorkflowOrchestrator`, `NodeCheckpointStore`, or any MCP tool file.
- **Placeholder scan**: no TBD/TODO; every step shows complete code, not descriptions of code.
- **Type consistency**: `RestartCompletionSink.completeRestartSuccess(String)`/`completeRestartFailure(String, Throwable)` used identically in Task 3 (implementation + tests) and Task 4 (processor code + tests) — same method names, same parameter order, throughout. `RestartCommand(executionId, newExecutionId)` / `RestartFromNodeCommand(executionId, fromNodeId, newExecutionId)` field order is consistent in every construction site shown (Tasks 1, 3, 4, 5's REST layer doesn't construct these directly — it only calls the already-fixed gateway methods).
- **Reactor semantics check**: Task 4 switches the processors from `flatMap` to `doOnNext` for the side-effecting block (unregister, trip safe-stop, detach-and-subscribe, report completion). Verified this is correct, not a shortcut: `doOnNext`'s consumer runs synchronously inline, and any exception it throws (e.g. `safeStopSink().emitEmpty(...)` on an already-completed sink) converts to an `onError` signal exactly like a `flatMap` mapper throwing would — so `completeRestartFailure` still fires via the outer `onErrorResume` for synchronous side-effect failures. `RestartFromNodeCommandProcessorTest` already had a test for this path (`process_safeStopSinkEmitFails_continuesWithRestart`); `RestartCommandProcessorTest` was missing the mirror case, so `process_safeStopSinkEmitFails_reportsFailure` was added to Task 4 Step 1 to close that gap.
