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
package com.infenia.yukta.service.control.directive;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infenia.yukta.plugin.control.ControlSignalProcessor;
import com.infenia.yukta.plugin.control.ExecutionControlCommand;
import com.infenia.yukta.plugin.control.WorkflowDirective;
import com.infenia.yukta.service.control.ControlBusService;
import com.infenia.yukta.service.control.ExecutionControl;
import com.infenia.yukta.service.control.store.ExecutionControlRegistry;
import com.infenia.yukta.service.control.store.InMemoryExecutionControlStore;
import com.infenia.yukta.service.orchestrator.WorkflowOrchestrator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

class DirectiveDispatcherTest {

  private ExecutionControlRegistry registry;
  private WorkflowOrchestrator orchestrator;
  private ControlBusService controlBusService;
  private ControlSignalProcessor processor;
  private DirectiveDispatcher dispatcher;

  private ExecutionControl createControl(
      final String sessionId, final String workflowId, final String executionId) {
    return new ExecutionControl(
        sessionId,
        workflowId,
        executionId,
        null,
        Map.of(),
        Sinks.one(),
        Sinks.one(),
        null,
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of());
  }

  @BeforeEach
  void setUp() {
    registry = new ExecutionControlRegistry(new InMemoryExecutionControlStore());
    orchestrator = mock(WorkflowOrchestrator.class);
    controlBusService = mock(ControlBusService.class);
    processor = mock(ControlSignalProcessor.class);

    when(controlBusService.getControlStream()).thenReturn(Flux.never());
    when(orchestrator.execute(any(), any(), any(), any(), any())).thenReturn(Mono.empty());
    when(orchestrator.restartFromNode(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(Mono.empty());

    dispatcher =
        new DirectiveDispatcher(
            List.of(processor),
            registry,
            orchestrator,
            new com.infenia.yukta.service.store.InMemoryNodeCheckpointStore(),
            controlBusService);
  }

  @Test
  void testDispatchStopCommand() {
    final String executionId = "exec-1";

    final ExecutionControl control = createControl("session-1", "workflow-1", executionId);
    registry.register(control);

    final ExecutionControlCommand command =
        new ExecutionControlCommand.StopNodeCommand(executionId, "node-1", false, "User requested");

    when(processor.canProcess(command)).thenReturn(true);
    when(processor.getPriority()).thenReturn(0);
    when(processor.process(command))
        .thenReturn(Mono.just(new WorkflowDirective.Stop("User requested")));

    StepVerifier.create(dispatcher.dispatch(command)).verifyComplete();

    verify(processor, times(1)).process(command);
  }

  @Test
  void testDispatchNoActiveExecution() {
    final ExecutionControlCommand command =
        new ExecutionControlCommand.StopNodeCommand("exec-no-exist", "node-1", false, null);

    when(processor.canProcess(command)).thenReturn(true);
    when(processor.getPriority()).thenReturn(0);

    StepVerifier.create(dispatcher.dispatch(command)).verifyComplete();

    verify(processor, times(0)).process(command);
  }

  @Test
  void testDispatchRestartCommand() {
    final String executionId = "exec-1";

    final ExecutionControl control = createControl("session-1", "workflow-1", executionId);
    registry.register(control);

    final ExecutionControlCommand command = new ExecutionControlCommand.RestartCommand(executionId);

    when(processor.canProcess(command)).thenReturn(true);
    when(processor.getPriority()).thenReturn(0);
    when(processor.process(command)).thenReturn(Mono.just(new WorkflowDirective.Restart()));

    StepVerifier.create(dispatcher.dispatch(command)).verifyComplete();

    verify(processor, times(1)).process(command);
  }

  @Test
  void testDispatchProcessorNotFound() {
    final String executionId = "exec-1";

    final ExecutionControl control = createControl("session-1", "workflow-1", executionId);
    registry.register(control);

    final ExecutionControlCommand command =
        new ExecutionControlCommand.StopNodeCommand(executionId, "node-1", false, null);

    when(processor.canProcess(command)).thenReturn(false);

    StepVerifier.create(dispatcher.dispatch(command))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void testDispatchMultipleProcessorsPriority() {
    final String executionId = "exec-1";

    final ExecutionControl control = createControl("session-1", "workflow-1", executionId);
    registry.register(control);

    final ControlSignalProcessor lowPriorityProcessor = mock(ControlSignalProcessor.class);
    final ControlSignalProcessor highPriorityProcessor = mock(ControlSignalProcessor.class);

    when(lowPriorityProcessor.canProcess(any())).thenReturn(true);
    when(lowPriorityProcessor.getPriority()).thenReturn(0);

    when(highPriorityProcessor.canProcess(any())).thenReturn(true);
    when(highPriorityProcessor.getPriority()).thenReturn(10);

    when(highPriorityProcessor.process(any()))
        .thenReturn(Mono.just(new WorkflowDirective.Restart()));

    final DirectiveDispatcher multiDispatcher =
        new DirectiveDispatcher(
            List.of(lowPriorityProcessor, highPriorityProcessor),
            registry,
            orchestrator,
            new com.infenia.yukta.service.store.InMemoryNodeCheckpointStore(),
            controlBusService);

    final ExecutionControlCommand command = new ExecutionControlCommand.RestartCommand(executionId);

    StepVerifier.create(multiDispatcher.dispatch(command)).verifyComplete();

    verify(highPriorityProcessor, times(1)).process(command);
    verify(lowPriorityProcessor, times(0)).process(command);
  }
}
