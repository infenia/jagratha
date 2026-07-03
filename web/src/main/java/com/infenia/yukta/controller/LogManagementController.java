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

import com.infenia.yukta.service.control.gateway.ControlBusGateway;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * REST controller for execution log streaming.
 *
 * <p>Provides real-time streaming access to execution logs via the ControlBusGateway. Logs are
 * captured during workflow execution and streamed as they are generated.
 */
@RestController
@RequestMapping("/api/sessions/{sessionId}/executions/{executionId}/logs")
@Slf4j
@Tag(name = "Execution Logs API", description = "Stream execution logs")
@RequiredArgsConstructor
public class LogManagementController {

  /** HTTP 200 OK response code. */
  private static final String RESPONSE_CODE_200 = "200";

  /** The control bus gateway for log streaming. */
  private final ControlBusGateway controlBus;

  /**
   * Stream logs for a specific execution.
   *
   * @param sessionId the session identifier
   * @param executionId the execution identifier
   * @return flux of log lines in chronological order
   */
  @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @Operation(
      summary = "Stream execution logs",
      description = "Streams log lines for a specific execution in real-time")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = RESPONSE_CODE_200,
      description = "Stream of log lines")
  public Flux<String> streamExecutionLogs(
      @Parameter(description = "Session identifier") @PathVariable final String sessionId,
      @Parameter(description = "Execution identifier") @PathVariable final String executionId) {
    log.atInfo()
        .addKeyValue("sessionId", sessionId)
        .addKeyValue("executionId", executionId)
        .log("Streaming execution logs");
    return controlBus.watchLogs(sessionId, executionId);
  }
}
