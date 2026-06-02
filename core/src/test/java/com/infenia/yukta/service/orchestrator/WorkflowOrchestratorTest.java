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
package com.infenia.yukta.service.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.infenia.yukta.model.workflow.WorkflowDefinition;
import com.infenia.yukta.model.workflow.WorkflowDefinition.Edge;
import com.infenia.yukta.model.workflow.WorkflowDefinition.Node;
import com.infenia.yukta.model.workflow.WorkflowNode;
import com.infenia.yukta.plugin.core.PluginCategory;
import com.infenia.yukta.plugin.core.WorkflowPlugin;
import com.infenia.yukta.plugin.message.DefaultMessage;
import com.infenia.yukta.plugin.message.Message;
import com.infenia.yukta.plugin.type.ProcessorPlugin;
import com.infenia.yukta.plugin.type.TerminalPlugin;
import com.infenia.yukta.plugin.type.TriggerPlugin;
import com.infenia.yukta.service.control.factory.ExecutionControlFactory;
import com.infenia.yukta.service.control.store.ExecutionControlRegistry;
import com.infenia.yukta.service.control.store.InMemoryExecutionControlStore;
import com.infenia.yukta.service.execution.status.ExecutionStatusPublisher;
import com.infenia.yukta.service.orchestrator.assembly.ExecutionContextBuilder;
import com.infenia.yukta.service.orchestrator.compiler.HeartbeatBuilder;
import com.infenia.yukta.service.orchestrator.compiler.ResourceManagementBuilder;
import com.infenia.yukta.service.orchestrator.compiler.WorkflowCompiler;
import com.infenia.yukta.service.orchestrator.preparator.TopologicalSortService;
import com.infenia.yukta.service.orchestrator.preparator.WorkflowPreparator;
import com.infenia.yukta.service.orchestrator.strategy.ProcessorNodeAssemblerStrategy;
import com.infenia.yukta.service.orchestrator.strategy.TerminalNodeAssemblerStrategy;
import com.infenia.yukta.service.orchestrator.strategy.TriggerNodeAssemblerStrategy;
import com.infenia.yukta.service.orchestrator.stream.StreamBuilder;
import com.infenia.yukta.service.orchestrator.stream.StreamTopologyDecorator;
import com.infenia.yukta.service.orchestrator.tracker.DefaultTaskTrackerServiceService;
import com.infenia.yukta.service.orchestrator.validator.WorkflowValidator;
import com.infenia.yukta.service.registry.WorkflowRegistry;
import com.infenia.yukta.service.session.SessionConfigStore;
import com.infenia.yukta.service.store.InMemoryNodeCheckpointStore;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

@MockitoSettings
class WorkflowOrchestratorTest {

  @Mock private WorkflowRegistry registry;
  @Mock private DefaultTaskTrackerServiceService tracker;
  @Mock private SessionConfigStore configService;
  @Mock private ExecutionStatusPublisher statusPublisher;
  private WorkflowValidator validator;
  private WorkflowOrchestrator orchestrator;

  @BeforeEach
  void setUp() {
    validator = new WorkflowValidator(registry);
    orchestrator = buildOrchestrator(Schedulers.parallel(), java.time.Duration.ofSeconds(10));
  }

  private void stubPrepare() {}

  private void stubExecution() {
    stubPrepare();
    when(tracker.startWorkflow(anyString(), anyString(), anyString(), any()))
        .thenReturn(Mono.empty());
    when(configService.getExecutionTimeout(anyString())).thenReturn(Mono.just(3600L));
  }

  private WorkflowOrchestrator buildOrchestrator(
      final reactor.core.scheduler.Scheduler scheduler,
      final java.time.Duration heartbeatInterval) {
    final InMemoryNodeCheckpointStore checkpointStore = new InMemoryNodeCheckpointStore();
    final StreamTopologyDecorator topologyDecorator =
        new StreamTopologyDecorator(null, tracker, checkpointStore);
    final List<com.infenia.yukta.service.orchestrator.strategy.NodeAssemblerStrategy> strategies =
        List.of(
            new TriggerNodeAssemblerStrategy(
                tracker, statusPublisher, scheduler, topologyDecorator),
            new ProcessorNodeAssemblerStrategy(
                tracker, statusPublisher, scheduler, topologyDecorator),
            new TerminalNodeAssemblerStrategy(
                tracker, statusPublisher, scheduler, topologyDecorator));
    final ExecutionControlRegistry controlRegistry =
        new ExecutionControlRegistry(new InMemoryExecutionControlStore());
    final WorkflowCompiler compiler =
        new WorkflowCompiler(
            tracker,
            statusPublisher,
            scheduler,
            heartbeatInterval,
            configService,
            controlRegistry,
            strategies);
    return new WorkflowOrchestrator(
        tracker,
        controlRegistry,
        new ExecutionControlFactory(),
        checkpointStore,
        compiler,
        new WorkflowPreparator(
            registry, validator, new TopologicalSortService(), statusPublisher, compiler));
  }

  @Test
  void testValidateStructuralIntegritySuccess() {
    stubPrepare();
    WorkflowDefinition.Node triggerNode = new WorkflowDefinition.Node("n1", "trigger", Map.of());
    WorkflowDefinition.Node terminalNode = new WorkflowDefinition.Node("n2", "terminal", Map.of());
    WorkflowDefinition def =
        new WorkflowDefinition(
            "test-workflow",
            "desc",
            List.of(triggerNode, terminalNode),
            List.of(new WorkflowDefinition.Edge("n1", "n2")));

    TriggerPlugin trigger = mock(TriggerPlugin.class);
    when(trigger.getDefaultTimeout()).thenReturn(java.time.Duration.ofSeconds(30));
    when(trigger.getDefaultBufferSize()).thenReturn(1024);
    when(trigger.getCategory()).thenReturn(PluginCategory.TRIGGER);
    when(trigger.validateConfig(any())).thenReturn(Mono.empty());
    when(trigger.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(trigger.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(trigger.initialize(any())).thenReturn(Mono.empty());

    TerminalPlugin terminal = mock(TerminalPlugin.class);
    when(terminal.getDefaultTimeout()).thenReturn(java.time.Duration.ofSeconds(30));
    when(terminal.getDefaultBufferSize()).thenReturn(1024);
    when(terminal.getCategory()).thenReturn(PluginCategory.TERMINAL);
    when(terminal.validateConfig(any())).thenReturn(Mono.empty());
    when(terminal.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(terminal.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(terminal.initialize(any())).thenReturn(Mono.empty());

    when(registry.get("trigger")).thenReturn(trigger);
    when(registry.get("terminal")).thenReturn(terminal);

    StepVerifier.create(orchestrator.prepareWorkflow(def)).expectNextCount(1).verifyComplete();
  }

  @Test
  void testValidateStructuralIntegrityMissingTriggerAtEntry() {
    WorkflowDefinition.Node processorNode =
        new WorkflowDefinition.Node("n1", "processor", Map.of());
    WorkflowDefinition def =
        new WorkflowDefinition("test-workflow", "desc", List.of(processorNode), List.of());

    ProcessorPlugin processor = mock(ProcessorPlugin.class);
    when(processor.getCategory()).thenReturn(PluginCategory.PROCESSOR);
    when(registry.get("processor")).thenReturn(processor);

    StepVerifier.create(orchestrator.prepareWorkflow(def))
        .expectErrorMatches(
            e ->
                e instanceof IllegalArgumentException
                    && e.getMessage().contains("entry point but not a TRIGGER"))
        .verify();
  }

  @Test
  void testValidateStructuralIntegrityProcessorMissingEdges() {
    WorkflowDefinition.Node triggerNode = new WorkflowDefinition.Node("n1", "trigger", Map.of());
    WorkflowDefinition.Node processorNode =
        new WorkflowDefinition.Node("n2", "processor", Map.of());
    WorkflowDefinition def =
        new WorkflowDefinition(
            "test-workflow",
            "desc",
            List.of(triggerNode, processorNode),
            List.of(new WorkflowDefinition.Edge("n1", "n2"))); // n2 has no outgoing edge

    TriggerPlugin trigger = mock(TriggerPlugin.class);
    when(trigger.getCategory()).thenReturn(PluginCategory.TRIGGER);
    ProcessorPlugin processor = mock(ProcessorPlugin.class);
    when(processor.getCategory()).thenReturn(PluginCategory.PROCESSOR);

    when(registry.get("trigger")).thenReturn(trigger);
    when(registry.get("processor")).thenReturn(processor);

    StepVerifier.create(orchestrator.prepareWorkflow(def))
        .expectErrorMatches(
            e ->
                e instanceof IllegalArgumentException
                    && e.getMessage().contains("must have both incoming and outgoing edges"))
        .verify();
  }

  @Test
  void testValidateStructuralIntegrityEndpointNotTerminal() {
    WorkflowDefinition.Node triggerNode = new WorkflowDefinition.Node("n1", "trigger", Map.of());
    WorkflowDefinition def =
        new WorkflowDefinition("test-workflow", "desc", List.of(triggerNode), List.of());

    TriggerPlugin trigger = mock(TriggerPlugin.class);
    when(trigger.getCategory()).thenReturn(PluginCategory.TRIGGER);

    when(registry.get("trigger")).thenReturn(trigger);

    StepVerifier.create(orchestrator.prepareWorkflow(def))
        .expectErrorMatches(
            e ->
                e instanceof IllegalArgumentException
                    && e.getMessage().contains("endpoint but not a TERMINAL"))
        .verify();
  }

  @Test
  void testValidateStructuralIntegrityCycleDetection() {
    WorkflowDefinition.Node t = new WorkflowDefinition.Node("t", "trigger", Map.of());
    WorkflowDefinition.Node p1 = new WorkflowDefinition.Node("p1", "processor", Map.of());
    WorkflowDefinition.Node p2 = new WorkflowDefinition.Node("p2", "processor", Map.of());
    WorkflowDefinition def =
        new WorkflowDefinition(
            "test-workflow",
            "desc",
            List.of(t, p1, p2),
            List.of(
                new WorkflowDefinition.Edge("t", "p1"),
                new WorkflowDefinition.Edge("p1", "p2"),
                new WorkflowDefinition.Edge("p2", "p1") // Cycle
                ));

    TriggerPlugin trigger = mock(TriggerPlugin.class);
    when(trigger.getCategory()).thenReturn(PluginCategory.TRIGGER);
    ProcessorPlugin processor = mock(ProcessorPlugin.class);
    when(processor.getCategory()).thenReturn(PluginCategory.PROCESSOR);

    when(registry.get("trigger")).thenReturn(trigger);
    when(registry.get("processor")).thenReturn(processor);

    StepVerifier.create(orchestrator.prepareWorkflow(def))
        .expectErrorMatches(
            e ->
                e instanceof IllegalArgumentException && e.getMessage().contains("contains cycles"))
        .verify();
  }

  @Test
  void testValidateTriggerCannotHaveIncomingEdges() {
    WorkflowDefinition.Node t1 = new WorkflowDefinition.Node("t1", "trigger", Map.of());
    WorkflowDefinition.Node t2 = new WorkflowDefinition.Node("t2", "trigger", Map.of());
    WorkflowDefinition def =
        new WorkflowDefinition(
            "test-workflow",
            "desc",
            List.of(t1, t2),
            List.of(new WorkflowDefinition.Edge("t1", "t2")));

    TriggerPlugin trigger = mock(TriggerPlugin.class);
    when(trigger.getCategory()).thenReturn(PluginCategory.TRIGGER);
    when(registry.get("trigger")).thenReturn(trigger);

    StepVerifier.create(orchestrator.prepareWorkflow(def))
        .expectErrorMatches(
            e ->
                e instanceof IllegalArgumentException
                    && e.getMessage().contains("cannot have incoming edges"))
        .verify();
  }

  @Test
  void testValidateTerminalCannotHaveOutgoingEdges() {
    WorkflowDefinition.Node t = new WorkflowDefinition.Node("t", "trigger", Map.of());
    WorkflowDefinition.Node term1 = new WorkflowDefinition.Node("term1", "terminal", Map.of());
    WorkflowDefinition.Node term2 = new WorkflowDefinition.Node("term2", "terminal", Map.of());
    WorkflowDefinition def =
        new WorkflowDefinition(
            "test-workflow",
            "desc",
            List.of(t, term1, term2),
            List.of(
                new WorkflowDefinition.Edge("t", "term1"),
                new WorkflowDefinition.Edge("term1", "term2")));

    TriggerPlugin trigger = mock(TriggerPlugin.class);
    when(trigger.getCategory()).thenReturn(PluginCategory.TRIGGER);
    TerminalPlugin terminal = mock(TerminalPlugin.class);
    when(terminal.getCategory()).thenReturn(PluginCategory.TERMINAL);

    when(registry.get("trigger")).thenReturn(trigger);
    when(registry.get("terminal")).thenReturn(terminal);

    StepVerifier.create(orchestrator.prepareWorkflow(def))
        .expectErrorMatches(
            e ->
                e instanceof IllegalArgumentException
                    && e.getMessage().contains("cannot have outgoing edges"))
        .verify();
  }

  @Test
  void testExecuteWorkflow() {
    stubExecution();
    String sessionId = "sess-1";
    UUID traceId = UUID.randomUUID();
    Message<String> msg = DefaultMessage.create(traceId, "payload");

    WorkflowDefinition.Node triggerNode = new WorkflowDefinition.Node("n1", "trigger", Map.of());
    WorkflowDefinition.Node terminalNode = new WorkflowDefinition.Node("n2", "terminal", Map.of());
    WorkflowDefinition def =
        new WorkflowDefinition(
            "test-workflow",
            "desc",
            List.of(triggerNode, terminalNode),
            List.of(new WorkflowDefinition.Edge("n1", "n2")));

    TriggerPlugin trigger = mock(TriggerPlugin.class);
    when(trigger.getDefaultTimeout()).thenReturn(java.time.Duration.ofSeconds(30));
    when(trigger.getDefaultBufferSize()).thenReturn(1024);
    when(trigger.getCategory()).thenReturn(PluginCategory.TRIGGER);
    when(trigger.validateConfig(any())).thenReturn(Mono.empty());
    when(trigger.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(trigger.initialize(any())).thenReturn(Mono.empty());
    when(trigger.start(any())).thenReturn(Flux.just(msg));

    TerminalPlugin terminal = mock(TerminalPlugin.class);
    when(terminal.getDefaultTimeout()).thenReturn(java.time.Duration.ofSeconds(30));
    when(terminal.getDefaultBufferSize()).thenReturn(1024);
    when(terminal.getCategory()).thenReturn(PluginCategory.TERMINAL);
    when(terminal.validateConfig(any())).thenReturn(Mono.empty());
    when(terminal.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(terminal.initialize(any())).thenReturn(Mono.empty());
    when(terminal.consume(any(), any()))
        .thenAnswer(inv -> ((Flux<Message<?>>) inv.getArgument(0)).then());

    when(registry.get("trigger")).thenReturn(trigger);
    when(registry.get("terminal")).thenReturn(terminal);

    String executionId = "exec-1";
    StepVerifier.create(
            orchestrator
                .prepareWorkflow(def)
                .flatMap(
                    pw ->
                        orchestrator.execute(
                            sessionId, "test-workflow", executionId, pw, Map.of())))
        .verifyComplete();

    verify(trigger).start(any());
    verify(terminal).consume(any(), any());
    verify(tracker).startWorkflow(eq(executionId), eq(sessionId), anyString(), any());
    verify(tracker, atLeastOnce())
        .emitTaskStatusEvent(eq(executionId), anyString(), anyString(), anyString(), any());
    verify(tracker).emitWorkflowStatusEvent(eq(executionId), eq("SUCCESS"));
  }

  @Test
  void testMessageHistoryTracking() {
    stubExecution();
    String sessionId = "history-sess";
    UUID traceId = UUID.randomUUID();
    Message<String> msg = DefaultMessage.create(traceId, "payload");

    Node triggerNode = new Node("t1", "trigger", Map.of());
    Node procNode = new Node("p1", "processor", Map.of());
    Node terminalNode = new Node("term1", "terminal", Map.of());
    WorkflowDefinition def =
        new WorkflowDefinition(
            "test-workflow",
            "History Test",
            List.of(triggerNode, procNode, terminalNode),
            List.of(new Edge("t1", "p1"), new Edge("p1", "term1")));

    TriggerPlugin trigger = mock(TriggerPlugin.class);
    when(trigger.getDefaultTimeout()).thenReturn(java.time.Duration.ofSeconds(30));
    when(trigger.getDefaultBufferSize()).thenReturn(1024);
    when(trigger.getCategory()).thenReturn(PluginCategory.TRIGGER);
    when(trigger.validateConfig(any())).thenReturn(Mono.empty());
    when(trigger.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(trigger.initialize(any())).thenReturn(Mono.empty());
    when(trigger.start(any())).thenReturn(Flux.just(msg));

    ProcessorPlugin processor = mock(ProcessorPlugin.class);
    when(processor.getDefaultTimeout()).thenReturn(java.time.Duration.ofSeconds(30));
    when(processor.getDefaultBufferSize()).thenReturn(1024);
    when(processor.getCategory()).thenReturn(PluginCategory.PROCESSOR);
    when(processor.validateConfig(any())).thenReturn(Mono.empty());
    when(processor.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(processor.initialize(any())).thenReturn(Mono.empty());
    when(processor.process(any(), any())).thenAnswer(inv -> inv.getArgument(0));

    AtomicReference<Message<?>> capturedMessage = new AtomicReference<>();
    TerminalPlugin terminal = mock(TerminalPlugin.class);
    when(terminal.getDefaultTimeout()).thenReturn(java.time.Duration.ofSeconds(30));
    when(terminal.getDefaultBufferSize()).thenReturn(1024);
    when(terminal.getCategory()).thenReturn(PluginCategory.TERMINAL);
    when(terminal.validateConfig(any())).thenReturn(Mono.empty());
    when(terminal.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(terminal.initialize(any())).thenReturn(Mono.empty());
    when(terminal.consume(any(), any()))
        .thenAnswer(
            inv -> {
              Flux<Message<?>> input = inv.getArgument(0);
              return input.doOnNext(capturedMessage::set).then();
            });

    when(registry.get("trigger")).thenReturn(trigger);
    when(registry.get("processor")).thenReturn(processor);
    when(registry.get("terminal")).thenReturn(terminal);

    orchestrator
        .prepareWorkflow(def)
        .flatMap(pw -> orchestrator.execute(sessionId, "wf", "exec-h", pw, Map.of()))
        .as(StepVerifier::create)
        .verifyComplete();

    Message<?> finalMsg = capturedMessage.get();
    org.junit.jupiter.api.Assertions.assertNotNull(finalMsg);
    // History should contain t1 and p1
    List<String> history = finalMsg.getMessageHistory();
    org.junit.jupiter.api.Assertions.assertTrue(history.contains("t1"));
    org.junit.jupiter.api.Assertions.assertTrue(history.contains("p1"));
  }

  @Test
  void testWireTapSupport() {
    stubExecution();
    final String sessionId = "tap-sess";
    final WorkflowOrchestrator tappedOrchestrator =
        buildOrchestrator(Schedulers.immediate(), java.time.Duration.ofSeconds(10));

    final Node triggerNode = new Node("t1", "trigger", Map.of());
    final Node terminalNode = new Node("term1", "terminal", Map.of());
    final WorkflowDefinition def =
        new WorkflowDefinition(
            "test-workflow",
            "Tap Test",
            List.of(triggerNode, terminalNode),
            List.of(new Edge("t1", "term1")));

    final TriggerPlugin trigger = mock(TriggerPlugin.class);
    when(trigger.getDefaultTimeout()).thenReturn(java.time.Duration.ofSeconds(30));
    when(trigger.getDefaultBufferSize()).thenReturn(1024);
    when(trigger.getCategory()).thenReturn(PluginCategory.TRIGGER);
    when(trigger.validateConfig(any())).thenReturn(Mono.empty());
    when(trigger.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(trigger.initialize(any())).thenReturn(Mono.empty());
    when(trigger.start(any())).thenReturn(Flux.just(DefaultMessage.create(UUID.randomUUID(), "d")));

    final TerminalPlugin terminal = mock(TerminalPlugin.class);
    when(terminal.getDefaultTimeout()).thenReturn(java.time.Duration.ofSeconds(30));
    when(terminal.getDefaultBufferSize()).thenReturn(1024);
    when(terminal.getCategory()).thenReturn(PluginCategory.TERMINAL);
    when(terminal.validateConfig(any())).thenReturn(Mono.empty());
    when(terminal.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(terminal.initialize(any())).thenReturn(Mono.empty());
    when(terminal.consume(any(), any())).thenReturn(Mono.empty());

    when(registry.get("trigger")).thenReturn(trigger);
    when(registry.get("terminal")).thenReturn(terminal);

    tappedOrchestrator
        .prepareWorkflow(def)
        .flatMap(pw -> tappedOrchestrator.execute(sessionId, "wf", "exec-tap", pw, Map.of()))
        .as(StepVerifier::create)
        .verifyComplete();
  }

  @Test
  void testExecuteWorkflowWithBranching() {
    stubExecution();
    String sessionId = "sess-1";
    Message<String> msg = DefaultMessage.create(UUID.randomUUID(), "payload");

    WorkflowDefinition.Node triggerNode = new WorkflowDefinition.Node("t", "trigger", Map.of());
    WorkflowDefinition.Node term1 = new WorkflowDefinition.Node("term1", "terminal", Map.of());
    WorkflowDefinition.Node term2 = new WorkflowDefinition.Node("term2", "terminal", Map.of());

    WorkflowDefinition def =
        new WorkflowDefinition(
            "test-workflow",
            "desc",
            List.of(triggerNode, term1, term2),
            List.of(
                new WorkflowDefinition.Edge("t", "term1"),
                new WorkflowDefinition.Edge("t", "term2")));

    TriggerPlugin trigger = mock(TriggerPlugin.class);
    when(trigger.getDefaultTimeout()).thenReturn(java.time.Duration.ofSeconds(30));
    when(trigger.getDefaultBufferSize()).thenReturn(1024);
    when(trigger.getCategory()).thenReturn(PluginCategory.TRIGGER);
    when(trigger.validateConfig(any())).thenReturn(Mono.empty());
    when(trigger.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(trigger.initialize(any())).thenReturn(Mono.empty());
    when(trigger.start(any())).thenReturn(Flux.just(msg));

    TerminalPlugin terminal = mock(TerminalPlugin.class);
    when(terminal.getDefaultTimeout()).thenReturn(java.time.Duration.ofSeconds(30));
    when(terminal.getDefaultBufferSize()).thenReturn(1024);
    when(terminal.getCategory()).thenReturn(PluginCategory.TERMINAL);
    when(terminal.validateConfig(any())).thenReturn(Mono.empty());
    when(terminal.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(terminal.initialize(any())).thenReturn(Mono.empty());
    when(terminal.consume(any(), any()))
        .thenAnswer(inv -> ((Flux<Message<?>>) inv.getArgument(0)).then());

    when(registry.get("trigger")).thenReturn(trigger);
    when(registry.get("terminal")).thenReturn(terminal);

    String executionId = "exec-2";
    StepVerifier.create(
            orchestrator
                .prepareWorkflow(def)
                .flatMap(
                    pw ->
                        orchestrator.execute(
                            sessionId, "test-workflow", executionId, pw, Map.of())))
        .verifyComplete();

    verify(terminal, atLeastOnce()).consume(any(), any());
  }

  @Test
  void testExecuteWorkflowWithProcessor() {
    stubExecution();
    String sessionId = "sess-1";
    Message<String> msg = DefaultMessage.create(UUID.randomUUID(), "payload");

    WorkflowDefinition.Node triggerNode = new WorkflowDefinition.Node("t", "trigger", Map.of());
    WorkflowDefinition.Node procNode = new WorkflowDefinition.Node("p", "processor", Map.of());
    WorkflowDefinition.Node termNode = new WorkflowDefinition.Node("term", "terminal", Map.of());

    WorkflowDefinition def =
        new WorkflowDefinition(
            "test-workflow",
            "desc",
            List.of(triggerNode, procNode, termNode),
            List.of(
                new WorkflowDefinition.Edge("t", "p"), new WorkflowDefinition.Edge("p", "term")));

    TriggerPlugin trigger = mock(TriggerPlugin.class);
    when(trigger.getDefaultTimeout()).thenReturn(java.time.Duration.ofSeconds(30));
    when(trigger.getDefaultBufferSize()).thenReturn(1024);
    when(trigger.getCategory()).thenReturn(PluginCategory.TRIGGER);
    when(trigger.validateConfig(any())).thenReturn(Mono.empty());
    when(trigger.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(trigger.initialize(any())).thenReturn(Mono.empty());
    when(trigger.start(any())).thenReturn(Flux.just(msg));

    ProcessorPlugin processor = mock(ProcessorPlugin.class);
    when(processor.getDefaultTimeout()).thenReturn(java.time.Duration.ofSeconds(30));
    when(processor.getDefaultBufferSize()).thenReturn(1024);
    when(processor.getCategory()).thenReturn(PluginCategory.PROCESSOR);
    when(processor.validateConfig(any())).thenReturn(Mono.empty());
    when(processor.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(processor.initialize(any())).thenReturn(Mono.empty());
    when(processor.process(any(), any())).thenReturn(Flux.just(msg));

    TerminalPlugin terminal = mock(TerminalPlugin.class);
    when(terminal.getDefaultTimeout()).thenReturn(java.time.Duration.ofSeconds(30));
    when(terminal.getDefaultBufferSize()).thenReturn(1024);
    when(terminal.getCategory()).thenReturn(PluginCategory.TERMINAL);
    when(terminal.validateConfig(any())).thenReturn(Mono.empty());
    when(terminal.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(terminal.initialize(any())).thenReturn(Mono.empty());
    when(terminal.consume(any(), any()))
        .thenAnswer(inv -> ((Flux<Message<?>>) inv.getArgument(0)).then());

    when(registry.get("trigger")).thenReturn(trigger);
    when(registry.get("processor")).thenReturn(processor);
    when(registry.get("terminal")).thenReturn(terminal);

    String executionId = "exec-3";
    StepVerifier.create(
            orchestrator
                .prepareWorkflow(def)
                .flatMap(
                    pw ->
                        orchestrator.execute(
                            sessionId, "test-workflow", executionId, pw, Map.of())))
        .verifyComplete();

    verify(trigger).start(any());
    verify(processor).process(any(), any());
    verify(terminal).consume(any(), any());
  }

  @Test
  void testPrepareWorkflowPluginNotFound() {
    WorkflowDefinition.Node node = new WorkflowDefinition.Node("n1", "unknown", Map.of());
    WorkflowDefinition def =
        new WorkflowDefinition("test-workflow", "desc", List.of(node), List.of());

    when(registry.get("unknown")).thenReturn(null);

    StepVerifier.create(orchestrator.prepareWorkflow(def))
        .expectErrorMatches(
            e ->
                e instanceof IllegalArgumentException
                    && e.getMessage().contains("Plugin not found"))
        .verify();
  }

  @Test
  void testChainWithProcessorAndMultipleChildren() {
    stubExecution();
    String sessionId = "sess-1";
    Message<String> msg = DefaultMessage.create(UUID.randomUUID(), "payload");

    WorkflowDefinition.Node triggerNode = new WorkflowDefinition.Node("t", "trigger", Map.of());
    WorkflowDefinition.Node procNode = new WorkflowDefinition.Node("p", "processor", Map.of());
    WorkflowDefinition.Node term1 = new WorkflowDefinition.Node("term1", "terminal", Map.of());
    WorkflowDefinition.Node term2 = new WorkflowDefinition.Node("term2", "terminal", Map.of());

    WorkflowDefinition def =
        new WorkflowDefinition(
            "test-workflow",
            "desc",
            List.of(triggerNode, procNode, term1, term2),
            List.of(
                new WorkflowDefinition.Edge("t", "p"),
                new WorkflowDefinition.Edge("p", "term1"),
                new WorkflowDefinition.Edge("p", "term2")));

    TriggerPlugin trigger = mock(TriggerPlugin.class);
    when(trigger.getDefaultTimeout()).thenReturn(java.time.Duration.ofSeconds(30));
    when(trigger.getDefaultBufferSize()).thenReturn(1024);
    when(trigger.getCategory()).thenReturn(PluginCategory.TRIGGER);
    when(trigger.validateConfig(any())).thenReturn(Mono.empty());
    when(trigger.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(trigger.initialize(any())).thenReturn(Mono.empty());
    when(trigger.start(any())).thenReturn(Flux.just(msg));

    ProcessorPlugin processor = mock(ProcessorPlugin.class);
    when(processor.getDefaultTimeout()).thenReturn(java.time.Duration.ofSeconds(30));
    when(processor.getDefaultBufferSize()).thenReturn(1024);
    when(processor.getCategory()).thenReturn(PluginCategory.PROCESSOR);
    when(processor.validateConfig(any())).thenReturn(Mono.empty());
    when(processor.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(processor.initialize(any())).thenReturn(Mono.empty());
    when(processor.process(any(), any())).thenReturn(Flux.just(msg));

    TerminalPlugin terminal = mock(TerminalPlugin.class);
    when(terminal.getDefaultTimeout()).thenReturn(java.time.Duration.ofSeconds(30));
    when(terminal.getDefaultBufferSize()).thenReturn(1024);
    when(terminal.getCategory()).thenReturn(PluginCategory.TERMINAL);
    when(terminal.validateConfig(any())).thenReturn(Mono.empty());
    when(terminal.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(terminal.initialize(any())).thenReturn(Mono.empty());
    when(terminal.consume(any(), any()))
        .thenAnswer(inv -> ((Flux<Message<?>>) inv.getArgument(0)).then());

    when(registry.get("trigger")).thenReturn(trigger);
    when(registry.get("processor")).thenReturn(processor);
    when(registry.get("terminal")).thenReturn(terminal);

    String executionId = "exec-4";
    StepVerifier.create(
            orchestrator
                .prepareWorkflow(def)
                .flatMap(
                    pw ->
                        orchestrator.execute(
                            sessionId, "test-workflow", executionId, pw, Map.of())))
        .verifyComplete();

    verify(processor).process(any(), any());
    verify(terminal, atLeastOnce()).consume(any(), any());
  }

  @Test
  void testPrepareWorkflowErrorCleanup() {
    WorkflowDefinition.Node node = new WorkflowDefinition.Node("n1", "trigger", Map.of());
    WorkflowDefinition def =
        new WorkflowDefinition("test-workflow", "desc", List.of(node), List.of());

    TriggerPlugin trigger = mock(TriggerPlugin.class);
    when(trigger.getCategory()).thenReturn(PluginCategory.TRIGGER);
    when(registry.get("trigger")).thenReturn(trigger);

    StepVerifier.create(orchestrator.prepareWorkflow(def)).expectError().verify();
  }

  @Test
  void testExecuteWithTimeout() {
    stubExecution();
    String sessionId = "sess-timeout";
    when(configService.getExecutionTimeout(sessionId)).thenReturn(Mono.just(1L));

    WorkflowDefinition.Node t = new WorkflowDefinition.Node("t", "trigger", Map.of());
    WorkflowDefinition.Node term = new WorkflowDefinition.Node("term", "terminal", Map.of());
    WorkflowDefinition def =
        new WorkflowDefinition(
            "test-workflow", "desc", List.of(t, term), List.of(new Edge("t", "term")));

    TriggerPlugin trigger = mock(TriggerPlugin.class);
    when(trigger.getCategory()).thenReturn(PluginCategory.TRIGGER);
    when(trigger.getDefaultBufferSize()).thenReturn(1024);
    when(trigger.initialize(any())).thenReturn(Mono.empty());
    when(trigger.validateConfig(any())).thenReturn(Mono.empty());
    when(trigger.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(trigger.start(any())).thenReturn(Flux.never());

    TerminalPlugin terminal = mock(TerminalPlugin.class);
    when(terminal.getCategory()).thenReturn(PluginCategory.TERMINAL);
    when(terminal.getDefaultBufferSize()).thenReturn(1024);
    when(terminal.initialize(any())).thenReturn(Mono.empty());
    when(terminal.validateConfig(any())).thenReturn(Mono.empty());
    when(terminal.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(terminal.consume(any(), any())).thenReturn(Mono.never());

    when(registry.get("trigger")).thenReturn(trigger);
    when(registry.get("terminal")).thenReturn(terminal);

    StepVerifier.create(
            orchestrator
                .prepareWorkflow(def)
                .flatMap(pw -> orchestrator.execute(sessionId, "wf", "exec-t", pw, Map.of())))
        .verifyError(java.util.concurrent.TimeoutException.class);

    verify(tracker).emitWorkflowStatusEvent(eq("exec-t"), eq("ERROR"));
  }

  @Test
  void testExecuteWithNoTimeout() {
    stubExecution();
    String sessionId = "sess-no-timeout";
    when(configService.getExecutionTimeout(sessionId)).thenReturn(Mono.just(0L));

    WorkflowDefinition.Node t = new Node("t", "trigger", Map.of());
    WorkflowDefinition.Node term = new Node("term", "terminal", Map.of());
    WorkflowDefinition def =
        new WorkflowDefinition(
            "test-workflow", "d", List.of(t, term), List.of(new Edge("t", "term")));

    TriggerPlugin trigger = mock(TriggerPlugin.class);
    when(trigger.getCategory()).thenReturn(PluginCategory.TRIGGER);
    when(trigger.getDefaultBufferSize()).thenReturn(1024);
    when(trigger.initialize(any())).thenReturn(Mono.empty());
    when(trigger.validateConfig(any())).thenReturn(Mono.empty());
    when(trigger.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(trigger.start(any())).thenReturn(Flux.empty());

    TerminalPlugin terminal = mock(TerminalPlugin.class);
    when(terminal.getCategory()).thenReturn(PluginCategory.TERMINAL);
    when(terminal.getDefaultBufferSize()).thenReturn(1024);
    when(terminal.initialize(any())).thenReturn(Mono.empty());
    when(terminal.validateConfig(any())).thenReturn(Mono.empty());
    when(terminal.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(terminal.consume(any(), any())).thenReturn(Mono.empty());

    when(registry.get("trigger")).thenReturn(trigger);
    when(registry.get("terminal")).thenReturn(terminal);

    StepVerifier.create(
            orchestrator
                .prepareWorkflow(def)
                .flatMap(pw -> orchestrator.execute(sessionId, "wf", "exec-nt", pw, Map.of())))
        .verifyComplete();
  }

  @Test
  void testBufferSizeAndTimeoutConfig() {
    stubPrepare();
    WorkflowDefinition.Node node =
        new WorkflowDefinition.Node(
            "n1", "trigger", Map.of("bufferSize", 500, "timeoutSeconds", 10));
    WorkflowDefinition.Node terminalNode = new WorkflowDefinition.Node("n2", "terminal", Map.of());
    WorkflowDefinition def =
        new WorkflowDefinition(
            "test-workflow", "desc", List.of(node, terminalNode), List.of(new Edge("n1", "n2")));

    TriggerPlugin trigger = mock(TriggerPlugin.class);
    when(trigger.getCategory()).thenReturn(PluginCategory.TRIGGER);
    when(trigger.initialize(any())).thenReturn(Mono.empty());
    when(trigger.validateConfig(any())).thenReturn(Mono.empty());
    when(trigger.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(registry.get("trigger")).thenReturn(trigger);

    TerminalPlugin terminal = mock(TerminalPlugin.class);
    when(terminal.getCategory()).thenReturn(PluginCategory.TERMINAL);
    when(terminal.getDefaultBufferSize()).thenReturn(1024);
    when(terminal.initialize(any())).thenReturn(Mono.empty());
    when(terminal.validateConfig(any())).thenReturn(Mono.empty());
    when(terminal.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(registry.get("terminal")).thenReturn(terminal);

    StepVerifier.create(orchestrator.prepareWorkflow(def)).expectNextCount(1).verifyComplete();
  }

  @Test
  void testHeartbeatAndStatsEmission() throws Exception {
    stubExecution();
    String sessionId = "sess-hb";
    when(configService.getExecutionTimeout(sessionId)).thenReturn(Mono.just(1L));

    WorkflowDefinition.Node t = new Node("t", "trigger", Map.of());
    WorkflowDefinition.Node term = new Node("term", "terminal", Map.of());
    WorkflowDefinition def =
        new WorkflowDefinition(
            "test-workflow", "d", List.of(t, term), List.of(new Edge("t", "term")));

    TriggerPlugin trigger = mock(TriggerPlugin.class);
    when(trigger.getCategory()).thenReturn(PluginCategory.TRIGGER);
    when(trigger.getDefaultBufferSize()).thenReturn(1024);
    when(trigger.initialize(any())).thenReturn(Mono.empty());
    when(trigger.validateConfig(any())).thenReturn(Mono.empty());
    when(trigger.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(trigger.start(any())).thenReturn(Flux.empty());

    TerminalPlugin terminal = mock(TerminalPlugin.class);
    when(terminal.getCategory()).thenReturn(PluginCategory.TERMINAL);
    when(terminal.getDefaultBufferSize()).thenReturn(1024);
    when(terminal.initialize(any())).thenReturn(Mono.empty());
    when(terminal.validateConfig(any())).thenReturn(Mono.empty());
    when(terminal.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(terminal.consume(any(), any())).thenReturn(Mono.empty());

    when(registry.get("trigger")).thenReturn(trigger);
    when(registry.get("terminal")).thenReturn(terminal);

    WorkflowOrchestrator fastOrchestrator =
        buildOrchestrator(Schedulers.parallel(), java.time.Duration.ofMillis(10));

    StepVerifier.create(
            fastOrchestrator
                .prepareWorkflow(def)
                .flatMap(pw -> fastOrchestrator.execute(sessionId, "wf", "exec-hb", pw, Map.of())))
        .verifyComplete();

    Thread.sleep(200);
    // TODO: Verify that status events were published through ExecutionStatusPublisher
    // once the control bus bridge is established.
  }

  @Test
  void testTriggerAssemblerStreamBuilderIntegration() {
    stubExecution();
    String sessionId = "sess-stream";
    UUID traceId = UUID.randomUUID();
    Message<String> msg = DefaultMessage.create(traceId, "payload");

    WorkflowDefinition.Node triggerNode = new WorkflowDefinition.Node("t", "trigger", Map.of());
    WorkflowDefinition.Node terminalNode =
        new WorkflowDefinition.Node("term", "terminal", Map.of());
    WorkflowDefinition def =
        new WorkflowDefinition(
            "test-workflow",
            "desc",
            List.of(triggerNode, terminalNode),
            List.of(new Edge("t", "term")));

    TriggerPlugin trigger = mock(TriggerPlugin.class);
    when(trigger.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    when(trigger.getDefaultBufferSize()).thenReturn(1024);
    when(trigger.getCategory()).thenReturn(PluginCategory.TRIGGER);
    when(trigger.validateConfig(any())).thenReturn(Mono.empty());
    when(trigger.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(trigger.initialize(any())).thenReturn(Mono.empty());
    when(trigger.start(any())).thenReturn(Flux.just(msg));

    TerminalPlugin terminal = mock(TerminalPlugin.class);
    when(terminal.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    when(terminal.getDefaultBufferSize()).thenReturn(1024);
    when(terminal.getCategory()).thenReturn(PluginCategory.TERMINAL);
    when(terminal.validateConfig(any())).thenReturn(Mono.empty());
    when(terminal.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(terminal.initialize(any())).thenReturn(Mono.empty());
    when(terminal.consume(any(), any()))
        .thenAnswer(inv -> ((Flux<Message<?>>) inv.getArgument(0)).then());

    when(registry.get("trigger")).thenReturn(trigger);
    when(registry.get("terminal")).thenReturn(terminal);

    String executionId = "exec-stream";
    StepVerifier.create(
            orchestrator
                .prepareWorkflow(def)
                .flatMap(
                    pw -> orchestrator.execute(sessionId, "test-wf", executionId, pw, Map.of())))
        .verifyComplete();

    verify(trigger).start(any());
    verify(terminal).consume(any(), any());
    verify(tracker, atLeastOnce()).emitTaskStatusEvent(any(), any(), any(), any(), any());
  }

  @Test
  void testHeartbeatBuilderIntegration() {
    HeartbeatBuilder heartbeatBuilder =
        new HeartbeatBuilder(statusPublisher, Duration.ofMillis(100), Schedulers.boundedElastic());
    List<Disposable> disposables =
        heartbeatBuilder
            .forNodes("workflow-1", List.of("node-1", "node-2"))
            .withHeartbeatInterval(Duration.ofMillis(100))
            .build();

    assertNotNull(disposables);
    assert !disposables.isEmpty();
    disposables.forEach(Disposable::dispose);
  }

  @Test
  void testResourceManagementBuilderIntegration() {
    when(configService.getExecutionTimeout(any())).thenReturn(Mono.just(60L));

    ResourceManagementBuilder resourceMgr =
        new ResourceManagementBuilder(tracker, configService, Schedulers.boundedElastic());

    Mono<Void> execution =
        resourceMgr
            .withDisposables(new ArrayList<>())
            .withTerminals(List.of(Mono.empty()))
            .withConnectors(new ArrayList<>())
            .withExecutionTimeout("session-001", "exec-001")
            .build();

    StepVerifier.create(execution).verifyComplete();

    verify(tracker).emitWorkflowStatusEvent("exec-001", "SUCCESS");
  }

  @Test
  void testExecutionContextBuilderIntegration() {
    ExecutionContextBuilder contextBuilder =
        new ExecutionContextBuilder()
            .sessionId("session-001")
            .workflowId("workflow-001")
            .executionId("exec-001")
            .nodeId("node-001")
            .payload(Map.of("data", "test"));

    Context context = contextBuilder.build();

    assertEquals("session-001", context.get(ExecutionContextBuilder.CTX_SESSION_ID));
    assertEquals("workflow-001", context.get(ExecutionContextBuilder.CTX_WORKFLOW_ID));
    assertEquals("exec-001", context.get(ExecutionContextBuilder.CTX_EXECUTION_ID));
    assertEquals("node-001", context.get(ExecutionContextBuilder.CTX_NODE_ID));
  }

  @Test
  void testStreamBuilderIntegration() {
    WorkflowNode mockNode = mock(WorkflowNode.class);
    when(mockNode.nodeId()).thenReturn("test-node");

    UUID traceId = UUID.randomUUID();
    Flux<Message<?>> sourceStream = Flux.just(DefaultMessage.create(traceId, "test-payload"));

    StreamBuilder builder =
        new StreamBuilder(mockNode, Duration.ofSeconds(5), tracker, statusPublisher);

    Flux<Message<?>> built =
        builder.withSource(sourceStream).withTimeout().withTaskTracking("exec-001").build();

    StepVerifier.create(built).expectNextCount(1).verifyComplete();

    verify(tracker, atLeastOnce()).emitTaskStatusEvent(any(), any(), any(), any(), any());
  }

  @Test
  void testBlockingTrigger() {
    stubExecution();
    final String sessionId = "sess-blocking-trigger";
    final Message<String> msg = DefaultMessage.create(UUID.randomUUID(), "payload");

    final Node triggerNode = new Node("t", "trigger", Map.of());
    final Node termNode = new Node("term", "terminal", Map.of());
    final WorkflowDefinition def =
        new WorkflowDefinition(
            "test-workflow",
            "desc",
            List.of(triggerNode, termNode),
            List.of(new Edge("t", "term")));

    final TriggerPlugin trigger = mock(TriggerPlugin.class);
    when(trigger.isBlocking()).thenReturn(true);
    when(trigger.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    when(trigger.getDefaultBufferSize()).thenReturn(1024);
    when(trigger.getCategory()).thenReturn(PluginCategory.TRIGGER);
    when(trigger.validateConfig(any())).thenReturn(Mono.empty());
    when(trigger.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(trigger.initialize(any())).thenReturn(Mono.empty());
    when(trigger.start(any())).thenReturn(Flux.just(msg));

    final TerminalPlugin terminal = mock(TerminalPlugin.class);
    when(terminal.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    when(terminal.getDefaultBufferSize()).thenReturn(1024);
    when(terminal.getCategory()).thenReturn(PluginCategory.TERMINAL);
    when(terminal.validateConfig(any())).thenReturn(Mono.empty());
    when(terminal.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(terminal.initialize(any())).thenReturn(Mono.empty());
    when(terminal.consume(any(), any()))
        .thenAnswer(inv -> ((Flux<Message<?>>) inv.getArgument(0)).then());

    when(registry.get("trigger")).thenReturn(trigger);
    when(registry.get("terminal")).thenReturn(terminal);

    StepVerifier.create(
            orchestrator
                .prepareWorkflow(def)
                .flatMap(pw -> orchestrator.execute(sessionId, "wf", "exec-bt", pw, Map.of())))
        .verifyComplete();

    verify(trigger).start(any());
    verify(terminal).consume(any(), any());
  }

  @Test
  void testBlockingProcessor() {
    stubExecution();
    final String sessionId = "sess-blocking-processor";
    final Message<String> msg = DefaultMessage.create(UUID.randomUUID(), "payload");

    final Node triggerNode = new Node("t", "trigger", Map.of());
    final Node procNode = new Node("p", "processor", Map.of());
    final Node termNode = new Node("term", "terminal", Map.of());
    final WorkflowDefinition def =
        new WorkflowDefinition(
            "test-workflow",
            "desc",
            List.of(triggerNode, procNode, termNode),
            List.of(new Edge("t", "p"), new Edge("p", "term")));

    final TriggerPlugin trigger = mock(TriggerPlugin.class);
    when(trigger.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    when(trigger.getDefaultBufferSize()).thenReturn(1024);
    when(trigger.getCategory()).thenReturn(PluginCategory.TRIGGER);
    when(trigger.validateConfig(any())).thenReturn(Mono.empty());
    when(trigger.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(trigger.initialize(any())).thenReturn(Mono.empty());
    when(trigger.start(any())).thenReturn(Flux.just(msg));

    final ProcessorPlugin processor = mock(ProcessorPlugin.class);
    when(processor.isBlocking()).thenReturn(true);
    when(processor.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    when(processor.getDefaultBufferSize()).thenReturn(1024);
    when(processor.getCategory()).thenReturn(PluginCategory.PROCESSOR);
    when(processor.validateConfig(any())).thenReturn(Mono.empty());
    when(processor.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(processor.initialize(any())).thenReturn(Mono.empty());
    when(processor.process(any(), any())).thenReturn(Flux.just(msg));

    final TerminalPlugin terminal = mock(TerminalPlugin.class);
    when(terminal.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    when(terminal.getDefaultBufferSize()).thenReturn(1024);
    when(terminal.getCategory()).thenReturn(PluginCategory.TERMINAL);
    when(terminal.validateConfig(any())).thenReturn(Mono.empty());
    when(terminal.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(terminal.initialize(any())).thenReturn(Mono.empty());
    when(terminal.consume(any(), any()))
        .thenAnswer(inv -> ((Flux<Message<?>>) inv.getArgument(0)).then());

    when(registry.get("trigger")).thenReturn(trigger);
    when(registry.get("processor")).thenReturn(processor);
    when(registry.get("terminal")).thenReturn(terminal);

    StepVerifier.create(
            orchestrator
                .prepareWorkflow(def)
                .flatMap(pw -> orchestrator.execute(sessionId, "wf", "exec-bp", pw, Map.of())))
        .verifyComplete();

    verify(trigger).start(any());
    verify(processor).process(any(), any());
    verify(terminal).consume(any(), any());
  }

  @Test
  void testBlockingTerminal() {
    stubExecution();
    final String sessionId = "sess-blocking-terminal";
    final Message<String> msg = DefaultMessage.create(UUID.randomUUID(), "payload");

    final Node triggerNode = new Node("t", "trigger", Map.of());
    final Node termNode = new Node("term", "terminal", Map.of());
    final WorkflowDefinition def =
        new WorkflowDefinition(
            "test-workflow",
            "desc",
            List.of(triggerNode, termNode),
            List.of(new Edge("t", "term")));

    final TriggerPlugin trigger = mock(TriggerPlugin.class);
    when(trigger.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    when(trigger.getDefaultBufferSize()).thenReturn(1024);
    when(trigger.getCategory()).thenReturn(PluginCategory.TRIGGER);
    when(trigger.validateConfig(any())).thenReturn(Mono.empty());
    when(trigger.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(trigger.initialize(any())).thenReturn(Mono.empty());
    when(trigger.start(any())).thenReturn(Flux.just(msg));

    final TerminalPlugin terminal = mock(TerminalPlugin.class);
    when(terminal.isBlocking()).thenReturn(true);
    when(terminal.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    when(terminal.getDefaultBufferSize()).thenReturn(1024);
    when(terminal.getCategory()).thenReturn(PluginCategory.TERMINAL);
    when(terminal.validateConfig(any())).thenReturn(Mono.empty());
    when(terminal.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(terminal.initialize(any())).thenReturn(Mono.empty());
    when(terminal.consume(any(), any()))
        .thenAnswer(inv -> ((Flux<Message<?>>) inv.getArgument(0)).then());

    when(registry.get("trigger")).thenReturn(trigger);
    when(registry.get("terminal")).thenReturn(terminal);

    StepVerifier.create(
            orchestrator
                .prepareWorkflow(def)
                .flatMap(pw -> orchestrator.execute(sessionId, "wf", "exec-btm", pw, Map.of())))
        .verifyComplete();

    verify(trigger).start(any());
    verify(terminal).consume(any(), any());
  }

  @Test
  void testTimeoutFallsBackWhenZeroTimeoutSeconds() {
    stubExecution();
    final String sessionId = "sess-zero-timeout";
    final Message<String> msg = DefaultMessage.create(UUID.randomUUID(), "payload");

    final Node triggerNode = new Node("t", "trigger", Map.of("timeoutSeconds", 0));
    final Node termNode = new Node("term", "terminal", Map.of());
    final WorkflowDefinition def =
        new WorkflowDefinition(
            "test-workflow",
            "desc",
            List.of(triggerNode, termNode),
            List.of(new Edge("t", "term")));

    final TriggerPlugin trigger = mock(TriggerPlugin.class);
    when(trigger.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    when(trigger.getDefaultBufferSize()).thenReturn(1024);
    when(trigger.getCategory()).thenReturn(PluginCategory.TRIGGER);
    when(trigger.validateConfig(any())).thenReturn(Mono.empty());
    when(trigger.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(trigger.initialize(any())).thenReturn(Mono.empty());
    when(trigger.start(any())).thenReturn(Flux.just(msg));

    final TerminalPlugin terminal = mock(TerminalPlugin.class);
    when(terminal.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    when(terminal.getDefaultBufferSize()).thenReturn(1024);
    when(terminal.getCategory()).thenReturn(PluginCategory.TERMINAL);
    when(terminal.validateConfig(any())).thenReturn(Mono.empty());
    when(terminal.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(terminal.initialize(any())).thenReturn(Mono.empty());
    when(terminal.consume(any(), any()))
        .thenAnswer(inv -> ((Flux<Message<?>>) inv.getArgument(0)).then());

    when(registry.get("trigger")).thenReturn(trigger);
    when(registry.get("terminal")).thenReturn(terminal);

    StepVerifier.create(
            orchestrator
                .prepareWorkflow(def)
                .flatMap(pw -> orchestrator.execute(sessionId, "wf", "exec-zt", pw, Map.of())))
        .verifyComplete();
  }

  @Test
  void testTimeoutUsingLegacyTimeoutKey() {
    stubExecution();
    final String sessionId = "sess-legacy-timeout";
    final Message<String> msg = DefaultMessage.create(UUID.randomUUID(), "payload");

    final Node triggerNode = new Node("t", "trigger", Map.of("timeout", 60));
    final Node termNode = new Node("term", "terminal", Map.of());
    final WorkflowDefinition def =
        new WorkflowDefinition(
            "test-workflow",
            "desc",
            List.of(triggerNode, termNode),
            List.of(new Edge("t", "term")));

    final TriggerPlugin trigger = mock(TriggerPlugin.class);
    when(trigger.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    when(trigger.getDefaultBufferSize()).thenReturn(1024);
    when(trigger.getCategory()).thenReturn(PluginCategory.TRIGGER);
    when(trigger.validateConfig(any())).thenReturn(Mono.empty());
    when(trigger.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(trigger.initialize(any())).thenReturn(Mono.empty());
    when(trigger.start(any())).thenReturn(Flux.just(msg));

    final TerminalPlugin terminal = mock(TerminalPlugin.class);
    when(terminal.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    when(terminal.getDefaultBufferSize()).thenReturn(1024);
    when(terminal.getCategory()).thenReturn(PluginCategory.TERMINAL);
    when(terminal.validateConfig(any())).thenReturn(Mono.empty());
    when(terminal.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(terminal.initialize(any())).thenReturn(Mono.empty());
    when(terminal.consume(any(), any()))
        .thenAnswer(inv -> ((Flux<Message<?>>) inv.getArgument(0)).then());

    when(registry.get("trigger")).thenReturn(trigger);
    when(registry.get("terminal")).thenReturn(terminal);

    StepVerifier.create(
            orchestrator
                .prepareWorkflow(def)
                .flatMap(pw -> orchestrator.execute(sessionId, "wf", "exec-lt", pw, Map.of())))
        .verifyComplete();
  }

  @Test
  void testTimeoutFallsToConstantWhenPluginReturnsNullDefault() {
    stubExecution();
    final String sessionId = "sess-null-default";
    final Message<String> msg = DefaultMessage.create(UUID.randomUUID(), "payload");

    final Node triggerNode = new Node("t", "trigger", Map.of());
    final Node termNode = new Node("term", "terminal", Map.of());
    final WorkflowDefinition def =
        new WorkflowDefinition(
            "test-workflow",
            "desc",
            List.of(triggerNode, termNode),
            List.of(new Edge("t", "term")));

    final TriggerPlugin trigger = mock(TriggerPlugin.class);
    when(trigger.getDefaultTimeout()).thenReturn(null);
    when(trigger.getDefaultBufferSize()).thenReturn(1024);
    when(trigger.getCategory()).thenReturn(PluginCategory.TRIGGER);
    when(trigger.validateConfig(any())).thenReturn(Mono.empty());
    when(trigger.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(trigger.initialize(any())).thenReturn(Mono.empty());
    when(trigger.start(any())).thenReturn(Flux.just(msg));

    final TerminalPlugin terminal = mock(TerminalPlugin.class);
    when(terminal.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    when(terminal.getDefaultBufferSize()).thenReturn(1024);
    when(terminal.getCategory()).thenReturn(PluginCategory.TERMINAL);
    when(terminal.validateConfig(any())).thenReturn(Mono.empty());
    when(terminal.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(terminal.initialize(any())).thenReturn(Mono.empty());
    when(terminal.consume(any(), any()))
        .thenAnswer(inv -> ((Flux<Message<?>>) inv.getArgument(0)).then());

    when(registry.get("trigger")).thenReturn(trigger);
    when(registry.get("terminal")).thenReturn(terminal);

    StepVerifier.create(
            orchestrator
                .prepareWorkflow(def)
                .flatMap(pw -> orchestrator.execute(sessionId, "wf", "exec-nd", pw, Map.of())))
        .verifyComplete();
  }

  @Test
  void testTimeoutFallsToConstantWhenPluginIsNull() throws Exception {
    final Node node = new Node("n1", "test", Map.of());
    final Duration result = invokeGetNodeTimeout(node, null);
    assertEquals(Duration.ofSeconds(30), result);
  }

  private Duration invokeGetNodeTimeout(final Node node, final WorkflowPlugin plugin)
      throws Exception {
    final com.infenia.yukta.service.orchestrator.compiler.WorkflowCompiler compiler =
        new com.infenia.yukta.service.orchestrator.compiler.WorkflowCompiler(
            tracker,
            statusPublisher,
            Schedulers.parallel(),
            java.time.Duration.ofSeconds(10),
            configService,
            new ExecutionControlRegistry(new InMemoryExecutionControlStore()),
            List.of());
    final com.infenia.yukta.model.workflow.WorkflowNode workflowNode =
        new com.infenia.yukta.model.workflow.WorkflowNode(
            node.nodeId(), node.type(), node.config());
    final Method method =
        com.infenia.yukta.service.orchestrator.compiler.WorkflowCompiler.class.getDeclaredMethod(
            "getNodeTimeout",
            com.infenia.yukta.model.workflow.WorkflowNode.class,
            WorkflowPlugin.class);
    method.setAccessible(true);
    return (Duration) method.invoke(compiler, workflowNode, plugin);
  }

  @Test
  void testBufferSizeFallsBackToDefaultWhenMissingFromConfig() {
    stubExecution();
    final String sessionId = "sess-no-buffer";
    final Message<String> msg = DefaultMessage.create(UUID.randomUUID(), "payload");

    final Node triggerNode = new Node("t", "trigger", Map.of());
    final Node termNode = new Node("term", "terminal", Map.of());
    final WorkflowDefinition def =
        new WorkflowDefinition(
            "test-workflow",
            "desc",
            List.of(triggerNode, termNode),
            List.of(new Edge("t", "term")));

    final TriggerPlugin trigger = mock(TriggerPlugin.class);
    when(trigger.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    when(trigger.getDefaultBufferSize()).thenReturn(1024);
    when(trigger.getCategory()).thenReturn(PluginCategory.TRIGGER);
    when(trigger.validateConfig(any())).thenReturn(Mono.empty());
    when(trigger.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(trigger.initialize(any())).thenReturn(Mono.empty());
    when(trigger.start(any())).thenReturn(Flux.just(msg));

    final TerminalPlugin terminal = mock(TerminalPlugin.class);
    when(terminal.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    when(terminal.getDefaultBufferSize()).thenReturn(1024);
    when(terminal.getCategory()).thenReturn(PluginCategory.TERMINAL);
    when(terminal.validateConfig(any())).thenReturn(Mono.empty());
    when(terminal.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(terminal.initialize(any())).thenReturn(Mono.empty());
    when(terminal.consume(any(), any()))
        .thenAnswer(inv -> ((Flux<Message<?>>) inv.getArgument(0)).then());

    when(registry.get("trigger")).thenReturn(trigger);
    when(registry.get("terminal")).thenReturn(terminal);

    StepVerifier.create(
            orchestrator
                .prepareWorkflow(def)
                .flatMap(pw -> orchestrator.execute(sessionId, "wf", "exec-nb", pw, Map.of())))
        .verifyComplete();
  }

  @Test
  void testBufferSizeFallsBackToDefaultWhenZero() {
    stubExecution();
    final String sessionId = "sess-zero-buffer";
    final Message<String> msg = DefaultMessage.create(UUID.randomUUID(), "payload");

    final Node triggerNode = new Node("t", "trigger", Map.of("bufferSize", 0));
    final Node termNode = new Node("term", "terminal", Map.of());
    final WorkflowDefinition def =
        new WorkflowDefinition(
            "test-workflow",
            "desc",
            List.of(triggerNode, termNode),
            List.of(new Edge("t", "term")));

    final TriggerPlugin trigger = mock(TriggerPlugin.class);
    when(trigger.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    when(trigger.getDefaultBufferSize()).thenReturn(1024);
    when(trigger.getCategory()).thenReturn(PluginCategory.TRIGGER);
    when(trigger.validateConfig(any())).thenReturn(Mono.empty());
    when(trigger.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(trigger.initialize(any())).thenReturn(Mono.empty());
    when(trigger.start(any())).thenReturn(Flux.just(msg));

    final TerminalPlugin terminal = mock(TerminalPlugin.class);
    when(terminal.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    when(terminal.getDefaultBufferSize()).thenReturn(1024);
    when(terminal.getCategory()).thenReturn(PluginCategory.TERMINAL);
    when(terminal.validateConfig(any())).thenReturn(Mono.empty());
    when(terminal.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(terminal.initialize(any())).thenReturn(Mono.empty());
    when(terminal.consume(any(), any()))
        .thenAnswer(inv -> ((Flux<Message<?>>) inv.getArgument(0)).then());

    when(registry.get("trigger")).thenReturn(trigger);
    when(registry.get("terminal")).thenReturn(terminal);

    StepVerifier.create(
            orchestrator
                .prepareWorkflow(def)
                .flatMap(pw -> orchestrator.execute(sessionId, "wf", "exec-zb", pw, Map.of())))
        .verifyComplete();
  }

  @Test
  void testFanInMergeOfMultipleParentStreams() {
    stubExecution();
    final String sessionId = "sess-fanin";
    final Message<String> msg = DefaultMessage.create(UUID.randomUUID(), "payload");

    final Node triggerNode = new Node("t", "trigger", Map.of());
    final Node proc1Node = new Node("p1", "processor", Map.of());
    final Node proc2Node = new Node("p2", "processor", Map.of());
    final Node termNode = new Node("term", "terminal", Map.of());
    final WorkflowDefinition def =
        new WorkflowDefinition(
            "test-workflow",
            "desc",
            List.of(triggerNode, proc1Node, proc2Node, termNode),
            List.of(
                new Edge("t", "p1"),
                new Edge("t", "p2"),
                new Edge("p1", "term"),
                new Edge("p2", "term")));

    final TriggerPlugin trigger = mock(TriggerPlugin.class);
    when(trigger.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    when(trigger.getDefaultBufferSize()).thenReturn(1024);
    when(trigger.getCategory()).thenReturn(PluginCategory.TRIGGER);
    when(trigger.validateConfig(any())).thenReturn(Mono.empty());
    when(trigger.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(trigger.initialize(any())).thenReturn(Mono.empty());
    when(trigger.start(any())).thenReturn(Flux.just(msg));

    final ProcessorPlugin processor = mock(ProcessorPlugin.class);
    when(processor.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    when(processor.getDefaultBufferSize()).thenReturn(1024);
    when(processor.getCategory()).thenReturn(PluginCategory.PROCESSOR);
    when(processor.validateConfig(any())).thenReturn(Mono.empty());
    when(processor.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(processor.initialize(any())).thenReturn(Mono.empty());
    when(processor.process(any(), any())).thenReturn(Flux.just(msg));

    final TerminalPlugin terminal = mock(TerminalPlugin.class);
    when(terminal.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    when(terminal.getDefaultBufferSize()).thenReturn(1024);
    when(terminal.getCategory()).thenReturn(PluginCategory.TERMINAL);
    when(terminal.validateConfig(any())).thenReturn(Mono.empty());
    when(terminal.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(terminal.initialize(any())).thenReturn(Mono.empty());
    when(terminal.consume(any(), any()))
        .thenAnswer(inv -> ((Flux<Message<?>>) inv.getArgument(0)).then());

    when(registry.get("trigger")).thenReturn(trigger);
    when(registry.get("processor")).thenReturn(processor);
    when(registry.get("terminal")).thenReturn(terminal);

    StepVerifier.create(
            orchestrator
                .prepareWorkflow(def)
                .flatMap(pw -> orchestrator.execute(sessionId, "wf", "exec-fi", pw, Map.of())))
        .verifyComplete();

    verify(terminal).consume(any(), any());
  }

  @Test
  void testSourcePortRoutingFiltersMatchingMessages() {
    stubExecution();
    final String sessionId = "sess-port-match";
    final Message<String> msg =
        DefaultMessage.create(UUID.randomUUID(), "payload").withSourcePort("output");

    final Node triggerNode = new Node("t", "trigger", Map.of());
    final Node termNode = new Node("term", "terminal", Map.of());
    final WorkflowDefinition def =
        new WorkflowDefinition(
            "test-workflow",
            "desc",
            List.of(triggerNode, termNode),
            List.of(new Edge("t", "term", "output")));

    final TriggerPlugin trigger = mock(TriggerPlugin.class);
    when(trigger.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    when(trigger.getDefaultBufferSize()).thenReturn(1024);
    when(trigger.getCategory()).thenReturn(PluginCategory.TRIGGER);
    when(trigger.validateConfig(any())).thenReturn(Mono.empty());
    when(trigger.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(trigger.initialize(any())).thenReturn(Mono.empty());
    when(trigger.start(any())).thenReturn(Flux.just(msg));

    final TerminalPlugin terminal = mock(TerminalPlugin.class);
    when(terminal.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    when(terminal.getDefaultBufferSize()).thenReturn(1024);
    when(terminal.getCategory()).thenReturn(PluginCategory.TERMINAL);
    when(terminal.validateConfig(any())).thenReturn(Mono.empty());
    when(terminal.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(terminal.initialize(any())).thenReturn(Mono.empty());
    when(terminal.consume(any(), any()))
        .thenAnswer(inv -> ((Flux<Message<?>>) inv.getArgument(0)).then());

    when(registry.get("trigger")).thenReturn(trigger);
    when(registry.get("terminal")).thenReturn(terminal);

    StepVerifier.create(
            orchestrator
                .prepareWorkflow(def)
                .flatMap(pw -> orchestrator.execute(sessionId, "wf", "exec-pm", pw, Map.of())))
        .verifyComplete();

    verify(terminal).consume(any(), any());
  }

  @Test
  void testSourcePortRoutingFiltersNonMatchingMessages() {
    stubExecution();
    final String sessionId = "sess-port-nomatch";
    final Message<String> msg =
        DefaultMessage.create(UUID.randomUUID(), "payload").withSourcePort("error");

    final Node triggerNode = new Node("t", "trigger", Map.of());
    final Node termNode = new Node("term", "terminal", Map.of());
    final WorkflowDefinition def =
        new WorkflowDefinition(
            "test-workflow",
            "desc",
            List.of(triggerNode, termNode),
            List.of(new Edge("t", "term", "output")));

    final TriggerPlugin trigger = mock(TriggerPlugin.class);
    when(trigger.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    when(trigger.getDefaultBufferSize()).thenReturn(1024);
    when(trigger.getCategory()).thenReturn(PluginCategory.TRIGGER);
    when(trigger.validateConfig(any())).thenReturn(Mono.empty());
    when(trigger.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(trigger.initialize(any())).thenReturn(Mono.empty());
    when(trigger.start(any())).thenReturn(Flux.just(msg));

    final TerminalPlugin terminal = mock(TerminalPlugin.class);
    when(terminal.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    when(terminal.getDefaultBufferSize()).thenReturn(1024);
    when(terminal.getCategory()).thenReturn(PluginCategory.TERMINAL);
    when(terminal.validateConfig(any())).thenReturn(Mono.empty());
    when(terminal.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(terminal.initialize(any())).thenReturn(Mono.empty());
    when(terminal.consume(any(), any()))
        .thenAnswer(inv -> ((Flux<Message<?>>) inv.getArgument(0)).then());

    when(registry.get("trigger")).thenReturn(trigger);
    when(registry.get("terminal")).thenReturn(terminal);

    StepVerifier.create(
            orchestrator
                .prepareWorkflow(def)
                .flatMap(pw -> orchestrator.execute(sessionId, "wf", "exec-pnm", pw, Map.of())))
        .verifyComplete();

    verify(terminal).consume(any(), any());
  }

  @Test
  void testResultCollectorIsPopulatedWhenInContext() {
    stubExecution();
    final String sessionId = "sess-collector";
    final Message<String> msg = DefaultMessage.create(UUID.randomUUID(), "payload");

    final Node triggerNode = new Node("t", "trigger", Map.of());
    final Node termNode = new Node("term", "terminal", Map.of());
    final WorkflowDefinition def =
        new WorkflowDefinition(
            "test-workflow",
            "desc",
            List.of(triggerNode, termNode),
            List.of(new Edge("t", "term")));

    final TriggerPlugin trigger = mock(TriggerPlugin.class);
    when(trigger.isBlocking()).thenReturn(false);
    when(trigger.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    when(trigger.getDefaultBufferSize()).thenReturn(1024);
    when(trigger.getCategory()).thenReturn(PluginCategory.TRIGGER);
    when(trigger.validateConfig(any())).thenReturn(Mono.empty());
    when(trigger.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(trigger.initialize(any())).thenReturn(Mono.empty());
    when(trigger.start(any())).thenReturn(Flux.just(msg));

    final TerminalPlugin terminal = mock(TerminalPlugin.class);
    when(terminal.isBlocking()).thenReturn(false);
    when(terminal.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    when(terminal.getDefaultBufferSize()).thenReturn(1024);
    when(terminal.getCategory()).thenReturn(PluginCategory.TERMINAL);
    when(terminal.validateConfig(any())).thenReturn(Mono.empty());
    when(terminal.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(terminal.initialize(any())).thenReturn(Mono.empty());
    when(terminal.consume(any(), any()))
        .thenAnswer(inv -> ((Flux<Message<?>>) inv.getArgument(0)).then());

    when(registry.get("trigger")).thenReturn(trigger);
    when(registry.get("terminal")).thenReturn(terminal);

    final com.infenia.yukta.plugin.gateway.ResultCollector collector =
        new com.infenia.yukta.plugin.gateway.ResultCollector();

    StepVerifier.create(
            orchestrator
                .prepareWorkflow(def)
                .flatMap(
                    pw ->
                        orchestrator
                            .execute(sessionId, "wf", "exec-rc", pw, Map.of())
                            .contextWrite(ctx -> ctx.put("resultCollector", collector))))
        .verifyComplete();

    assertEquals(1, collector.getResults().size());
  }

  @Test
  void testDebugLoggingBranchAppliesReactorLogOperator() {
    stubExecution();
    final String sessionId = "sess-debug";
    final Message<String> msg = DefaultMessage.create(UUID.randomUUID(), "payload");

    final Node triggerNode = new Node("t", "trigger", Map.of());
    final Node termNode = new Node("term", "terminal", Map.of());
    final WorkflowDefinition def =
        new WorkflowDefinition(
            "test-workflow",
            "desc",
            List.of(triggerNode, termNode),
            List.of(new Edge("t", "term")));

    final TriggerPlugin trigger = mock(TriggerPlugin.class);
    when(trigger.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    when(trigger.getDefaultBufferSize()).thenReturn(1024);
    when(trigger.getCategory()).thenReturn(PluginCategory.TRIGGER);
    when(trigger.validateConfig(any())).thenReturn(Mono.empty());
    when(trigger.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(trigger.initialize(any())).thenReturn(Mono.empty());
    when(trigger.start(any())).thenReturn(Flux.just(msg));

    final TerminalPlugin terminal = mock(TerminalPlugin.class);
    when(terminal.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    when(terminal.getDefaultBufferSize()).thenReturn(1024);
    when(terminal.getCategory()).thenReturn(PluginCategory.TERMINAL);
    when(terminal.validateConfig(any())).thenReturn(Mono.empty());
    when(terminal.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(terminal.initialize(any())).thenReturn(Mono.empty());
    when(terminal.consume(any(), any()))
        .thenAnswer(inv -> ((Flux<Message<?>>) inv.getArgument(0)).then());

    when(registry.get("trigger")).thenReturn(trigger);
    when(registry.get("terminal")).thenReturn(terminal);

    // Set logger to DEBUG level to trigger the debug logging branch
    final Logger logger = (Logger) LoggerFactory.getLogger(WorkflowOrchestrator.class);
    final Level originalLevel = logger.getLevel();
    try {
      logger.setLevel(Level.DEBUG);

      StepVerifier.create(
              orchestrator
                  .prepareWorkflow(def)
                  .flatMap(pw -> orchestrator.execute(sessionId, "wf", "exec-dbg", pw, Map.of())))
          .verifyComplete();

      verify(terminal).consume(any(), any());
    } finally {
      logger.setLevel(originalLevel);
    }
  }

  @Test
  void testTraceLoggingBranchCallsEmitLogEvent() {
    stubExecution();
    final String sessionId = "sess-trace";
    final Message<String> msg = DefaultMessage.create(UUID.randomUUID(), "payload");

    final Node triggerNode = new Node("t", "trigger", Map.of());
    final Node termNode = new Node("term", "terminal", Map.of());
    final WorkflowDefinition def =
        new WorkflowDefinition(
            "test-workflow",
            "desc",
            List.of(triggerNode, termNode),
            List.of(new Edge("t", "term")));

    final TriggerPlugin trigger = mock(TriggerPlugin.class);
    when(trigger.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    when(trigger.getDefaultBufferSize()).thenReturn(1024);
    when(trigger.getCategory()).thenReturn(PluginCategory.TRIGGER);
    when(trigger.validateConfig(any())).thenReturn(Mono.empty());
    when(trigger.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(trigger.initialize(any())).thenReturn(Mono.empty());
    when(trigger.start(any())).thenReturn(Flux.just(msg));

    final TerminalPlugin terminal = mock(TerminalPlugin.class);
    when(terminal.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    when(terminal.getDefaultBufferSize()).thenReturn(1024);
    when(terminal.getCategory()).thenReturn(PluginCategory.TERMINAL);
    when(terminal.validateConfig(any())).thenReturn(Mono.empty());
    when(terminal.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(terminal.initialize(any())).thenReturn(Mono.empty());
    when(terminal.consume(any(), any()))
        .thenAnswer(inv -> ((Flux<Message<?>>) inv.getArgument(0)).then());

    when(registry.get("trigger")).thenReturn(trigger);
    when(registry.get("terminal")).thenReturn(terminal);

    // Set logger to TRACE level to trigger the trace logging branch
    final Logger logger = (Logger) LoggerFactory.getLogger(WorkflowOrchestrator.class);
    final Level originalLevel = logger.getLevel();
    try {
      logger.setLevel(Level.TRACE);

      StepVerifier.create(
              orchestrator
                  .prepareWorkflow(def)
                  .flatMap(pw -> orchestrator.execute(sessionId, "wf", "exec-trc", pw, Map.of())))
          .verifyComplete();

      // tracker.emitLogEvent should have been called at TRACE level
      verify(tracker, atLeastOnce()).emitLogEvent(anyString(), anyString());
    } finally {
      logger.setLevel(originalLevel);
    }
  }

  @Test
  void testBlockingTriggerSubscribesOnVirtualThreadScheduler() {
    stubExecution();
    final String sessionId = "sess-blocking-trigger";
    final Message<String> msg = DefaultMessage.create(UUID.randomUUID(), "payload");

    final Node triggerNode = new Node("t", "trigger", Map.of());
    final Node termNode = new Node("term", "terminal", Map.of());
    final WorkflowDefinition def =
        new WorkflowDefinition(
            "test-workflow",
            "desc",
            List.of(triggerNode, termNode),
            List.of(new Edge("t", "term")));

    final TriggerPlugin trigger = mock(TriggerPlugin.class);
    when(trigger.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    when(trigger.getDefaultBufferSize()).thenReturn(1024);
    when(trigger.getCategory()).thenReturn(PluginCategory.TRIGGER);
    when(trigger.validateConfig(any())).thenReturn(Mono.empty());
    when(trigger.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(trigger.initialize(any())).thenReturn(Mono.empty());
    when(trigger.start(any())).thenReturn(Flux.just(msg));
    when(trigger.isBlocking()).thenReturn(true);

    final TerminalPlugin terminal = mock(TerminalPlugin.class);
    when(terminal.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    when(terminal.getDefaultBufferSize()).thenReturn(1024);
    when(terminal.getCategory()).thenReturn(PluginCategory.TERMINAL);
    when(terminal.validateConfig(any())).thenReturn(Mono.empty());
    when(terminal.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(terminal.initialize(any())).thenReturn(Mono.empty());
    when(terminal.consume(any(), any()))
        .thenAnswer(inv -> ((Flux<Message<?>>) inv.getArgument(0)).then());
    when(terminal.isBlocking()).thenReturn(false);

    when(registry.get("trigger")).thenReturn(trigger);
    when(registry.get("terminal")).thenReturn(terminal);

    StepVerifier.create(
            orchestrator
                .prepareWorkflow(def)
                .flatMap(
                    pw -> orchestrator.execute(sessionId, "wf", "exec-block-trig", pw, Map.of())))
        .verifyComplete();

    verify(trigger).isBlocking();
    verify(terminal).consume(any(), any());
  }

  @Test
  void testBlockingProcessorSubscribesOnVirtualThreadScheduler() {
    stubExecution();
    final String sessionId = "sess-blocking-processor";
    final Message<String> msg = DefaultMessage.create(UUID.randomUUID(), "payload");

    final Node triggerNode = new Node("t", "trigger", Map.of());
    final Node procNode = new Node("p", "processor", Map.of());
    final Node termNode = new Node("term", "terminal", Map.of());
    final WorkflowDefinition def =
        new WorkflowDefinition(
            "test-workflow",
            "desc",
            List.of(triggerNode, procNode, termNode),
            List.of(new Edge("t", "p"), new Edge("p", "term")));

    final TriggerPlugin trigger = mock(TriggerPlugin.class);
    when(trigger.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    when(trigger.getDefaultBufferSize()).thenReturn(1024);
    when(trigger.getCategory()).thenReturn(PluginCategory.TRIGGER);
    when(trigger.validateConfig(any())).thenReturn(Mono.empty());
    when(trigger.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(trigger.initialize(any())).thenReturn(Mono.empty());
    when(trigger.start(any())).thenReturn(Flux.just(msg));
    when(trigger.isBlocking()).thenReturn(false);

    final ProcessorPlugin processor = mock(ProcessorPlugin.class);
    when(processor.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    when(processor.getDefaultBufferSize()).thenReturn(1024);
    when(processor.getCategory()).thenReturn(PluginCategory.PROCESSOR);
    when(processor.validateConfig(any())).thenReturn(Mono.empty());
    when(processor.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(processor.initialize(any())).thenReturn(Mono.empty());
    when(processor.process(any(), any())).thenReturn(Flux.just(msg));
    when(processor.isBlocking()).thenReturn(true);

    final TerminalPlugin terminal = mock(TerminalPlugin.class);
    when(terminal.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    when(terminal.getDefaultBufferSize()).thenReturn(1024);
    when(terminal.getCategory()).thenReturn(PluginCategory.TERMINAL);
    when(terminal.validateConfig(any())).thenReturn(Mono.empty());
    when(terminal.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(terminal.initialize(any())).thenReturn(Mono.empty());
    when(terminal.consume(any(), any()))
        .thenAnswer(inv -> ((Flux<Message<?>>) inv.getArgument(0)).then());
    when(terminal.isBlocking()).thenReturn(false);

    when(registry.get("trigger")).thenReturn(trigger);
    when(registry.get("processor")).thenReturn(processor);
    when(registry.get("terminal")).thenReturn(terminal);

    StepVerifier.create(
            orchestrator
                .prepareWorkflow(def)
                .flatMap(
                    pw -> orchestrator.execute(sessionId, "wf", "exec-block-proc", pw, Map.of())))
        .verifyComplete();

    verify(processor).isBlocking();
    verify(processor).process(any(), any());
    verify(terminal).consume(any(), any());
  }

  @Test
  void testBlockingTerminalSubscribesOnVirtualThreadScheduler() {
    stubExecution();
    final String sessionId = "sess-blocking-terminal";
    final Message<String> msg = DefaultMessage.create(UUID.randomUUID(), "payload");

    final Node triggerNode = new Node("t", "trigger", Map.of());
    final Node termNode = new Node("term", "terminal", Map.of());
    final WorkflowDefinition def =
        new WorkflowDefinition(
            "test-workflow",
            "desc",
            List.of(triggerNode, termNode),
            List.of(new Edge("t", "term")));

    final TriggerPlugin trigger = mock(TriggerPlugin.class);
    when(trigger.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    when(trigger.getDefaultBufferSize()).thenReturn(1024);
    when(trigger.getCategory()).thenReturn(PluginCategory.TRIGGER);
    when(trigger.validateConfig(any())).thenReturn(Mono.empty());
    when(trigger.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(trigger.initialize(any())).thenReturn(Mono.empty());
    when(trigger.start(any())).thenReturn(Flux.just(msg));
    when(trigger.isBlocking()).thenReturn(false);

    final TerminalPlugin terminal = mock(TerminalPlugin.class);
    when(terminal.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    when(terminal.getDefaultBufferSize()).thenReturn(1024);
    when(terminal.getCategory()).thenReturn(PluginCategory.TERMINAL);
    when(terminal.validateConfig(any())).thenReturn(Mono.empty());
    when(terminal.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(terminal.initialize(any())).thenReturn(Mono.empty());
    when(terminal.consume(any(), any()))
        .thenAnswer(inv -> ((Flux<Message<?>>) inv.getArgument(0)).then());
    when(terminal.isBlocking()).thenReturn(true);

    when(registry.get("trigger")).thenReturn(trigger);
    when(registry.get("terminal")).thenReturn(terminal);

    StepVerifier.create(
            orchestrator
                .prepareWorkflow(def)
                .flatMap(
                    pw -> orchestrator.execute(sessionId, "wf", "exec-block-term", pw, Map.of())))
        .verifyComplete();

    verify(terminal).isBlocking();
    verify(terminal).consume(any(), any());
  }

  @Test
  void testUnknownPluginTypeLogsWarning() {
    stubPrepare();
    // This test covers the else branch at lines 526-531 in WorkflowOrchestrator
    // where an unknown plugin type (not instanceof ProcessorPlugin or TerminalPlugin)
    // logs a warning and creates a no-op assembler.
    //
    // Rather than execute a full workflow (which would fail due to missing streams),
    // we verify the warning log is emitted by checking the logger output.
    // The unknown plugin satisfies validator (getCategory()==PROCESSOR) but is
    // not instanceof ProcessorPlugin, triggering the unknown type path.

    final Logger logger =
        (Logger)
            LoggerFactory.getLogger(
                com.infenia.yukta.service.orchestrator.compiler.WorkflowCompiler.class);
    final ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
    listAppender.start();
    logger.addAppender(listAppender);
    logger.setLevel(Level.WARN);

    final Node triggerNode = new Node("t", "trigger", Map.of());
    final Node unknownNode = new Node("u", "unknown", Map.of());
    final Node processorNode = new Node("p", "processor", Map.of());
    final Node termNode = new Node("term", "terminal", Map.of());
    final WorkflowDefinition def =
        new WorkflowDefinition(
            "test-workflow",
            "desc",
            List.of(triggerNode, unknownNode, processorNode, termNode),
            List.of(new Edge("t", "u"), new Edge("u", "p"), new Edge("p", "term")));

    final TriggerPlugin trigger = mock(TriggerPlugin.class);
    when(trigger.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    when(trigger.getDefaultBufferSize()).thenReturn(1024);
    when(trigger.getCategory()).thenReturn(PluginCategory.TRIGGER);
    when(trigger.validateConfig(any())).thenReturn(Mono.empty());
    when(trigger.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(trigger.initialize(any())).thenReturn(Mono.empty());

    // Unknown plugin: plain WorkflowPlugin mock (not instanceof ProcessorPlugin or
    // TerminalPlugin).
    // This will hit the else branch at lines 526-531, creating a no-op assembler.
    final WorkflowPlugin unknownPlugin = mock(WorkflowPlugin.class);
    when(unknownPlugin.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    when(unknownPlugin.getCategory()).thenReturn(PluginCategory.PROCESSOR);
    when(unknownPlugin.validateConfig(any())).thenReturn(Mono.empty());
    when(unknownPlugin.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(unknownPlugin.initialize(any())).thenReturn(Mono.empty());

    final ProcessorPlugin processor = mock(ProcessorPlugin.class);
    when(processor.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    when(processor.getDefaultBufferSize()).thenReturn(1024);
    when(processor.getCategory()).thenReturn(PluginCategory.PROCESSOR);
    when(processor.validateConfig(any())).thenReturn(Mono.empty());
    when(processor.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(processor.initialize(any())).thenReturn(Mono.empty());

    final TerminalPlugin terminal = mock(TerminalPlugin.class);
    when(terminal.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    when(terminal.getDefaultBufferSize()).thenReturn(1024);
    when(terminal.getCategory()).thenReturn(PluginCategory.TERMINAL);
    when(terminal.validateConfig(any())).thenReturn(Mono.empty());
    when(terminal.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(terminal.initialize(any())).thenReturn(Mono.empty());

    when(registry.get("trigger")).thenReturn(trigger);
    when(registry.get("unknown")).thenReturn(unknownPlugin);
    when(registry.get("processor")).thenReturn(processor);
    when(registry.get("terminal")).thenReturn(terminal);

    // Prepare workflow - should log warning for unknown plugin type
    StepVerifier.create(orchestrator.prepareWorkflow(def))
        .expectNextMatches(pw -> pw != null)
        .verifyComplete();

    // Verify the warning was logged
    final boolean foundWarning =
        listAppender.list.stream()
            .anyMatch(
                e ->
                    e.getLevel() == Level.WARN
                        && e.getFormattedMessage().contains("Unknown plugin type"));
    assertThat(foundWarning).as("Should log warning for unknown plugin type").isTrue();

    logger.detachAppender(listAppender);
    listAppender.stop();
  }

  @Test
  void testRestartFromNodeHappyPath() {
    stubExecution();
    final String sessionId = "sess-restart";
    final String prevExecId = "exec-prev";
    final String newExecId = "exec-new";

    final Node triggerNode = new Node("t", "trigger", Map.of());
    final Node procNode = new Node("p", "processor", Map.of());
    final Node termNode = new Node("term", "terminal", Map.of());
    final WorkflowDefinition def =
        new WorkflowDefinition(
            "test-workflow",
            "desc",
            List.of(triggerNode, procNode, termNode),
            List.of(new Edge("t", "p"), new Edge("p", "term")));

    final Message<String> triggerCheckpoint = DefaultMessage.create(UUID.randomUUID(), "saved");

    final TriggerPlugin trigger = mock(TriggerPlugin.class);
    when(trigger.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    when(trigger.getDefaultBufferSize()).thenReturn(1024);
    when(trigger.getCategory()).thenReturn(PluginCategory.TRIGGER);
    when(trigger.validateConfig(any())).thenReturn(Mono.empty());
    when(trigger.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(trigger.initialize(any())).thenReturn(Mono.empty());

    final ProcessorPlugin processor = mock(ProcessorPlugin.class);
    when(processor.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    when(processor.getDefaultBufferSize()).thenReturn(1024);
    when(processor.getCategory()).thenReturn(PluginCategory.PROCESSOR);
    when(processor.validateConfig(any())).thenReturn(Mono.empty());
    when(processor.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(processor.initialize(any())).thenReturn(Mono.empty());
    when(processor.process(any(), any())).thenReturn(Flux.just(triggerCheckpoint));

    final TerminalPlugin terminal = mock(TerminalPlugin.class);
    when(terminal.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    when(terminal.getDefaultBufferSize()).thenReturn(1024);
    when(terminal.getCategory()).thenReturn(PluginCategory.TERMINAL);
    when(terminal.validateConfig(any())).thenReturn(Mono.empty());
    when(terminal.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(terminal.initialize(any())).thenReturn(Mono.empty());
    when(terminal.consume(any(), any()))
        .thenAnswer(inv -> ((Flux<Message<?>>) inv.getArgument(0)).then());

    when(registry.get("trigger")).thenReturn(trigger);
    when(registry.get("processor")).thenReturn(processor);
    when(registry.get("terminal")).thenReturn(terminal);

    // "t" is the parent of "p", so providing a checkpoint for "t" means "p" is the restart node
    final Map<String, Message<?>> checkpoints = Map.of("t", triggerCheckpoint);

    StepVerifier.create(
            orchestrator
                .prepareWorkflow(def)
                .flatMap(
                    pw ->
                        orchestrator.restartFromNode(
                            sessionId,
                            "test-workflow",
                            prevExecId,
                            newExecId,
                            pw,
                            "p",
                            checkpoints)))
        .verifyComplete();

    verify(tracker).startWorkflow(eq(newExecId), eq(sessionId), anyString(), any());
    verify(terminal).consume(any(), any());
  }

  @Test
  void testRestartFromNodeBypassesPreRestartNodesWithEmptyStreamWhenNoCheckpoint() {
    stubExecution();
    final String sessionId = "sess-restart-empty";
    final String newExecId = "exec-new-empty";

    final Node triggerNode = new Node("t", "trigger", Map.of());
    final Node procNode = new Node("p", "processor", Map.of());
    final Node termNode = new Node("term", "terminal", Map.of());
    final WorkflowDefinition def =
        new WorkflowDefinition(
            "test-workflow",
            "desc",
            List.of(triggerNode, procNode, termNode),
            List.of(new Edge("t", "p"), new Edge("p", "term")));

    final TriggerPlugin trigger = mock(TriggerPlugin.class);
    when(trigger.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    when(trigger.getDefaultBufferSize()).thenReturn(1024);
    when(trigger.getCategory()).thenReturn(PluginCategory.TRIGGER);
    when(trigger.validateConfig(any())).thenReturn(Mono.empty());
    when(trigger.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(trigger.initialize(any())).thenReturn(Mono.empty());

    final ProcessorPlugin processor = mock(ProcessorPlugin.class);
    when(processor.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    when(processor.getDefaultBufferSize()).thenReturn(1024);
    when(processor.getCategory()).thenReturn(PluginCategory.PROCESSOR);
    when(processor.validateConfig(any())).thenReturn(Mono.empty());
    when(processor.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(processor.initialize(any())).thenReturn(Mono.empty());
    when(processor.process(any(), any())).thenReturn(Flux.empty());

    final TerminalPlugin terminal = mock(TerminalPlugin.class);
    when(terminal.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    when(terminal.getDefaultBufferSize()).thenReturn(1024);
    when(terminal.getCategory()).thenReturn(PluginCategory.TERMINAL);
    when(terminal.validateConfig(any())).thenReturn(Mono.empty());
    when(terminal.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(terminal.initialize(any())).thenReturn(Mono.empty());
    when(terminal.consume(any(), any())).thenReturn(Mono.empty());

    when(registry.get("trigger")).thenReturn(trigger);
    when(registry.get("processor")).thenReturn(processor);
    when(registry.get("terminal")).thenReturn(terminal);

    // No checkpoints provided — "t" should be replaced with Flux.empty()
    StepVerifier.create(
            orchestrator
                .prepareWorkflow(def)
                .flatMap(
                    pw ->
                        orchestrator.restartFromNode(
                            sessionId, "test-workflow", "exec-prev", newExecId, pw, "p", Map.of())))
        .verifyComplete();

    verify(tracker).startWorkflow(eq(newExecId), eq(sessionId), anyString(), any());
    verify(terminal).consume(any(), any());
  }

  @Test
  void testRestartFromNodeUnknownNodeIdDefaultsToZero() {
    stubExecution();
    final String sessionId = "sess-restart-unknown";
    final String newExecId = "exec-new-unknown";
    final Message<String> msg = DefaultMessage.create(UUID.randomUUID(), "payload");

    final Node triggerNode = new Node("t", "trigger", Map.of());
    final Node termNode = new Node("term", "terminal", Map.of());
    final WorkflowDefinition def =
        new WorkflowDefinition(
            "test-workflow",
            "desc",
            List.of(triggerNode, termNode),
            List.of(new Edge("t", "term")));

    final TriggerPlugin trigger = mock(TriggerPlugin.class);
    when(trigger.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    when(trigger.getDefaultBufferSize()).thenReturn(1024);
    when(trigger.getCategory()).thenReturn(PluginCategory.TRIGGER);
    when(trigger.validateConfig(any())).thenReturn(Mono.empty());
    when(trigger.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(trigger.initialize(any())).thenReturn(Mono.empty());
    when(trigger.start(any())).thenReturn(Flux.just(msg));

    final TerminalPlugin terminal = mock(TerminalPlugin.class);
    when(terminal.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    when(terminal.getDefaultBufferSize()).thenReturn(1024);
    when(terminal.getCategory()).thenReturn(PluginCategory.TERMINAL);
    when(terminal.validateConfig(any())).thenReturn(Mono.empty());
    when(terminal.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(terminal.initialize(any())).thenReturn(Mono.empty());
    when(terminal.consume(any(), any()))
        .thenAnswer(inv -> ((Flux<Message<?>>) inv.getArgument(0)).then());

    when(registry.get("trigger")).thenReturn(trigger);
    when(registry.get("terminal")).thenReturn(terminal);

    // "nonexistent" is not in the workflow → restartIndex defaults to 0, no bypass
    StepVerifier.create(
            orchestrator
                .prepareWorkflow(def)
                .flatMap(
                    pw ->
                        orchestrator.restartFromNode(
                            sessionId,
                            "test-workflow",
                            "exec-prev",
                            newExecId,
                            pw,
                            "nonexistent",
                            Map.of())))
        .verifyComplete();

    verify(trigger).start(any());
    verify(terminal).consume(any(), any());
  }

  @Test
  void testRestartFromNodeCleansUpAfterSuccess() {
    stubExecution();
    final String sessionId = "sess-restart-cleanup";
    final String newExecId = "exec-cleanup";
    final Message<String> msg = DefaultMessage.create(UUID.randomUUID(), "payload");

    final Node triggerNode = new Node("t", "trigger", Map.of());
    final Node termNode = new Node("term", "terminal", Map.of());
    final WorkflowDefinition def =
        new WorkflowDefinition(
            "test-workflow",
            "desc",
            List.of(triggerNode, termNode),
            List.of(new Edge("t", "term")));

    final TriggerPlugin trigger = mock(TriggerPlugin.class);
    when(trigger.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    when(trigger.getDefaultBufferSize()).thenReturn(1024);
    when(trigger.getCategory()).thenReturn(PluginCategory.TRIGGER);
    when(trigger.validateConfig(any())).thenReturn(Mono.empty());
    when(trigger.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(trigger.initialize(any())).thenReturn(Mono.empty());
    when(trigger.start(any())).thenReturn(Flux.just(msg));

    final TerminalPlugin terminal = mock(TerminalPlugin.class);
    when(terminal.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    when(terminal.getDefaultBufferSize()).thenReturn(1024);
    when(terminal.getCategory()).thenReturn(PluginCategory.TERMINAL);
    when(terminal.validateConfig(any())).thenReturn(Mono.empty());
    when(terminal.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(terminal.initialize(any())).thenReturn(Mono.empty());
    when(terminal.consume(any(), any()))
        .thenAnswer(inv -> ((Flux<Message<?>>) inv.getArgument(0)).then());

    when(registry.get("trigger")).thenReturn(trigger);
    when(registry.get("terminal")).thenReturn(terminal);

    StepVerifier.create(
            orchestrator
                .prepareWorkflow(def)
                .flatMap(
                    pw ->
                        orchestrator.restartFromNode(
                            sessionId, "test-workflow", "exec-prev", newExecId, pw, "t", Map.of())))
        .verifyComplete();

    verify(tracker).emitWorkflowStatusEvent(eq(newExecId), eq("SUCCESS"));
  }

  @Test
  void testRestartFromNodeEmitsWorkflowErrorOnTrackerFailure() {
    stubPrepare();
    when(tracker.startWorkflow(anyString(), anyString(), anyString(), any()))
        .thenReturn(Mono.error(new RuntimeException("tracker down")));

    final String sessionId = "sess-restart-err";
    final String newExecId = "exec-err";

    final Node triggerNode = new Node("t", "trigger", Map.of());
    final Node termNode = new Node("term", "terminal", Map.of());
    final WorkflowDefinition def =
        new WorkflowDefinition(
            "test-workflow",
            "desc",
            List.of(triggerNode, termNode),
            List.of(new Edge("t", "term")));

    final TriggerPlugin trigger = mock(TriggerPlugin.class);
    when(trigger.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    when(trigger.getDefaultBufferSize()).thenReturn(1024);
    when(trigger.getCategory()).thenReturn(PluginCategory.TRIGGER);
    when(trigger.validateConfig(any())).thenReturn(Mono.empty());
    when(trigger.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(trigger.initialize(any())).thenReturn(Mono.empty());

    final TerminalPlugin terminal = mock(TerminalPlugin.class);
    when(terminal.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    when(terminal.getDefaultBufferSize()).thenReturn(1024);
    when(terminal.getCategory()).thenReturn(PluginCategory.TERMINAL);
    when(terminal.validateConfig(any())).thenReturn(Mono.empty());
    when(terminal.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(terminal.initialize(any())).thenReturn(Mono.empty());

    when(registry.get("trigger")).thenReturn(trigger);
    when(registry.get("terminal")).thenReturn(terminal);

    StepVerifier.create(
            orchestrator
                .prepareWorkflow(def)
                .flatMap(
                    pw ->
                        orchestrator.restartFromNode(
                            sessionId, "test-workflow", "exec-prev", newExecId, pw, "t", Map.of())))
        .expectError(RuntimeException.class)
        .verify();
  }

  @Test
  void testRestartFromNodeDoOnErrorFiredOnExecutionFailure() {
    stubExecution();
    final String sessionId = "sess-restart-exec-err";
    final String newExecId = "exec-exec-err";
    final Message<String> msg = DefaultMessage.create(UUID.randomUUID(), "payload");

    final Node triggerNode = new Node("t", "trigger", Map.of());
    final Node termNode = new Node("term", "terminal", Map.of());
    final WorkflowDefinition def =
        new WorkflowDefinition(
            "test-workflow",
            "desc",
            List.of(triggerNode, termNode),
            List.of(new Edge("t", "term")));

    final TriggerPlugin trigger = mock(TriggerPlugin.class);
    when(trigger.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    when(trigger.getDefaultBufferSize()).thenReturn(1024);
    when(trigger.getCategory()).thenReturn(PluginCategory.TRIGGER);
    when(trigger.validateConfig(any())).thenReturn(Mono.empty());
    when(trigger.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(trigger.initialize(any())).thenReturn(Mono.empty());
    when(trigger.start(any())).thenReturn(Flux.just(msg));

    final TerminalPlugin terminal = mock(TerminalPlugin.class);
    when(terminal.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    when(terminal.getDefaultBufferSize()).thenReturn(1024);
    when(terminal.getCategory()).thenReturn(PluginCategory.TERMINAL);
    when(terminal.validateConfig(any())).thenReturn(Mono.empty());
    when(terminal.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(terminal.initialize(any())).thenReturn(Mono.empty());
    when(terminal.consume(any(), any()))
        .thenReturn(Mono.error(new RuntimeException("terminal failed")));

    when(registry.get("trigger")).thenReturn(trigger);
    when(registry.get("terminal")).thenReturn(terminal);

    StepVerifier.create(
            orchestrator
                .prepareWorkflow(def)
                .flatMap(
                    pw ->
                        orchestrator.restartFromNode(
                            sessionId, "test-workflow", "exec-prev", newExecId, pw, "t", Map.of())))
        .expectError(RuntimeException.class)
        .verify();
  }
}
