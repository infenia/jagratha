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

import com.infenia.yukta.model.workflow.PreparedWorkflow;
import com.infenia.yukta.plugin.core.NodeState;
import com.infenia.yukta.plugin.gateway.ControlBusGateway;
import com.infenia.yukta.plugin.message.DefaultMessage;
import com.infenia.yukta.plugin.message.Message;
import com.infenia.yukta.plugin.message.control.ControlError;
import com.infenia.yukta.plugin.message.control.NodeStateEvent;
import com.infenia.yukta.plugin.message.control.RetryEvent;
import com.infenia.yukta.plugin.retry.RetryPolicy;
import com.infenia.yukta.service.ExecutionRegistry;
import com.infenia.yukta.service.TaskTrackerService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Control signal handler for ControlError messages.
 *
 * <p>Processes node execution failures and determines whether to retry or mark the node as failed.
 * Emits RetryEvent if retries remain, or NodeStateEvent(FAILED) if exhausted.
 */
@Slf4j
@Component
public class ControlErrorHandler implements ControlSignalHandler {

  private final ExecutionRegistry executionRegistry;
  private final TaskTrackerService taskTrackerService;
  private final ControlBusGateway controlBusGateway;

  /**
   * Creates a new ControlErrorHandler.
   *
   * @param executionRegistry the execution registry
   * @param taskTrackerService the task tracker service
   * @param controlBusGateway the control bus gateway
   */
  public ControlErrorHandler(
      final ExecutionRegistry executionRegistry,
      final TaskTrackerService taskTrackerService,
      final ControlBusGateway controlBusGateway) {
    this.executionRegistry = executionRegistry;
    this.taskTrackerService = taskTrackerService;
    this.controlBusGateway = controlBusGateway;
  }

  @Override
  public boolean canHandle(final Object payload) {
    return payload instanceof ControlError;
  }

  @Override
  public void handle(final String nodeId, final Message<?> message, final Object payload) {
    final ControlError error = (ControlError) payload;
    final String executionId = error.executionId();
    final String errorReason = error.reason();
    final String errorDetail = error.detail();

    log.atWarn()
        .log(
            "Control error for node {} (execution {}): {} - {}",
            nodeId,
            executionId,
            errorReason,
            errorDetail);

    // Look up retry policy from execution's prepared workflow
    final Optional<PreparedWorkflow> preparedOpt =
        executionRegistry.getPreparedWorkflow(executionId);
    if (preparedOpt.isEmpty()) {
      log.atWarn().log("Prepared workflow not found for execution: {}", executionId);
      return;
    }

    final PreparedWorkflow prepared = preparedOpt.get();
    final RetryPolicy retryPolicy =
        prepared.definition().nodes().stream()
            .filter(n -> n.nodeId().equals(nodeId))
            .findFirst()
            .map(n -> n.retryPolicy())
            .orElse(RetryPolicy.none());

    // Check retry counter
    final int attemptNumber = executionRegistry.getAndIncrementRetry(executionId, nodeId);

    if (retryPolicy.maxAttempts() > 0 && attemptNumber <= retryPolicy.maxAttempts()) {
      // Emit RetryEvent
      final long backoffMs = calculateBackoff(retryPolicy, attemptNumber);
      final RetryEvent retryEvent =
          new RetryEvent(
              nodeId,
              executionId,
              attemptNumber,
              retryPolicy.maxAttempts(),
              backoffMs,
              Instant.now());

      emitEvent(retryEvent, nodeId);
      log.atInfo().log(
          "Retry scheduled for node {} (execution {}) - attempt {}/{}, backoff: {}ms",
          nodeId,
          executionId,
          attemptNumber,
          retryPolicy.maxAttempts(),
          backoffMs);
    } else {
      // Max retries exhausted or no retries configured - mark as failed
      final NodeStateEvent failedEvent =
          new NodeStateEvent(
              nodeId, executionId, NodeState.RUNNING, NodeState.FAILED, Instant.now());
      emitEvent(failedEvent, nodeId);

      // Emit task status event
      taskTrackerService.emitTaskStatusEvent(
          executionId, nodeId, "default", "FAILURE", Map.of("error", errorDetail));

      log.atWarn()
          .log(
              "Node {} (execution {}) failed after {} attempts",
              nodeId,
              executionId,
              attemptNumber);

      // Cleanup retry counter
      executionRegistry.resetRetry(executionId, nodeId);
    }
  }

  @Override
  public void removeNode(final String nodeId) {
    // No cleanup needed
  }

  private long calculateBackoff(final RetryPolicy policy, final int attemptNumber) {
    return switch (policy.backoffType()) {
      case FIXED -> policy.initialDelay();
      case EXPONENTIAL ->
          Math.min(
              policy.initialDelay() * (long) Math.pow(2, attemptNumber - 1), policy.maxDelay());
    };
  }

  private void emitEvent(final Object eventPayload, final String nodeId) {
    try {
      @SuppressWarnings("rawtypes")
      final DefaultMessage msg =
          new DefaultMessage(
              UUID.randomUUID().toString(),
              null,
              null,
              null,
              0,
              null,
              Map.of(),
              List.of(),
              null,
              0,
              0,
              0,
              true,
              null,
              null,
              null,
              0,
              eventPayload,
              Instant.now(),
              null,
              nodeId);
      controlBusGateway.emit(msg).subscribe();
    } catch (final Exception e) {
      log.atWarn().setCause(e).log("Failed to emit event for node {}", nodeId);
    }
  }
}
