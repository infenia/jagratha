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
package com.infenia.yukta.service.orchestrator.compiler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infenia.yukta.model.workflow.NodeAssembler;
import com.infenia.yukta.model.workflow.WorkflowDefinition;
import com.infenia.yukta.model.workflow.WorkflowDefinition.Edge;
import com.infenia.yukta.model.workflow.WorkflowDefinition.Node;
import com.infenia.yukta.plugin.core.PluginCategory;
import com.infenia.yukta.plugin.core.WorkflowPlugin;
import com.infenia.yukta.plugin.message.DefaultMessage;
import com.infenia.yukta.plugin.message.Message;
import com.infenia.yukta.plugin.type.ProcessorPlugin;
import com.infenia.yukta.plugin.type.TerminalPlugin;
import com.infenia.yukta.plugin.type.TriggerPlugin;
import com.infenia.yukta.service.TaskTrackerService;
import com.infenia.yukta.service.TopologicalSortService;
import com.infenia.yukta.service.WorkflowRegistry;
import com.infenia.yukta.service.WorkflowValidator;
import com.infenia.yukta.service.control.factory.ExecutionControlFactory;
import com.infenia.yukta.service.control.store.ExecutionControlRegistry;
import com.infenia.yukta.service.control.store.InMemoryExecutionControlStore;
import com.infenia.yukta.service.orchestrator.AssemblyContext;
import com.infenia.yukta.service.orchestrator.WorkflowOrchestrator;
import com.infenia.yukta.service.orchestrator.preparator.WorkflowPreparator;
import com.infenia.yukta.service.orchestrator.strategy.NodeAssemblerStrategy;
import com.infenia.yukta.service.session.SessionConfigStore;
import com.infenia.yukta.service.store.InMemoryNodeCheckpointStore;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class WorkflowCompilerTest {

  private WorkflowRegistry registry;
  private TaskTrackerService tracker;
  private WorkflowValidator validator;
  private SessionConfigStore configService;
  private com.infenia.yukta.plugin.gateway.ControlBusGateway controlBusGateway;
  private WorkflowOrchestrator orchestrator;
  private WorkflowCompiler compiler;
  private ExecutionControlRegistry executionControlRegistry;

  @BeforeEach
  void setUp() {
    registry = mock(WorkflowRegistry.class);
    tracker = mock(TaskTrackerService.class);
    configService = mock(SessionConfigStore.class);
    validator = mock(WorkflowValidator.class);
    controlBusGateway = mock(com.infenia.yukta.plugin.gateway.ControlBusGateway.class);
    executionControlRegistry = new ExecutionControlRegistry(new InMemoryExecutionControlStore());

    when(controlBusGateway.emit(any())).thenReturn(Mono.empty());
    when(tracker.startWorkflow(any(), any(), any(), any())).thenReturn(Mono.empty());
    when(tracker.emitTaskStatusEvent(anyString(), anyString(), anyString(), anyString(), any())).thenReturn(Mono.empty());
    when(tracker.emitWorkflowStatusEvent(anyString(), anyString())).thenReturn(Mono.empty());
    when(configService.getExecutionTimeout(anyString())).thenReturn(Mono.just(3600L));

    compiler =
        new WorkflowCompiler(
            tracker,
            controlBusGateway,
            Schedulers.parallel(),
            Duration.ofSeconds(10),
            configService,
            executionControlRegistry,
            List.of());

    orchestrator =
        new WorkflowOrchestrator(
            tracker,
            executionControlRegistry,
            new ExecutionControlFactory(),
            new InMemoryNodeCheckpointStore(),
            compiler,
            new WorkflowPreparator(
                registry,
                validator,
                new TopologicalSortService(),
                controlBusGateway,
                compiler));
  }

  @Test
  void testFanInFromTwoTriggersToProcessor() {
    String sessionId = "sess-fan-in-1";
    Node t1 = new Node("t1", "trigger", Map.of());
    Node t2 = new Node("t2", "trigger", Map.of());
    Node p1 = new Node("p1", "processor", Map.of());
    Node term = new Node("term", "terminal", Map.of());

    WorkflowDefinition def =
        new WorkflowDefinition(
            "Fan-in Test",
            List.of(t1, t2, p1, term),
            List.of(new Edge("t1", "p1"), new Edge("t2", "p1"), new Edge("p1", "term")));

    TriggerPlugin trigger = mock(TriggerPlugin.class);
    when(trigger.getDefaultTimeout()).thenReturn(java.time.Duration.ofSeconds(30));
    when(trigger.getCategory()).thenReturn(PluginCategory.TRIGGER);
    when(trigger.validateConfig(any())).thenReturn(Mono.empty());
    when(trigger.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(trigger.initialize(any())).thenReturn(Mono.empty());
    when(trigger.start(any()))
        .thenAnswer(
            invocation -> {
              Map<String, Object> config = invocation.getArgument(0);
              return Flux.just(DefaultMessage.create(UUID.randomUUID(), "msg-from-trigger"));
            });

    ProcessorPlugin processor = mock(ProcessorPlugin.class);
    when(processor.getDefaultTimeout()).thenReturn(java.time.Duration.ofSeconds(30));
    when(processor.getCategory()).thenReturn(PluginCategory.PROCESSOR);
    when(processor.validateConfig(any())).thenReturn(Mono.empty());
    when(processor.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(processor.initialize(any())).thenReturn(Mono.empty());
    when(processor.process(any(), any()))
        .thenAnswer(
            invocation -> {
              Flux<Message<?>> input = invocation.getArgument(0);
              return input.map(
                  msg -> {
                    final String traceIdStr = msg.getTraceId();
                    final UUID traceId =
                        traceIdStr != null ? UUID.fromString(traceIdStr) : UUID.randomUUID();
                    return DefaultMessage.create(traceId, "processed-" + msg.getPayload());
                  });
            });

    TerminalPlugin terminal = mock(TerminalPlugin.class);
    when(terminal.getDefaultTimeout()).thenReturn(java.time.Duration.ofSeconds(30));
    when(terminal.getCategory()).thenReturn(PluginCategory.TERMINAL);
    when(terminal.validateConfig(any())).thenReturn(Mono.empty());
    when(terminal.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(terminal.initialize(any())).thenReturn(Mono.empty());
    when(terminal.consume(any(), any()))
        .thenAnswer(
            inv -> {
              Flux<Message<?>> input = inv.getArgument(0);
              return input.then();
            });

    when(registry.get("trigger")).thenReturn(trigger);
    when(registry.get("processor")).thenReturn(processor);
    when(registry.get("terminal")).thenReturn(terminal);

    String executionId = "exec-fan-in-1";
    StepVerifier.create(
            orchestrator
                .prepareWorkflow(def)
                .flatMap(
                    p ->
                        orchestrator.execute(sessionId, "test-workflow", executionId, p, Map.of())))
        .verifyComplete();

    verify(terminal).consume(any(), any());
    verify(tracker, atLeastOnce()).emitWorkflowStatusEvent(eq(executionId), eq("SUCCESS"));
  }

  @Test
  void testFanInToTerminal() {
    String sessionId = "sess-fan-in-2";
    Node t = new Node("t", "trigger", Map.of());
    Node p1 = new Node("p1", "processor", Map.of());
    Node p2 = new Node("p2", "processor", Map.of());
    Node term = new Node("term", "terminal", Map.of());

    WorkflowDefinition def =
        new WorkflowDefinition(
            "Fan-in to Terminal",
            List.of(t, p1, p2, term),
            List.of(
                new Edge("t", "p1"),
                new Edge("t", "p2"),
                new Edge("p1", "term"),
                new Edge("p2", "term")));

    TriggerPlugin trigger = mock(TriggerPlugin.class);
    when(trigger.getDefaultTimeout()).thenReturn(java.time.Duration.ofSeconds(30));
    when(trigger.getCategory()).thenReturn(PluginCategory.TRIGGER);
    when(trigger.validateConfig(any())).thenReturn(Mono.empty());
    when(trigger.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(trigger.initialize(any())).thenReturn(Mono.empty());
    when(trigger.start(any()))
        .thenReturn(Flux.just(DefaultMessage.create(UUID.randomUUID(), "data")));

    ProcessorPlugin processor = mock(ProcessorPlugin.class);
    when(processor.getDefaultTimeout()).thenReturn(java.time.Duration.ofSeconds(30));
    when(processor.getCategory()).thenReturn(PluginCategory.PROCESSOR);
    when(processor.validateConfig(any())).thenReturn(Mono.empty());
    when(processor.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(processor.initialize(any())).thenReturn(Mono.empty());
    when(processor.process(any(), any())).thenAnswer(inv -> inv.getArgument(0));

    TerminalPlugin terminal = mock(TerminalPlugin.class);
    when(terminal.getDefaultTimeout()).thenReturn(java.time.Duration.ofSeconds(30));
    when(terminal.getCategory()).thenReturn(PluginCategory.TERMINAL);
    when(terminal.validateConfig(any())).thenReturn(Mono.empty());
    when(terminal.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(terminal.initialize(any())).thenReturn(Mono.empty());
    when(terminal.consume(any(), any()))
        .thenAnswer(
            inv -> {
              Flux<Message<?>> input = inv.getArgument(0);
              return input
                  .collectList()
                  .flatMap(
                      list -> {
                        if (list.size() == 2) {
                          return Mono.empty();
                        } else {
                          return Mono.error(
                              new RuntimeException("Expected 2 messages, got " + list.size()));
                        }
                      });
            });

    when(registry.get("trigger")).thenReturn(trigger);
    when(registry.get("processor")).thenReturn(processor);
    when(registry.get("terminal")).thenReturn(terminal);

    String executionId = "exec-fan-in-2";
    StepVerifier.create(
            orchestrator
                .prepareWorkflow(def)
                .flatMap(
                    p ->
                        orchestrator.execute(sessionId, "test-workflow", executionId, p, Map.of())))
        .verifyComplete();

    verify(tracker, atLeastOnce()).emitWorkflowStatusEvent(eq(executionId), eq("SUCCESS"));
  }

  @Test
  void testFanInWithErrorPropagation() {
    String sessionId = "sess-fan-in-error";
    Node t1 = new Node("t1", "trigger", Map.of());
    Node t2 = new Node("t2", "trigger", Map.of());
    Node p = new Node("p", "processor", Map.of());
    Node term = new Node("term", "terminal", Map.of());

    WorkflowDefinition def =
        new WorkflowDefinition(
            "Fan-in Error Test",
            List.of(t1, t2, p, term),
            List.of(new Edge("t1", "p"), new Edge("t2", "p"), new Edge("p", "term")));

    TriggerPlugin trigger1 = mock(TriggerPlugin.class);
    when(trigger1.getDefaultTimeout()).thenReturn(java.time.Duration.ofSeconds(30));
    when(trigger1.getCategory()).thenReturn(PluginCategory.TRIGGER);
    when(trigger1.validateConfig(any())).thenReturn(Mono.empty());
    when(trigger1.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(trigger1.initialize(any())).thenReturn(Mono.empty());
    when(trigger1.start(any())).thenReturn(Flux.error(new RuntimeException("Trigger 1 failed")));

    TriggerPlugin trigger2 = mock(TriggerPlugin.class);
    when(trigger2.getDefaultTimeout()).thenReturn(java.time.Duration.ofSeconds(30));
    when(trigger2.getCategory()).thenReturn(PluginCategory.TRIGGER);
    when(trigger2.validateConfig(any())).thenReturn(Mono.empty());
    when(trigger2.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(trigger2.initialize(any())).thenReturn(Mono.empty());
    when(trigger2.start(any()))
        .thenReturn(Flux.just(DefaultMessage.create(UUID.randomUUID(), "t2-data")));

    ProcessorPlugin processor = mock(ProcessorPlugin.class);
    when(processor.getDefaultTimeout()).thenReturn(java.time.Duration.ofSeconds(30));
    when(processor.getCategory()).thenReturn(PluginCategory.PROCESSOR);
    when(processor.validateConfig(any())).thenReturn(Mono.empty());
    when(processor.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(processor.initialize(any())).thenReturn(Mono.empty());
    when(processor.process(any(), any())).thenAnswer(inv -> inv.getArgument(0));

    TerminalPlugin terminal = mock(TerminalPlugin.class);
    when(terminal.getDefaultTimeout()).thenReturn(java.time.Duration.ofSeconds(30));
    when(terminal.getCategory()).thenReturn(PluginCategory.TERMINAL);
    when(terminal.validateConfig(any())).thenReturn(Mono.empty());
    when(terminal.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(terminal.initialize(any())).thenReturn(Mono.empty());
    when(terminal.consume(any(), any()))
        .thenAnswer(
            inv -> {
              Flux<Message<?>> input = inv.getArgument(0);
              return input.then();
            });

    Node t1_node = new Node("t1", "trigger1", Map.of());
    Node t2_node = new Node("t2", "trigger2", Map.of());
    WorkflowDefinition def2 =
        new WorkflowDefinition(
            "Fan-in Error Test",
            List.of(t1_node, t2_node, p, term),
            List.of(new Edge("t1", "p"), new Edge("t2", "p"), new Edge("p", "term")));

    when(registry.get("trigger1")).thenReturn(trigger1);
    when(registry.get("trigger2")).thenReturn(trigger2);
    when(registry.get("processor")).thenReturn(processor);
    when(registry.get("terminal")).thenReturn(terminal);

    String executionId = "exec-fan-in-error";
    StepVerifier.create(
            orchestrator
                .prepareWorkflow(def2)
                .flatMap(
                    pw ->
                        orchestrator.execute(
                            sessionId, "test-workflow", executionId, pw, Map.of())))
        .expectErrorMatches(
            e ->
                e.getMessage().contains("Trigger 1 failed")
                    || (e.getCause() != null
                        && e.getCause().getMessage().contains("Trigger 1 failed")))
        .verify();

    verify(tracker, timeout(1000).atLeastOnce())
        .emitTaskStatusEvent(eq(executionId), eq("t1"), anyString(), eq("FAILURE"), any());
    verify(tracker, timeout(1000)).emitWorkflowStatusEvent(eq(executionId), eq("ERROR"));
  }

  @Test
  void testCompileAssemblers() {
    Node t1 = new Node("t1", "trigger", Map.of());
    Node p1 = new Node("p1", "processor", Map.of());
    Node term = new Node("term", "terminal", Map.of());

    WorkflowDefinition def =
        new WorkflowDefinition(
            "Test",
            List.of(t1, p1, term),
            List.of(new Edge("t1", "p1"), new Edge("p1", "term")));

    Map<String, List<Node>> parentsList = new HashMap<>();
    parentsList.put("t1", Collections.emptyList());
    parentsList.put("p1", List.of(t1));
    parentsList.put("term", List.of(p1));

    Map<String, WorkflowPlugin> pluginCache = new HashMap<>();
    TriggerPlugin trigger = mock(TriggerPlugin.class);
    when(trigger.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    pluginCache.put("t1", trigger);
    pluginCache.put("p1", mock(ProcessorPlugin.class));
    pluginCache.put("term", mock(TerminalPlugin.class));

    List<Node> topologicalOrder = List.of(t1, p1, term);

    NodeAssembler[] assemblers =
        compiler.compileAssemblers(def, parentsList, pluginCache, topologicalOrder);

    assertThat(assemblers).hasSize(3);
  }

  @Test
  void testCompileTemplateWithContext() {
    String sessionId = "sess-compile";
    String workflowId = "wf-compile";
    String executionId = "exec-compile";

    Node t = new Node("t", "trigger", Map.of());
    Node term = new Node("term", "terminal", Map.of());

    WorkflowDefinition def =
        new WorkflowDefinition(
            "Compile Test",
            List.of(t, term),
            List.of(new Edge("t", "term")));

    TriggerPlugin trigger = mock(TriggerPlugin.class);
    when(trigger.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    when(trigger.getCategory()).thenReturn(PluginCategory.TRIGGER);
    when(trigger.validateConfig(any())).thenReturn(Mono.empty());
    when(trigger.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(trigger.initialize(any())).thenReturn(Mono.empty());
    when(trigger.start(any()))
        .thenReturn(Flux.just(DefaultMessage.create(UUID.randomUUID(), "data")));

    TerminalPlugin terminal = mock(TerminalPlugin.class);
    when(terminal.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    when(terminal.getCategory()).thenReturn(PluginCategory.TERMINAL);
    when(terminal.validateConfig(any())).thenReturn(Mono.empty());
    when(terminal.validateInContext(any(), any())).thenReturn(Mono.empty());
    when(terminal.initialize(any())).thenReturn(Mono.empty());
    when(terminal.consume(any(), any())).thenAnswer(inv -> {
      Flux<Message<?>> input = inv.getArgument(0);
      return input.then();
    });

    when(registry.get("trigger")).thenReturn(trigger);
    when(registry.get("terminal")).thenReturn(terminal);

    StepVerifier.create(
            orchestrator
                .prepareWorkflow(def)
                .flatMap(
                    p ->
                        orchestrator.execute(
                            sessionId, workflowId, executionId, p, Map.of())))
        .verifyComplete();

    verify(tracker).startWorkflow(eq(executionId), eq(sessionId), eq(workflowId), any());
  }

  @Test
  void testGetBufferSizeFromConfig() {
    Node t = new Node("t", "trigger", Map.of("bufferSize", 512));
    Node p = new Node("p", "processor", Map.of());
    Node term = new Node("term", "terminal", Map.of());

    WorkflowDefinition def =
        new WorkflowDefinition(
            "Buffer Test",
            List.of(t, p, term),
            List.of(new Edge("t", "p"), new Edge("p", "term")));

    Map<String, List<Node>> parentsList = new HashMap<>();
    parentsList.put("t", Collections.emptyList());
    parentsList.put("p", List.of(t));
    parentsList.put("term", List.of(p));

    Map<String, WorkflowPlugin> pluginCache = new HashMap<>();
    pluginCache.put("t", mock(TriggerPlugin.class));
    pluginCache.put("p", mock(ProcessorPlugin.class));
    pluginCache.put("term", mock(TerminalPlugin.class));

    List<Node> topologicalOrder = List.of(t, p, term);
    NodeAssembler[] assemblers =
        compiler.compileAssemblers(def, parentsList, pluginCache, topologicalOrder);

    assertThat(assemblers).isNotNull();
  }

  @Test
  void testGetBufferSizeDefault() {
    Node t = new Node("t", "trigger", Map.of());
    Node p = new Node("p", "processor", Map.of("bufferSize", "invalid"));
    Node term = new Node("term", "terminal", Map.of("bufferSize", 0));

    WorkflowDefinition def =
        new WorkflowDefinition(
            "Buffer Default Test",
            List.of(t, p, term),
            List.of(new Edge("t", "p"), new Edge("p", "term")));

    Map<String, List<Node>> parentsList = new HashMap<>();
    parentsList.put("t", Collections.emptyList());
    parentsList.put("p", List.of(t));
    parentsList.put("term", List.of(p));

    Map<String, WorkflowPlugin> pluginCache = new HashMap<>();
    pluginCache.put("t", mock(TriggerPlugin.class));
    pluginCache.put("p", mock(ProcessorPlugin.class));
    pluginCache.put("term", mock(TerminalPlugin.class));

    List<Node> topologicalOrder = List.of(t, p, term);
    NodeAssembler[] assemblers =
        compiler.compileAssemblers(def, parentsList, pluginCache, topologicalOrder);

    assertThat(assemblers).hasSize(3);
  }

  @Test
  void testGetNodeTimeoutFromConfig() {
    Node t = new Node("t", "trigger", Map.of("timeoutSeconds", 60L));
    Node p = new Node("p", "processor", Map.of());
    Node term = new Node("term", "terminal", Map.of());

    WorkflowDefinition def =
        new WorkflowDefinition(
            "Timeout Config Test",
            List.of(t, p, term),
            List.of(new Edge("t", "p"), new Edge("p", "term")));

    Map<String, List<Node>> parentsList = new HashMap<>();
    parentsList.put("t", Collections.emptyList());
    parentsList.put("p", List.of(t));
    parentsList.put("term", List.of(p));

    TriggerPlugin trigger = mock(TriggerPlugin.class);
    when(trigger.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    ProcessorPlugin processor = mock(ProcessorPlugin.class);
    when(processor.getDefaultTimeout()).thenReturn(Duration.ofSeconds(45));

    Map<String, WorkflowPlugin> pluginCache = new HashMap<>();
    pluginCache.put("t", trigger);
    pluginCache.put("p", processor);
    pluginCache.put("term", mock(TerminalPlugin.class));

    List<Node> topologicalOrder = List.of(t, p, term);
    NodeAssembler[] assemblers =
        compiler.compileAssemblers(def, parentsList, pluginCache, topologicalOrder);

    assertThat(assemblers).hasSize(3);
  }

  @Test
  void testGetNodeTimeoutLegacyTimeoutKey() {
    Node t = new Node("t", "trigger", Map.of("timeout", 75L));
    Node p = new Node("p", "processor", Map.of());
    Node term = new Node("term", "terminal", Map.of());

    WorkflowDefinition def =
        new WorkflowDefinition(
            "Timeout Legacy Test",
            List.of(t, p, term),
            List.of(new Edge("t", "p"), new Edge("p", "term")));

    Map<String, List<Node>> parentsList = new HashMap<>();
    parentsList.put("t", Collections.emptyList());
    parentsList.put("p", List.of(t));
    parentsList.put("term", List.of(p));

    Map<String, WorkflowPlugin> pluginCache = new HashMap<>();
    pluginCache.put("t", mock(TriggerPlugin.class));
    pluginCache.put("p", mock(ProcessorPlugin.class));
    pluginCache.put("term", mock(TerminalPlugin.class));

    List<Node> topologicalOrder = List.of(t, p, term);
    NodeAssembler[] assemblers =
        compiler.compileAssemblers(def, parentsList, pluginCache, topologicalOrder);

    assertThat(assemblers).isNotNull();
  }

  @Test
  void testGetNodeTimeoutDefaultFromPlugin() {
    Node t = new Node("t", "trigger", Map.of());
    Node p = new Node("p", "processor", Map.of());
    Node term = new Node("term", "terminal", Map.of());

    WorkflowDefinition def =
        new WorkflowDefinition(
            "Timeout Default Test",
            List.of(t, p, term),
            List.of(new Edge("t", "p"), new Edge("p", "term")));

    Map<String, List<Node>> parentsList = new HashMap<>();
    parentsList.put("t", Collections.emptyList());
    parentsList.put("p", List.of(t));
    parentsList.put("term", List.of(p));

    TriggerPlugin trigger = mock(TriggerPlugin.class);
    when(trigger.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));
    ProcessorPlugin processor = mock(ProcessorPlugin.class);
    when(processor.getDefaultTimeout()).thenReturn(Duration.ofSeconds(45));
    TerminalPlugin terminal = mock(TerminalPlugin.class);
    when(terminal.getDefaultTimeout()).thenReturn(Duration.ofSeconds(20));

    Map<String, WorkflowPlugin> pluginCache = new HashMap<>();
    pluginCache.put("t", trigger);
    pluginCache.put("p", processor);
    pluginCache.put("term", terminal);

    List<Node> topologicalOrder = List.of(t, p, term);
    NodeAssembler[] assemblers =
        compiler.compileAssemblers(def, parentsList, pluginCache, topologicalOrder);

    assertThat(assemblers).hasSize(3);
  }

  @Test
  void testGetNodeTimeoutDefaultFallback() {
    Node t = new Node("t", "trigger", Map.of());
    Node p = new Node("p", "processor", Map.of());
    Node term = new Node("term", "terminal", Map.of());

    WorkflowDefinition def =
        new WorkflowDefinition(
            "Timeout Fallback Test",
            List.of(t, p, term),
            List.of(new Edge("t", "p"), new Edge("p", "term")));

    Map<String, List<Node>> parentsList = new HashMap<>();
    parentsList.put("t", Collections.emptyList());
    parentsList.put("p", List.of(t));
    parentsList.put("term", List.of(p));

    Map<String, WorkflowPlugin> pluginCache = new HashMap<>();
    pluginCache.put("t", null);
    pluginCache.put("p", null);
    pluginCache.put("term", null);

    List<Node> topologicalOrder = List.of(t, p, term);
    NodeAssembler[] assemblers =
        compiler.compileAssemblers(def, parentsList, pluginCache, topologicalOrder);

    assertThat(assemblers).hasSize(3);
  }

  @Test
  void testCreateNodeAssemblerWithParents() {
    Node t = new Node("t", "trigger", Map.of());
    Node p = new Node("p", "processor", Map.of());
    Node term = new Node("term", "terminal", Map.of());

    WorkflowDefinition def =
        new WorkflowDefinition(
            "Parent Test",
            List.of(t, p, term),
            List.of(new Edge("t", "p"), new Edge("p", "term")));

    Map<String, List<Node>> parentsList = new HashMap<>();
    parentsList.put("t", Collections.emptyList());
    parentsList.put("p", List.of(t));
    parentsList.put("term", List.of(p));

    Map<String, WorkflowPlugin> pluginCache = new HashMap<>();
    pluginCache.put("t", mock(TriggerPlugin.class));
    pluginCache.put("p", mock(ProcessorPlugin.class));
    pluginCache.put("term", mock(TerminalPlugin.class));

    List<Node> topologicalOrder = List.of(t, p, term);
    NodeAssembler[] assemblers =
        compiler.compileAssemblers(def, parentsList, pluginCache, topologicalOrder);

    assertThat(assemblers).hasSize(3);
  }

  @Test
  void testCreateNodeAssemblerNoParents() {
    Node t = new Node("t", "trigger", Map.of());
    Node p = new Node("p", "processor", Map.of());

    WorkflowDefinition def =
        new WorkflowDefinition(
            "No Parent Test",
            List.of(t, p),
            List.of(new Edge("t", "p")));

    Map<String, List<Node>> parentsList = new HashMap<>();
    parentsList.put("t", Collections.emptyList());
    parentsList.put("p", List.of(t));

    Map<String, WorkflowPlugin> pluginCache = new HashMap<>();
    pluginCache.put("t", mock(TriggerPlugin.class));
    pluginCache.put("p", mock(ProcessorPlugin.class));

    List<Node> topologicalOrder = List.of(t, p);
    NodeAssembler[] assemblers =
        compiler.compileAssemblers(def, parentsList, pluginCache, topologicalOrder);

    assertThat(assemblers).hasSize(2);
  }

  @Test
  void testExecuteTemplateWithMissingExecutionControl() {
    String executionId = "exec-missing";

    Node t = new Node("t", "trigger", Map.of());
    Node term = new Node("term", "terminal", Map.of());
    WorkflowDefinition def =
        new WorkflowDefinition(
            "Missing Control Test",
            List.of(t, term),
            List.of(new Edge("t", "term")));

    Map<String, List<Node>> parentsList = new HashMap<>();
    parentsList.put("t", Collections.emptyList());
    parentsList.put("term", List.of(t));

    Map<String, WorkflowPlugin> pluginCache = new HashMap<>();
    pluginCache.put("t", mock(TriggerPlugin.class));
    pluginCache.put("term", mock(TerminalPlugin.class));

    List<Node> topologicalOrder = List.of(t, term);
    NodeAssembler[] assemblers =
        compiler.compileAssemblers(def, parentsList, pluginCache, topologicalOrder);

    Mono<Void> result =
        compiler
            .executeTemplate(
                executionId,
                Map.of(),
                2,
                assemblers,
                "sess-test",
                "wf-test",
                List.of("t", "term"))
            .contextWrite(c -> c.put("sessionId", "sess-test").put("workflowId", "wf-test"));

    StepVerifier.create(result)
        .expectErrorMatches(
            e ->
                e instanceof IllegalStateException
                    && e.getMessage().contains("ExecutionControl not registered"))
        .verify();
  }

  @Test
  void testMultipleParents() {
    Node t1 = new Node("t1", "trigger", Map.of());
    Node t2 = new Node("t2", "trigger", Map.of());
    Node p = new Node("p", "processor", Map.of());

    WorkflowDefinition def =
        new WorkflowDefinition(
            "Multiple Parents",
            List.of(t1, t2, p),
            List.of(new Edge("t1", "p"), new Edge("t2", "p")));

    Map<String, List<Node>> parentsList = new HashMap<>();
    parentsList.put("t1", Collections.emptyList());
    parentsList.put("t2", Collections.emptyList());
    parentsList.put("p", List.of(t1, t2));

    Map<String, WorkflowPlugin> pluginCache = new HashMap<>();
    pluginCache.put("t1", mock(TriggerPlugin.class));
    pluginCache.put("t2", mock(TriggerPlugin.class));
    pluginCache.put("p", mock(ProcessorPlugin.class));

    List<Node> topologicalOrder = List.of(t1, t2, p);
    NodeAssembler[] assemblers =
        compiler.compileAssemblers(def, parentsList, pluginCache, topologicalOrder);

    assertThat(assemblers).hasSize(3);
  }

  @Test
  void testNodeAssemblerWithEdgeSourcePort() {
    Node t = new Node("t", "trigger", Map.of());
    Node p = new Node("p", "processor", Map.of());
    Node term = new Node("term", "terminal", Map.of());

    WorkflowDefinition def =
        new WorkflowDefinition(
            "Source Port Test",
            List.of(t, p, term),
            List.of(
                new Edge("t", "p", "output"),
                new Edge("p", "term", "processed")));

    Map<String, List<Node>> parentsList = new HashMap<>();
    parentsList.put("t", Collections.emptyList());
    parentsList.put("p", List.of(t));
    parentsList.put("term", List.of(p));

    Map<String, WorkflowPlugin> pluginCache = new HashMap<>();
    pluginCache.put("t", mock(TriggerPlugin.class));
    pluginCache.put("p", mock(ProcessorPlugin.class));
    pluginCache.put("term", mock(TerminalPlugin.class));

    List<Node> topologicalOrder = List.of(t, p, term);
    NodeAssembler[] assemblers =
        compiler.compileAssemblers(def, parentsList, pluginCache, topologicalOrder);

    assertThat(assemblers).hasSize(3);
  }
}
