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
package com.infenia.yukta.service.control;

import com.infenia.yukta.api.WorkflowDefinition;
import com.infenia.yukta.model.workflow.PreparedWorkflow;
import reactor.core.publisher.Mono;

/**
 * Unified Facade for the entire workflow engine lifecycle.
 *
 * <p>Covers initialization, execution, and runtime control. Single entry point for workflow
 * management: prepare → execute → pause/stop/restart/skip.
 *
 * <p>Encapsulates complex Project Reactor state manipulations (Sinks, Valves, etc.) behind a clean,
 * declarative API.
 *
 * <p><strong>Usage:</strong> Controllers and dispatchers inject this interface for full workflow
 * management without needing separate orchestrator and control API dependencies.
 */
public interface WorkflowControlApi {

  // --- Initialization & Execution ---

  /**
   * Prepares a workflow for execution.
   *
   * @param def the workflow definition
   * @return a Mono containing the prepared workflow
   */
  Mono<PreparedWorkflow> prepareWorkflow(@jakarta.validation.Valid WorkflowDefinition def);

  /**
   * Executes a prepared workflow.
   *
   * @param sessionId the session identifier
   * @param workflowId the workflow identifier
   * @param executionId the unique execution identifier
   * @param prepared the prepared workflow
   * @param payload the initial payload
   * @return a Mono that completes when execution finishes
   */
  Mono<Void> executeWorkflow(
      String sessionId,
      String workflowId,
      String executionId,
      PreparedWorkflow prepared,
      java.util.Map<String, Object> payload);

  // --- Runtime Control ---

  /**
   * Stop execution immediately, cancelling upstream operations.
   *
   * @param executionId the execution to stop
   * @return a Mono that completes when the stop is applied
   */
  Mono<Void> stopImmediately(String executionId);

  /**
   * Stop execution safely, draining inflight work before stopping.
   *
   * @param executionId the execution to stop
   * @return a Mono that completes when the stop is applied
   */
  Mono<Void> stopSafely(String executionId);

  /**
   * Pause all nodes in an execution via global backpressure.
   *
   * @param executionId the execution to pause
   * @return a Mono that completes when the pause is applied
   */
  Mono<Void> pauseWorkflow(String executionId);

  /**
   * Resume a paused execution.
   *
   * @param executionId the execution to resume
   * @return a Mono that completes when the resume is applied
   */
  Mono<Void> unpauseWorkflow(String executionId);

  /**
   * Pause a single node via backpressure.
   *
   * @param executionId the execution to target
   * @param nodeId the node to pause
   * @return a Mono that completes when the pause is applied
   */
  Mono<Void> pauseNode(String executionId, String nodeId);

  /**
   * Resume a paused node.
   *
   * @param executionId the execution to target
   * @param nodeId the node to resume
   * @return a Mono that completes when the resume is applied
   */
  Mono<Void> unpauseNode(String executionId, String nodeId);

  /**
   * Stop a single node immediately, cancelling its operations.
   *
   * @param executionId the execution to target
   * @param nodeId the node to stop
   * @return a Mono that completes when the stop is applied
   */
  Mono<Void> stopNodeImmediately(String executionId, String nodeId);

  /**
   * Stop a single node safely, draining inflight work.
   *
   * @param executionId the execution to target
   * @param nodeId the node to stop
   * @return a Mono that completes when the stop is applied
   */
  Mono<Void> stopNodeSafely(String executionId, String nodeId);

  /**
   * Mark a node as skipped or not.
   *
   * <p>When skip is true, the node's pre-processing control will pass messages through without
   * invoking the processor. When false, the node operates normally.
   *
   * @param executionId the execution to target
   * @param nodeId the node to skip
   * @param skip true to skip, false to unskip
   * @return a Mono that completes when the flag is applied
   */
  Mono<Void> skipNode(String executionId, String nodeId, boolean skip);

  /**
   * Get the current execution status and snapshot.
   *
   * @param executionId the execution to query
   * @return a Mono containing the current status, or empty if not found
   */
  Mono<WorkflowExecutionSnapshot> getStatus(String executionId);

  /**
   * Enable step-through debug mode on a node. Each element must be stepped via stepNode().
   *
   * <p>Automatically pauses the node until step signals are sent.
   *
   * @param executionId the execution to target
   * @param nodeId the node to enable step mode on
   * @return a Mono that completes when step mode is enabled
   */
  Mono<Void> enableNodeStepMode(String executionId, String nodeId);

  /**
   * Disable step-through debug mode on a node. Returns to normal pause/resume.
   *
   * @param executionId the execution to target
   * @param nodeId the node to disable step mode on
   * @return a Mono that completes when step mode is disabled
   */
  Mono<Void> disableNodeStepMode(String executionId, String nodeId);

  /**
   * Step to the next element when node is in step-through mode.
   *
   * <p>Allows exactly one element to pass through the node before blocking again.
   *
   * @param executionId the execution to target
   * @param nodeId the node to step
   * @return a Mono that completes when the step signal is emitted
   */
  Mono<Void> stepNode(String executionId, String nodeId);

  // --- Restart & Replay Controls ---

  /**
   * Safely stops the current execution and restarts the entire workflow from the beginning using
   * the original payload.
   *
   * @param executionId the execution to restart
   * @return a Mono containing the newly generated executionId
   */
  Mono<String> restartWorkflow(String executionId);

  /**
   * Safely stops the current execution and restarts the workflow from a specific node, replaying
   * the last known checkpoints for its parent nodes.
   *
   * @param executionId the execution to restart
   * @param nodeId the node from which to resume execution
   * @return a Mono containing the newly generated executionId
   */
  Mono<String> restartFromNode(String executionId, String nodeId);
}
