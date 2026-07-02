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

import com.infenia.yukta.logging.api.ExecutionSummary;
import com.infenia.yukta.logging.api.PluginLogEntry;
import com.infenia.yukta.logging.api.PluginLogReader;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * REST controller for plugin execution log management.
 *
 * <p>Provides storage-agnostic access to plugin execution logs via PluginLogReader abstraction.
 * Logs are captured during workflow node execution and can be stored in various backends
 * (in-memory, file, database) based on configuration.
 */
@RestController
@RequestMapping("/api/executions")
@Slf4j
@Tag(name = "Execution Logs API", description = "Endpoints for retrieving plugin execution logs")
public class LogManagementController {

  /** HTTP 200 OK response code. */
  private static final String RESPONSE_CODE_200 = "200";

  /** HTTP 503 Service Unavailable response code. */
  private static final String RESPONSE_CODE_503 = "503";

  /** Message for when log service is not available. */
  private static final String LOG_SERVICE_NOT_AVAILABLE = "Log service not available";

  /** Message for when log service is not configured. */
  private static final String LOG_SERVICE_NOT_CONFIGURED = "Log service not configured";

  /** The plugin log reader service (optional - may not be configured). */
  private final ObjectProvider<PluginLogReader> logReader;

  /**
   * Create a new LogManagementController.
   *
   * @param logReader the plugin log reader (optional via ObjectProvider)
   */
  public LogManagementController(final ObjectProvider<PluginLogReader> logReader) {
    this.logReader = logReader;
  }

  /**
   * Get all log entries for a specific execution.
   *
   * @param executionId the execution identifier
   * @return flux of log entries in chronological order
   */
  @GetMapping("/{executionId}/logs")
  @Operation(
      summary = "Get execution logs",
      description = "Retrieves all log entries for a specific plugin execution")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = RESPONSE_CODE_200,
      description = "List of log entries")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = RESPONSE_CODE_503,
      description = LOG_SERVICE_NOT_AVAILABLE)
  public Flux<PluginLogEntry> getExecutionLogs(
      @Parameter(description = "Execution identifier") @PathVariable final String executionId) {
    log.atInfo().addKeyValue("executionId", executionId).log("Retrieving execution logs");
    return logReader.stream()
        .findFirst()
        .map(reader -> reader.readExecution(executionId))
        .orElse(Flux.error(new IllegalStateException(LOG_SERVICE_NOT_CONFIGURED)));
  }

  /**
   * Get raw log content as a single string for an execution.
   *
   * @param executionId the execution identifier
   * @return mono containing concatenated log content
   */
  @GetMapping("/{executionId}/logs/raw")
  @Operation(
      summary = "Get raw execution logs",
      description = "Retrieves raw concatenated log content for a specific execution")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = RESPONSE_CODE_200,
      description = "Raw log content")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = RESPONSE_CODE_503,
      description = LOG_SERVICE_NOT_AVAILABLE)
  public Mono<String> getRawExecutionLogs(
      @Parameter(description = "Execution identifier") @PathVariable final String executionId) {
    log.atInfo().addKeyValue("executionId", executionId).log("Retrieving raw execution logs");
    return logReader.stream()
        .findFirst()
        .map(reader -> reader.getRawContent(executionId))
        .orElse(Mono.error(new IllegalStateException(LOG_SERVICE_NOT_CONFIGURED)));
  }

  /**
   * Get all log entries for a session.
   *
   * @param sessionId the session identifier
   * @return flux of all log entries in the session
   */
  @GetMapping("/session/{sessionId}/logs")
  @Operation(
      summary = "Get session logs",
      description = "Retrieves all log entries for all executions in a session")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = RESPONSE_CODE_200,
      description = "List of log entries for session")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = RESPONSE_CODE_503,
      description = LOG_SERVICE_NOT_AVAILABLE)
  public Flux<PluginLogEntry> getSessionLogs(
      @Parameter(description = "Session identifier") @PathVariable final String sessionId) {
    log.atInfo().addKeyValue("sessionId", sessionId).log("Retrieving session logs");
    return logReader.stream()
        .findFirst()
        .map(reader -> reader.readSession(sessionId))
        .orElse(Flux.error(new IllegalStateException(LOG_SERVICE_NOT_CONFIGURED)));
  }

  /**
   * List all executions with log data for a session.
   *
   * @param sessionId the session identifier
   * @return mono containing list of execution summaries with log metadata
   */
  @GetMapping("/session/{sessionId}/executions")
  @Operation(
      summary = "List session executions",
      description = "Lists all executions with their log metadata for a session")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = RESPONSE_CODE_200,
      description = "List of execution summaries")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = RESPONSE_CODE_503,
      description = LOG_SERVICE_NOT_AVAILABLE)
  public Mono<List<ExecutionSummary>> listSessionExecutions(
      @Parameter(description = "Session identifier") @PathVariable final String sessionId) {
    log.atInfo().addKeyValue("sessionId", sessionId).log("Listing session executions");
    return logReader.stream()
        .findFirst()
        .map(reader -> reader.listExecutions(sessionId))
        .orElse(Mono.error(new IllegalStateException(LOG_SERVICE_NOT_CONFIGURED)));
  }
}
