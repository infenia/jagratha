# Merge Workflow Controllers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Merge WorkflowTriggerController and WorkflowStatusController into a single WorkflowController for simpler code organization.

**Architecture:** We'll consolidate the trigger and status/monitoring endpoints into one controller since they all operate on workflows. This reduces file count and simplifies navigation while maintaining clean endpoint organization.

**Tech Stack:** Java 25, Spring Boot 4.0.2 (WebFlux), JUnit 5, Mockito

---

## File Structure

**New Controller (Create):**
- `web/src/main/java/com/infenia/yukta/controller/WorkflowController.java` — Handles both workflow triggering and status/monitoring

**New Test Class (Create):**
- `web/src/test/java/com/infenia/yukta/controller/WorkflowControllerTest.java` — Tests both trigger and status functionality

**Files to Delete:**
- `web/src/main/java/com/infenia/yukta/controller/WorkflowTriggerController.java`
- `web/src/main/java/com/infenia/yukta/controller/WorkflowStatusController.java`
- `web/src/test/java/com/infenia/yukta/controller/WorkflowTriggerControllerTest.java`
- `web/src/test/java/com/infenia/yukta/controller/WorkflowStatusControllerTest.java`

---

## Task 1: Create Merged WorkflowController

**Files:**
- Create: `web/src/main/java/com/infenia/yukta/controller/WorkflowController.java`

- [ ] **Step 1: Create the merged WorkflowController**

Create the file combining all endpoints from both trigger and status controllers:

```java
/*
 * Copyright 2026 Infenia Private Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.infenia.yukta.controller;

import com.infenia.yukta.model.api.ApiResponse;
import com.infenia.yukta.model.api.TriggerResponse;
import com.infenia.yukta.model.api.WorkflowTriggerRequest;
import com.infenia.yukta.model.monitoring.WorkflowExecutionSummary;
import com.infenia.yukta.model.monitoring.WorkflowProgress;
import com.infenia.yukta.service.WorkflowService;
import com.infenia.yukta.service.control.gateway.ControlBusGateway;
import com.infenia.yukta.service.session.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(
    name = "Workflow API",
    description = "Endpoints for triggering and monitoring workflow executions")
public class WorkflowController {
  private final WorkflowService workflowService;
  private final ControlBusGateway controlBus;
  private final SessionService sessionService;

  private static final String HTTP_200 = "200";
  private static final String SESSION_ID_PARAM = "Session ID";

  /**
   * Trigger a workflow execution for a session.
   *
   * @param request the trigger request containing sessionId and workflowId
   * @return response entity with acknowledgment and execution ID
   */
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
  public Mono<ResponseEntity<ApiResponse<TriggerResponse>>> triggerWorkflow(
      @Valid @RequestBody final WorkflowTriggerRequest request, final ServerWebExchange exchange) {
    return workflowService
        .validateAndTriggerWorkflow(request.sessionId(), request.workflowId(), request.payload())
        .map(
            execution ->
                ResponseEntity.accepted()
                    .body(
                        ApiResponse.success(
                            202,
                            "Workflow trigger accepted",
                            new TriggerResponse(execution.executionId()))))
        .onErrorResume(
            e -> {
              final String path = exchange.getRequest().getPath().value();
              final List<ApiResponse.FieldError> errors =
                  List.of(new ApiResponse.FieldError("workflow", e.getMessage()));
              return Mono.just(
                  ResponseEntity.status(HttpStatus.NOT_FOUND)
                      .body(
                          ApiResponse.error(404, "Not Found", "Workflow not found", path, errors)));
            });
  }

  /**
   * Get the status of a specific workflow execution.
   *
   * @param sessionId the session identifier
   * @param executionId the execution identifier
   * @return response entity with workflow progress
   */
  @GetMapping("/workflow/{sessionId}/status/{executionId}")
  @Operation(
      summary = "Get workflow execution status",
      description = "Retrieves the current status and progress of a specific workflow execution")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = HTTP_200,
      description = "Workflow status retrieved successfully")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "404",
      description = "Execution not found")
  public Mono<ResponseEntity<ApiResponse<WorkflowProgress>>> getWorkflowStatus(
      @Parameter(description = SESSION_ID_PARAM) @PathVariable final String sessionId,
      @Parameter(description = "Execution ID") @PathVariable final String executionId,
      final ServerWebExchange exchange) {
    return Mono.fromCallable(() -> controlBus.getCurrentProgress(executionId))
        .flatMap(progress -> Mono.justOrEmpty(progress))
        .map(
            progress ->
                ResponseEntity.ok(
                    ApiResponse.success(200, "Workflow status retrieved successfully", progress)))
        .switchIfEmpty(
            Mono.fromSupplier(
                () -> {
                  final String path = exchange.getRequest().getPath().value();
                  final List<ApiResponse.FieldError> errors =
                      List.of(
                          new ApiResponse.FieldError(
                              "executionId", "Execution not found: '" + executionId + "'"));
                  return ResponseEntity.status(HttpStatus.NOT_FOUND)
                      .<ApiResponse<WorkflowProgress>>body(
                          ApiResponse.error(404, "Not Found", "Execution not found", path, errors));
                }));
  }

  /**
   * Stream status updates for a workflow execution via SSE.
   *
   * @param sessionId the session identifier
   * @param executionId the execution identifier
   * @return a flux of status update events
   */
  @GetMapping(
      value = "/workflow/{sessionId}/status/{executionId}/stream",
      produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @Operation(
      summary = "Stream workflow execution status",
      description = "Streams the status and progress of a specific workflow execution via SSE")
  public Flux<ServerSentEvent<WorkflowProgress>> streamWorkflowStatus(
      @Parameter(description = SESSION_ID_PARAM) @PathVariable final String sessionId,
      @Parameter(description = "Execution ID") @PathVariable final String executionId) {
    return controlBus
        .watchExecution(executionId)
        .map(progress -> ServerSentEvent.<WorkflowProgress>builder().data(progress).build());
  }

  /**
   * Get history of workflow executions for a session.
   *
   * @param sessionId the session identifier
   * @return response entity with list of execution summaries
   */
  @GetMapping("/workflow/{sessionId}/history")
  @Operation(
      summary = "Get workflow history",
      description = "Retrieves the history of all workflow executions for a session")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = HTTP_200,
      description = "Workflow history retrieved successfully")
  public Mono<ResponseEntity<ApiResponse<List<WorkflowExecutionSummary>>>> getWorkflowHistory(
      @Parameter(description = SESSION_ID_PARAM) @PathVariable final String sessionId,
      final ServerWebExchange exchange) {
    return sessionService
        .getSessionConfig(sessionId)
        .flatMap(
            ignored ->
                Mono.fromCallable(() -> controlBus.getHistory(sessionId))
                    .map(
                        history ->
                            ResponseEntity.ok(
                                ApiResponse.success(
                                    200, "Workflow history retrieved successfully", history))))
        .switchIfEmpty(
            Mono.fromSupplier(
                () -> {
                  final String path = exchange.getRequest().getPath().value();
                  final List<ApiResponse.FieldError> errors =
                      List.of(
                          new ApiResponse.FieldError(
                              "sessionId", "Session not found: '" + sessionId + "'"));
                  return ResponseEntity.status(HttpStatus.NOT_FOUND)
                      .<ApiResponse<List<WorkflowExecutionSummary>>>body(
                          ApiResponse.error(404, "Not Found", "Session not found", path, errors));
                }));
  }
}
```

- [ ] **Step 2: Verify the file compiles**

Run: `./gradlew :web:compileJava`
Expected: BUILD SUCCESSFUL

---

## Task 2: Create Merged WorkflowControllerTest

**Files:**
- Create: `web/src/test/java/com/infenia/yukta/controller/WorkflowControllerTest.java`

- [ ] **Step 1: Create the merged test class**

```java
/*
 * Copyright 2026 Infenia Private Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.infenia.yukta.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infenia.yukta.model.api.WorkflowTriggerRequest;
import com.infenia.yukta.model.monitoring.WorkflowProgress;
import com.infenia.yukta.model.session.TaskResponse;
import com.infenia.yukta.model.workflow.WorkflowExecution;
import com.infenia.yukta.service.WorkflowService;
import com.infenia.yukta.service.control.gateway.ControlBusGateway;
import com.infenia.yukta.service.session.SessionService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class WorkflowControllerTest {

  private WebTestClient webClient;
  private WorkflowService workflowService;
  private ControlBusGateway controlBusGateway;
  private SessionService sessionService;

  @BeforeEach
  void setUp() {
    workflowService = mock(WorkflowService.class);
    controlBusGateway = mock(ControlBusGateway.class);
    sessionService = mock(SessionService.class);
    WorkflowController controller =
        new WorkflowController(workflowService, controlBusGateway, sessionService);
    webClient = WebTestClient.bindToController(controller).build();
  }

  // --- Trigger Tests ---

  @Test
  void testTriggerWorkflowSuccess() {
    WorkflowTriggerRequest request = new WorkflowTriggerRequest("session-1", "w1", Map.of());
    TaskResponse response = new TaskResponse("SUCCESS", "Build successful");
    String executionId = "exec-123";
    WorkflowExecution execution = new WorkflowExecution(executionId, Mono.just(response));

    when(workflowService.validateAndTriggerWorkflow(anyString(), anyString(), any()))
        .thenReturn(Mono.just(execution));

    webClient
        .post()
        .uri("/api/workflow/trigger")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isAccepted()
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo(202)
        .jsonPath("$.message")
        .isEqualTo("Workflow trigger accepted")
        .jsonPath("$.data.executionId")
        .isEqualTo(executionId);
  }

  // --- Status Tests ---

  @Test
  void testGetWorkflowStatus() {
    WorkflowProgress progress =
        new WorkflowProgress(
            "exec-1", "sess-1", "wf-1", "RUNNING", List.of(), LocalDateTime.now(), null);
    when(controlBusGateway.getCurrentProgress("exec-1")).thenReturn(progress);

    webClient
        .get()
        .uri("/api/workflow/sess-1/status/exec-1")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.data.executionId")
        .isEqualTo("exec-1");
  }

  @Test
  void testGetWorkflowStatusNotFound() {
    when(controlBusGateway.getCurrentProgress("exec-1")).thenReturn(null);

    webClient
        .get()
        .uri("/api/workflow/sess-1/status/exec-1")
        .exchange()
        .expectStatus()
        .isNotFound();
  }

  @Test
  void testStreamWorkflowStatus() {
    WorkflowProgress progress =
        new WorkflowProgress(
            "exec-1", "sess-1", "wf-1", "RUNNING", List.of(), LocalDateTime.now(), null);
    when(controlBusGateway.watchExecution("exec-1")).thenReturn(Flux.just(progress));

    webClient
        .get()
        .uri("/api/workflow/sess-1/status/exec-1/stream")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM);
  }

  @Test
  void testGetWorkflowHistory() {
    when(controlBusGateway.getHistory("sess-1")).thenReturn(List.of());

    webClient.get().uri("/api/workflow/sess-1/history").exchange().expectStatus().isOk();
  }
}
```

- [ ] **Step 2: Run the test to verify it passes**

Run: `./gradlew :web:test --tests com.infenia.yukta.controller.WorkflowControllerTest`
Expected: BUILD SUCCESSFUL with all 4 tests passing

---

## Task 3: Delete Old WorkflowTriggerController

**Files:**
- Delete: `web/src/main/java/com/infenia/yukta/controller/WorkflowTriggerController.java`

- [ ] **Step 1: Delete the file**

Run: `rm web/src/main/java/com/infenia/yukta/controller/WorkflowTriggerController.java`

- [ ] **Step 2: Verify deletion**

Run: `git status`
Expected: WorkflowTriggerController.java should show as deleted

---

## Task 4: Delete Old WorkflowStatusController

**Files:**
- Delete: `web/src/main/java/com/infenia/yukta/controller/WorkflowStatusController.java`

- [ ] **Step 1: Delete the file**

Run: `rm web/src/main/java/com/infenia/yukta/controller/WorkflowStatusController.java`

- [ ] **Step 2: Verify deletion**

Run: `git status`
Expected: WorkflowStatusController.java should show as deleted

---

## Task 5: Delete Old WorkflowTriggerControllerTest

**Files:**
- Delete: `web/src/test/java/com/infenia/yukta/controller/WorkflowTriggerControllerTest.java`

- [ ] **Step 1: Delete the file**

Run: `rm web/src/test/java/com/infenia/yukta/controller/WorkflowTriggerControllerTest.java`

- [ ] **Step 2: Verify deletion**

Run: `git status`
Expected: WorkflowTriggerControllerTest.java should show as deleted

---

## Task 6: Delete Old WorkflowStatusControllerTest

**Files:**
- Delete: `web/src/test/java/com/infenia/yukta/controller/WorkflowStatusControllerTest.java`

- [ ] **Step 1: Delete the file**

Run: `rm web/src/test/java/com/infenia/yukta/controller/WorkflowStatusControllerTest.java`

- [ ] **Step 2: Verify deletion**

Run: `git status`
Expected: WorkflowStatusControllerTest.java should show as deleted

---

## Task 7: Run Quality Checks

**Files:**
- Verify: Merged controller and test classes

- [ ] **Step 1: Run all web module tests**

Run: `./gradlew :web:test`
Expected: BUILD SUCCESSFUL with all tests passing

- [ ] **Step 2: Run full quality checks**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL (all Checkstyle, PMD, SpotBugs, JaCoCo checks pass)

- [ ] **Step 3: Run spotless formatting**

Run: `./gradlew spotlessApply`
Expected: BUILD SUCCESSFUL with formatting applied

---

## Task 8: Commit Changes

**Files:**
- Staged: 2 new merged files, 4 deletions

- [ ] **Step 1: Stage all changes**

Run: `git add web/src/main/java/com/infenia/yukta/controller/WorkflowController.java web/src/test/java/com/infenia/yukta/controller/WorkflowControllerTest.java`

- [ ] **Step 2: Create commit**

Run:
```bash
git commit -m "$(cat <<'EOF'
refactor: merge WorkflowTriggerController and WorkflowStatusController

Combined trigger and status endpoints into a single WorkflowController
to simplify code organization. All workflow-related operations (trigger,
status monitoring, and history) are now in one focused controller.

Co-Authored-By: Claude Haiku 4.5 <noreply@anthropic.com>
EOF
)"
```

Expected: BUILD SUCCESSFUL with commit created

- [ ] **Step 3: Verify commit**

Run: `git log -1 --pretty=format:"%B"`
Expected: Shows the commit message with all changes

---

## Summary

This plan merges the separate WorkflowTriggerController and WorkflowStatusController into a single WorkflowController, reducing file count from 4 controller files to 3 while maintaining all functionality and test coverage. All endpoints remain exactly the same with no API changes.
