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
import lombok.extern.slf4j.Slf4j;
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
 * REST controller for workflow management API.
 *
 * <p>Provides endpoints for triggering workflow executions and monitoring their progress, including
 * real-time updates via Server-Sent Events.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
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
    log.atInfo().log(
        "triggerWorkflow: sessionId={}, workflowId={}", request.sessionId(), request.workflowId());
    return workflowService
        .validateAndTriggerWorkflow(request.sessionId(), request.workflowId(), request.payload())
        .doOnNext(
            _ ->
                log.atInfo().log(
                    "triggerWorkflow service call succeeded: sessionId={}, workflowId={}",
                    request.sessionId(),
                    request.workflowId()))
        .map(
            execution ->
                ResponseEntity.accepted()
                    .body(
                        ApiResponse.success(
                            202,
                            "Workflow trigger accepted",
                            new TriggerResponse(execution.executionId()))))
        .doOnSuccess(
            _ ->
                log.atInfo().log(
                    "triggerWorkflow response sent successfully: sessionId={}, workflowId={}",
                    request.sessionId(),
                    request.workflowId()))
        .onErrorResume(
            e -> {
              log.atError()
                  .log(
                      "triggerWorkflow error occurred: sessionId={}, workflowId={}, error={}",
                      request.sessionId(),
                      request.workflowId(),
                      e.getMessage());
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
    log.atInfo().log("getWorkflowStatus: sessionId={}, executionId={}", sessionId, executionId);
    return Mono.fromCallable(() -> controlBus.getCurrentProgress(executionId))
        .flatMap(Mono::justOrEmpty)
        .doOnNext(
            _ ->
                log.atInfo().log(
                    "getWorkflowStatus service call succeeded: sessionId={}, executionId={}",
                    sessionId,
                    executionId))
        .map(
            progress ->
                ResponseEntity.ok(
                    ApiResponse.success(200, "Workflow status retrieved successfully", progress)))
        .doOnSuccess(
            _ ->
                log.atInfo().log(
                    "getWorkflowStatus response sent successfully: sessionId={}, executionId={}",
                    sessionId,
                    executionId))
        .switchIfEmpty(
            Mono.fromSupplier(
                () -> {
                  log.atWarn()
                      .log(
                          "getWorkflowStatus execution not found: sessionId={}, executionId={}",
                          sessionId,
                          executionId);
                  final String path = exchange.getRequest().getPath().value();
                  final List<ApiResponse.FieldError> errors =
                      List.of(
                          new ApiResponse.FieldError(
                              "executionId", "Execution not found: '" + executionId + "'"));
                  return ResponseEntity.status(HttpStatus.NOT_FOUND)
                      .body(
                          ApiResponse.error(404, "Not Found", "Execution not found", path, errors));
                }))
        .doOnError(
            error ->
                log.atError()
                    .log(
                        "getWorkflowStatus error occurred: sessionId={}, executionId={}, error={}",
                        sessionId,
                        executionId,
                        error.getMessage()));
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
    log.atInfo().log("streamWorkflowStatus: sessionId={}, executionId={}", sessionId, executionId);
    return controlBus
        .watchExecution(executionId)
        .doOnNext(
            _ ->
                log.atDebug().log(
                    "streamWorkflowStatus progress received: sessionId={}, executionId={}",
                    sessionId,
                    executionId))
        .map(progress -> ServerSentEvent.<WorkflowProgress>builder().data(progress).build())
        .doOnError(
            error ->
                log.atError()
                    .log(
                        "streamWorkflowStatus error occurred: sessionId={}, executionId={},"
                            + " error={}",
                        sessionId,
                        executionId,
                        error.getMessage()))
        .doOnComplete(
            () ->
                log.atInfo().log(
                    "streamWorkflowStatus stream completed: sessionId={}, executionId={}",
                    sessionId,
                    executionId));
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
    log.atInfo().log("getWorkflowHistory: sessionId={}", sessionId);
    return sessionService
        .getSessionConfig(sessionId)
        .doOnNext(
            config ->
                log.atInfo().log(
                    "getWorkflowHistory session config retrieved: sessionId={}", sessionId))
        .flatMap(
            ignored ->
                Mono.fromCallable(() -> controlBus.getHistory(sessionId))
                    .map(
                        history ->
                            ResponseEntity.ok(
                                ApiResponse.success(
                                    200, "Workflow history retrieved successfully", history))))
        .doOnSuccess(
            _ ->
                log.atInfo().log(
                    "getWorkflowHistory response sent successfully: sessionId={}", sessionId))
        .switchIfEmpty(
            Mono.fromSupplier(
                () -> {
                  log.atWarn().log("getWorkflowHistory session not found: sessionId={}", sessionId);
                  final String path = exchange.getRequest().getPath().value();
                  final List<ApiResponse.FieldError> errors =
                      List.of(
                          new ApiResponse.FieldError(
                              "sessionId", "Session not found: '" + sessionId + "'"));
                  return ResponseEntity.status(HttpStatus.NOT_FOUND)
                      .body(ApiResponse.error(404, "Not Found", "Session not found", path, errors));
                }))
        .doOnError(
            error ->
                log.atError()
                    .log(
                        "getWorkflowHistory error occurred: sessionId={}, error={}",
                        sessionId,
                        error.getMessage()));
  }
}
