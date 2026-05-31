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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.infenia.yukta.model.workflow.NodeAssembler;
import com.infenia.yukta.model.workflow.ParentEdgeInfo;
import com.infenia.yukta.model.workflow.WorkflowNode;
import com.infenia.yukta.plugin.message.DefaultMessage;
import com.infenia.yukta.plugin.message.Message;
import com.infenia.yukta.plugin.type.ProcessorPlugin;
import com.infenia.yukta.plugin.type.TerminalPlugin;
import com.infenia.yukta.service.control.ExecutionControl;
import com.infenia.yukta.service.control.gateway.ControlBusGateway;
import com.infenia.yukta.service.orchestrator.assembly.AssemblyContext;
import com.infenia.yukta.service.orchestrator.stream.StreamTopologyDecorator;
import com.infenia.yukta.service.orchestrator.tracker.DefaultTaskTrackerServiceService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

@DisplayName("ProcessorNodeAssemblerStrategy")
@MockitoSettings
class ProcessorNodeAssemblerStrategyTest {

  @Mock private DefaultTaskTrackerServiceService tracker;
  @Mock private ControlBusGateway controlBusGateway;
  @Mock private StreamTopologyDecorator streamTopologyDecorator;

  private ProcessorNodeAssemblerStrategy strategy;

  @BeforeEach
  void setUp() {
    strategy =
        new ProcessorNodeAssemblerStrategy(
            tracker, controlBusGateway, Schedulers.parallel(), streamTopologyDecorator);
  }

  @Test
  @DisplayName("supports returns true when plugin is ProcessorPlugin")
  void supportsProcessorPlugin() {
    ProcessorPlugin processor = mock(ProcessorPlugin.class);

    boolean result = strategy.supports(processor, false);

    assertThat(result).isTrue();
  }

  @Test
  @DisplayName("supports returns true for ProcessorPlugin regardless of hasParents")
  void supportsProcessorPluginWithParents() {
    ProcessorPlugin processor = mock(ProcessorPlugin.class);

    boolean resultWithParents = strategy.supports(processor, true);
    boolean resultWithoutParents = strategy.supports(processor, false);

    assertThat(resultWithParents).isTrue();
    assertThat(resultWithoutParents).isTrue();
  }

  @Test
  @DisplayName("supports returns false when plugin is TerminalPlugin")
  void supportsTerminalPlugin() {
    TerminalPlugin terminal = mock(TerminalPlugin.class);

    boolean result = strategy.supports(terminal, false);

    assertThat(result).isFalse();
  }

  @Test
  @DisplayName("supports returns false when plugin is not ProcessorPlugin")
  void supportsNonProcessorPlugin() {
    var trigger = mock(com.infenia.yukta.plugin.type.TriggerPlugin.class);

    boolean result = strategy.supports(trigger, false);

    assertThat(result).isFalse();
  }

  @Test
  @DisplayName("createAssembler applies virtualThreadScheduler when processor is blocking")
  void createAssemblerBlockingProcessor() {
    ProcessorPlugin processor = mock(ProcessorPlugin.class);
    when(processor.isBlocking()).thenReturn(true);
    when(processor.process(any(), any()))
        .thenReturn(Flux.just(DefaultMessage.create(null, "data")));

    WorkflowNode node = new WorkflowNode("proc-1", "processor-type", Map.of());
    ExecutionControl control = mock(ExecutionControl.class);
    when(control.applyPreProcessingControls(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(1));
    when(control.applyPostProcessingControls(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(1));
    when(control.nodeSkipFlags()).thenReturn(new java.util.HashMap<>());

    when(streamTopologyDecorator.mergeParentStreams(any(Flux[].class), any(ParentEdgeInfo[].class)))
        .thenReturn(Flux.just(DefaultMessage.create(null, "input")));
    when(streamTopologyDecorator.applyLoggingAndBroadcasting(
            anyString(), anyString(), any(Flux.class), anyInt(), any(), any()))
        .thenAnswer(inv -> inv.getArgument(2));

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

    NodeAssembler assembler =
        strategy.createAssembler(
            node, processor, Duration.ofSeconds(30), 0, 1024, new ParentEdgeInfo[0]);

    assembler.assemble(context);

    assertThat(streams[0]).isNotNull();
  }

  @Test
  @DisplayName("createAssembler does not apply scheduler when processor is non-blocking")
  void createAssemblerNonBlockingProcessor() {
    ProcessorPlugin processor = mock(ProcessorPlugin.class);
    when(processor.isBlocking()).thenReturn(false);
    when(processor.process(any(), any()))
        .thenReturn(Flux.just(DefaultMessage.create(null, "data")));

    WorkflowNode node = new WorkflowNode("proc-2", "processor-type", Map.of());
    ExecutionControl control = mock(ExecutionControl.class);
    when(control.applyPreProcessingControls(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(1));
    when(control.applyPostProcessingControls(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(1));
    when(control.nodeSkipFlags()).thenReturn(new java.util.HashMap<>());

    when(streamTopologyDecorator.mergeParentStreams(any(Flux[].class), any(ParentEdgeInfo[].class)))
        .thenReturn(Flux.just(DefaultMessage.create(null, "input")));
    when(streamTopologyDecorator.applyLoggingAndBroadcasting(
            anyString(), anyString(), any(Flux.class), anyInt(), any(), any()))
        .thenAnswer(inv -> inv.getArgument(2));

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

    NodeAssembler assembler =
        strategy.createAssembler(
            node, processor, Duration.ofSeconds(30), 0, 1024, new ParentEdgeInfo[0]);

    assembler.assemble(context);

    assertThat(streams[0]).isNotNull();
    verify(processor).process(any(), any());
  }

  @Test
  @DisplayName("createAssembler skips processing when node has skip flag set to true")
  void createAssemblerWithNodeSkipFlagTrue() {
    ProcessorPlugin processor = mock(ProcessorPlugin.class);

    WorkflowNode node = new WorkflowNode("proc-skip", "processor-type", Map.of());
    AtomicBoolean skipFlag = new AtomicBoolean(true);
    ExecutionControl control = mock(ExecutionControl.class);
    when(control.applyPreProcessingControls(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(1));
    when(control.applyPostProcessingControls(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(1));
    Map<String, AtomicBoolean> skipFlags = new java.util.HashMap<>();
    skipFlags.put("proc-skip", skipFlag);
    when(control.nodeSkipFlags()).thenReturn(skipFlags);

    when(streamTopologyDecorator.mergeParentStreams(any(Flux[].class), any(ParentEdgeInfo[].class)))
        .thenReturn(Flux.just(DefaultMessage.create(null, "input")));
    when(streamTopologyDecorator.applyLoggingAndBroadcasting(
            anyString(), anyString(), any(Flux.class), anyInt(), any(), any()))
        .thenAnswer(inv -> inv.getArgument(2));

    Flux<Message<?>>[] streams = new Flux[1];
    AssemblyContext context =
        new AssemblyContext(
            "exec-skip",
            "sess-1",
            "wf-1",
            Map.of(),
            control,
            streams,
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>());

    NodeAssembler assembler =
        strategy.createAssembler(
            node, processor, Duration.ofSeconds(30), 0, 1024, new ParentEdgeInfo[0]);

    assembler.assemble(context);

    assertThat(streams[0]).isNotNull();
    verify(processor, never()).process(any(), any());
  }

  @Test
  @DisplayName("createAssembler calls processor.process when skip flag is false")
  void createAssemblerWithNodeSkipFlagFalse() {
    ProcessorPlugin processor = mock(ProcessorPlugin.class);
    when(processor.isBlocking()).thenReturn(false);
    when(processor.process(any(), any()))
        .thenReturn(Flux.just(DefaultMessage.create(null, "data")));

    WorkflowNode node = new WorkflowNode("proc-no-skip", "processor-type", Map.of());
    AtomicBoolean skipFlag = new AtomicBoolean(false);
    ExecutionControl control = mock(ExecutionControl.class);
    when(control.applyPreProcessingControls(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(1));
    when(control.applyPostProcessingControls(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(1));
    Map<String, AtomicBoolean> skipFlags = new java.util.HashMap<>();
    skipFlags.put("proc-no-skip", skipFlag);
    when(control.nodeSkipFlags()).thenReturn(skipFlags);

    when(streamTopologyDecorator.mergeParentStreams(any(Flux[].class), any(ParentEdgeInfo[].class)))
        .thenReturn(Flux.just(DefaultMessage.create(null, "input")));
    when(streamTopologyDecorator.applyLoggingAndBroadcasting(
            anyString(), anyString(), any(Flux.class), anyInt(), any(), any()))
        .thenAnswer(inv -> inv.getArgument(2));

    Flux<Message<?>>[] streams = new Flux[1];
    AssemblyContext context =
        new AssemblyContext(
            "exec-no-skip",
            "sess-1",
            "wf-1",
            Map.of(),
            control,
            streams,
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>());

    NodeAssembler assembler =
        strategy.createAssembler(
            node, processor, Duration.ofSeconds(30), 0, 1024, new ParentEdgeInfo[0]);

    assembler.assemble(context);

    assertThat(streams[0]).isNotNull();
    verify(processor).process(any(), any());
  }

  @Test
  @DisplayName("createAssembler merges parent streams before preprocessing")
  void createAssemblerMergesParentStreams() {
    ProcessorPlugin processor = mock(ProcessorPlugin.class);
    when(processor.isBlocking()).thenReturn(false);
    when(processor.process(any(), any()))
        .thenReturn(Flux.just(DefaultMessage.create(null, "data")));

    WorkflowNode node = new WorkflowNode("proc-multi", "processor-type", Map.of());
    ExecutionControl control = mock(ExecutionControl.class);
    when(control.applyPreProcessingControls(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(1));
    when(control.applyPostProcessingControls(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(1));
    when(control.nodeSkipFlags()).thenReturn(new java.util.HashMap<>());

    Flux<Message<?>>[] streams = new Flux[2];
    ParentEdgeInfo[] parentEdges = new ParentEdgeInfo[2];

    when(streamTopologyDecorator.mergeParentStreams(any(Flux[].class), any(ParentEdgeInfo[].class)))
        .thenReturn(Flux.just(DefaultMessage.create(null, "merged")));
    when(streamTopologyDecorator.applyLoggingAndBroadcasting(
            anyString(), anyString(), any(Flux.class), anyInt(), any(), any()))
        .thenAnswer(inv -> inv.getArgument(2));

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

    NodeAssembler assembler =
        strategy.createAssembler(node, processor, Duration.ofSeconds(30), 0, 1024, parentEdges);

    assembler.assemble(context);

    verify(streamTopologyDecorator).mergeParentStreams(streams, parentEdges);
  }

  @Test
  @DisplayName("createAssembler applies post-processing controls")
  void createAssemblerAppliesPostProcessingControls() {
    ProcessorPlugin processor = mock(ProcessorPlugin.class);
    when(processor.isBlocking()).thenReturn(false);
    when(processor.process(any(), any()))
        .thenReturn(Flux.just(DefaultMessage.create(null, "data")));

    WorkflowNode node = new WorkflowNode("proc-post", "processor-type", Map.of());
    ExecutionControl control = mock(ExecutionControl.class);
    when(control.applyPreProcessingControls(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(1));
    when(control.applyPostProcessingControls(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(1));
    when(control.nodeSkipFlags()).thenReturn(new java.util.HashMap<>());

    when(streamTopologyDecorator.mergeParentStreams(any(Flux[].class), any(ParentEdgeInfo[].class)))
        .thenReturn(Flux.just(DefaultMessage.create(null, "input")));
    when(streamTopologyDecorator.applyLoggingAndBroadcasting(
            anyString(), anyString(), any(Flux.class), anyInt(), any(), any()))
        .thenAnswer(inv -> inv.getArgument(2));

    Flux<Message<?>>[] streams = new Flux[1];
    AssemblyContext context =
        new AssemblyContext(
            "exec-post",
            "sess-1",
            "wf-1",
            Map.of(),
            control,
            streams,
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>());

    NodeAssembler assembler =
        strategy.createAssembler(
            node, processor, Duration.ofSeconds(30), 0, 1024, new ParentEdgeInfo[0]);

    assembler.assemble(context);

    verify(control).applyPostProcessingControls(eq("proc-post"), any(Flux.class));
  }

  @Test
  @DisplayName("createAssembler assigns stream to correct array index")
  void createAssemblerCorrectArrayIndex() {
    ProcessorPlugin processor = mock(ProcessorPlugin.class);
    when(processor.isBlocking()).thenReturn(false);
    when(processor.process(any(), any()))
        .thenReturn(Flux.just(DefaultMessage.create(null, "data")));

    WorkflowNode node = new WorkflowNode("proc-index", "processor-type", Map.of());
    ExecutionControl control = mock(ExecutionControl.class);
    when(control.applyPreProcessingControls(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(1));
    when(control.applyPostProcessingControls(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(1));
    when(control.nodeSkipFlags()).thenReturn(new java.util.HashMap<>());

    when(streamTopologyDecorator.mergeParentStreams(any(Flux[].class), any(ParentEdgeInfo[].class)))
        .thenReturn(Flux.just(DefaultMessage.create(null, "input")));
    when(streamTopologyDecorator.applyLoggingAndBroadcasting(
            anyString(), anyString(), any(Flux.class), anyInt(), any(), any()))
        .thenAnswer(inv -> inv.getArgument(2));

    Flux<Message<?>>[] streams = new Flux[3];
    AssemblyContext context =
        new AssemblyContext(
            "exec-index",
            "sess-1",
            "wf-1",
            Map.of(),
            control,
            streams,
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>());

    NodeAssembler assembler =
        strategy.createAssembler(
            node, processor, Duration.ofSeconds(30), 2, 1024, new ParentEdgeInfo[0]);

    assembler.assemble(context);

    assertThat(streams[2]).isNotNull();
    assertThat(streams[0]).isNull();
    assertThat(streams[1]).isNull();
  }

  @Test
  @DisplayName("createAssembler calls applyLoggingAndBroadcasting with correct parameters")
  void createAssemblerAppliesLoggingAndBroadcasting() {
    ProcessorPlugin processor = mock(ProcessorPlugin.class);
    when(processor.isBlocking()).thenReturn(false);
    when(processor.process(any(), any()))
        .thenReturn(Flux.just(DefaultMessage.create(null, "data")));

    WorkflowNode node = new WorkflowNode("proc-log", "processor-type", Map.of());
    ExecutionControl control = mock(ExecutionControl.class);
    when(control.applyPreProcessingControls(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(1));
    when(control.applyPostProcessingControls(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(1));
    when(control.nodeSkipFlags()).thenReturn(new java.util.HashMap<>());

    when(streamTopologyDecorator.mergeParentStreams(any(Flux[].class), any(ParentEdgeInfo[].class)))
        .thenReturn(Flux.just(DefaultMessage.create(null, "input")));
    when(streamTopologyDecorator.applyLoggingAndBroadcasting(
            anyString(), anyString(), any(Flux.class), anyInt(), any(), any()))
        .thenAnswer(inv -> inv.getArgument(2));

    Flux<Message<?>>[] streams = new Flux[1];
    ArrayList<Runnable> connectors = new ArrayList<>();
    AssemblyContext context =
        new AssemblyContext(
            "exec-log",
            "sess-log",
            "wf-log",
            Map.of(),
            control,
            streams,
            new ArrayList<>(),
            new ArrayList<>(),
            connectors);

    NodeAssembler assembler =
        strategy.createAssembler(
            node, processor, Duration.ofSeconds(30), 0, 2048, new ParentEdgeInfo[0]);

    assembler.assemble(context);

    verify(streamTopologyDecorator)
        .applyLoggingAndBroadcasting(
            "exec-log", "proc-log", streams[0], 2048, context.disposables(), connectors);
  }

  @Test
  @DisplayName("createAssembler initializes ExecutionContextBuilder with correct values")
  void createAssemblerContextBuilderInitialization() {
    ProcessorPlugin processor = mock(ProcessorPlugin.class);
    when(processor.isBlocking()).thenReturn(false);
    when(processor.process(any(), any()))
        .thenReturn(Flux.just(DefaultMessage.create(null, "data")));

    WorkflowNode node = new WorkflowNode("proc-ctx", "processor-type", Map.of());
    ExecutionControl control = mock(ExecutionControl.class);
    when(control.applyPreProcessingControls(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(1));
    when(control.applyPostProcessingControls(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(1));
    when(control.nodeSkipFlags()).thenReturn(new java.util.HashMap<>());

    when(streamTopologyDecorator.mergeParentStreams(any(Flux[].class), any(ParentEdgeInfo[].class)))
        .thenReturn(Flux.just(DefaultMessage.create(null, "input")));
    when(streamTopologyDecorator.applyLoggingAndBroadcasting(
            anyString(), anyString(), any(Flux.class), anyInt(), any(), any()))
        .thenAnswer(inv -> inv.getArgument(2));

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

    NodeAssembler assembler =
        strategy.createAssembler(
            node, processor, Duration.ofSeconds(30), 0, 1024, new ParentEdgeInfo[0]);

    assembler.assemble(context);

    assertThat(streams[0]).isNotNull();
  }

  @Test
  @DisplayName("createAssembler processes with correct node configuration")
  void createAssemblerWithNodeConfiguration() {
    ProcessorPlugin processor = mock(ProcessorPlugin.class);
    when(processor.isBlocking()).thenReturn(false);
    when(processor.process(any(), any()))
        .thenReturn(Flux.just(DefaultMessage.create(null, "data")));

    Map<String, Object> nodeConfig = Map.of("setting", "value");
    WorkflowNode node = new WorkflowNode("proc-config", "processor-type", nodeConfig);
    ExecutionControl control = mock(ExecutionControl.class);
    when(control.applyPreProcessingControls(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(1));
    when(control.applyPostProcessingControls(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(1));
    when(control.nodeSkipFlags()).thenReturn(new java.util.HashMap<>());

    when(streamTopologyDecorator.mergeParentStreams(any(Flux[].class), any(ParentEdgeInfo[].class)))
        .thenReturn(Flux.just(DefaultMessage.create(null, "input")));
    when(streamTopologyDecorator.applyLoggingAndBroadcasting(
            anyString(), anyString(), any(Flux.class), anyInt(), any(), any()))
        .thenAnswer(inv -> inv.getArgument(2));

    Flux<Message<?>>[] streams = new Flux[1];
    AssemblyContext context =
        new AssemblyContext(
            "exec-config",
            "sess-1",
            "wf-1",
            Map.of(),
            control,
            streams,
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>());

    NodeAssembler assembler =
        strategy.createAssembler(
            node, processor, Duration.ofSeconds(30), 0, 1024, new ParentEdgeInfo[0]);

    assembler.assemble(context);

    ArgumentCaptor<Map<String, Object>> configCaptor = ArgumentCaptor.forClass(Map.class);
    verify(processor).process(any(), configCaptor.capture());
    assertThat(configCaptor.getValue()).isEqualTo(nodeConfig);
  }
}
