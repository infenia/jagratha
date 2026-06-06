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
import com.infenia.yukta.model.workflow.WorkflowDefinition;
import com.infenia.yukta.plugin.core.WorkflowPlugin;
import com.infenia.yukta.plugin.message.DefaultMessage;
import com.infenia.yukta.plugin.message.Message;
import com.infenia.yukta.plugin.message.control.ExecutionControlCommand;
import com.infenia.yukta.plugin.message.control.ExecutionControlCommand.DisableStepModeCommand;
import com.infenia.yukta.plugin.message.control.ExecutionControlCommand.EnableStepModeCommand;
import com.infenia.yukta.plugin.message.control.ExecutionControlCommand.PauseNodeCommand;
import com.infenia.yukta.plugin.message.control.ExecutionControlCommand.PauseWorkflowCommand;
import com.infenia.yukta.plugin.message.control.ExecutionControlCommand.RestartCommand;
import com.infenia.yukta.plugin.message.control.ExecutionControlCommand.RestartFromNodeCommand;
import com.infenia.yukta.plugin.message.control.ExecutionControlCommand.ResumeNodeCommand;
import com.infenia.yukta.plugin.message.control.ExecutionControlCommand.ResumeWorkflowCommand;
import com.infenia.yukta.plugin.message.control.ExecutionControlCommand.SkipNodeCommand;
import com.infenia.yukta.plugin.message.control.ExecutionControlCommand.StepNodeCommand;
import com.infenia.yukta.plugin.message.control.ExecutionControlCommand.StopNodeCommand;
import com.infenia.yukta.service.control.ControlBusService;
import com.infenia.yukta.service.control.command.PrepareWorkflowCommand;
import com.infenia.yukta.service.execution.status.ExecutionStatusEvent;
import com.infenia.yukta.service.execution.status.ExecutionStatusPublisher;
import com.infenia.yukta.service.orchestrator.tracker.DefaultTaskTrackerServiceService;
import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.util.concurrent.Queues;

/**
 * Default implementation of the {@link ControlBusGateway} and {@link ExecutionStatusPublisher}.
 *
 * <p>Combines control bus gateway functionality with execution status publishing. Delegates
 * low-level plugin management and message operations to {@link ControlBusService}, and high-level
 * control commands and observability operations to {@link DefaultTaskTrackerServiceService}.
 * Manages status event publication via internal Reactor Sinks.
 */
@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class DefaultControlBusGateway implements ControlBusGateway, ExecutionStatusPublisher {

  private static final String CONTROL_BUS_SOURCE = "CONTROL_BUS";
  private static final int CONTROL_COMMAND_PRIORITY = 100;
  private static final Sinks.EmitFailureHandler RETRY_HANDLER =
      Sinks.EmitFailureHandler.busyLooping(Duration.ofMillis(100));

  private final ControlBusService controlBusService;
  private final DefaultTaskTrackerServiceService taskTracker;
  private final Sinks.Many<ExecutionStatusEvent> statusSink =
      Sinks.many().multicast().onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);

  private <T extends ExecutionControlCommand> Message<T> buildCommand(
      final T command, final int priority) {
    return DefaultMessage.create(null, command)
        .withSourceNodeId(CONTROL_BUS_SOURCE)
        .withPriority(priority)
        .withControl(true);
  }

  /**
   * Subscribes to status events emitted by the control bus and forwards them to the task tracker.
   * This method is called automatically during Spring bean initialization.
   */
  @PostConstruct
  public void subscribeToStatusEvents() {
    statusSink
        .asFlux()
        .subscribe(
            event -> {
              // Forward status updates to task tracker
              taskTracker
                  .updateTaskStatus(
                      event.executionId(),
                      event.nodeId(),
                      event.module(),
                      event.status(),
                      event.metadata() != null ? event.metadata() : Map.of())
                  .subscribe();
            },
            error -> log.atError().setCause(error).log("Status event stream error"));
  }

  // --- Plugin & Message Management ---

  @Override
  public <T> Mono<Void> emit(final Message<T> signal) {
    return controlBusService.emit(signal);
  }

  @Override
  public void registerPlugin(
      final String workflowId, final String nodeId, final WorkflowPlugin plugin) {
    controlBusService.registerPlugin(workflowId, nodeId, plugin);
  }

  @Override
  public void unregisterPlugin(final String workflowId, final String nodeId) {
    controlBusService.unregisterPlugin(workflowId, nodeId);
  }

  @Override
  public Mono<Message<?>> sendCommand(
      final String workflowId, final String nodeId, final Message<?> command) {
    return controlBusService.sendCommand(workflowId, nodeId, command);
  }

  // --- Configuration & Preparation ---

  @Override
  public Mono<Void> prepareWorkflow(final WorkflowDefinition workflowDefinition) {
    return controlBusService.prepareWorkflow(new PrepareWorkflowCommand(workflowDefinition));
  }

  // --- Execution Control ---

  @Override
  public <T extends ExecutionControlCommand> Mono<Void> executeCommand(final Message<T> command) {
    return emit(command);
  }

  @Override
  public Mono<Void> pauseWorkflow(final String executionId) {
    return executeCommand(
        buildCommand(new PauseWorkflowCommand(executionId), CONTROL_COMMAND_PRIORITY));
  }

  @Override
  public Mono<Void> resumeWorkflow(final String executionId) {
    return executeCommand(
        buildCommand(new ResumeWorkflowCommand(executionId), CONTROL_COMMAND_PRIORITY));
  }

  @Override
  public Mono<Void> pauseNode(final String executionId, final String nodeId) {
    return executeCommand(
        buildCommand(new PauseNodeCommand(executionId, nodeId), CONTROL_COMMAND_PRIORITY));
  }

  @Override
  public Mono<Void> resumeNode(final String executionId, final String nodeId) {
    return executeCommand(
        buildCommand(new ResumeNodeCommand(executionId, nodeId), CONTROL_COMMAND_PRIORITY));
  }

  @Override
  public Mono<Void> stopNode(
      final String executionId, final String nodeId, final boolean immediate, final String reason) {
    return executeCommand(
        buildCommand(
            new StopNodeCommand(executionId, nodeId, immediate, reason),
            CONTROL_COMMAND_PRIORITY + 10));
  }

  @Override
  public Mono<Void> skipNode(final String executionId, final String nodeId, final boolean skip) {
    return executeCommand(
        buildCommand(new SkipNodeCommand(executionId, nodeId, skip), CONTROL_COMMAND_PRIORITY));
  }

  @Override
  public Mono<Void> enableStepMode(final String executionId, final String nodeId) {
    return executeCommand(
        buildCommand(new EnableStepModeCommand(executionId, nodeId), CONTROL_COMMAND_PRIORITY));
  }

  @Override
  public Mono<Void> disableStepMode(final String executionId, final String nodeId) {
    return executeCommand(
        buildCommand(new DisableStepModeCommand(executionId, nodeId), CONTROL_COMMAND_PRIORITY));
  }

  @Override
  public Mono<Void> stepNode(final String executionId, final String nodeId) {
    return executeCommand(
        buildCommand(new StepNodeCommand(executionId, nodeId), CONTROL_COMMAND_PRIORITY));
  }

  @Override
  public Mono<String> restartWorkflow(final String executionId) {
    final String newExecutionId = UUID.randomUUID().toString();
    return executeCommand(
            buildCommand(new RestartCommand(executionId), CONTROL_COMMAND_PRIORITY + 20))
        .then(Mono.just(newExecutionId));
  }

  @Override
  public Mono<String> restartFromNode(final String executionId, final String fromNodeId) {
    final String newExecutionId = UUID.randomUUID().toString();
    return executeCommand(
            buildCommand(
                new RestartFromNodeCommand(executionId, fromNodeId), CONTROL_COMMAND_PRIORITY + 20))
        .then(Mono.just(newExecutionId));
  }

  // --- Observability ---

  @Override
  public Flux<WorkflowProgress> watchExecution(final String executionId) {
    return taskTracker.getStatusStream(executionId);
  }

  @Override
  public Flux<String> watchLogs(final String executionId) {
    return taskTracker.getLogStream(executionId);
  }

  @Override
  public WorkflowProgress getCurrentProgress(final String executionId) {
    return taskTracker.getProgressByExecutionId(executionId);
  }

  @Override
  public List<WorkflowExecutionSummary> getHistory(final String sessionId) {
    return taskTracker.getHistory(sessionId);
  }

  // --- State Queries ---

  @Override
  public Message<?> getLastHeartbeat(final String workflowId, final String nodeId) {
    return controlBusService.getLastHeartbeat(workflowId, nodeId);
  }

  @Override
  public Message<?> getLastStatistics(final String workflowId, final String nodeId) {
    return controlBusService.getLastStatistics(workflowId, nodeId);
  }

  @Override
  public List<String> getActiveNodes(final String workflowId) {
    return controlBusService.getActiveNodes(workflowId);
  }

  @Override
  public List<String> getActiveNodes() {
    return controlBusService.getActiveNodes();
  }

  // --- ExecutionStatusPublisher Implementation ---

  @Override
  public Mono<Void> publishStatus(@NotNull final ExecutionStatusEvent event) {
    return Mono.create(
        sink -> {
          try {
            statusSink.emitNext(event, RETRY_HANDLER);
            sink.success();
          } catch (final RuntimeException e) {
            log.atError()
                .setCause(e)
                .addKeyValue("executionId", event.executionId())
                .log("Failed to publish status event");
            sink.error(new IllegalStateException("Status event publish failed", e));
          }
        });
  }

  @Override
  public Flux<ExecutionStatusEvent> statusStream() {
    return statusSink.asFlux();
  }
}
