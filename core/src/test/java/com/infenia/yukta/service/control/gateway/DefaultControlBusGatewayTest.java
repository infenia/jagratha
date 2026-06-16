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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infenia.yukta.model.monitoring.WorkflowExecutionSummary;
import com.infenia.yukta.model.monitoring.WorkflowProgress;
import com.infenia.yukta.model.workflow.WorkflowDefinition;
import com.infenia.yukta.plugin.core.WorkflowPlugin;
import com.infenia.yukta.plugin.message.DefaultMessage;
import com.infenia.yukta.plugin.message.Message;
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
import com.infenia.yukta.service.execution.status.ExecutionStatusEvent;
import com.infenia.yukta.service.orchestrator.tracker.DefaultTaskTrackerService;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class DefaultControlBusGatewayTest {

  @Mock private ControlBusService controlBusService;
  @Mock private DefaultTaskTrackerService taskTracker;

  private DefaultControlBusGateway gateway;

  @BeforeEach
  void setUp() {
    gateway = new DefaultControlBusGateway(controlBusService, taskTracker);
  }

  // --- Plugin & Message Management Tests ---

  @Test
  void emit_validMessage_delegatesToControlBusService() {
    // Given
    Message<String> signal = DefaultMessage.create(null, "test-payload");
    when(controlBusService.emit(signal)).thenReturn(Mono.empty());

    // When
    Mono<Void> result = gateway.emit(signal);

    // Then
    StepVerifier.create(result).verifyComplete();
    verify(controlBusService).emit(signal);
  }

  @Test
  void registerPlugin_validInputs_delegatesToControlBusService() {
    // Given
    String workflowId = "workflow-1";
    String nodeId = "node-1";
    WorkflowPlugin plugin = mock(WorkflowPlugin.class);

    // When
    gateway.registerPlugin(workflowId, nodeId, plugin);

    // Then
    verify(controlBusService).registerPlugin(workflowId, nodeId, plugin);
  }

  @Test
  void unregisterPlugin_validInputs_delegatesToControlBusService() {
    // Given
    String workflowId = "workflow-1";
    String nodeId = "node-1";

    // When
    gateway.unregisterPlugin(workflowId, nodeId);

    // Then
    verify(controlBusService).unregisterPlugin(workflowId, nodeId);
  }

  @Test
  void sendCommand_validInputs_delegatesToControlBusService() {
    // Given
    String workflowId = "workflow-1";
    String nodeId = "node-1";
    Message<?> command = DefaultMessage.create(null, "command-payload");
    Message<?> response = DefaultMessage.create(null, "response-payload");
    when(controlBusService.sendCommand(workflowId, nodeId, command))
        .thenReturn(Mono.just(response));

    // When
    Mono<Message<?>> result = gateway.sendCommand(workflowId, nodeId, command);

    // Then
    StepVerifier.create(result).expectNext(response).verifyComplete();
    verify(controlBusService).sendCommand(workflowId, nodeId, command);
  }

  @Test
  void compileAndCacheWorkflow_validInputs_delegatesToControlBusService() {
    // Given
    String sessionId = "session-1";
    WorkflowDefinition definition =
        new WorkflowDefinition("workflow-1", "description", List.of(), List.of());
    when(controlBusService.compileAndCacheWorkflow(sessionId, definition)).thenReturn(Mono.empty());

    // When
    Mono<Void> result = gateway.compileAndCacheWorkflow(sessionId, definition);

    // Then
    StepVerifier.create(result).verifyComplete();
    verify(controlBusService).compileAndCacheWorkflow(sessionId, definition);
  }

  // --- Execution Control Command Tests ---

  @Test
  void pauseWorkflow_validExecutionId_emitsPauseCommand() {
    // Given
    String executionId = "exec-1";
    when(controlBusService.emit(any())).thenReturn(Mono.empty());

    // When
    Mono<Void> result = gateway.pauseWorkflow(executionId);

    // Then
    StepVerifier.create(result).verifyComplete();

    ArgumentCaptor<Message<?>> captor = ArgumentCaptor.forClass(Message.class);
    verify(controlBusService).emit(captor.capture());
    Message<?> emittedMessage = captor.getValue();
    assertThat(emittedMessage.getPayload()).isInstanceOf(PauseWorkflowCommand.class);
    PauseWorkflowCommand cmd = (PauseWorkflowCommand) emittedMessage.getPayload();
    assertThat(cmd.executionId()).isEqualTo(executionId);
    assertThat(emittedMessage.getPriority()).isEqualTo(100);
    assertThat(emittedMessage.getSourceNodeId()).isEqualTo("CONTROL_BUS");
    assertThat(emittedMessage.isControlMessage()).isTrue();
  }

  @Test
  void resumeWorkflow_validExecutionId_emitsResumeCommand() {
    // Given
    String executionId = "exec-2";
    when(controlBusService.emit(any())).thenReturn(Mono.empty());

    // When
    Mono<Void> result = gateway.resumeWorkflow(executionId);

    // Then
    StepVerifier.create(result).verifyComplete();

    ArgumentCaptor<Message<?>> captor = ArgumentCaptor.forClass(Message.class);
    verify(controlBusService).emit(captor.capture());
    Message<?> emittedMessage = captor.getValue();
    assertThat(emittedMessage.getPayload()).isInstanceOf(ResumeWorkflowCommand.class);
    ResumeWorkflowCommand cmd = (ResumeWorkflowCommand) emittedMessage.getPayload();
    assertThat(cmd.executionId()).isEqualTo(executionId);
  }

  @Test
  void pauseNode_validInputs_emitsPauseNodeCommand() {
    // Given
    String executionId = "exec-3";
    String nodeId = "node-2";
    when(controlBusService.emit(any())).thenReturn(Mono.empty());

    // When
    Mono<Void> result = gateway.pauseNode(executionId, nodeId);

    // Then
    StepVerifier.create(result).verifyComplete();

    ArgumentCaptor<Message<?>> captor = ArgumentCaptor.forClass(Message.class);
    verify(controlBusService).emit(captor.capture());
    Message<?> emittedMessage = captor.getValue();
    assertThat(emittedMessage.getPayload()).isInstanceOf(PauseNodeCommand.class);
    PauseNodeCommand cmd = (PauseNodeCommand) emittedMessage.getPayload();
    assertThat(cmd.executionId()).isEqualTo(executionId);
    assertThat(cmd.nodeId()).isEqualTo(nodeId);
  }

  @Test
  void resumeNode_validInputs_emitsResumeNodeCommand() {
    // Given
    String executionId = "exec-4";
    String nodeId = "node-3";
    when(controlBusService.emit(any())).thenReturn(Mono.empty());

    // When
    Mono<Void> result = gateway.resumeNode(executionId, nodeId);

    // Then
    StepVerifier.create(result).verifyComplete();

    ArgumentCaptor<Message<?>> captor = ArgumentCaptor.forClass(Message.class);
    verify(controlBusService).emit(captor.capture());
    Message<?> emittedMessage = captor.getValue();
    assertThat(emittedMessage.getPayload()).isInstanceOf(ResumeNodeCommand.class);
    ResumeNodeCommand cmd = (ResumeNodeCommand) emittedMessage.getPayload();
    assertThat(cmd.executionId()).isEqualTo(executionId);
    assertThat(cmd.nodeId()).isEqualTo(nodeId);
  }

  @Test
  void stopNode_normalStop_emitsStopCommandWithElevatedPriority() {
    // Given
    String executionId = "exec-5";
    String nodeId = "node-4";
    boolean immediate = false;
    String reason = "test reason";
    when(controlBusService.emit(any())).thenReturn(Mono.empty());

    // When
    Mono<Void> result = gateway.stopNode(executionId, nodeId, immediate, reason);

    // Then
    StepVerifier.create(result).verifyComplete();

    ArgumentCaptor<Message<?>> captor = ArgumentCaptor.forClass(Message.class);
    verify(controlBusService).emit(captor.capture());
    Message<?> emittedMessage = captor.getValue();
    assertThat(emittedMessage.getPayload()).isInstanceOf(StopNodeCommand.class);
    StopNodeCommand cmd = (StopNodeCommand) emittedMessage.getPayload();
    assertThat(cmd.executionId()).isEqualTo(executionId);
    assertThat(cmd.nodeId()).isEqualTo(nodeId);
    assertThat(cmd.immediate()).isEqualTo(immediate);
    assertThat(cmd.reason()).isEqualTo(reason);
    assertThat(emittedMessage.getPriority()).isEqualTo(110); // CONTROL_COMMAND_PRIORITY + 10
  }

  @Test
  void stopNode_immediateStop_emitsStopCommandWithImmediateFlag() {
    // Given
    String executionId = "exec-6";
    String nodeId = "node-5";
    boolean immediate = true;
    String reason = "immediate stop";
    when(controlBusService.emit(any())).thenReturn(Mono.empty());

    // When
    Mono<Void> result = gateway.stopNode(executionId, nodeId, immediate, reason);

    // Then
    StepVerifier.create(result).verifyComplete();

    ArgumentCaptor<Message<?>> captor = ArgumentCaptor.forClass(Message.class);
    verify(controlBusService).emit(captor.capture());
    Message<?> emittedMessage = captor.getValue();
    StopNodeCommand cmd = (StopNodeCommand) emittedMessage.getPayload();
    assertThat(cmd.immediate()).isTrue();
  }

  @Test
  void skipNode_skipTrue_emitsSkipCommand() {
    // Given
    String executionId = "exec-7";
    String nodeId = "node-6";
    boolean skip = true;
    when(controlBusService.emit(any())).thenReturn(Mono.empty());

    // When
    Mono<Void> result = gateway.skipNode(executionId, nodeId, skip);

    // Then
    StepVerifier.create(result).verifyComplete();

    ArgumentCaptor<Message<?>> captor = ArgumentCaptor.forClass(Message.class);
    verify(controlBusService).emit(captor.capture());
    Message<?> emittedMessage = captor.getValue();
    assertThat(emittedMessage.getPayload()).isInstanceOf(SkipNodeCommand.class);
    SkipNodeCommand cmd = (SkipNodeCommand) emittedMessage.getPayload();
    assertThat(cmd.skip()).isTrue();
  }

  @Test
  void skipNode_skipFalse_emitsUnskipCommand() {
    // Given
    String executionId = "exec-8";
    String nodeId = "node-7";
    boolean skip = false;
    when(controlBusService.emit(any())).thenReturn(Mono.empty());

    // When
    Mono<Void> result = gateway.skipNode(executionId, nodeId, skip);

    // Then
    StepVerifier.create(result).verifyComplete();

    ArgumentCaptor<Message<?>> captor = ArgumentCaptor.forClass(Message.class);
    verify(controlBusService).emit(captor.capture());
    Message<?> emittedMessage = captor.getValue();
    SkipNodeCommand cmd = (SkipNodeCommand) emittedMessage.getPayload();
    assertThat(cmd.skip()).isFalse();
  }

  @Test
  void enableStepMode_validInputs_emitsEnableStepModeCommand() {
    // Given
    String executionId = "exec-9";
    String nodeId = "node-8";
    when(controlBusService.emit(any())).thenReturn(Mono.empty());

    // When
    Mono<Void> result = gateway.enableStepMode(executionId, nodeId);

    // Then
    StepVerifier.create(result).verifyComplete();

    ArgumentCaptor<Message<?>> captor = ArgumentCaptor.forClass(Message.class);
    verify(controlBusService).emit(captor.capture());
    Message<?> emittedMessage = captor.getValue();
    assertThat(emittedMessage.getPayload()).isInstanceOf(EnableStepModeCommand.class);
    EnableStepModeCommand cmd = (EnableStepModeCommand) emittedMessage.getPayload();
    assertThat(cmd.executionId()).isEqualTo(executionId);
    assertThat(cmd.nodeId()).isEqualTo(nodeId);
  }

  @Test
  void disableStepMode_validInputs_emitsDisableStepModeCommand() {
    // Given
    String executionId = "exec-10";
    String nodeId = "node-9";
    when(controlBusService.emit(any())).thenReturn(Mono.empty());

    // When
    Mono<Void> result = gateway.disableStepMode(executionId, nodeId);

    // Then
    StepVerifier.create(result).verifyComplete();

    ArgumentCaptor<Message<?>> captor = ArgumentCaptor.forClass(Message.class);
    verify(controlBusService).emit(captor.capture());
    Message<?> emittedMessage = captor.getValue();
    assertThat(emittedMessage.getPayload()).isInstanceOf(DisableStepModeCommand.class);
    DisableStepModeCommand cmd = (DisableStepModeCommand) emittedMessage.getPayload();
    assertThat(cmd.executionId()).isEqualTo(executionId);
    assertThat(cmd.nodeId()).isEqualTo(nodeId);
  }

  @Test
  void stepNode_validInputs_emitsStepNodeCommand() {
    // Given
    String executionId = "exec-11";
    String nodeId = "node-10";
    when(controlBusService.emit(any())).thenReturn(Mono.empty());

    // When
    Mono<Void> result = gateway.stepNode(executionId, nodeId);

    // Then
    StepVerifier.create(result).verifyComplete();

    ArgumentCaptor<Message<?>> captor = ArgumentCaptor.forClass(Message.class);
    verify(controlBusService).emit(captor.capture());
    Message<?> emittedMessage = captor.getValue();
    assertThat(emittedMessage.getPayload()).isInstanceOf(StepNodeCommand.class);
    StepNodeCommand cmd = (StepNodeCommand) emittedMessage.getPayload();
    assertThat(cmd.executionId()).isEqualTo(executionId);
    assertThat(cmd.nodeId()).isEqualTo(nodeId);
  }

  @Test
  void restartWorkflow_validExecutionId_generatesNewExecutionId() {
    // Given
    String executionId = "exec-12";
    when(controlBusService.emit(any())).thenReturn(Mono.empty());

    // When
    Mono<String> result = gateway.restartWorkflow(executionId);

    // Then
    StepVerifier.create(result)
        .assertNext(
            newExecId -> {
              assertThat(newExecId).isNotNull();
              assertThat(newExecId).isNotEqualTo(executionId);
              // Verify it's a valid UUID format - should not throw exception
              try {
                UUID.fromString(newExecId);
              } catch (IllegalArgumentException e) {
                throw new AssertionError("Not a valid UUID: " + newExecId, e);
              }
            })
        .verifyComplete();

    ArgumentCaptor<Message<?>> captor = ArgumentCaptor.forClass(Message.class);
    verify(controlBusService).emit(captor.capture());
    Message<?> emittedMessage = captor.getValue();
    assertThat(emittedMessage.getPayload()).isInstanceOf(RestartCommand.class);
    RestartCommand cmd = (RestartCommand) emittedMessage.getPayload();
    assertThat(cmd.executionId()).isEqualTo(executionId);
    assertThat(emittedMessage.getPriority()).isEqualTo(120); // CONTROL_COMMAND_PRIORITY + 20
  }

  @Test
  void restartFromNode_validInputs_generatesNewExecutionId() {
    // Given
    String executionId = "exec-13";
    String fromNodeId = "node-11";
    when(controlBusService.emit(any())).thenReturn(Mono.empty());

    // When
    Mono<String> result = gateway.restartFromNode(executionId, fromNodeId);

    // Then
    StepVerifier.create(result)
        .assertNext(
            newExecId -> {
              assertThat(newExecId).isNotNull();
              assertThat(newExecId).isNotEqualTo(executionId);
              // Verify it's a valid UUID format - should not throw exception
              try {
                UUID.fromString(newExecId);
              } catch (IllegalArgumentException e) {
                throw new AssertionError("Not a valid UUID: " + newExecId, e);
              }
            })
        .verifyComplete();

    ArgumentCaptor<Message<?>> captor = ArgumentCaptor.forClass(Message.class);
    verify(controlBusService).emit(captor.capture());
    Message<?> emittedMessage = captor.getValue();
    assertThat(emittedMessage.getPayload()).isInstanceOf(RestartFromNodeCommand.class);
    RestartFromNodeCommand cmd = (RestartFromNodeCommand) emittedMessage.getPayload();
    assertThat(cmd.executionId()).isEqualTo(executionId);
    assertThat(cmd.fromNodeId()).isEqualTo(fromNodeId);
    assertThat(emittedMessage.getPriority()).isEqualTo(120); // CONTROL_COMMAND_PRIORITY + 20
  }

  // --- Observability Tests ---

  @Test
  void watchExecution_validExecutionId_delegatesToTaskTracker() {
    // Given
    String executionId = "exec-14";
    WorkflowProgress progress = mock(WorkflowProgress.class);
    when(taskTracker.getStatusStream(executionId)).thenReturn(Flux.just(progress));

    // When
    Flux<WorkflowProgress> result = gateway.watchExecution(executionId);

    // Then
    StepVerifier.create(result).expectNext(progress).verifyComplete();
    verify(taskTracker).getStatusStream(executionId);
  }

  @Test
  void watchLogs_validExecutionId_delegatesToTaskTracker() {
    // Given
    String executionId = "exec-15";
    String logLine = "test log";
    when(taskTracker.getLogStream(executionId)).thenReturn(Flux.just(logLine));

    // When
    Flux<String> result = gateway.watchLogs(executionId);

    // Then
    StepVerifier.create(result).expectNext(logLine).verifyComplete();
    verify(taskTracker).getLogStream(executionId);
  }

  @Test
  void getCurrentProgress_validExecutionId_delegatesToTaskTracker() {
    // Given
    String executionId = "exec-16";
    WorkflowProgress progress = mock(WorkflowProgress.class);
    when(taskTracker.getProgressByExecutionId(executionId)).thenReturn(progress);

    // When
    WorkflowProgress result = gateway.getCurrentProgress(executionId);

    // Then
    assertThat(result).isEqualTo(progress);
    verify(taskTracker).getProgressByExecutionId(executionId);
  }

  @Test
  void getHistory_validSessionId_delegatesToTaskTracker() {
    // Given
    String sessionId = "session-2";
    WorkflowExecutionSummary summary = mock(WorkflowExecutionSummary.class);
    List<WorkflowExecutionSummary> history = List.of(summary);
    when(taskTracker.getHistory(sessionId)).thenReturn(history);

    // When
    List<WorkflowExecutionSummary> result = gateway.getHistory(sessionId);

    // Then
    assertThat(result).isEqualTo(history);
    verify(taskTracker).getHistory(sessionId);
  }

  // --- State Query Tests ---

  @Test
  void getLastHeartbeat_validInputs_delegatesToControlBusService() {
    // Given
    String workflowId = "workflow-2";
    String nodeId = "node-12";
    when(controlBusService.getLastHeartbeat(workflowId, nodeId)).thenReturn(null);

    // When
    Message<?> result = gateway.getLastHeartbeat(workflowId, nodeId);

    // Then
    assertThat(result).isNull();
    verify(controlBusService).getLastHeartbeat(workflowId, nodeId);
  }

  @Test
  void getLastStatistics_validInputs_delegatesToControlBusService() {
    // Given
    String workflowId = "workflow-3";
    String nodeId = "node-13";
    when(controlBusService.getLastStatistics(workflowId, nodeId)).thenReturn(null);

    // When
    Message<?> result = gateway.getLastStatistics(workflowId, nodeId);

    // Then
    assertThat(result).isNull();
    verify(controlBusService).getLastStatistics(workflowId, nodeId);
  }

  @Test
  void getActiveNodes_withWorkflowId_delegatesToControlBusService() {
    // Given
    String workflowId = "workflow-4";
    List<String> activeNodes = List.of("node-14", "node-15");
    when(controlBusService.getActiveNodes(workflowId)).thenReturn(activeNodes);

    // When
    List<String> result = gateway.getActiveNodes(workflowId);

    // Then
    assertThat(result).isEqualTo(activeNodes);
    verify(controlBusService).getActiveNodes(workflowId);
  }

  @Test
  void getActiveNodes_noWorkflowId_delegatesToControlBusService() {
    // Given
    List<String> allActiveNodes = List.of("node-16", "node-17", "node-18");
    when(controlBusService.getActiveNodes()).thenReturn(allActiveNodes);

    // When
    List<String> result = gateway.getActiveNodes();

    // Then
    assertThat(result).isEqualTo(allActiveNodes);
    verify(controlBusService).getActiveNodes();
  }

  // --- ExecutionStatusPublisher Tests ---

  @Test
  void publishStatus_validEvent_emitsToSink() {
    // Given
    ExecutionStatusEvent event =
        new ExecutionStatusEvent(
            "exec-17",
            "node-19",
            "workflow-5",
            "session-3",
            "RUNNING",
            "module-1",
            Map.of("key", "value"),
            null,
            Instant.now());
    gateway.subscribeToStatusEvents(); // Initialize subscription

    // When
    Mono<Void> result = gateway.publishStatus(event);

    // Then
    StepVerifier.create(result).verifyComplete();
  }

  @Test
  void publishStatus_eventWithNullMetadata_emitsToSink() {
    // Given
    ExecutionStatusEvent event =
        new ExecutionStatusEvent(
            "exec-18",
            "node-20",
            "workflow-6",
            "session-4",
            "SUCCESS",
            "module-2",
            null,
            null,
            Instant.now());
    gateway.subscribeToStatusEvents();

    // When
    Mono<Void> result = gateway.publishStatus(event);

    // Then
    StepVerifier.create(result).verifyComplete();
  }

  @Test
  void statusStream_returnsFluxOfEvents() {
    // Given
    ExecutionStatusEvent event =
        new ExecutionStatusEvent(
            "exec-19",
            "node-21",
            "workflow-7",
            "session-5",
            "FAILURE",
            "module-3",
            null,
            null,
            Instant.now());

    // When - get stream and publish concurrently
    Flux<ExecutionStatusEvent> stream = gateway.statusStream();

    // Then - verify stream returns events published to it
    // Subscribe before publishing to avoid race condition with multicast sink
    StepVerifier.create(stream.doFirst(() -> gateway.publishStatus(event).block()).take(1))
        .assertNext(
            receivedEvent -> {
              assertThat(receivedEvent.executionId()).isEqualTo("exec-19");
              assertThat(receivedEvent.status()).isEqualTo("FAILURE");
            })
        .verifyComplete();
  }

  @Test
  void subscribeToStatusEvents_initializesSubscription() {
    // Given - just verify subscribeToStatusEvents is callable and doesn't error

    // When
    gateway.subscribeToStatusEvents();

    // Then - method completes without error (subscription is established internally)
    // This verifies the @PostConstruct method works correctly
  }

  @Test
  void executeCommand_validCommand_delegatesToEmit() {
    // Given
    PauseWorkflowCommand command = new PauseWorkflowCommand("exec-22");
    Message<PauseWorkflowCommand> cmdMessage = DefaultMessage.create(null, command);
    when(controlBusService.emit(any())).thenReturn(Mono.empty());

    // When
    Mono<Void> result = gateway.executeCommand(cmdMessage);

    // Then
    StepVerifier.create(result).verifyComplete();
    verify(controlBusService).emit(any());
  }

  @Test
  void publishStatus_emitsEventToSink() {
    // Given
    ExecutionStatusEvent event =
        new ExecutionStatusEvent(
            "exec-publish",
            "node-publish",
            "workflow-publish",
            "session-publish",
            "RUNNING",
            "module-publish",
            Map.of("key", "value"),
            null,
            Instant.now());
    gateway.subscribeToStatusEvents();

    // When
    Mono<Void> result = gateway.publishStatus(event);

    // Then
    StepVerifier.create(result).verifyComplete();
    verify(taskTracker)
        .updateTaskStatus(
            eq("exec-publish"), eq("node-publish"), eq("module-publish"), eq("RUNNING"), anyMap());
  }

  @Test
  void buildCommand_createsMessageWithCorrectProperties() {
    // This test verifies the buildCommand method is executed
    // by testing that control commands create proper messages
    String executionId = "exec-build-cmd";
    when(controlBusService.emit(any())).thenReturn(Mono.empty());

    // When
    Mono<Void> result = gateway.pauseWorkflow(executionId);

    // Then - verify the message has correct properties
    StepVerifier.create(result).verifyComplete();

    ArgumentCaptor<Message<?>> captor = ArgumentCaptor.forClass(Message.class);
    verify(controlBusService).emit(captor.capture());
    Message<?> emittedMessage = captor.getValue();
    assertThat(emittedMessage.isControlMessage()).isTrue();
    assertThat(emittedMessage.getSourceNodeId()).isEqualTo("CONTROL_BUS");
  }

  @Test
  void subscribeToStatusEvents_forwardsEventsToTaskTracker() {
    // Given
    gateway.subscribeToStatusEvents();
    ExecutionStatusEvent event =
        new ExecutionStatusEvent(
            "exec-forward",
            "node-forward",
            "workflow-forward",
            "session-forward",
            "COMPLETED",
            "module-forward",
            Map.of("key", "value"),
            null,
            Instant.now());

    // When
    gateway.publishStatus(event).block();

    // Then - verify task tracker received the update
    verify(taskTracker)
        .updateTaskStatus(
            eq("exec-forward"),
            eq("node-forward"),
            eq("module-forward"),
            eq("COMPLETED"),
            anyMap());
  }

  @Test
  void subscribeToStatusEvents_withNullMetadata_usesEmptyMap() {
    // Given
    gateway.subscribeToStatusEvents();
    ExecutionStatusEvent event =
        new ExecutionStatusEvent(
            "exec-null-meta",
            "node-null-meta",
            "workflow-null-meta",
            "session-null-meta",
            "PENDING",
            "module-null-meta",
            null,
            null,
            Instant.now());

    // When
    gateway.publishStatus(event).block();

    // Then - verify empty map is used when metadata is null
    verify(taskTracker)
        .updateTaskStatus(
            eq("exec-null-meta"),
            eq("node-null-meta"),
            eq("module-null-meta"),
            eq("PENDING"),
            eq(Map.of()));
  }
}
