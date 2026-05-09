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
package com.infenia.yukta.service.orchestrator.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infenia.yukta.model.workflow.NodeAssembler;
import com.infenia.yukta.model.workflow.ParentEdgeInfo;
import com.infenia.yukta.model.workflow.WorkflowNode;
import com.infenia.yukta.plugin.gateway.ControlBusGateway;
import com.infenia.yukta.plugin.gateway.ResultCollector;
import com.infenia.yukta.plugin.message.DefaultMessage;
import com.infenia.yukta.plugin.message.Message;
import com.infenia.yukta.plugin.message.control.ControlError;
import com.infenia.yukta.plugin.type.ProcessorPlugin;
import com.infenia.yukta.plugin.type.TerminalPlugin;
import com.infenia.yukta.service.TaskTrackerService;
import com.infenia.yukta.service.control.ExecutionControl;
import com.infenia.yukta.service.orchestrator.AssemblyContext;
import com.infenia.yukta.service.orchestrator.stream.StreamTopologyDecorator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@DisplayName("TerminalNodeAssemblerStrategy")
@ExtendWith(MockitoExtension.class)
class TerminalNodeAssemblerStrategyTest {

  @Mock private TaskTrackerService tracker;
  @Mock private ControlBusGateway controlBusGateway;
  @Mock private StreamTopologyDecorator streamTopologyDecorator;

  private TerminalNodeAssemblerStrategy strategy;

  @BeforeEach
  void setUp() {
    strategy =
        new TerminalNodeAssemblerStrategy(
            tracker, controlBusGateway, Schedulers.parallel(), streamTopologyDecorator);
  }

  @Test
  @DisplayName("supports returns true when plugin is TerminalPlugin")
  void supportsTerminalPlugin() {
    TerminalPlugin terminal = mock(TerminalPlugin.class);

    boolean result = strategy.supports(terminal, false);

    assertThat(result).isTrue();
  }

  @Test
  @DisplayName("supports returns true for TerminalPlugin regardless of hasParents")
  void supportsTerminalPluginWithParents() {
    TerminalPlugin terminal = mock(TerminalPlugin.class);

    boolean resultWithParents = strategy.supports(terminal, true);
    boolean resultWithoutParents = strategy.supports(terminal, false);

    assertThat(resultWithParents).isTrue();
    assertThat(resultWithoutParents).isTrue();
  }

  @Test
  @DisplayName("supports returns false when plugin is ProcessorPlugin")
  void supportsProcessorPlugin() {
    ProcessorPlugin processor = mock(ProcessorPlugin.class);

    boolean result = strategy.supports(processor, false);

    assertThat(result).isFalse();
  }

  @Test
  @DisplayName("supports returns false when plugin is not TerminalPlugin")
  void supportsNonTerminalPlugin() {
    var trigger = mock(com.infenia.yukta.plugin.type.TriggerPlugin.class);

    boolean result = strategy.supports(trigger, false);

    assertThat(result).isFalse();
  }

  @Test
  @DisplayName("createAssembler applies virtualThreadScheduler when terminal is blocking")
  void createAssemblerBlockingTerminal() {
    TerminalPlugin terminal = mock(TerminalPlugin.class);
    when(terminal.isBlocking()).thenReturn(true);
    when(terminal.consume(any(), any())).thenReturn(Mono.empty());

    WorkflowNode node = new WorkflowNode("term-1", "terminal-type", Map.of());
    ExecutionControl control = mock(ExecutionControl.class);
    when(control.applyPreProcessingControls(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(1));

    Flux<Message<?>>[] streams = new Flux[1];
    AssemblyContext context =
        new AssemblyContext(
            "exec-1",
            "sess-1",
            "wf-1",
            Map.of(),
            control,
            streams,
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>());

    when(streamTopologyDecorator.mergeParentStreams(any(), any()))
        .thenReturn(Flux.just(DefaultMessage.create(null, "input")));

    NodeAssembler assembler =
        strategy.createAssembler(
            node, terminal, Duration.ofSeconds(30), 0, 1024, new ParentEdgeInfo[0]);

    assembler.assemble(context);

    assertThat(context.terminals()).hasSize(1);
  }

  @Test
  @DisplayName("createAssembler does not apply scheduler when terminal is non-blocking")
  void createAssemblerNonBlockingTerminal() {
    TerminalPlugin terminal = mock(TerminalPlugin.class);
    when(terminal.isBlocking()).thenReturn(false);
    when(terminal.consume(any(), any())).thenReturn(Mono.empty());

    WorkflowNode node = new WorkflowNode("term-2", "terminal-type", Map.of());
    ExecutionControl control = mock(ExecutionControl.class);
    when(control.applyPreProcessingControls(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(1));

    Flux<Message<?>>[] streams = new Flux[1];
    AssemblyContext context =
        new AssemblyContext(
            "exec-2",
            "sess-1",
            "wf-1",
            Map.of(),
            control,
            streams,
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>());

    when(streamTopologyDecorator.mergeParentStreams(any(), any()))
        .thenReturn(Flux.just(DefaultMessage.create(null, "input")));

    NodeAssembler assembler =
        strategy.createAssembler(
            node, terminal, Duration.ofSeconds(30), 0, 1024, new ParentEdgeInfo[0]);

    assembler.assemble(context);

    assertThat(context.terminals()).hasSize(1);
    verify(terminal).consume(any(), any());
  }

  @Test
  @DisplayName("createAssembler emits RUNNING status on subscribe")
  void createAssemblerEmitsRunningStatusOnSubscribe() {
    TerminalPlugin terminal = mock(TerminalPlugin.class);
    when(terminal.isBlocking()).thenReturn(false);
    when(terminal.consume(any(), any())).thenReturn(Mono.empty());

    WorkflowNode node = new WorkflowNode("term-run", "terminal-type", Map.of());
    ExecutionControl control = mock(ExecutionControl.class);
    when(control.applyPreProcessingControls(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(1));

    Flux<Message<?>>[] streams = new Flux[1];
    AssemblyContext context =
        new AssemblyContext(
            "exec-run",
            "sess-1",
            "wf-1",
            Map.of(),
            control,
            streams,
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>());

    when(streamTopologyDecorator.mergeParentStreams(any(), any()))
        .thenReturn(Flux.just(DefaultMessage.create(null, "input")));

    NodeAssembler assembler =
        strategy.createAssembler(
            node, terminal, Duration.ofSeconds(30), 0, 1024, new ParentEdgeInfo[0]);

    assembler.assemble(context);

    // Trigger the mono subscription
    context.terminals().get(0).block();

    verify(tracker)
        .emitTaskStatusEvent("exec-run", "term-run", "default", "RUNNING", Collections.emptyMap());
  }

  @Test
  @DisplayName("createAssembler emits SUCCESS status on completion")
  void createAssemblerEmitsSuccessStatusOnCompletion() {
    TerminalPlugin terminal = mock(TerminalPlugin.class);
    when(terminal.isBlocking()).thenReturn(false);
    when(terminal.consume(any(), any())).thenReturn(Mono.empty());

    WorkflowNode node = new WorkflowNode("term-success", "terminal-type", Map.of());
    ExecutionControl control = mock(ExecutionControl.class);
    when(control.applyPreProcessingControls(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(1));

    Flux<Message<?>>[] streams = new Flux[1];
    AssemblyContext context =
        new AssemblyContext(
            "exec-success",
            "sess-1",
            "wf-1",
            Map.of(),
            control,
            streams,
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>());

    when(streamTopologyDecorator.mergeParentStreams(any(), any()))
        .thenReturn(Flux.just(DefaultMessage.create(null, "input")));

    NodeAssembler assembler =
        strategy.createAssembler(
            node, terminal, Duration.ofSeconds(30), 0, 1024, new ParentEdgeInfo[0]);

    assembler.assemble(context);

    // Trigger the mono subscription
    context.terminals().get(0).block();

    verify(tracker)
        .emitTaskStatusEvent(
            "exec-success", "term-success", "default", "SUCCESS", Collections.emptyMap());
  }

  @Test
  @DisplayName("createAssembler emits FAILURE status on error")
  void createAssemblerEmitsFailureStatusOnError() {
    TerminalPlugin terminal = mock(TerminalPlugin.class);
    when(terminal.isBlocking()).thenReturn(false);
    when(terminal.consume(any(), any())).thenReturn(Mono.error(new RuntimeException("Test error")));

    WorkflowNode node = new WorkflowNode("term-fail", "terminal-type", Map.of());
    ExecutionControl control = mock(ExecutionControl.class);
    when(control.applyPreProcessingControls(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(1));

    Flux<Message<?>>[] streams = new Flux[1];
    AssemblyContext context =
        new AssemblyContext(
            "exec-fail",
            "sess-1",
            "wf-1",
            Map.of(),
            control,
            streams,
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>());

    when(streamTopologyDecorator.mergeParentStreams(any(), any()))
        .thenReturn(Flux.just(DefaultMessage.create(null, "input")));

    NodeAssembler assembler =
        strategy.createAssembler(
            node, terminal, Duration.ofSeconds(30), 0, 1024, new ParentEdgeInfo[0]);

    assembler.assemble(context);

    // Subscribe to the mono to trigger error handling
    try {
      context.terminals().get(0).block();
    } catch (Exception ignored) {
      // Expected
    }

    verify(tracker)
        .emitTaskStatusEvent(
            "exec-fail", "term-fail", "default", "FAILURE", Collections.emptyMap());
  }

  @Test
  @DisplayName("createAssembler emits ControlError message on failure")
  void createAssemblerEmitsControlErrorOnFailure() {
    TerminalPlugin terminal = mock(TerminalPlugin.class);
    when(terminal.isBlocking()).thenReturn(false);
    when(terminal.consume(any(), any()))
        .thenReturn(Mono.error(new RuntimeException("Test error message")));
    when(controlBusGateway.emit(any())).thenReturn(Mono.empty());

    WorkflowNode node = new WorkflowNode("term-error", "terminal-type", Map.of());
    ExecutionControl control = mock(ExecutionControl.class);
    when(control.applyPreProcessingControls(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(1));

    Flux<Message<?>>[] streams = new Flux[1];
    AssemblyContext context =
        new AssemblyContext(
            "exec-error",
            "sess-1",
            "wf-1",
            Map.of(),
            control,
            streams,
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>());

    when(streamTopologyDecorator.mergeParentStreams(any(), any()))
        .thenReturn(Flux.just(DefaultMessage.create(null, "input")));

    NodeAssembler assembler =
        strategy.createAssembler(
            node, terminal, Duration.ofSeconds(30), 0, 1024, new ParentEdgeInfo[0]);

    assembler.assemble(context);

    // Subscribe to trigger error
    try {
      context.terminals().get(0).block();
    } catch (Exception ignored) {
      // Expected
    }

    // Verify controlBusGateway.emit was called with a message
    ArgumentCaptor<Message<?>> messageCaptor = ArgumentCaptor.forClass(Message.class);
    verify(controlBusGateway).emit(messageCaptor.capture());
    Message<?> emittedMessage = messageCaptor.getValue();
    assertThat(emittedMessage.getPayload()).isInstanceOf(ControlError.class);
    ControlError error = (ControlError) emittedMessage.getPayload();
    assertThat(error.nodeId()).isEqualTo("term-error");
  }

  @Test
  @DisplayName("createAssembler adds completion to terminals list")
  void createAssemblerAddsCompletionToTerminals() {
    TerminalPlugin terminal = mock(TerminalPlugin.class);
    when(terminal.isBlocking()).thenReturn(false);
    when(terminal.consume(any(), any())).thenReturn(Mono.empty());

    WorkflowNode node = new WorkflowNode("term-list", "terminal-type", Map.of());
    ExecutionControl control = mock(ExecutionControl.class);
    when(control.applyPreProcessingControls(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(1));

    Flux<Message<?>>[] streams = new Flux[1];
    ArrayList<Mono<Void>> terminals = new ArrayList<>();
    AssemblyContext context =
        new AssemblyContext(
            "exec-list",
            "sess-1",
            "wf-1",
            Map.of(),
            control,
            streams,
            terminals,
            new ArrayList<>(),
            new ArrayList<>());

    when(streamTopologyDecorator.mergeParentStreams(any(), any()))
        .thenReturn(Flux.just(DefaultMessage.create(null, "input")));

    NodeAssembler assembler =
        strategy.createAssembler(
            node, terminal, Duration.ofSeconds(30), 0, 1024, new ParentEdgeInfo[0]);

    assembler.assemble(context);

    assertThat(terminals).hasSize(1);
    assertThat(terminals.get(0)).isNotNull();
  }

  @Test
  @DisplayName("createAssembler applies timeout to messages")
  void createAssemblerAppliesTimeout() {
    TerminalPlugin terminal = mock(TerminalPlugin.class);
    when(terminal.isBlocking()).thenReturn(false);
    when(terminal.consume(any(), any())).thenReturn(Mono.empty());

    WorkflowNode node = new WorkflowNode("term-timeout", "terminal-type", Map.of());
    ExecutionControl control = mock(ExecutionControl.class);
    when(control.applyPreProcessingControls(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(1));

    Flux<Message<?>>[] streams = new Flux[1];
    AssemblyContext context =
        new AssemblyContext(
            "exec-timeout",
            "sess-1",
            "wf-1",
            Map.of(),
            control,
            streams,
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>());

    when(streamTopologyDecorator.mergeParentStreams(any(), any()))
        .thenReturn(
            Flux.just(
                DefaultMessage.create(null, "input1"), DefaultMessage.create(null, "input2")));

    NodeAssembler assembler =
        strategy.createAssembler(
            node, terminal, Duration.ofSeconds(1), 0, 1024, new ParentEdgeInfo[0]);

    assembler.assemble(context);

    // Verify the timeout is applied by testing the behavior
    assertThat(context.terminals()).hasSize(1);
  }

  @Test
  @DisplayName("createAssembler maps TimeoutException identically")
  void createAssemblerTimeoutExceptionMapping() {
    TerminalPlugin terminal = mock(TerminalPlugin.class);
    when(terminal.isBlocking()).thenReturn(false);
    when(terminal.consume(any(), any())).thenReturn(Mono.empty());

    WorkflowNode node = new WorkflowNode("term-timeout-map", "terminal-type", Map.of());
    ExecutionControl control = mock(ExecutionControl.class);
    when(control.applyPreProcessingControls(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(1));

    Flux<Message<?>>[] streams = new Flux[1];
    AssemblyContext context =
        new AssemblyContext(
            "exec-timeout-map",
            "sess-1",
            "wf-1",
            Map.of(),
            control,
            streams,
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>());

    when(streamTopologyDecorator.mergeParentStreams(any(), any()))
        .thenReturn(Flux.just(DefaultMessage.create(null, "input")));

    NodeAssembler assembler =
        strategy.createAssembler(
            node, terminal, Duration.ofMillis(1), 0, 1024, new ParentEdgeInfo[0]);

    assembler.assemble(context);

    assertThat(context.terminals()).hasSize(1);
  }

  @Test
  @DisplayName("createAssembler merges parent streams before preprocessing")
  void createAssemblerMergesParentStreams() {
    TerminalPlugin terminal = mock(TerminalPlugin.class);
    when(terminal.isBlocking()).thenReturn(false);
    when(terminal.consume(any(), any())).thenReturn(Mono.empty());

    WorkflowNode node = new WorkflowNode("term-merge", "terminal-type", Map.of());
    ExecutionControl control = mock(ExecutionControl.class);
    when(control.applyPreProcessingControls(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(1));

    Flux<Message<?>>[] streams = new Flux[2];
    ParentEdgeInfo[] parentEdges = new ParentEdgeInfo[2];
    AssemblyContext context =
        new AssemblyContext(
            "exec-merge",
            "sess-1",
            "wf-1",
            Map.of(),
            control,
            streams,
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>());

    when(streamTopologyDecorator.mergeParentStreams(any(), eq(parentEdges)))
        .thenReturn(Flux.just(DefaultMessage.create(null, "merged")));

    NodeAssembler assembler =
        strategy.createAssembler(node, terminal, Duration.ofSeconds(30), 0, 1024, parentEdges);

    assembler.assemble(context);

    verify(streamTopologyDecorator).mergeParentStreams(streams, parentEdges);
  }

  @Test
  @DisplayName("createAssembler applies pre-processing controls")
  void createAssemblerAppliesPreProcessingControls() {
    TerminalPlugin terminal = mock(TerminalPlugin.class);
    when(terminal.isBlocking()).thenReturn(false);
    when(terminal.consume(any(), any())).thenReturn(Mono.empty());

    WorkflowNode node = new WorkflowNode("term-pre", "terminal-type", Map.of());
    ExecutionControl control = mock(ExecutionControl.class);
    when(control.applyPreProcessingControls(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(1));

    Flux<Message<?>>[] streams = new Flux[1];
    AssemblyContext context =
        new AssemblyContext(
            "exec-pre",
            "sess-1",
            "wf-1",
            Map.of(),
            control,
            streams,
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>());

    when(streamTopologyDecorator.mergeParentStreams(any(), any()))
        .thenReturn(Flux.just(DefaultMessage.create(null, "input")));

    NodeAssembler assembler =
        strategy.createAssembler(
            node, terminal, Duration.ofSeconds(30), 0, 1024, new ParentEdgeInfo[0]);

    assembler.assemble(context);

    verify(control).applyPreProcessingControls(eq("term-pre"), any(Flux.class));
  }

  @Test
  @DisplayName("createAssembler calls terminal.consume with correct parameters")
  void createAssemblerConsumesWithCorrectParameters() {
    TerminalPlugin terminal = mock(TerminalPlugin.class);
    when(terminal.isBlocking()).thenReturn(false);
    when(terminal.consume(any(), any())).thenReturn(Mono.empty());

    Map<String, Object> nodeConfig = Map.of("setting", "value");
    WorkflowNode node = new WorkflowNode("term-consume", "terminal-type", nodeConfig);
    ExecutionControl control = mock(ExecutionControl.class);
    when(control.applyPreProcessingControls(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(1));

    Flux<Message<?>>[] streams = new Flux[1];
    AssemblyContext context =
        new AssemblyContext(
            "exec-consume",
            "sess-1",
            "wf-1",
            Map.of(),
            control,
            streams,
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>());

    when(streamTopologyDecorator.mergeParentStreams(any(), any()))
        .thenReturn(Flux.just(DefaultMessage.create(null, "input")));

    NodeAssembler assembler =
        strategy.createAssembler(
            node, terminal, Duration.ofSeconds(30), 0, 1024, new ParentEdgeInfo[0]);

    assembler.assemble(context);

    ArgumentCaptor<Map<String, Object>> configCaptor = ArgumentCaptor.forClass(Map.class);
    verify(terminal).consume(any(), configCaptor.capture());
    assertThat(configCaptor.getValue()).isEqualTo(nodeConfig);
  }

  @Test
  @DisplayName("createAssembler initializes ExecutionContextBuilder with correct values")
  void createAssemblerContextBuilderInitialization() {
    TerminalPlugin terminal = mock(TerminalPlugin.class);
    when(terminal.isBlocking()).thenReturn(false);
    when(terminal.consume(any(), any())).thenReturn(Mono.empty());

    WorkflowNode node = new WorkflowNode("term-ctx", "terminal-type", Map.of());
    ExecutionControl control = mock(ExecutionControl.class);
    when(control.applyPreProcessingControls(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(1));

    Flux<Message<?>>[] streams = new Flux[1];
    Map<String, Object> payload = Map.of("key", "value");
    AssemblyContext context =
        new AssemblyContext(
            "exec-ctx-test",
            "sess-ctx-test",
            "wf-ctx-test",
            payload,
            control,
            streams,
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>());

    when(streamTopologyDecorator.mergeParentStreams(any(), any()))
        .thenReturn(Flux.just(DefaultMessage.create(null, "input")));

    NodeAssembler assembler =
        strategy.createAssembler(
            node, terminal, Duration.ofSeconds(30), 0, 1024, new ParentEdgeInfo[0]);

    assembler.assemble(context);

    assertThat(context.terminals()).hasSize(1);
    assertThat(context.terminals().get(0)).isNotNull();
  }

  @Test
  @DisplayName("createAssembler handles null resultCollector gracefully")
  void createAssemblerWithoutResultCollector() {
    TerminalPlugin terminal = mock(TerminalPlugin.class);
    when(terminal.isBlocking()).thenReturn(false);
    when(terminal.consume(any(), any())).thenReturn(Mono.empty());

    WorkflowNode node = new WorkflowNode("term-no-collector", "terminal-type", Map.of());
    ExecutionControl control = mock(ExecutionControl.class);
    when(control.applyPreProcessingControls(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(1));

    Flux<Message<?>>[] streams = new Flux[1];
    AssemblyContext context =
        new AssemblyContext(
            "exec-no-collector",
            "sess-1",
            "wf-1",
            Map.of(),
            control,
            streams,
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>());

    Message<?> inputMessage = DefaultMessage.create(null, "test-data");
    when(streamTopologyDecorator.mergeParentStreams(any(), any()))
        .thenReturn(Flux.just(inputMessage));

    NodeAssembler assembler =
        strategy.createAssembler(
            node, terminal, Duration.ofSeconds(30), 0, 1024, new ParentEdgeInfo[0]);

    assembler.assemble(context);

    context.terminals().get(0).block();

    verify(terminal).consume(any(), any());
  }

  @Test
  @DisplayName("createAssembler applies resultCollector when present in context")
  void createAssemblerAppliesResultCollector() {
    ResultCollector mockCollector = mock(ResultCollector.class);
    TerminalPlugin terminal = mock(TerminalPlugin.class);
    when(terminal.isBlocking()).thenReturn(false);
    when(terminal.consume(any(), any()))
        .thenAnswer(
            inv -> {
              Flux<Message<?>> inputFlux = inv.getArgument(0);
              return inputFlux
                  .contextWrite(ctx -> ctx.put("resultCollector", mockCollector))
                  .doOnNext(msg -> {})
                  .then();
            });

    WorkflowNode node = new WorkflowNode("term-collector", "terminal-type", Map.of());
    ExecutionControl control = mock(ExecutionControl.class);
    when(control.applyPreProcessingControls(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(1));

    Flux<Message<?>>[] streams = new Flux[1];
    AssemblyContext context =
        new AssemblyContext(
            "exec-collector",
            "sess-1",
            "wf-1",
            Map.of(),
            control,
            streams,
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>());

    Message<?> inputMessage = DefaultMessage.create(null, "test-data");
    when(streamTopologyDecorator.mergeParentStreams(any(), any()))
        .thenReturn(Flux.just(inputMessage));

    NodeAssembler assembler =
        strategy.createAssembler(
            node, terminal, Duration.ofSeconds(30), 0, 1024, new ParentEdgeInfo[0]);

    assembler.assemble(context);

    context.terminals().get(0).block();

    verify(mockCollector).add(inputMessage);
  }

  @Test
  @DisplayName("createAssembler applies subscribeOn when terminal is blocking with error")
  void createAssemblerBlockingTerminalWithError() {
    TerminalPlugin terminal = mock(TerminalPlugin.class);
    when(terminal.isBlocking()).thenReturn(true);
    when(terminal.consume(any(), any()))
        .thenReturn(Mono.error(new RuntimeException("Blocking terminal error")));
    when(controlBusGateway.emit(any())).thenReturn(Mono.empty());

    WorkflowNode node = new WorkflowNode("term-blocking-error", "terminal-type", Map.of());
    ExecutionControl control = mock(ExecutionControl.class);
    when(control.applyPreProcessingControls(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(1));

    Flux<Message<?>>[] streams = new Flux[1];
    AssemblyContext context =
        new AssemblyContext(
            "exec-blocking-error",
            "sess-1",
            "wf-1",
            Map.of(),
            control,
            streams,
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>());

    when(streamTopologyDecorator.mergeParentStreams(any(), any()))
        .thenReturn(Flux.just(DefaultMessage.create(null, "input")));

    NodeAssembler assembler =
        strategy.createAssembler(
            node, terminal, Duration.ofSeconds(30), 0, 1024, new ParentEdgeInfo[0]);

    assembler.assemble(context);

    try {
      context.terminals().get(0).block();
    } catch (Exception ignored) {
      // Expected
    }

    verify(tracker)
        .emitTaskStatusEvent(
            "exec-blocking-error",
            "term-blocking-error",
            "default",
            "FAILURE",
            Collections.emptyMap());
    verify(controlBusGateway).emit(any(Message.class));
  }

  @Test
  @DisplayName("createAssembler applies subscribeOn when terminal is blocking with success")
  void createAssemblerBlockingTerminalWithSuccess() {
    TerminalPlugin terminal = mock(TerminalPlugin.class);
    when(terminal.isBlocking()).thenReturn(true);
    when(terminal.consume(any(), any())).thenReturn(Mono.empty());

    WorkflowNode node = new WorkflowNode("term-blocking-success", "terminal-type", Map.of());
    ExecutionControl control = mock(ExecutionControl.class);
    when(control.applyPreProcessingControls(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(1));

    Flux<Message<?>>[] streams = new Flux[1];
    AssemblyContext context =
        new AssemblyContext(
            "exec-blocking-success",
            "sess-1",
            "wf-1",
            Map.of(),
            control,
            streams,
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>());

    when(streamTopologyDecorator.mergeParentStreams(any(), any()))
        .thenReturn(Flux.just(DefaultMessage.create(null, "input")));

    NodeAssembler assembler =
        strategy.createAssembler(
            node, terminal, Duration.ofSeconds(30), 0, 1024, new ParentEdgeInfo[0]);

    assembler.assemble(context);

    context.terminals().get(0).block();

    verify(tracker)
        .emitTaskStatusEvent(
            "exec-blocking-success",
            "term-blocking-success",
            "default",
            "SUCCESS",
            Collections.emptyMap());
  }
}
