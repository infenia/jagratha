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

import com.infenia.yukta.mapper.AppConfigMapper;
import com.infenia.yukta.model.api.ApiResponse;
import com.infenia.yukta.model.api.ConfigRequest;
import com.infenia.yukta.model.api.SessionDetails;
import com.infenia.yukta.model.session.SessionConfigData;
import com.infenia.yukta.model.workflow.WorkflowDefinition;
import com.infenia.yukta.service.session.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Controller for session management and configuration. */
@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
@Tag(
    name = "Session API",
    description = "Endpoints for session and workflow discovery, configuration management")
public class SessionConfigController {

  private final SessionService sessionService;
  private final AppConfigMapper configMapper;

  /**
   * Get details of a specific session.
   *
   * @param sessionId the session identifier
   * @return session details
   */
  @GetMapping("/{sessionId}")
  @Operation(
      summary = "Get session details",
      description = "Retrieves details of a specific session including workflow IDs")
  @SuppressWarnings("unchecked")
  public Mono<ResponseEntity<ApiResponse<SessionDetails>>> getSessionDetails(
      @PathVariable final String sessionId, final ServerWebExchange exchange) {
    return sessionService
        .getSessionConfig(sessionId)
        .map(
            config -> {
              final Map<String, Object> workflows =
                  (Map<String, Object>) config.getOrDefault("workflows", Map.of());
              final List<String> workflowIds = List.copyOf(workflows.keySet());
              return ResponseEntity.ok(
                  ApiResponse.success(
                      200,
                      "Session details retrieved",
                      new SessionDetails(sessionId, workflowIds)));
            })
        .switchIfEmpty(
            Mono.fromSupplier(
                () -> {
                  final String path = exchange.getRequest().getPath().value();
                  final List<ApiResponse.FieldError> errors =
                      List.of(
                          new ApiResponse.FieldError(
                              "sessionId", "Session not found: '" + sessionId + "'"));
                  return ResponseEntity.status(HttpStatus.NOT_FOUND)
                      .<ApiResponse<SessionDetails>>body(
                          ApiResponse.error(404, "Not Found", "Session not found", path, errors));
                }));
  }

  /**
   * Get workflow definition.
   *
   * @param sessionId the session identifier
   * @param workflowId the workflow identifier
   * @return workflow definition
   */
  @GetMapping("/{sessionId}/workflows/{workflowId}")
  @Operation(summary = "Get workflow", description = "Retrieves the definition of a workflow")
  public Mono<ResponseEntity<ApiResponse<WorkflowDefinition>>> getWorkflow(
      @PathVariable final String sessionId,
      @PathVariable final String workflowId,
      final ServerWebExchange exchange) {
    return sessionService
        .getSessionWorkflow(sessionId, workflowId)
        .map(def -> ResponseEntity.ok(ApiResponse.success(200, "Workflow retrieved", def)))
        .switchIfEmpty(
            Mono.fromSupplier(
                () -> {
                  final String path = exchange.getRequest().getPath().value();
                  final List<ApiResponse.FieldError> errors =
                      List.of(
                          new ApiResponse.FieldError(
                              "workflowId",
                              "Workflow not found: '"
                                  + workflowId
                                  + "' in session: '"
                                  + sessionId
                                  + "'"));
                  return ResponseEntity.status(HttpStatus.NOT_FOUND)
                      .<ApiResponse<WorkflowDefinition>>body(
                          ApiResponse.error(404, "Not Found", "Workflow not found", path, errors));
                }));
  }

  /**
   * Initialize a new session or update an existing one with the provided configuration.
   *
   * <p>This endpoint allows callers to dynamically configure project paths, workflows, and session
   * metadata. If the session ID already exists, it overrides the current configuration; otherwise,
   * it creates a new session context.
   *
   * @param request the config request containing session identifiers and configuration values
   * @return response entity with success message indicating the configuration has been applied
   */
  @PostMapping
  @Operation(
      summary = "Apply session configuration",
      description =
          "Initializes a new session or updates an existing session's configuration at runtime. "
              + "Configures project paths, workflow definitions, and session metadata.")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "200",
      description = "Session configuration applied successfully")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "400",
      description = "Invalid configuration data provided in the request")
  public Mono<ResponseEntity<ApiResponse<Void>>> applyConfig(
      @Valid @RequestBody final ConfigRequest request) {
    final SessionConfigData configData = configMapper.toData(request);
    return sessionService
        .applyConfig(configData)
        .thenReturn(
            ResponseEntity.ok(
                ApiResponse.success(200, "Configuration applied successfully", null)));
  }
}
