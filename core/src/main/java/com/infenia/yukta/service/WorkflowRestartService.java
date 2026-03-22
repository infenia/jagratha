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

import com.infenia.yukta.model.workflow.PreparedWorkflow;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Service for restarting workflow executions.
 *
 * <p>Supports both full restart (from the beginning) and checkpoint-based resume (from a specific
 * node).
 */
@Slf4j
@Service
public class WorkflowRestartService {

  private final ExecutionRegistry executionRegistry;
  private final CheckpointService checkpointService;
  private final TaskTrackerService taskTrackerService;
  private final WorkflowOrchestrator workflowOrchestrator;

  /**
   * Creates a new WorkflowRestartService.
   *
   * @param executionRegistry the execution registry
   * @param checkpointService the checkpoint service
   * @param taskTrackerService the task tracker service
   * @param workflowOrchestrator the workflow orchestrator for re-execution
   */
  @Autowired
  public WorkflowRestartService(
      final ExecutionRegistry executionRegistry,
      final CheckpointService checkpointService,
      final TaskTrackerService taskTrackerService,
      final WorkflowOrchestrator workflowOrchestrator) {
    this.executionRegistry = executionRegistry;
    this.checkpointService = checkpointService;
    this.taskTrackerService = taskTrackerService;
    this.workflowOrchestrator = workflowOrchestrator;
  }

  /**
   * Restarts a workflow execution from the beginning with a new execution ID.
   *
   * @param executionId the original execution ID to restart
   * @return a Mono containing the new execution ID
   */
  public Mono<String> restartExecution(@NotBlank final String executionId) {
    return Mono.fromCallable(
            () -> {
              final Optional<PreparedWorkflow> prepared =
                  executionRegistry.getPreparedWorkflow(executionId);
              if (prepared.isEmpty()) {
                throw new IllegalArgumentException("Execution not found: " + executionId);
              }
              final String newExecutionId = UUID.randomUUID().toString();
              log.atInfo()
                  .addKeyValue("originalExecutionId", executionId)
                  .addKeyValue("newExecutionId", newExecutionId)
                  .log("Restarting workflow execution from beginning");
              return newExecutionId;
            })
        .flatMap(
            newExecutionId -> {
              final Optional<PreparedWorkflow> prepared =
                  executionRegistry.getPreparedWorkflow(executionId);
              final String newSessionId = UUID.randomUUID().toString();
              final String newWorkflowId = UUID.randomUUID().toString();
              return workflowOrchestrator
                  .execute(newSessionId, newWorkflowId, newExecutionId, prepared.get(), Map.of())
                  .thenReturn(newExecutionId);
            });
  }

  /**
   * Restarts a workflow execution from a specific node using checkpointed state.
   *
   * @param executionId the original execution ID
   * @param fromNodeId the node ID to resume from
   * @return a Mono containing the new execution ID
   */
  public Mono<String> restartFromNode(
      @NotBlank final String executionId, @NotBlank final String fromNodeId) {
    return checkpointService
        .getOrEmpty(executionId, fromNodeId)
        .switchIfEmpty(
            Mono.error(new IllegalArgumentException("No checkpoint found for node: " + fromNodeId)))
        .flatMap(
            checkpointedMessage ->
                Mono.fromCallable(
                        () -> {
                          final Optional<PreparedWorkflow> prepared =
                              executionRegistry.getPreparedWorkflow(executionId);
                          if (prepared.isEmpty()) {
                            throw new IllegalArgumentException(
                                "Execution not found: " + executionId);
                          }
                          final String newExecutionId = UUID.randomUUID().toString();
                          log.atInfo()
                              .addKeyValue("originalExecutionId", executionId)
                              .addKeyValue("newExecutionId", newExecutionId)
                              .addKeyValue("fromNodeId", fromNodeId)
                              .log("Restarting workflow execution from checkpoint");
                          return newExecutionId;
                        })
                    .flatMap(
                        newExecutionId -> {
                          final Optional<PreparedWorkflow> prepared =
                              executionRegistry.getPreparedWorkflow(executionId);
                          final String newSessionId = UUID.randomUUID().toString();
                          final String newWorkflowId = UUID.randomUUID().toString();
                          return workflowOrchestrator
                              .execute(
                                  newSessionId,
                                  newWorkflowId,
                                  newExecutionId,
                                  prepared.get(),
                                  Map.of())
                              .thenReturn(newExecutionId);
                        }));
  }
}
