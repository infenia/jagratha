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
package com.infenia.yukta.service.session;

import com.infenia.yukta.model.session.SessionConfigData;
import com.infenia.yukta.model.workflow.WorkflowDefinition;
import com.infenia.yukta.service.execution.status.ExecutionStatusPublisher;
import com.infenia.yukta.validation.SessionId;
import com.infenia.yukta.validation.WorkflowId;
import jakarta.validation.Valid;
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
  private final ExecutionStatusPublisher statusPublisher;

  /**
   * Apply configuration for a session.
   *
   * <p>This method initializes a new session or updates an existing one by applying all
   * configuration data atomically via the store. Workflow preparation is now decoupled to break
   * circular dependencies.
   *
   * @param data the configuration data to apply
   * @return Mono that completes when the configuration is successfully applied and persisted
   */
  public Mono<Void> applyConfig(@Valid final SessionConfigData data) {
    return configService
        .applySessionConfig(data)
        .then(
            // TODO: Workflow preparation decoupled from session service to break circular
            // dependency. Workflows will be prepared through ExecutionStatusPublisher once the
            // control bus bridge is established.
            Mono.empty());
  }

  /**
   * Get all available session IDs.
   *
   * <p>Retrieves all session identifiers that have configurations stored in the system, regardless
   * of whether they are currently active or not.
   *
   * @return Flux of session IDs
   */
  public Flux<String> getSessionIds() {
    return configService.getSessionIds();
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
   * Get workflow for a session.
   *
   * @param sessionId the session identifier
   * @param workflowId the workflow identifier
   * @return Mono containing the workflow definition
   */
  public Mono<WorkflowDefinition> getSessionWorkflow(
      @SessionId final String sessionId, @WorkflowId final String workflowId) {
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
