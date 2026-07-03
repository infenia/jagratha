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

import com.infenia.yukta.logging.api.PluginLogEntry;
import com.infenia.yukta.service.LogStreamingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

/**
 * REST controller for plugin execution log streaming.
 *
 * <p>Provides storage-agnostic streaming access to plugin execution logs via LogStreamingService.
 * Logs are captured during workflow node execution and streamed in real-time.
 */
@RestController
@RequestMapping("/api/sessions/{sessionId}/executions/{executionId}/logs")
@Slf4j
@Tag(name = "Execution Logs API", description = "Stream plugin execution logs")
@RequiredArgsConstructor
public class LogManagementController {

  /** HTTP 200 OK response code. */
  private static final String RESPONSE_CODE_200 = "200";

  /** HTTP 503 Service Unavailable response code. */
  private static final String RESPONSE_CODE_503 = "503";

  /** Message for when log service is not available. */
  private static final String LOG_SERVICE_NOT_AVAILABLE = "Log service not available";

  /** The log streaming service. */
  private final LogStreamingService logStreamingService;

  /**
   * Stream log entries for a specific execution.
   *
   * @param sessionId the session identifier
   * @param executionId the execution identifier
   * @return flux of log entries in chronological order
   */
  @GetMapping
  @Operation(
      summary = "Stream execution logs",
      description = "Streams log entries for a specific execution in real-time")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = RESPONSE_CODE_200,
      description = "Stream of log entries")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = RESPONSE_CODE_503,
      description = LOG_SERVICE_NOT_AVAILABLE)
  public Flux<PluginLogEntry> streamExecutionLogs(
      @Parameter(description = "Session identifier") @PathVariable final String sessionId,
      @Parameter(description = "Execution identifier") @PathVariable final String executionId) {
    log.atInfo()
        .addKeyValue("sessionId", sessionId)
        .addKeyValue("executionId", executionId)
        .log("Streaming execution logs");
    return logStreamingService.isConfigured()
        ? logStreamingService.streamExecutionLogs(executionId)
        : Flux.error(
            new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, LOG_SERVICE_NOT_AVAILABLE));
  }
}
