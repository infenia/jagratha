# Split ExecutionManagementController Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Split ExecutionManagementController into four focused controllers (WorkflowTriggerController, WorkflowStatusController, LogManagementController, ControlBusController) with corresponding test classes, improving code organization and reducing cognitive load.

**Architecture:** The monolithic ExecutionManagementController handles four distinct responsibilities: workflow triggering, workflow status/progress, log management, and control bus operations. We'll create four focused controllers, each with a single responsibility, and split the test class accordingly. Controllers remain in `web/src/main/java/com/infenia/yukta/controller/` and tests in `web/src/test/java/com/infenia/yukta/controller/`.

**Tech Stack:** Java 25, Spring Boot 4.0.2 (WebFlux), JUnit 5, Mockito, MapStruct

---

## File Structure

**New Controllers (Create):**
- `web/src/main/java/com/infenia/yukta/controller/WorkflowTriggerController.java` — Handles workflow triggering
- `web/src/main/java/com/infenia/yukta/controller/WorkflowStatusController.java` — Handles workflow status and history queries
- `web/src/main/java/com/infenia/yukta/controller/LogManagementController.java` — Handles log retrieval and streaming
- `web/src/main/java/com/infenia/yukta/controller/ControlBusController.java` — Handles control bus operations and observability

**New Test Classes (Create):**
- `web/src/test/java/com/infenia/yukta/controller/WorkflowTriggerControllerTest.java`
- `web/src/test/java/com/infenia/yukta/controller/WorkflowStatusControllerTest.java`
- `web/src/test/java/com/infenia/yukta/controller/LogManagementControllerTest.java`
- `web/src/test/java/com/infenia/yukta/controller/ControlBusControllerTest.java`

**Files to Delete:**
- `web/src/main/java/com/infenia/yukta/controller/ExecutionManagementController.java`
- `web/src/test/java/com/infenia/yukta/controller/ExecutionManagementControllerTest.java`

---

## Task 1: Create WorkflowTriggerController

**Files:**
- Create: `web/src/main/java/com/infenia/yukta/controller/WorkflowTriggerController.java`

- [ ] **Step 1: Create the new WorkflowTriggerController**

Create the file with the workflow triggering endpoint extracted from ExecutionManagementController:

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
import com.infenia.yukta.service.WorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(
    name = "Workflow Trigger API",
    description = "Endpoints for triggering workflow executions")
public class WorkflowTriggerController {
  private final WorkflowService workflowService;

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
}
```

- [ ] **Step 2: Verify the file compiles**

Run: `./gradlew :web:compileJava`
Expected: BUILD SUCCESSFUL

---

## Task 2: Create WorkflowStatusController

**Files:**
- Create: `web/src/main/java/com/infenia/yukta/controller/WorkflowStatusController.java`

- [ ] **Step 1: Create the new WorkflowStatusController**

Create the file with workflow status and history endpoints:

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
import com.infenia.yukta.model.monitoring.WorkflowExecutionSummary;
import com.infenia.yukta.model.monitoring.WorkflowProgress;
import com.infenia.yukta.service.control.gateway.ControlBusGateway;
import com.infenia.yukta.service.session.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(
    name = "Workflow Status API",
    description = "Endpoints for monitoring workflow execution status and history")
public class WorkflowStatusController {
  private final ControlBusGateway controlBus;
  private final SessionService sessionService;

  private static final String HTTP_200 = "200";
  private static final String SESSION_ID_PARAM = "Session ID";

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

## Task 3: Create LogManagementController

**Files:**
- Create: `web/src/main/java/com/infenia/yukta/controller/LogManagementController.java`

- [ ] **Step 1: Create the new LogManagementController**

Create the file with log retrieval endpoints:

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
import com.infenia.yukta.service.LogRetrievalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(
    name = "Log Management API",
    description = "Endpoints for retrieving and managing log files")
public class LogManagementController {
  private final LogRetrievalService logs;

  private static final String HTTP_200 = "200";
  private static final String SESSION_ID_PARAM = "Session ID";

  /**
   * List logs for a session.
   *
   * @param sessionId the session identifier
   * @return list of log filenames
   */
  @GetMapping("/logs/{sessionId}")
  @Operation(
      summary = "List logs",
      description = "Lists all log files available for a given session")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = HTTP_200,
      description = "List of log filenames")
  public Mono<ApiResponse<List<String>>> listLogs(
      @Parameter(description = SESSION_ID_PARAM) @PathVariable final String sessionId) {
    return logs.listLogs(sessionId)
        .map(logs -> ApiResponse.success(200, "List of log filenames", logs));
  }

  /**
   * Get content of a specific log file.
   *
   * @param sessionId the session identifier
   * @param filename the log filename
   * @return log content
   */
  @GetMapping("/logs/{sessionId}/{filename}")
  @Operation(
      summary = "Get log content",
      description = "Retrieves the content of a specific log file for a session")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = HTTP_200,
      description = "Log content retrieved successfully")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "404",
      description = "Log file not found")
  public Mono<ResponseEntity<ApiResponse<String>>> getLogContent(
      @Parameter(description = SESSION_ID_PARAM) @PathVariable final String sessionId,
      @Parameter(description = "Log filename") @PathVariable final String filename) {
    return logs.getLogContent(sessionId, filename)
        .map(
            content ->
                ResponseEntity.ok(
                    ApiResponse.success(200, "Log content retrieved successfully", content)))
        .onErrorResume(
            e ->
                Mono.just(
                    ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(
                            ApiResponse.error(
                                404, "Not Found", "Log file not found", null, List.of()))));
  }

  /**
   * Get raw content of a specific log file.
   *
   * @param sessionId the session identifier
   * @param filename the log filename
   * @return raw log content
   */
  @GetMapping("/logs/{sessionId}/{filename}/raw")
  @Operation(
      summary = "Get raw log content",
      description = "Retrieves the raw content of a specific log file for a session")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = HTTP_200,
      description = "Raw log content retrieved successfully")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "404",
      description = "Log file not found")
  public Mono<ResponseEntity<String>> getRawLogContent(
      @Parameter(description = SESSION_ID_PARAM) @PathVariable final String sessionId,
      @Parameter(description = "Log filename") @PathVariable final String filename) {
    return logs.getLogContent(sessionId, filename)
        .map(ResponseEntity::ok)
        .onErrorResume(e -> Mono.just(ResponseEntity.notFound().build()));
  }
}
```

- [ ] **Step 2: Verify the file compiles**

Run: `./gradlew :web:compileJava`
Expected: BUILD SUCCESSFUL

---

## Task 4: Create ControlBusController

**Files:**
- Create: `web/src/main/java/com/infenia/yukta/controller/ControlBusController.java`

- [ ] **Step 1: Create the new ControlBusController**

Create the file with control bus and observability endpoints:

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
import com.infenia.yukta.model.monitoring.WorkflowProgress;
import com.infenia.yukta.plugin.message.DefaultMessage;
import com.infenia.yukta.plugin.message.Message;
import com.infenia.yukta.service.control.gateway.ControlBusGateway;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(
    name = "Control Bus & Observability API",
    description = "Endpoints for control bus operations, node management, and execution observability")
public class ControlBusController {
  private final ControlBusGateway controlBus;

  /**
   * Get all active nodes in a specific workflow that have emitted heartbeats.
   *
   * @param workflowId the workflow identifier
   * @return list of active node IDs in the workflow
   */
  @GetMapping("/control/workflows/{workflowId}/nodes")
  @Operation(
      summary = "Get active nodes in workflow",
      description =
          "Lists all nodes in a specific workflow currently registered on the Control Bus")
  public Mono<ApiResponse<List<String>>> getActiveNodes(@PathVariable final String workflowId) {
    return Mono.fromCallable(() -> controlBus.getActiveNodes(workflowId))
        .map(nodes -> ApiResponse.success(200, "Active nodes retrieved", nodes));
  }

  /**
   * Get the last heartbeat for a node in a specific workflow.
   *
   * @param workflowId the workflow identifier
   * @param nodeId the node identifier
   * @return the last heartbeat message
   */
  @GetMapping("/control/workflows/{workflowId}/nodes/{nodeId}/heartbeat")
  @Operation(
      summary = "Get node heartbeat in workflow",
      description = "Retrieves the most recent heartbeat for a specific node in a workflow")
  public Mono<ApiResponse<Message<?>>> getLastHeartbeat(
      @PathVariable final String workflowId, @PathVariable final String nodeId) {
    return Mono.fromCallable(() -> controlBus.getLastHeartbeat(workflowId, nodeId))
        .map(hb -> ApiResponse.success(200, "Node heartbeat retrieved", hb));
  }

  /**
   * Send a command to a specific node in a workflow.
   *
   * @param workflowId the workflow identifier
   * @param nodeId the target node identifier
   * @param payload the command payload
   * @return a Mono of the response API response
   */
  @PostMapping("/control/workflows/{workflowId}/nodes/{nodeId}/command")
  @Operation(
      summary = "Send command to node in workflow",
      description = "Sends an administrative command to a specific node in a workflow")
  public Mono<ApiResponse<Message<?>>> sendCommand(
      @PathVariable final String workflowId,
      @PathVariable final String nodeId,
      @RequestBody final Map<String, Object> payload) {
    final Message<?> command =
        DefaultMessage.create(null, payload)
            .withControl(true)
            .withSourceNodeId("CONSOLE")
            .withWorkflowId(workflowId);
    return controlBus
        .sendCommand(workflowId, nodeId, command)
        .map(resp -> ApiResponse.success(200, "Command processed", resp));
  }

  /**
   * Get all active nodes across all workflows that have emitted heartbeats.
   *
   * @return list of all active node IDs
   */
  @GetMapping("/control/nodes")
  @Operation(
      summary = "Get active nodes (global)",
      description = "Lists all nodes currently registered on the Control Bus across all workflows")
  public Mono<ApiResponse<List<String>>> getAllActiveNodes() {
    return Mono.fromCallable(controlBus::getActiveNodes)
        .map(nodes -> ApiResponse.success(200, "Active nodes retrieved", nodes));
  }

  /**
   * Get current execution progress snapshot.
   *
   * @param executionId the execution identifier
   * @return the current progress
   */
  @GetMapping("/control/executions/{executionId}/progress")
  @Operation(
      summary = "Get execution progress",
      description = "Returns the current progress snapshot for an execution")
  public Mono<ApiResponse<WorkflowProgress>> getProgress(@PathVariable final String executionId) {
    return Mono.fromCallable(() -> controlBus.getCurrentProgress(executionId))
        .map(progress -> ApiResponse.success(200, "Progress retrieved", progress));
  }

  /**
   * Stream execution progress in real-time via SSE.
   *
   * @param executionId the execution identifier
   * @return a flux of progress updates
   */
  @GetMapping(
      value = "/control/executions/{executionId}/progress/stream",
      produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @Operation(
      summary = "Stream execution progress",
      description = "Streams progress updates for an execution in real-time via Server-Sent Events")
  public Flux<WorkflowProgress> streamProgress(@PathVariable final String executionId) {
    return controlBus.watchExecution(executionId);
  }

  /**
   * Stream execution logs in real-time via SSE.
   *
   * @param executionId the execution identifier
   * @return a flux of log lines
   */
  @GetMapping(
      value = "/control/executions/{executionId}/logs/stream",
      produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @Operation(
      summary = "Stream execution logs",
      description = "Streams log lines for an execution in real-time via Server-Sent Events")
  public Flux<String> streamLogs(@PathVariable final String executionId) {
    return controlBus.watchLogs(executionId);
  }

  /**
   * Get execution history for a session.
   *
   * @param sessionId the session identifier
   * @return list of execution summaries
   */
  @GetMapping("/control/sessions/{sessionId}/history")
  @Operation(
      summary = "Get session execution history",
      description = "Returns all executions (completed and in-progress) for a session")
  public Mono<ApiResponse<Object>> getHistory(@PathVariable final String sessionId) {
    return Mono.fromCallable(() -> controlBus.getHistory(sessionId))
        .map(history -> ApiResponse.success(200, "History retrieved", history));
  }
}
```

- [ ] **Step 2: Verify the file compiles**

Run: `./gradlew :web:compileJava`
Expected: BUILD SUCCESSFUL

---

## Task 5: Create WorkflowTriggerControllerTest

**Files:**
- Create: `web/src/test/java/com/infenia/yukta/controller/WorkflowTriggerControllerTest.java`

- [ ] **Step 1: Create the test class**

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
import com.infenia.yukta.model.session.TaskResponse;
import com.infenia.yukta.model.workflow.WorkflowExecution;
import com.infenia.yukta.service.WorkflowService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

class WorkflowTriggerControllerTest {

  private WebTestClient webClient;
  private WorkflowService workflowService;

  @BeforeEach
  void setUp() {
    workflowService = mock(WorkflowService.class);
    WorkflowTriggerController controller = new WorkflowTriggerController(workflowService);
    webClient = WebTestClient.bindToController(controller).build();
  }

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
}
```

- [ ] **Step 2: Run the test to verify it passes**

Run: `./gradlew :web:test --tests com.infenia.yukta.controller.WorkflowTriggerControllerTest`
Expected: BUILD SUCCESSFUL

---

## Task 6: Create WorkflowStatusControllerTest

**Files:**
- Create: `web/src/test/java/com/infenia/yukta/controller/WorkflowStatusControllerTest.java`

- [ ] **Step 1: Create the test class**

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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infenia.yukta.model.monitoring.WorkflowProgress;
import com.infenia.yukta.service.control.gateway.ControlBusGateway;
import com.infenia.yukta.service.session.SessionService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class WorkflowStatusControllerTest {

  private WebTestClient webClient;
  private ControlBusGateway controlBusGateway;
  private SessionService sessionService;

  @BeforeEach
  void setUp() {
    controlBusGateway = mock(ControlBusGateway.class);
    sessionService = mock(SessionService.class);
    WorkflowStatusController controller =
        new WorkflowStatusController(controlBusGateway, sessionService);
    webClient = WebTestClient.bindToController(controller).build();
  }

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

Run: `./gradlew :web:test --tests com.infenia.yukta.controller.WorkflowStatusControllerTest`
Expected: BUILD SUCCESSFUL

---

## Task 7: Create LogManagementControllerTest

**Files:**
- Create: `web/src/test/java/com/infenia/yukta/controller/LogManagementControllerTest.java`

- [ ] **Step 1: Create the test class**

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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infenia.yukta.service.LogRetrievalService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

class LogManagementControllerTest {

  private WebTestClient webClient;
  private LogRetrievalService logs;

  @BeforeEach
  void setUp() {
    logs = mock(LogRetrievalService.class);
    LogManagementController controller = new LogManagementController(logs);
    webClient = WebTestClient.bindToController(controller).build();
  }

  @Test
  void testListLogs() {
    when(logs.listLogs("sess-1")).thenReturn(Mono.just(List.of("test.log")));

    webClient
        .get()
        .uri("/api/logs/sess-1")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.data[0]")
        .isEqualTo("test.log");
  }

  @Test
  void testGetLogContent() {
    when(logs.getLogContent("sess-1", "test.log")).thenReturn(Mono.just("content"));

    webClient
        .get()
        .uri("/api/logs/sess-1/test.log")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.data")
        .isEqualTo("content");
  }

  @Test
  void testGetLogContentNotFound() {
    when(logs.getLogContent("sess-1", "test.log"))
        .thenReturn(Mono.error(new java.io.IOException()));

    webClient.get().uri("/api/logs/sess-1/test.log").exchange().expectStatus().isNotFound();
  }

  @Test
  void testGetRawLogContent() {
    when(logs.getLogContent("sess-1", "test.log")).thenReturn(Mono.just("content"));

    webClient
        .get()
        .uri("/api/logs/sess-1/test.log/raw")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .isEqualTo("content");
  }

  @Test
  void testGetRawLogContentNotFound() {
    when(logs.getLogContent("sess-1", "test.log"))
        .thenReturn(Mono.error(new java.io.IOException()));

    webClient.get().uri("/api/logs/sess-1/test.log/raw").exchange().expectStatus().isNotFound();
  }
}
```

- [ ] **Step 2: Run the test to verify it passes**

Run: `./gradlew :web:test --tests com.infenia.yukta.controller.LogManagementControllerTest`
Expected: BUILD SUCCESSFUL

---

## Task 8: Create ControlBusControllerTest

**Files:**
- Create: `web/src/test/java/com/infenia/yukta/controller/ControlBusControllerTest.java`

- [ ] **Step 1: Create the test class**

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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infenia.yukta.model.monitoring.WorkflowProgress;
import com.infenia.yukta.plugin.message.DefaultMessage;
import com.infenia.yukta.plugin.message.Message;
import com.infenia.yukta.service.control.gateway.ControlBusGateway;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class ControlBusControllerTest {

  private WebTestClient webClient;
  private ControlBusGateway controlBusGateway;

  @BeforeEach
  void setUp() {
    controlBusGateway = mock(ControlBusGateway.class);
    ControlBusController controller = new ControlBusController(controlBusGateway);
    webClient = WebTestClient.bindToController(controller).build();
  }

  @Test
  void testGetActiveNodes() {
    when(controlBusGateway.getActiveNodes()).thenReturn(List.of("node1", "node2"));
    webClient
        .get()
        .uri("/api/control/nodes")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.data[0]")
        .isEqualTo("node1")
        .jsonPath("$.message")
        .isEqualTo("Active nodes retrieved");
  }

  @Test
  void testGetLastHeartbeat() {
    final Message<?> hb = DefaultMessage.create(null, "ok").withControl(true);
    doReturn(hb).when(controlBusGateway).getLastHeartbeat("wf1", "n1");

    webClient
        .get()
        .uri("/api/control/workflows/wf1/nodes/n1/heartbeat")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.data.payload")
        .isEqualTo("ok");
  }

  @Test
  void testSendCommand() {
    final Message<?> resp = DefaultMessage.create(null, "done");
    when(controlBusGateway.sendCommand(eq("wf1"), eq("n1"), any())).thenReturn(Mono.just(resp));

    webClient
        .post()
        .uri("/api/control/workflows/wf1/nodes/n1/command")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(Map.of("cmd", "reset"))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.message")
        .isEqualTo("Command processed");
  }

  @Test
  void testStreamProgress() {
    WorkflowProgress progress = mock(WorkflowProgress.class);
    when(controlBusGateway.watchExecution("exec1")).thenReturn(Flux.just(progress));

    webClient
        .get()
        .uri("/api/control/executions/exec1/progress/stream")
        .accept(MediaType.TEXT_EVENT_STREAM)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM);
  }

  @Test
  void testGetActiveNodesInWorkflow() {
    when(controlBusGateway.getActiveNodes("wf1")).thenReturn(List.of("node1", "node2"));
    webClient
        .get()
        .uri("/api/control/workflows/wf1/nodes")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.data[0]")
        .isEqualTo("node1")
        .jsonPath("$.message")
        .isEqualTo("Active nodes retrieved");
  }

  @Test
  void testGetProgress() {
    WorkflowProgress progress = mock(WorkflowProgress.class);
    when(controlBusGateway.getCurrentProgress("exec1")).thenReturn(progress);

    webClient
        .get()
        .uri("/api/control/executions/exec1/progress")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.message")
        .isEqualTo("Progress retrieved");
  }

  @Test
  void testStreamLogs() {
    when(controlBusGateway.watchLogs("exec1")).thenReturn(Flux.just("log1", "log2"));

    webClient
        .get()
        .uri("/api/control/executions/exec1/logs/stream")
        .accept(MediaType.TEXT_EVENT_STREAM)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM);
  }

  @Test
  void testGetHistory() {
    when(controlBusGateway.getHistory("session1")).thenReturn(List.of());

    webClient
        .get()
        .uri("/api/control/sessions/session1/history")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.message")
        .isEqualTo("History retrieved");
  }
}
```

- [ ] **Step 2: Run the test to verify it passes**

Run: `./gradlew :web:test --tests com.infenia.yukta.controller.ControlBusControllerTest`
Expected: BUILD SUCCESSFUL

---

## Task 9: Delete Old ExecutionManagementController

**Files:**
- Delete: `web/src/main/java/com/infenia/yukta/controller/ExecutionManagementController.java`

- [ ] **Step 1: Delete the file**

Run: `rm web/src/main/java/com/infenia/yukta/controller/ExecutionManagementController.java`

- [ ] **Step 2: Verify deletion**

Run: `git status`
Expected: ExecutionManagementController.java should show as deleted

---

## Task 10: Delete Old ExecutionManagementControllerTest

**Files:**
- Delete: `web/src/test/java/com/infenia/yukta/controller/ExecutionManagementControllerTest.java`

- [ ] **Step 1: Delete the file**

Run: `rm web/src/test/java/com/infenia/yukta/controller/ExecutionManagementControllerTest.java`

- [ ] **Step 2: Verify deletion**

Run: `git status`
Expected: ExecutionManagementControllerTest.java should show as deleted

---

## Task 11: Run Full Test Suite & Quality Checks

**Files:**
- Verify: All modified/created controller and test files

- [ ] **Step 1: Run all web module tests**

Run: `./gradlew :web:test`
Expected: BUILD SUCCESSFUL with all tests passing

- [ ] **Step 2: Run full quality checks**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL (all Checkstyle, PMD, SpotBugs, JaCoCo checks pass)

- [ ] **Step 3: Run spotless formatting**

Run: `./gradlew spotlessApply`
Expected: BUILD SUCCESSFUL with formatting applied

- [ ] **Step 4: Verify clean git status**

Run: `git status`
Expected: No uncommitted changes except those to be staged

---

## Task 12: Commit Changes

**Files:**
- Staged: All 8 new controller/test files, 2 deletions

- [ ] **Step 1: Stage all changes**

Run: `git add web/src/main/java/com/infenia/yukta/controller/Workflow* web/src/main/java/com/infenia/yukta/controller/LogManagement* web/src/main/java/com/infenia/yukta/controller/ControlBus* web/src/test/java/com/infenia/yukta/controller/Workflow* web/src/test/java/com/infenia/yukta/controller/LogManagement* web/src/test/java/com/infenia/yukta/controller/ControlBus*`

- [ ] **Step 2: Create commit**

Run: 
```bash
git commit -m "$(cat <<'EOF'
refactor: split ExecutionManagementController into focused domain controllers

Separated monolithic ExecutionManagementController into four focused controllers:
- WorkflowTriggerController: handles workflow triggering
- WorkflowStatusController: handles status queries and history
- LogManagementController: handles log retrieval
- ControlBusController: handles control bus and observability endpoints

Split corresponding test classes to maintain 1:1 controller-test mapping.
Improves code organization and reduces cognitive load per controller.

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

This plan splits the 434-line ExecutionManagementController and its corresponding 334-line test class into four focused, single-responsibility controllers with accompanying tests. The refactoring improves code maintainability by reducing cognitive load and following the Single Responsibility Principle while preserving all functionality and test coverage.
