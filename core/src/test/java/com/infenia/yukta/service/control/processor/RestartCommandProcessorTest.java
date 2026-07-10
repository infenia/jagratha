// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Infenia Private Limited
package com.infenia.yukta.service.control.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infenia.yukta.model.workflow.PreparedWorkflow;
import com.infenia.yukta.plugin.control.ExecutionControlCommand;
import com.infenia.yukta.plugin.control.ExecutionControlCommand.PauseWorkflowCommand;
import com.infenia.yukta.plugin.control.ExecutionControlCommand.RestartCommand;
import com.infenia.yukta.service.control.ExecutionControl;
import com.infenia.yukta.service.control.gateway.RestartCompletionSink;
import com.infenia.yukta.service.control.store.ExecutionControlRegistry;
import com.infenia.yukta.service.orchestrator.WorkflowOrchestrator;
import java.util.Map;
import java.util.Optional;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@NoArgsConstructor
@SuppressWarnings({"PMD.TooManyStaticImports", "PMD.CommentRequired", "PMD.LinguisticNaming"})
class RestartCommandProcessorTest {

  @Mock private ExecutionControlRegistry registry;
  @Mock private WorkflowOrchestrator orchestrator;
  @Mock private RestartCompletionSink completionSink;
  @Mock private ExecutionControl executionControl;
  @Mock private PreparedWorkflow preparedWorkflow;

  @InjectMocks private RestartCommandProcessor processor;

  @Test
  void canProcess_restartCommand_returnsTrue() {
    final ExecutionControlCommand command = new RestartCommand("exec-1", "new-1");
    assertThat(processor.canProcess(command)).isTrue();
  }

  @Test
  void canProcess_otherCommand_returnsFalse() {
    final ExecutionControlCommand command = new PauseWorkflowCommand("exec-1");
    assertThat(processor.canProcess(command)).isFalse();
  }

  @Test
  void process_executionFound_detachesExecutionAndReportsSuccessImmediately() {
    // Given
    final String executionId = "exec-restart";
    final String newExecutionId = "new-exec-restart";
    final String sessionId = "session-1";
    final String workflowId = "workflow-1";
    final Map<String, Object> payload = Map.of("key", "value");
    final Sinks.One<Void> safeStopSink = Sinks.one();
    final RestartCommand command = new RestartCommand(executionId, newExecutionId);

    when(registry.findByExecutionId(executionId)).thenReturn(Optional.of(executionControl));
    when(executionControl.executionId()).thenReturn(executionId);
    when(executionControl.sessionId()).thenReturn(sessionId);
    when(executionControl.workflowId()).thenReturn(workflowId);
    when(executionControl.prepared()).thenReturn(preparedWorkflow);
    when(executionControl.payload()).thenReturn(payload);
    when(executionControl.safeStopSink()).thenReturn(safeStopSink);
    // Never completes — proves the processor does not wait for it.
    when(orchestrator.execute(
            eq(sessionId), eq(workflowId), eq(newExecutionId), eq(preparedWorkflow), eq(payload)))
        .thenReturn(Mono.never());

    // When
    final var result = processor.process(command);

    // Then — process() completes without waiting for orchestrator.execute()
    StepVerifier.create(result).verifyComplete();
    verify(registry).unregister(executionId);
    verify(completionSink).completeRestartSuccess(newExecutionId);
  }

  @Test
  void process_executionNotFound_reportsFailureAndCompletesEmpty() {
    // Given
    final String executionId = "exec-not-found";
    final String newExecutionId = "new-exec-not-found";
    final RestartCommand command = new RestartCommand(executionId, newExecutionId);
    when(registry.findByExecutionId(executionId)).thenReturn(Optional.empty());

    // When
    final var result = processor.process(command);

    // Then
    StepVerifier.create(result).verifyComplete();
    verify(completionSink)
        .completeRestartFailure(eq(newExecutionId), any(IllegalArgumentException.class));
    verify(orchestrator, never()).execute(any(), any(), any(), any(), any());
  }

  @Test
  void process_orchestratorFailsAsynchronously_doesNotReportFailureViaCompletionSink() {
    // Given: detachment means an async orchestrator failure is not observable by process()'s
    // own Mono — it surfaces via normal task-tracker/watchExecution channels instead, same as
    // any other execution failure. completeRestartSuccess was already called at subscribe time.
    final String executionId = "exec-orch-fail";
    final String newExecutionId = "new-exec-orch-fail";
    final String sessionId = "session-1";
    final String workflowId = "workflow-1";
    final Sinks.One<Void> safeStopSink = Sinks.one();
    final RestartCommand command = new RestartCommand(executionId, newExecutionId);

    when(registry.findByExecutionId(executionId)).thenReturn(Optional.of(executionControl));
    when(executionControl.executionId()).thenReturn(executionId);
    when(executionControl.sessionId()).thenReturn(sessionId);
    when(executionControl.workflowId()).thenReturn(workflowId);
    when(executionControl.prepared()).thenReturn(preparedWorkflow);
    when(executionControl.payload()).thenReturn(Map.of());
    when(executionControl.safeStopSink()).thenReturn(safeStopSink);
    when(orchestrator.execute(
            eq(sessionId), eq(workflowId), eq(newExecutionId), eq(preparedWorkflow), eq(Map.of())))
        .thenReturn(Mono.error(new RuntimeException("Orchestrator failure")));

    // When
    final var result = processor.process(command);

    // Then
    StepVerifier.create(result).verifyComplete();
    verify(completionSink, timeout(1000)).completeRestartSuccess(newExecutionId);
    verify(completionSink, never()).completeRestartFailure(eq(newExecutionId), any());
  }

  @Test
  void process_safeStopSinkEmitFails_reportsFailure() {
    // Given: safeStopSink already completed, so emitting the stop signal fails synchronously
    // inside the defer block — proving that unregister never happens when the sink emit fails,
    // ensuring registry remains consistent on error.
    final String executionId = "exec-sink-emit-fail";
    final String newExecutionId = "new-exec-sink-emit-fail";
    final Sinks.One<Void> failingSink = Sinks.one();
    failingSink.emitEmpty(Sinks.EmitFailureHandler.FAIL_FAST);
    final RestartCommand command = new RestartCommand(executionId, newExecutionId);

    when(registry.findByExecutionId(executionId)).thenReturn(Optional.of(executionControl));
    when(executionControl.safeStopSink()).thenReturn(failingSink);

    // When
    final var result = processor.process(command);

    // Then
    StepVerifier.create(result).verifyComplete();
    verify(registry, never()).unregister(executionId);
    verify(completionSink).completeRestartFailure(eq(newExecutionId), any(RuntimeException.class));
    verify(completionSink, never()).completeRestartSuccess(newExecutionId);
    verify(orchestrator, never()).execute(any(), any(), any(), any(), any());
  }

  @Test
  void process_duplicateRestartWhileFirstInProgress_rejectsSecondRestart() {
    // Given
    final String executionId = "exec-duplicate";
    final String newExecutionId1 = "new-exec-1";
    final String newExecutionId2 = "new-exec-2";
    final String sessionId = "session-1";
    final String workflowId = "workflow-1";
    final Sinks.One<Void> safeStopSink = Sinks.one();
    final RestartCommand command1 = new RestartCommand(executionId, newExecutionId1);
    final RestartCommand command2 = new RestartCommand(executionId, newExecutionId2);

    when(registry.findByExecutionId(executionId)).thenReturn(Optional.of(executionControl));
    when(executionControl.executionId()).thenReturn(executionId);
    when(executionControl.sessionId()).thenReturn(sessionId);
    when(executionControl.workflowId()).thenReturn(workflowId);
    when(executionControl.prepared()).thenReturn(preparedWorkflow);
    when(executionControl.payload()).thenReturn(Map.of());
    when(executionControl.safeStopSink()).thenReturn(safeStopSink);
    // First restart never completes, keeping the lock held
    when(orchestrator.execute(
            eq(sessionId), eq(workflowId), eq(newExecutionId1), eq(preparedWorkflow), eq(Map.of())))
        .thenReturn(Mono.never());

    // When — first restart starts
    processor.process(command1).subscribe();
    // Second restart attempt while first is still in progress
    final var result2 = processor.process(command2);

    // Then — second restart is rejected immediately
    StepVerifier.create(result2).verifyComplete();
    verify(completionSink)
        .completeRestartFailure(eq(newExecutionId2), any(IllegalStateException.class));
    verify(completionSink).completeRestartSuccess(newExecutionId1);
  }

  @Test
  void getPriority_returnsCorrectValue() {
    assertThat(processor.getPriority()).isEqualTo(20);
  }
}
