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

import com.infenia.yukta.plugin.message.DefaultMessage;
import com.infenia.yukta.plugin.message.Message;
import com.infenia.yukta.plugin.message.control.ExecutionControlCommand;
import com.infenia.yukta.plugin.message.control.ExecutionControlCommand.DisableStepModeCommand;
import com.infenia.yukta.plugin.message.control.ExecutionControlCommand.EnableStepModeCommand;
import com.infenia.yukta.plugin.message.control.ExecutionControlCommand.PauseNodeCommand;
import com.infenia.yukta.plugin.message.control.ExecutionControlCommand.PauseWorkflowCommand;
import com.infenia.yukta.plugin.message.control.ExecutionControlCommand.ResumeNodeCommand;
import com.infenia.yukta.plugin.message.control.ExecutionControlCommand.ResumeWorkflowCommand;
import com.infenia.yukta.plugin.message.control.ExecutionControlCommand.RestartCommand;
import com.infenia.yukta.plugin.message.control.ExecutionControlCommand.RestartFromNodeCommand;
import com.infenia.yukta.plugin.message.control.ExecutionControlCommand.SkipNodeCommand;
import com.infenia.yukta.plugin.message.control.ExecutionControlCommand.StepNodeCommand;
import com.infenia.yukta.plugin.message.control.ExecutionControlCommand.StopNodeCommand;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Default implementation of UnifiedControlBusGateway.
 *
 * <p>Wraps the lower-level ControlBusGateway and provides convenience methods for building and
 * emitting control commands. All commands flow through the unified ControlBus channel.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultUnifiedControlBusGateway implements UnifiedControlBusGateway {

  private static final String CONTROL_BUS_SOURCE = "CONTROL_BUS";
  private static final int CONTROL_COMMAND_PRIORITY = 100;

  private final ControlBusGateway controlBusGateway;

  private <T extends ExecutionControlCommand> Message<T> buildCommand(
      final T command, final int priority) {
    return DefaultMessage.create(null, command)
        .withSourceNodeId(CONTROL_BUS_SOURCE)
        .withPriority(priority)
        .withControl(true);
  }

  @Override
  public <T extends ExecutionControlCommand> Mono<Void> executeCommand(final Message<T> command) {
    return controlBusGateway.emit(command);
  }

  @Override
  public Mono<Void> pauseWorkflow(final String executionId) {
    return executeCommand(buildCommand(new PauseWorkflowCommand(executionId), CONTROL_COMMAND_PRIORITY));
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
      final String executionId,
      final String nodeId,
      final boolean immediate,
      final String reason) {
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
    return executeCommand(buildCommand(new StepNodeCommand(executionId, nodeId), CONTROL_COMMAND_PRIORITY));
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

  @Override
  public List<String> getActiveNodes() {
    return controlBusGateway.getActiveNodes();
  }

  @Override
  public List<String> getActiveNodes(final String workflowId) {
    return controlBusGateway.getActiveNodes(workflowId);
  }

  @Override
  public Message<?> getLastHeartbeat(final String workflowId, final String nodeId) {
    return controlBusGateway.getLastHeartbeat(workflowId, nodeId);
  }

  @Override
  public Message<?> getLastStatistics(final String workflowId, final String nodeId) {
    return controlBusGateway.getLastStatistics(workflowId, nodeId);
  }
}
