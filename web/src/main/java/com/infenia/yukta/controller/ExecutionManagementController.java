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
import com.infenia.yukta.plugin.message.DefaultMessage;
import com.infenia.yukta.plugin.message.Message;
import com.infenia.yukta.service.LogRetrievalService;
import com.infenia.yukta.service.WorkflowService;
import com.infenia.yukta.service.control.gateway.ControlBusGateway;
import com.infenia.yukta.service.session.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
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

/**
 * REST controller for execution management and control bus operations.
 *
 * <p>Provides endpoints for workflow execution, execution monitoring, log management, and control
 * bus operations across distributed workflow nodes.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(
    name = "Execution Management API",
    description =
        "Endpoints for workflow execution, monitoring, logging, and distributed node control")
public class ExecutionManagementController {
  private final ControlBusGateway controlBus;
  private final WorkflowService workflowService;
  private final LogRetrievalService logs;
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
                          ApiResponse.error(
                              404, "Not Found", "Workflow not found", path, errors)));
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
                          ApiResponse.error(
                              404, "Not Found", "Execution not found", path, errors));
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

  // --- Control Bus Endpoints ---

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

  // --- Observability Endpoints ---

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
