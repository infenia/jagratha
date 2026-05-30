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
package com.infenia.yukta.service.control.gateway;

import com.infenia.yukta.model.monitoring.WorkflowExecutionSummary;
import com.infenia.yukta.model.monitoring.WorkflowProgress;
import com.infenia.yukta.plugin.message.Message;
import com.infenia.yukta.plugin.message.control.ExecutionControlCommand;
import java.util.List;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Unified gateway for all execution control operations.
 *
 * <p>Single entry point for controlling workflow executions. All execution control (pause, stop,
 * skip, step, restart) flows through the ControlBus as typed command signals, ensuring:
 *
 * <ul>
 *   <li>Single control channel (no dual paths)
 *   <li>Consistent ordering via message queue
 *   <li>Full audit trail and observability
 *   <li>No race conditions from multiple access paths
 * </ul>
 *
 * <p><strong>Usage:</strong> Inject this interface in controllers, UI services, and dispatchers.
 * Call convenience methods to control executions. All operations are async and non-blocking.
 */
public interface UnifiedControlBusGateway {

  /**
   * Execute a control command through the control bus.
   *
   * @param <T> the command type
   * @param command the control command message to execute
   * @return a Mono that completes when the command has been routed through the bus
   */
  <T extends ExecutionControlCommand> Mono<Void> executeCommand(Message<T> command);

  // --- Workflow-Level Control ---

  /**
   * Pause all nodes in an execution via global backpressure.
   *
   * <p>Execution continues to process inflight work but no new elements are pulled from input
   * streams.
   *
   * @param executionId the execution to pause
   * @return a Mono that completes when the pause command is emitted
   */
  Mono<Void> pauseWorkflow(String executionId);

  /**
   * Resume a paused execution.
   *
   * <p>Nodes resume normal backpressure-driven element processing.
   *
   * @param executionId the execution to resume
   * @return a Mono that completes when the resume command is emitted
   */
  Mono<Void> resumeWorkflow(String executionId);

  // --- Node-Level Control ---

  /**
   * Pause a single node via backpressure.
   *
   * <p>The target node stops pulling elements but inflight work continues to completion.
   *
   * @param executionId the execution to target
   * @param nodeId the node to pause
   * @return a Mono that completes when the pause command is emitted
   */
  Mono<Void> pauseNode(String executionId, String nodeId);

  /**
   * Resume a paused node.
   *
   * <p>The node resumes pulling elements and normal processing.
   *
   * @param executionId the execution to target
   * @param nodeId the node to resume
   * @return a Mono that completes when the resume command is emitted
   */
  Mono<Void> resumeNode(String executionId, String nodeId);

  /**
   * Stop a single node.
   *
   * @param executionId the execution to target
   * @param nodeId the node to stop
   * @param immediate true for hard stop (cancel upstream), false for drain & stop
   * @param reason human-readable explanation for logging
   * @return a Mono that completes when the stop command is emitted
   */
  Mono<Void> stopNode(String executionId, String nodeId, boolean immediate, String reason);

  /**
   * Mark a node as skipped or not.
   *
   * <p>When skipped, the node passes messages through without invoking the processor. When not
   * skipped, normal processing resumes.
   *
   * @param executionId the execution to target
   * @param nodeId the node to skip
   * @param skip true to skip, false to unskip
   * @return a Mono that completes when the skip command is emitted
   */
  Mono<Void> skipNode(String executionId, String nodeId, boolean skip);

  // --- Node-Level Debug Control ---

  /**
   * Enable step-through debug mode on a node.
   *
   * <p>Each element must be explicitly stepped via {@link #stepNode}. The node automatically pauses
   * until step signals are sent.
   *
   * @param executionId the execution to target
   * @param nodeId the node to enable step mode on
   * @return a Mono that completes when the enable command is emitted
   */
  Mono<Void> enableStepMode(String executionId, String nodeId);

  /**
   * Disable step-through debug mode on a node.
   *
   * <p>The node returns to normal pause/resume behavior.
   *
   * @param executionId the execution to target
   * @param nodeId the node to disable step mode on
   * @return a Mono that completes when the disable command is emitted
   */
  Mono<Void> disableStepMode(String executionId, String nodeId);

  /**
   * Step to the next element when a node is in step-through mode.
   *
   * <p>Allows exactly one element to pass through the node before blocking again.
   *
   * @param executionId the execution to target
   * @param nodeId the node to step
   * @return a Mono that completes when the step command is emitted
   */
  Mono<Void> stepNode(String executionId, String nodeId);

  // --- Restart & Replay Control ---

  /**
   * Safely stop the current execution and restart the entire workflow from the beginning using the
   * original payload.
   *
   * @param executionId the execution to restart
   * @return a Mono containing the newly generated execution ID
   */
  Mono<String> restartWorkflow(String executionId);

  /**
   * Safely stop the current execution and restart the workflow from a specific node, replaying the
   * last known checkpoints for its parent nodes.
   *
   * @param executionId the execution to restart
   * @param fromNodeId the node from which to resume execution
   * @return a Mono containing the newly generated execution ID
   */
  Mono<String> restartFromNode(String executionId, String fromNodeId);

  // --- Query & Inspection ---

  /**
   * List all node IDs across all workflows that have emitted heartbeats.
   *
   * @return list of all active node IDs
   */
  List<String> getActiveNodes();

  /**
   * List all node IDs in a specific workflow that have emitted heartbeats.
   *
   * @param workflowId the workflow identifier
   * @return list of node IDs scoped to the workflow
   */
  List<String> getActiveNodes(String workflowId);

  /**
   * Get the last heartbeat for a node in a specific workflow.
   *
   * @param workflowId the workflow identifier
   * @param nodeId the node identifier
   * @return the last heartbeat message, or null if none
   */
  Message<?> getLastHeartbeat(String workflowId, String nodeId);

  /**
   * Get the last statistics message for a node in a specific workflow.
   *
   * @param workflowId the workflow identifier
   * @param nodeId the node identifier
   * @return the last statistics message, or null if none
   */
  Message<?> getLastStatistics(String workflowId, String nodeId);

  // --- Observability Methods ---

  /**
   * Watch workflow execution progress in real-time.
   *
   * <p>Emits progress updates as the execution progresses (status changes, tasks complete, etc.).
   * Useful for UI streaming, progress tracking, and monitoring.
   *
   * @param executionId the execution to watch
   * @return a Flux of progress updates that completes when execution finishes
   */
  Flux<WorkflowProgress> watchExecution(String executionId);

  /**
   * Watch workflow logs in real-time.
   *
   * <p>Emits log lines as they are generated during execution. Useful for live log streaming to
   * UIs.
   *
   * @param executionId the execution to watch
   * @return a Flux of log lines that completes when execution finishes
   */
  Flux<String> watchLogs(String executionId);

  /**
   * Get current execution progress snapshot.
   *
   * <p>Returns the current progress of an execution without subscribing to a stream. Useful for
   * one-off queries.
   *
   * @param executionId the execution to query
   * @return the current progress, or null if execution not found
   */
  WorkflowProgress getCurrentProgress(String executionId);

  /**
   * Get execution history for a session.
   *
   * <p>Returns all completed and in-progress executions for a session. Useful for listing past
   * runs.
   *
   * @param sessionId the session to query
   * @return list of execution summaries
   */
  List<WorkflowExecutionSummary> getHistory(String sessionId);
}
