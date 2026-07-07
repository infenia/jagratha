# Pause/Resume Workflow REST Endpoints Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `pauseWorkflow`/`resumeWorkflow` REST endpoints to `WorkflowController`, and fix a validation gap where the underlying gateway methods silently succeed for a nonexistent `executionId`.

**Architecture:** `DefaultControlBusGateway.pauseWorkflow`/`resumeWorkflow` are changed to validate the execution exists (via `ExecutionControlRegistry.findByExecutionId`) before emitting the command, mirroring the existing `stopExecution` pattern. Two new `WorkflowController` endpoints call these gateway methods and follow the exact structure of the existing `stopExecution` endpoint (structured logging, `ApiResponse` wrapper, `onErrorResume` → 404).

**Tech Stack:** Java 25, Spring Boot 4.1.0 (WebFlux), Project Reactor (`Mono`), JUnit 5, Mockito, `reactor-test` (`StepVerifier`), `WebTestClient`.

## Global Constraints

- Every Java file must have the Apache License 2.0 header (see any existing file in this repo for the exact text — copied verbatim into new/modified files below).
- Formatting: Google Java Style via Spotless, 2-space indent, 100-char line limit. Run `./gradlew spotlessApply` before finalizing.
- Run `./gradlew :core:test` and `./gradlew :web:test` after each task; run `./gradlew check` before the final commit.
- Follow Conventional Commits (`feat: ...`, `test: ...`) per `.claude/rules/git-workflow.md`.

---

### Task 1: Fix `pauseWorkflow`/`resumeWorkflow` validation gap in `DefaultControlBusGateway`

**Files:**
- Modify: `core/src/main/java/com/infenia/yukta/service/control/gateway/DefaultControlBusGateway.java:323-359`
- Test: `core/src/test/java/com/infenia/yukta/service/control/gateway/DefaultControlBusGatewayTest.java`

**Interfaces:**
- Consumes: `ExecutionControlRegistry.findByExecutionId(String executionId) -> Optional<ExecutionControl>` (already a field: `executionControlRegistry`, already used by `stopExecution` at line ~480-511).
- Produces: `pauseWorkflow(String executionId) -> Mono<Void>` and `resumeWorkflow(String executionId) -> Mono<Void>` now emit `IllegalArgumentException("Execution not found: " + executionId)` through the Mono's error channel when `executionId` is unknown, instead of silently succeeding. Signature unchanged — `ControlBusGateway` interface is untouched.

- [ ] **Step 1: Write failing tests for the new not-found behavior**

Add these two tests to `DefaultControlBusGatewayTest.java`, placed directly after the existing `resumeWorkflow_validExecutionId_emitsResumeCommand` test (currently ending at line 225):

```java
  @Test
  void pauseWorkflow_executionNotFound_throwsIllegalArgumentException() {
    // Given
    final String executionId = "exec-pause-not-found";
    when(executionControlRegistry.findByExecutionId(executionId)).thenReturn(Optional.empty());

    // When
    final Mono<Void> result = gateway.pauseWorkflow(executionId);

    // Then
    StepVerifier.create(result)
        .expectErrorMatches(
            err ->
                err instanceof IllegalArgumentException
                    && err.getMessage().contains("Execution not found")
                    && err.getMessage().contains(executionId))
        .verify();
  }

  @Test
  void resumeWorkflow_executionNotFound_throwsIllegalArgumentException() {
    // Given
    final String executionId = "exec-resume-not-found";
    when(executionControlRegistry.findByExecutionId(executionId)).thenReturn(Optional.empty());

    // When
    final Mono<Void> result = gateway.resumeWorkflow(executionId);

    // Then
    StepVerifier.create(result)
        .expectErrorMatches(
            err ->
                err instanceof IllegalArgumentException
                    && err.getMessage().contains("Execution not found")
                    && err.getMessage().contains(executionId))
        .verify();
  }
```

- [ ] **Step 2: Run the new tests to verify they fail**

Run: `./gradlew :core:test --tests com.infenia.yukta.service.control.gateway.DefaultControlBusGatewayTest`

Expected: FAIL — `pauseWorkflow_executionNotFound_throwsIllegalArgumentException` and `resumeWorkflow_executionNotFound_throwsIllegalArgumentException` fail because `pauseWorkflow`/`resumeWorkflow` currently emit successfully regardless of registry state (`StepVerifier` sees `verifyComplete()`-like success, not the expected error).

- [ ] **Step 3: Update the existing pauseWorkflow/resumeWorkflow tests to stub the registry**

The existing tests below currently don't stub `executionControlRegistry`, so once Step 4's production fix lands they'll throw `IllegalArgumentException` instead of emitting. Update each to stub a found execution first.

Replace the existing `pauseWorkflow_validExecutionId_emitsPauseCommand` test (lines 184-205) with:

```java
  @Test
  void pauseWorkflow_validExecutionId_emitsPauseCommand() {
    // Given
    final String executionId = "exec-1";
    final ExecutionControl control = mock(ExecutionControl.class);
    when(executionControlRegistry.findByExecutionId(executionId)).thenReturn(Optional.of(control));
    when(controlBusService.emit(any())).thenReturn(Mono.empty());

    // When
    final Mono<Void> result = gateway.pauseWorkflow(executionId);

    // Then
    StepVerifier.create(result).verifyComplete();

    final ArgumentCaptor<Message<?>> captor = ArgumentCaptor.forClass(Message.class);
    verify(controlBusService).emit(captor.capture());
    final Message<?> emittedMessage = captor.getValue();
    assertThat(emittedMessage.getPayload()).isInstanceOf(PauseWorkflowCommand.class);
    final PauseWorkflowCommand cmd = (PauseWorkflowCommand) emittedMessage.getPayload();
    assertThat(cmd.executionId()).isEqualTo(executionId);
    assertThat(emittedMessage.getPriority()).isEqualTo(100);
    assertThat(emittedMessage.getSourceNodeId()).isEqualTo("CONTROL_BUS");
    assertThat(emittedMessage.isControlMessage()).isTrue();
  }
```

Replace the existing `resumeWorkflow_validExecutionId_emitsResumeCommand` test (lines 207-225) with:

```java
  @Test
  void resumeWorkflow_validExecutionId_emitsResumeCommand() {
    // Given
    final String executionId = "exec-2";
    final ExecutionControl control = mock(ExecutionControl.class);
    when(executionControlRegistry.findByExecutionId(executionId)).thenReturn(Optional.of(control));
    when(controlBusService.emit(any())).thenReturn(Mono.empty());

    // When
    final Mono<Void> result = gateway.resumeWorkflow(executionId);

    // Then
    StepVerifier.create(result).verifyComplete();

    final ArgumentCaptor<Message<?>> captor = ArgumentCaptor.forClass(Message.class);
    verify(controlBusService).emit(captor.capture());
    final Message<?> emittedMessage = captor.getValue();
    assertThat(emittedMessage.getPayload()).isInstanceOf(ResumeWorkflowCommand.class);
    final ResumeWorkflowCommand cmd = (ResumeWorkflowCommand) emittedMessage.getPayload();
    assertThat(cmd.executionId()).isEqualTo(executionId);
  }
```

Replace the existing `buildCommand_createsMessageWithCorrectProperties` test (lines 855-873) with:

```java
  @Test
  void buildCommand_createsMessageWithCorrectProperties() {
    // This test verifies the buildCommand method is executed
    // by testing that control commands create proper messages
    final String executionId = "exec-build-cmd";
    final ExecutionControl control = mock(ExecutionControl.class);
    when(executionControlRegistry.findByExecutionId(executionId)).thenReturn(Optional.of(control));
    when(controlBusService.emit(any())).thenReturn(Mono.empty());

    // When
    final Mono<Void> result = gateway.pauseWorkflow(executionId);

    // Then - verify the message has correct properties
    StepVerifier.create(result).verifyComplete();

    final ArgumentCaptor<Message<?>> captor = ArgumentCaptor.forClass(Message.class);
    verify(controlBusService).emit(captor.capture());
    final Message<?> emittedMessage = captor.getValue();
    assertThat(emittedMessage.isControlMessage()).isTrue();
    assertThat(emittedMessage.getSourceNodeId()).isEqualTo("CONTROL_BUS");
  }
```

Replace the existing `pauseWorkflow_emitError_logsErrorAndPropagates` test (lines 875-887) with:

```java
  @Test
  void pauseWorkflow_emitError_logsErrorAndPropagates() {
    // Given
    final String executionId = "exec-error";
    final ExecutionControl control = mock(ExecutionControl.class);
    when(executionControlRegistry.findByExecutionId(executionId)).thenReturn(Optional.of(control));
    final RuntimeException testError = new RuntimeException("Emit failed");
    when(controlBusService.emit(any())).thenReturn(Mono.error(testError));

    // When
    final Mono<Void> result = gateway.pauseWorkflow(executionId);

    // Then - error should be propagated
    StepVerifier.create(result).expectError(RuntimeException.class).verify();
  }
```

Replace the existing `resumeWorkflow_emitError_logsErrorAndPropagates` test (lines 889-901) with:

```java
  @Test
  void resumeWorkflow_emitError_logsErrorAndPropagates() {
    // Given
    final String executionId = "exec-error";
    final ExecutionControl control = mock(ExecutionControl.class);
    when(executionControlRegistry.findByExecutionId(executionId)).thenReturn(Optional.of(control));
    final RuntimeException testError = new RuntimeException("Emit failed");
    when(controlBusService.emit(any())).thenReturn(Mono.error(testError));

    // When
    final Mono<Void> result = gateway.resumeWorkflow(executionId);

    // Then
    StepVerifier.create(result).expectError(RuntimeException.class).verify();
  }
```

- [ ] **Step 4: Implement the production fix**

In `DefaultControlBusGateway.java`, replace the existing `pauseWorkflow` method (lines 323-340):

```java
  @Override
  public Mono<Void> pauseWorkflow(final String executionId) {
    return Mono.fromSupplier(
            () ->
                executionControlRegistry
                    .findByExecutionId(executionId)
                    .orElseThrow(
                        () -> new IllegalArgumentException("Execution not found: " + executionId)))
        .flatMap(
            control ->
                executeCommand(
                    buildCommand(new PauseWorkflowCommand(executionId), CONTROL_COMMAND_PRIORITY)))
        .doOnSubscribe(
            _ -> log.atInfo().addKeyValue("executionId", executionId).log("Pausing workflow"))
        .doOnSuccess(
            _ ->
                log.atDebug()
                    .addKeyValue("executionId", executionId)
                    .log("Workflow pause command executed"))
        .doOnError(
            err ->
                log.atError()
                    .setCause(err)
                    .addKeyValue("executionId", executionId)
                    .log("Failed to pause workflow"));
  }
```

And replace the existing `resumeWorkflow` method (lines 342-359):

```java
  @Override
  public Mono<Void> resumeWorkflow(final String executionId) {
    return Mono.fromSupplier(
            () ->
                executionControlRegistry
                    .findByExecutionId(executionId)
                    .orElseThrow(
                        () -> new IllegalArgumentException("Execution not found: " + executionId)))
        .flatMap(
            control ->
                executeCommand(
                    buildCommand(new ResumeWorkflowCommand(executionId), CONTROL_COMMAND_PRIORITY)))
        .doOnSubscribe(
            _ -> log.atInfo().addKeyValue("executionId", executionId).log("Resuming workflow"))
        .doOnSuccess(
            _ ->
                log.atDebug()
                    .addKeyValue("executionId", executionId)
                    .log("Workflow resume command executed"))
        .doOnError(
            err ->
                log.atError()
                    .setCause(err)
                    .addKeyValue("executionId", executionId)
                    .log("Failed to resume workflow"));
  }
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :core:test --tests com.infenia.yukta.service.control.gateway.DefaultControlBusGatewayTest`

Expected: PASS — all tests in the class, including the two new not-found tests and the five updated tests.

- [ ] **Step 6: Run spotless and full core test suite**

Run: `./gradlew spotlessApply :core:test`

Expected: PASS with no formatting diffs and no test failures.

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/com/infenia/yukta/service/control/gateway/DefaultControlBusGateway.java core/src/test/java/com/infenia/yukta/service/control/gateway/DefaultControlBusGatewayTest.java
git commit -m "fix: validate execution exists before pausing or resuming workflow

pauseWorkflow and resumeWorkflow previously returned success even for
a nonexistent executionId, because the only validation happened
asynchronously inside the command processor and errors there were
swallowed by the dispatcher. Mirrors the existing stopExecution
pattern: validate synchronously via the execution control registry
before emitting the command."
```

---

### Task 2: Add `pauseWorkflow`/`resumeWorkflow` REST endpoints to `WorkflowController`

**Files:**
- Modify: `web/src/main/java/com/infenia/yukta/controller/WorkflowController.java` (add two methods after `stopExecution`, currently ending at line 271)
- Test: `web/src/test/java/com/infenia/yukta/controller/WorkflowControllerTest.java` (add tests after the `stopExecution` tests, currently ending at line 680)

**Interfaces:**
- Consumes: `ControlBusGateway.pauseWorkflow(String executionId) -> Mono<Void>`, `ControlBusGateway.resumeWorkflow(String executionId) -> Mono<Void>` (fixed in Task 1; already declared on the interface, no interface changes needed). Also consumes existing `WorkflowStartResponse(String executionId)` record (`web/src/main/java/com/infenia/yukta/dto/response/WorkflowStartResponse.java`) and `ApiResponse.success`/`ApiResponse.error` statics.
- Produces: `POST /api/workflow/{sessionId}/{executionId}/pause` and `POST /api/workflow/{sessionId}/{executionId}/resume`, each returning `Mono<ResponseEntity<ApiResponse<WorkflowStartResponse>>>` — 200 with `{executionId}` body on success, 404 with `ApiResponse.error(...)` on `IllegalArgumentException`.

- [ ] **Step 1: Write failing controller tests**

Add these constants to `WorkflowControllerTest.java`, directly after the existing `EXECUTION_NOT_FOUND` constant (line 113):

```java

  /** Pause endpoint path. */
  private static final String PAUSE_ENDPOINT = "/api/workflow/sess-1/exec-1/pause";

  /** Resume endpoint path. */
  private static final String RESUME_ENDPOINT = "/api/workflow/sess-1/exec-1/resume";

  /** Pause accepted message. */
  private static final String PAUSE_ACCEPTED = "Workflow pause signal accepted";

  /** Resume accepted message. */
  private static final String RESUME_ACCEPTED = "Workflow resume signal accepted";
```

Add these tests directly before the final closing brace of the class (after line 680, the end of `testStopExecutionNotFoundLogging`):

```java

  // --- Pause Workflow Tests ---

  @Test
  void testPauseWorkflowSuccess() {
    when(controlBusGateway.pauseWorkflow(EXEC_ID_1)).thenReturn(Mono.empty());

    final var result =
        webClient
            .post()
            .uri(PAUSE_ENDPOINT)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath(DOLLAR_STATUS)
            .isEqualTo(200)
            .jsonPath(DOLLAR_MESSAGE)
            .isEqualTo(PAUSE_ACCEPTED)
            .jsonPath("$.data.executionId")
            .isEqualTo(EXEC_ID_1)
            .returnResult();
    assertThat(result.getStatus().value()).isEqualTo(200);
  }

  @Test
  void testPauseWorkflowSuccessLogging(final CapturedOutput output) {
    when(controlBusGateway.pauseWorkflow(EXEC_ID_1)).thenReturn(Mono.empty());

    webClient.post().uri(PAUSE_ENDPOINT).exchange().expectStatus().isOk();

    assertThat(output.toString())
        .contains("pauseWorkflow: executionId=" + EXEC_ID_1)
        .contains("pauseWorkflow command accepted")
        .contains("pauseWorkflow response sent successfully");
  }

  @Test
  void testPauseWorkflowNotFound() {
    when(controlBusGateway.pauseWorkflow(EXEC_ID_1))
        .thenReturn(Mono.error(new IllegalArgumentException(EXECUTION_NOT_FOUND)));

    final var result =
        webClient
            .post()
            .uri(PAUSE_ENDPOINT)
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
  void testPauseWorkflowNotFoundLogging(final CapturedOutput output) {
    when(controlBusGateway.pauseWorkflow(EXEC_ID_1))
        .thenReturn(Mono.error(new IllegalArgumentException(EXECUTION_NOT_FOUND)));

    webClient.post().uri(PAUSE_ENDPOINT).exchange().expectStatus().isNotFound();

    assertThat(output.toString())
        .contains("pauseWorkflow: executionId=" + EXEC_ID_1)
        .contains("pauseWorkflow error occurred")
        .contains(EXECUTION_NOT_FOUND);
  }

  // --- Resume Workflow Tests ---

  @Test
  void testResumeWorkflowSuccess() {
    when(controlBusGateway.resumeWorkflow(EXEC_ID_1)).thenReturn(Mono.empty());

    final var result =
        webClient
            .post()
            .uri(RESUME_ENDPOINT)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath(DOLLAR_STATUS)
            .isEqualTo(200)
            .jsonPath(DOLLAR_MESSAGE)
            .isEqualTo(RESUME_ACCEPTED)
            .jsonPath("$.data.executionId")
            .isEqualTo(EXEC_ID_1)
            .returnResult();
    assertThat(result.getStatus().value()).isEqualTo(200);
  }

  @Test
  void testResumeWorkflowSuccessLogging(final CapturedOutput output) {
    when(controlBusGateway.resumeWorkflow(EXEC_ID_1)).thenReturn(Mono.empty());

    webClient.post().uri(RESUME_ENDPOINT).exchange().expectStatus().isOk();

    assertThat(output.toString())
        .contains("resumeWorkflow: executionId=" + EXEC_ID_1)
        .contains("resumeWorkflow command accepted")
        .contains("resumeWorkflow response sent successfully");
  }

  @Test
  void testResumeWorkflowNotFound() {
    when(controlBusGateway.resumeWorkflow(EXEC_ID_1))
        .thenReturn(Mono.error(new IllegalArgumentException(EXECUTION_NOT_FOUND)));

    final var result =
        webClient
            .post()
            .uri(RESUME_ENDPOINT)
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
  void testResumeWorkflowNotFoundLogging(final CapturedOutput output) {
    when(controlBusGateway.resumeWorkflow(EXEC_ID_1))
        .thenReturn(Mono.error(new IllegalArgumentException(EXECUTION_NOT_FOUND)));

    webClient.post().uri(RESUME_ENDPOINT).exchange().expectStatus().isNotFound();

    assertThat(output.toString())
        .contains("resumeWorkflow: executionId=" + EXEC_ID_1)
        .contains("resumeWorkflow error occurred")
        .contains(EXECUTION_NOT_FOUND);
  }
```

- [ ] **Step 2: Run the new tests to verify they fail**

Run: `./gradlew :web:test --tests com.infenia.yukta.controller.WorkflowControllerTest`

Expected: FAIL — compilation error or 404s, since `WorkflowController` has no `/pause` or `/resume` mappings yet.

- [ ] **Step 3: Implement the two controller endpoints**

In `WorkflowController.java`, insert these two methods directly after `stopExecution` (after line 271, before the `getWorkflowStatus` method's javadoc at line 273):

```java

  /**
   * Pause a workflow execution.
   *
   * <p>Applies global backpressure so the execution stops pulling new elements while inflight work
   * continues to completion.
   *
   * @param sessionId the session identifier
   * @param executionId the execution to pause
   * @return response entity with the paused execution ID
   */
  @PostMapping("/workflow/{sessionId}/{executionId}/pause")
  @Operation(
      summary = "Pause a workflow execution",
      description = "Pauses a specific workflow execution via global backpressure")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = HTTP_200,
      description = "Workflow pause signal accepted")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "404",
      description = "Execution not found")
  public Mono<ResponseEntity<ApiResponse<WorkflowStartResponse>>> pauseWorkflow(
      @Parameter(description = SESSION_ID_PARAM) @PathVariable final String sessionId,
      @Parameter(description = "Execution ID") @PathVariable final String executionId,
      final ServerWebExchange exchange) {
    log.atInfo().log("pauseWorkflow: sessionId={}, executionId={}", sessionId, executionId);
    return controlBus
        .pauseWorkflow(executionId)
        .doOnSuccess(
            _ ->
                log.atInfo().log(
                    "pauseWorkflow command accepted: executionId={}", executionId))
        .thenReturn(
            ResponseEntity.ok(
                ApiResponse.success(
                    200, "Workflow pause signal accepted", new WorkflowStartResponse(executionId))))
        .doOnSuccess(
            _ ->
                log.atInfo().log(
                    "pauseWorkflow response sent successfully: executionId={}", executionId))
        .onErrorResume(
            e -> {
              log.atError()
                  .log("pauseWorkflow error occurred: executionId={}, error={}", executionId, e.getMessage());
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
   * Resume a paused workflow execution.
   *
   * <p>Restores normal backpressure-driven element processing.
   *
   * @param sessionId the session identifier
   * @param executionId the execution to resume
   * @return response entity with the resumed execution ID
   */
  @PostMapping("/workflow/{sessionId}/{executionId}/resume")
  @Operation(
      summary = "Resume a workflow execution",
      description = "Resumes a specific paused workflow execution")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = HTTP_200,
      description = "Workflow resume signal accepted")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "404",
      description = "Execution not found")
  public Mono<ResponseEntity<ApiResponse<WorkflowStartResponse>>> resumeWorkflow(
      @Parameter(description = SESSION_ID_PARAM) @PathVariable final String sessionId,
      @Parameter(description = "Execution ID") @PathVariable final String executionId,
      final ServerWebExchange exchange) {
    log.atInfo().log("resumeWorkflow: sessionId={}, executionId={}", sessionId, executionId);
    return controlBus
        .resumeWorkflow(executionId)
        .doOnSuccess(
            _ ->
                log.atInfo().log(
                    "resumeWorkflow command accepted: executionId={}", executionId))
        .thenReturn(
            ResponseEntity.ok(
                ApiResponse.success(
                    200,
                    "Workflow resume signal accepted",
                    new WorkflowStartResponse(executionId))))
        .doOnSuccess(
            _ ->
                log.atInfo().log(
                    "resumeWorkflow response sent successfully: executionId={}", executionId))
        .onErrorResume(
            e -> {
              log.atError()
                  .log(
                      "resumeWorkflow error occurred: executionId={}, error={}",
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
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :web:test --tests com.infenia.yukta.controller.WorkflowControllerTest`

Expected: PASS — all tests, including the 8 new pause/resume tests and all pre-existing tests.

- [ ] **Step 5: Run spotless and full web test suite**

Run: `./gradlew spotlessApply :web:test`

Expected: PASS with no formatting diffs and no test failures.

- [ ] **Step 6: Commit**

```bash
git add web/src/main/java/com/infenia/yukta/controller/WorkflowController.java web/src/test/java/com/infenia/yukta/controller/WorkflowControllerTest.java
git commit -m "feat: add pause and resume workflow REST endpoints

Exposes ControlBusGateway.pauseWorkflow/resumeWorkflow via
POST /api/workflow/{sessionId}/{executionId}/pause and .../resume,
following the same structure as the existing stopExecution endpoint."
```

---

### Task 3: Full verification

**Files:** None (verification only).

- [ ] **Step 1: Run the full quality gate**

Run: `./gradlew check`

Expected: PASS — all tests, Checkstyle, PMD, SpotBugs pass across all modules (OpenGrep only runs if the CLI is installed).

- [ ] **Step 2: Manually verify the endpoints via the running app**

Start the app: `./gradlew bootRun`

In a separate terminal, start a workflow to get an `executionId` (adjust `sessionId`/`workflowId` to a real workflow configured in the running instance), then exercise the new endpoints:

```bash
curl -s -X POST http://localhost:8080/api/workflow/start \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"<real-session-id>","workflowId":"<real-workflow-id>"}'

# Using the executionId returned above:
curl -s -X POST "http://localhost:8080/api/workflow/<real-session-id>/<execution-id>/pause" | jq .
curl -s -X POST "http://localhost:8080/api/workflow/<real-session-id>/<execution-id>/resume" | jq .

# Verify 404 on a bogus execution ID:
curl -s -o /dev/null -w "%{http_code}\n" -X POST \
  "http://localhost:8080/api/workflow/<real-session-id>/does-not-exist/pause"
```

Expected: pause/resume on the real execution ID return HTTP 200 with `{"data":{"executionId":"..."}}`; the bogus execution ID returns HTTP 404. Check the running app's logs to confirm `NODE_PAUSED`/`PAUSED` (and the resume equivalent) status events are emitted, verifying the fix actually reaches `PauseWorkflowCommandProcessor`/`ResumeWorkflowCommandProcessor`.

- [ ] **Step 3: Stop the app**

Stop `bootRun` (Ctrl+C in its terminal, or `kill` the process).
