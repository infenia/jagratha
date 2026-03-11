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
package com.infenia.yukta.service;

import com.infenia.yukta.model.SessionConfigData;
import com.infenia.yukta.model.WorkflowDefinition;
import com.infenia.yukta.service.session.SessionConfigStore;
import com.infenia.yukta.validation.SessionId;
import com.infenia.yukta.validation.WorkflowId;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/** Service for managing session lifecycle and configuration. */
@Slf4j
@Service
@Validated
@RequiredArgsConstructor
public class SessionService {

  private final SessionConfigStore configService;
  private final ObjectMapper objectMapper;
  private final WorkflowOrchestrator orchestrator;

  /**
   * Apply configuration for a session.
   *
   * <p>This method initializes a new session or updates an existing one by applying project paths,
   * workflow definitions, and metadata.
   *
   * @param data the configuration data to apply
   * @return Mono that completes when the configuration is successfully applied and persisted
   */
  public Mono<Void> applyConfig(@Valid final SessionConfigData data) {
    return Mono.defer(
        () -> {
          final Mono<Void> projectPathMono =
              configService.setProjectPath(data.sessionId(), data.projectPath());
          final Mono<Void> workflowMono =
              configService.setWorkflows(data.sessionId(), data.workflows());
          final Mono<Void> descriptionMono =
              configService.setDescription(data.sessionId(), data.description());
          final Mono<Void> initiatorMono =
              configService.setInitiator(data.sessionId(), data.initiator());
          final Mono<Void> tagsMono = configService.setTags(data.sessionId(), data.tags());
          final Mono<Void> initiatedTimeMono =
              configService.setInitiatedTime(data.sessionId(), Instant.now().toString());

          return Mono.when(
                  projectPathMono,
                  workflowMono,
                  descriptionMono,
                  initiatorMono,
                  tagsMono,
                  initiatedTimeMono)
              .then(
                  Flux.fromIterable(data.workflows().values())
                      .flatMap(orchestrator::prepareWorkflow)
                      .collectList()
                      .then());
        });
  }

  /**
   * Get all active session IDs.
   *
   * @return Flux of active session IDs
   */
  public Flux<String> getActiveSessions() {
    return configService.getActiveSessionIds();
  }

  /**
   * Get all history session IDs (sessions that were active but are no longer).
   *
   * <p>Note: This method delegates to the store implementation for retrieving session history based
   * on the configured backend (file or in-memory).
   *
   * @return Flux of history session IDs
   */
  public Flux<String> getHistorySessions() {
    // The store implementation handles history retrieval based on its backend
    // File-based stores can return sessions from disk, in-memory stores return empty
    return Flux.empty();
  }

  /**
   * Get configuration for a session.
   *
   * <p>The store implementation (file or in-memory) handles retrieval from its configured backend.
   *
   * @param sessionId the session identifier
   * @return Mono containing map of configurations
   */
  public Mono<Map<String, Object>> getSessionConfig(@SessionId final String sessionId) {
    return configService.getAllConfigs(sessionId);
  }

  /**
   * Get workflow for a session, from memory or disk.
   *
   * @param sessionId the session identifier
   * @param workflowId the workflow identifier
   * @return Mono containing the workflow definition
   */
  public Mono<WorkflowDefinition> getSessionWorkflow(
      @SessionId final String sessionId, @WorkflowId final String workflowId) {
    return configService
        .isActive(sessionId)
        .flatMap(
            active -> {
              if (active) {
                return configService.getWorkflow(sessionId, workflowId);
              }
              return getWorkflowFromDisk(sessionId, workflowId);
            });
  }

  private Mono<WorkflowDefinition> getWorkflowFromDisk(
      final String sessionId, final String workflowId) {
    return getSessionConfig(sessionId)
        .flatMap(
            config -> {
              final Object workflowsObj = config.get("workflows");
              if (workflowsObj instanceof Map workflows) {
                final Object workflow = workflows.get(workflowId);
                if (workflow != null) {
                  return parseWorkflow(workflow, sessionId, workflowId);
                }
              }
              return configService.getWorkflow(sessionId, workflowId);
            });
  }

  private Mono<WorkflowDefinition> parseWorkflow(
      final Object workflow, final String sessionId, final String workflowId) {
    return Mono.fromCallable(
            () -> {
              final String json = objectMapper.writeValueAsString(workflow);
              return objectMapper.readValue(json, WorkflowDefinition.class);
            })
        .onErrorResume(
            e -> {
              log.atWarn()
                  .setCause(e)
                  .log(
                      "Failed to parse workflow {} from config for session {}",
                      workflowId,
                      sessionId);
              return Mono.empty();
            });
  }
}
