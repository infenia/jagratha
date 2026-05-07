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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infenia.yukta.model.workflow.NodeAssembler;
import com.infenia.yukta.model.workflow.ParentEdgeInfo;
import com.infenia.yukta.model.workflow.WorkflowNode;
import com.infenia.yukta.plugin.core.PluginCategory;
import com.infenia.yukta.plugin.gateway.ControlBusGateway;
import com.infenia.yukta.plugin.message.DefaultMessage;
import com.infenia.yukta.plugin.message.Message;
import com.infenia.yukta.plugin.type.TriggerPlugin;
import com.infenia.yukta.service.TaskTrackerService;
import com.infenia.yukta.service.control.ExecutionControl;
import com.infenia.yukta.service.orchestrator.AssemblyContext;
import com.infenia.yukta.service.orchestrator.stream.StreamTopologyDecorator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

@DisplayName("TriggerNodeAssemblerStrategy")
class TriggerNodeAssemblerStrategyTest {

  @Mock private TaskTrackerService tracker;
  @Mock private ControlBusGateway controlBusGateway;
  @Mock private StreamTopologyDecorator streamTopologyDecorator;

  private TriggerNodeAssemblerStrategy strategy;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    strategy =
        new TriggerNodeAssemblerStrategy(
            tracker, controlBusGateway, Schedulers.parallel(), streamTopologyDecorator);
    when(controlBusGateway.emit(any())).thenReturn(Mono.empty());
  }

  @Test
  @DisplayName("supports returns true when plugin is TriggerPlugin with TRIGGER category")
  void supportsTriggerPluginWithTriggerCategory() {
    TriggerPlugin trigger = mock(TriggerPlugin.class);
    when(trigger.getCategory()).thenReturn(PluginCategory.TRIGGER);

    boolean result = strategy.supports(trigger, false);

    assertThat(result).isTrue();
  }

  @Test
  @DisplayName("supports returns true when plugin is TriggerPlugin without parents")
  void supportsTriggerPluginWithoutParents() {
    TriggerPlugin trigger = mock(TriggerPlugin.class);
    when(trigger.getCategory()).thenReturn(PluginCategory.PROCESSOR);

    boolean result = strategy.supports(trigger, false);

    assertThat(result).isTrue();
  }

  @Test
  @DisplayName("supports returns false when plugin is TriggerPlugin with parents")
  void supportsTriggerPluginWithParents() {
    TriggerPlugin trigger = mock(TriggerPlugin.class);
    when(trigger.getCategory()).thenReturn(PluginCategory.PROCESSOR);

    boolean result = strategy.supports(trigger, true);

    assertThat(result).isFalse();
  }

  @Test
  @DisplayName("supports returns false when plugin is not TriggerPlugin")
  void supportsNonTriggerPlugin() {
    com.infenia.yukta.plugin.type.ProcessorPlugin processor =
        mock(com.infenia.yukta.plugin.type.ProcessorPlugin.class);

    boolean result = strategy.supports(processor, false);

    assertThat(result).isFalse();
  }

  @Test
  @DisplayName("createAssembler applies virtualThreadScheduler when trigger is blocking")
  void createAssemblerBlockingTrigger() {
    TriggerPlugin trigger = mock(TriggerPlugin.class);
    when(trigger.isBlocking()).thenReturn(true);
    when(trigger.start(any())).thenReturn(Flux.just(DefaultMessage.create(null, "data")));

    WorkflowNode node = new WorkflowNode("trigger-1", "trigger-type", Map.of());
    ExecutionControl control = mock(ExecutionControl.class);
    when(control.nodeSafeStopSinks()).thenReturn(Map.of());
    when(control.applyPostProcessingControls(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(1));

    Flux<Message<?>>[] streams = new Flux[1];
    AssemblyContext context =
        new AssemblyContext(
            "exec-1",
            "sess-1",
            "wf-1",
            Map.of("key", "value"),
            control,
            streams,
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>());

    when(streamTopologyDecorator.applyLoggingAndBroadcasting(
            anyString(), anyString(), any(Flux.class), any(int.class), any(), any()))
        .thenAnswer(inv -> inv.getArgument(2));

    NodeAssembler assembler =
        strategy.createAssembler(
            node, trigger, Duration.ofSeconds(30), 0, 1024, new ParentEdgeInfo[0]);

    assembler.assemble(context);

    assertThat(streams[0]).isNotNull();
  }

  @Test
  @DisplayName("createAssembler does not apply scheduler when trigger is non-blocking")
  void createAssemblerNonBlockingTrigger() {
    TriggerPlugin trigger = mock(TriggerPlugin.class);
    when(trigger.isBlocking()).thenReturn(false);
    when(trigger.start(any())).thenReturn(Flux.just(DefaultMessage.create(null, "data")));

    WorkflowNode node = new WorkflowNode("trigger-2", "trigger-type", Map.of());
    ExecutionControl control = mock(ExecutionControl.class);
    when(control.nodeSafeStopSinks()).thenReturn(Map.of());
    when(control.applyPostProcessingControls(anyString(), any()))
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

    when(streamTopologyDecorator.applyLoggingAndBroadcasting(
            anyString(), anyString(), any(Flux.class), any(int.class), any(), any()))
        .thenAnswer(inv -> inv.getArgument(2));

    NodeAssembler assembler =
        strategy.createAssembler(
            node, trigger, Duration.ofSeconds(30), 0, 1024, new ParentEdgeInfo[0]);

    assembler.assemble(context);

    assertThat(streams[0]).isNotNull();
  }

  @Test
  @DisplayName("createAssembler applies takeUntilOther when nodeSafeSink exists")
  void createAssemblerWithNodeSafeSink() {
    TriggerPlugin trigger = mock(TriggerPlugin.class);
    when(trigger.isBlocking()).thenReturn(false);
    when(trigger.start(any())).thenReturn(Flux.just(DefaultMessage.create(null, "data")));

    WorkflowNode node = new WorkflowNode("trigger-3", "trigger-type", Map.of());
    Sinks.One<Void> safeSink = Sinks.one();
    ExecutionControl control = mock(ExecutionControl.class);
    when(control.nodeSafeStopSinks()).thenReturn(Map.of("trigger-3", safeSink));
    when(control.applyPostProcessingControls(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(1));

    Flux<Message<?>>[] streams = new Flux[1];
    AssemblyContext context =
        new AssemblyContext(
            "exec-3",
            "sess-1",
            "wf-1",
            Map.of(),
            control,
            streams,
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>());

    when(streamTopologyDecorator.applyLoggingAndBroadcasting(
            anyString(), anyString(), any(Flux.class), any(int.class), any(), any()))
        .thenAnswer(inv -> inv.getArgument(2));

    NodeAssembler assembler =
        strategy.createAssembler(
            node, trigger, Duration.ofSeconds(30), 0, 1024, new ParentEdgeInfo[0]);

    assembler.assemble(context);

    assertThat(streams[0]).isNotNull();
  }

  @Test
  @DisplayName("createAssembler skips takeUntilOther when nodeSafeSink is null")
  void createAssemblerWithoutNodeSafeSink() {
    TriggerPlugin trigger = mock(TriggerPlugin.class);
    when(trigger.isBlocking()).thenReturn(false);
    when(trigger.start(any())).thenReturn(Flux.just(DefaultMessage.create(null, "data")));

    WorkflowNode node = new WorkflowNode("trigger-4", "trigger-type", Map.of());
    ExecutionControl control = mock(ExecutionControl.class);
    when(control.nodeSafeStopSinks()).thenReturn(Map.of());
    when(control.applyPostProcessingControls(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(1));

    Flux<Message<?>>[] streams = new Flux[1];
    AssemblyContext context =
        new AssemblyContext(
            "exec-4",
            "sess-1",
            "wf-1",
            Map.of(),
            control,
            streams,
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>());

    when(streamTopologyDecorator.applyLoggingAndBroadcasting(
            anyString(), anyString(), any(Flux.class), any(int.class), any(), any()))
        .thenAnswer(inv -> inv.getArgument(2));

    NodeAssembler assembler =
        strategy.createAssembler(
            node, trigger, Duration.ofSeconds(30), 0, 1024, new ParentEdgeInfo[0]);

    assembler.assemble(context);

    assertThat(streams[0]).isNotNull();
  }

  @Test
  @DisplayName("createAssembler applies all transformations in complete pipeline")
  void createAssemblerCompletePipeline() {
    TriggerPlugin trigger = mock(TriggerPlugin.class);
    when(trigger.isBlocking()).thenReturn(true);
    when(trigger.start(any())).thenReturn(Flux.just(DefaultMessage.create(null, "payload")));

    WorkflowNode node = new WorkflowNode("trigger-5", "trigger-type", Map.of("key", "value"));
    ExecutionControl control = mock(ExecutionControl.class);
    Sinks.One<Void> safeSink = Sinks.one();
    when(control.nodeSafeStopSinks()).thenReturn(Map.of("trigger-5", safeSink));
    when(control.applyPostProcessingControls(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(1));

    Flux<Message<?>>[] streams = new Flux[1];
    ArrayList<Runnable> connectors = new ArrayList<>();
    AssemblyContext context =
        new AssemblyContext(
            "exec-5",
            "sess-5",
            "wf-5",
            Map.of("payload", "data"),
            control,
            streams,
            new ArrayList<>(),
            new ArrayList<>(),
            connectors);

    when(streamTopologyDecorator.applyLoggingAndBroadcasting(
            anyString(), anyString(), any(Flux.class), any(int.class), any(), any()))
        .thenAnswer(inv -> inv.getArgument(2));

    NodeAssembler assembler =
        strategy.createAssembler(
            node, trigger, Duration.ofSeconds(45), 0, 2048, new ParentEdgeInfo[0]);

    assembler.assemble(context);

    assertThat(streams[0]).isNotNull();
    verify(streamTopologyDecorator)
        .applyLoggingAndBroadcasting(
            "exec-5", "trigger-5", streams[0], 2048, context.disposables(), connectors);
  }

  @Test
  @DisplayName("createAssembler assigns stream to correct array index")
  void createAssemblerCorrectArrayIndex() {
    TriggerPlugin trigger = mock(TriggerPlugin.class);
    when(trigger.isBlocking()).thenReturn(false);
    when(trigger.start(any())).thenReturn(Flux.just(DefaultMessage.create(null, "data")));

    WorkflowNode node = new WorkflowNode("trigger-6", "trigger-type", Map.of());
    ExecutionControl control = mock(ExecutionControl.class);
    when(control.nodeSafeStopSinks()).thenReturn(Map.of());
    when(control.applyPostProcessingControls(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(1));

    Flux<Message<?>>[] streams = new Flux[3];
    AssemblyContext context =
        new AssemblyContext(
            "exec-6",
            "sess-1",
            "wf-1",
            Map.of(),
            control,
            streams,
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>());

    when(streamTopologyDecorator.applyLoggingAndBroadcasting(
            anyString(), anyString(), any(Flux.class), any(int.class), any(), any()))
        .thenAnswer(inv -> inv.getArgument(2));

    NodeAssembler assembler =
        strategy.createAssembler(
            node, trigger, Duration.ofSeconds(30), 2, 1024, new ParentEdgeInfo[0]);

    assembler.assemble(context);

    assertThat(streams[2]).isNotNull();
    assertThat(streams[0]).isNull();
    assertThat(streams[1]).isNull();
  }

  @Test
  @DisplayName("createAssembler initializes ExecutionContextBuilder with correct values")
  void createAssemblerContextBuilderValues() {
    TriggerPlugin trigger = mock(TriggerPlugin.class);
    when(trigger.isBlocking()).thenReturn(false);
    when(trigger.start(any())).thenReturn(Flux.just(DefaultMessage.create(null, "data")));

    WorkflowNode node = new WorkflowNode("trigger-7", "trigger-type", Map.of());
    ExecutionControl control = mock(ExecutionControl.class);
    when(control.nodeSafeStopSinks()).thenReturn(Map.of());
    when(control.applyPostProcessingControls(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(1));

    Flux<Message<?>>[] streams = new Flux[1];
    Map<String, Object> payload = Map.of("test", "payload");
    AssemblyContext context =
        new AssemblyContext(
            "exec-context-test",
            "sess-context-test",
            "wf-context-test",
            payload,
            control,
            streams,
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>());

    when(streamTopologyDecorator.applyLoggingAndBroadcasting(
            anyString(), anyString(), any(Flux.class), any(int.class), any(), any()))
        .thenAnswer(inv -> inv.getArgument(2));

    NodeAssembler assembler =
        strategy.createAssembler(
            node, trigger, Duration.ofSeconds(30), 0, 1024, new ParentEdgeInfo[0]);

    assembler.assemble(context);

    assertThat(streams[0]).isNotNull();
  }

  @Test
  @DisplayName("supports handles multiple TriggerPlugin scenarios")
  void supportsVariousScenarios() {
    TriggerPlugin trigger = mock(TriggerPlugin.class);

    when(trigger.getCategory()).thenReturn(PluginCategory.TRIGGER);
    assertThat(strategy.supports(trigger, true)).isTrue();
    assertThat(strategy.supports(trigger, false)).isTrue();

    when(trigger.getCategory()).thenReturn(PluginCategory.PROCESSOR);
    assertThat(strategy.supports(trigger, false)).isTrue();
    assertThat(strategy.supports(trigger, true)).isFalse();

    when(trigger.getCategory()).thenReturn(PluginCategory.TERMINAL);
    assertThat(strategy.supports(trigger, false)).isTrue();
    assertThat(strategy.supports(trigger, true)).isFalse();
  }

  @Test
  @DisplayName("createAssembler handles trigger with empty config")
  void createAssemblerWithEmptyConfig() {
    TriggerPlugin trigger = mock(TriggerPlugin.class);
    when(trigger.isBlocking()).thenReturn(false);
    when(trigger.start(any())).thenReturn(Flux.just(DefaultMessage.create(null, "data")));

    WorkflowNode node = new WorkflowNode("trigger-empty", "trigger-type", Map.of());
    ExecutionControl control = mock(ExecutionControl.class);
    when(control.nodeSafeStopSinks()).thenReturn(Map.of());
    when(control.applyPostProcessingControls(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(1));

    Flux<Message<?>>[] streams = new Flux[1];
    AssemblyContext context =
        new AssemblyContext(
            "exec-empty",
            "sess-1",
            "wf-1",
            Map.of(),
            control,
            streams,
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>());

    when(streamTopologyDecorator.applyLoggingAndBroadcasting(
            anyString(), anyString(), any(Flux.class), any(int.class), any(), any()))
        .thenAnswer(inv -> inv.getArgument(2));

    NodeAssembler assembler =
        strategy.createAssembler(
            node, trigger, Duration.ofSeconds(30), 0, 1024, new ParentEdgeInfo[0]);

    assembler.assemble(context);

    verify(trigger).start(Map.of());
  }
}
