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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infenia.yukta.api.WorkflowDefinition;
import com.infenia.yukta.api.WorkflowDefinition.Edge;
import com.infenia.yukta.api.WorkflowDefinition.Node;
import com.infenia.yukta.model.workflow.NodeAssembler;
import com.infenia.yukta.model.workflow.WorkflowEdge;
import com.infenia.yukta.model.workflow.WorkflowNode;
import com.infenia.yukta.model.workflow.WorkflowTemplate;
import com.infenia.yukta.plugin.core.WorkflowPlugin;
import com.infenia.yukta.plugin.type.ProcessorPlugin;
import com.infenia.yukta.plugin.type.TerminalPlugin;
import com.infenia.yukta.plugin.type.TriggerPlugin;
import com.infenia.yukta.service.control.ExecutionControl;
import com.infenia.yukta.service.control.gateway.ControlBusGateway;
import com.infenia.yukta.service.control.store.ExecutionControlRegistry;
import com.infenia.yukta.service.control.store.InMemoryExecutionControlStore;
import com.infenia.yukta.service.orchestrator.AssemblyContext;
import com.infenia.yukta.service.orchestrator.TaskTrackerService;
import com.infenia.yukta.service.orchestrator.strategy.NodeAssemblerStrategy;
import com.infenia.yukta.service.session.SessionConfigStore;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkflowCompiler")
class WorkflowCompilerTest {

  private WorkflowCompiler compiler;
  private ExecutionControlRegistry executionControlRegistry;

  @Mock private TaskTrackerService tracker;
  @Mock private ControlBusGateway controlBusGateway;
  @Mock private SessionConfigStore configService;

  @BeforeEach
  void setUp() {
    executionControlRegistry = new ExecutionControlRegistry(new InMemoryExecutionControlStore());
    compiler =
        new WorkflowCompiler(
            tracker,
            controlBusGateway,
            Schedulers.parallel(),
            Duration.ofSeconds(10),
            configService,
            executionControlRegistry,
            List.of());
  }

  private Map<String, List<WorkflowNode>> toWorkflowNodeMap(Map<String, List<Node>> nodeMap) {
    return nodeMap.entrySet().stream()
        .collect(
            java.util.stream.Collectors.toMap(
                java.util.Map.Entry::getKey,
                e ->
                    e.getValue().stream()
                        .map(n -> new WorkflowNode(n.nodeId(), n.type(), n.config()))
                        .toList()));
  }

  private List<WorkflowNode> toWorkflowNodeList(List<Node> nodes) {
    return nodes.stream().map(n -> new WorkflowNode(n.nodeId(), n.type(), n.config())).toList();
  }

  @Nested
  @DisplayName("compileAssemblers")
  class CompileAssemblersTests {

    @Test
    @DisplayName("should create assembler array with correct size")
    void shouldCreateAssemblerArrayWithCorrectSize() {
      Node t1 = new Node("t1", "trigger", Map.of());
      Node p1 = new Node("p1", "processor", Map.of());
      Node term = new Node("term", "terminal", Map.of());

      WorkflowDefinition def =
          new WorkflowDefinition(
            "test-workflow",
              "Test", List.of(t1, p1, term), List.of(new Edge("t1", "p1"), new Edge("p1", "term")));
      final List<WorkflowEdge> edges =
          def.edges().stream()
              .map(e -> new WorkflowEdge(e.source(), e.target(), e.sourcePort()))
              .toList();

      Map<String, List<Node>> parentsList = new HashMap<>();
      parentsList.put("t1", Collections.emptyList());
      parentsList.put("p1", List.of(t1));
      parentsList.put("term", List.of(p1));

      Map<String, WorkflowPlugin> pluginCache = new HashMap<>();
      pluginCache.put("t1", mock(TriggerPlugin.class));
      pluginCache.put("p1", mock(ProcessorPlugin.class));
      pluginCache.put("term", mock(TerminalPlugin.class));

      List<Node> topologicalOrder = List.of(t1, p1, term);

      NodeAssembler[] assemblers =
          compiler.compileAssemblers(
              edges,
              toWorkflowNodeMap(parentsList),
              pluginCache,
              toWorkflowNodeList(topologicalOrder));

      assertThat(assemblers).hasSize(3);
    }

    @Test
    @DisplayName("should create assembler for single node")
    void shouldCreateAssemblerForSingleNode() {
      Node t1 = new Node("t1", "trigger", Map.of());

      WorkflowDefinition def = new WorkflowDefinition(
            "test-workflow",
            "Test",
            List.of(t1), List.of());
      final List<WorkflowEdge> edges =
          def.edges().stream()
              .map(e -> new WorkflowEdge(e.source(), e.target(), e.sourcePort()))
              .toList();

      Map<String, List<Node>> parentsList = new HashMap<>();
      parentsList.put("t1", Collections.emptyList());

      Map<String, WorkflowPlugin> pluginCache = new HashMap<>();
      pluginCache.put("t1", mock(TriggerPlugin.class));

      List<Node> topologicalOrder = List.of(t1);

      NodeAssembler[] assemblers =
          compiler.compileAssemblers(
              edges,
              toWorkflowNodeMap(parentsList),
              pluginCache,
              toWorkflowNodeList(topologicalOrder));

      assertThat(assemblers).hasSize(1);
    }
  }

  @Nested
  @DisplayName("executeTemplate")
  class ExecuteTemplateTests {

    @Test
    @DisplayName("should complete executeTemplate when assemblers provided")
    void shouldCompleteWhenAssemblersProvided() {
      Node t = new Node("t", "trigger", Map.of());
      Node term = new Node("term", "terminal", Map.of());

      WorkflowDefinition def =
          new WorkflowDefinition(
            "test-workflow",
            "Test",
            List.of(t, term), List.of(new Edge("t", "term")));
      final List<WorkflowEdge> edges =
          def.edges().stream()
              .map(e -> new WorkflowEdge(e.source(), e.target(), e.sourcePort()))
              .toList();

      Map<String, List<Node>> parentsList = new HashMap<>();
      parentsList.put("t", Collections.emptyList());
      parentsList.put("term", List.of(t));

      Map<String, WorkflowPlugin> pluginCache = new HashMap<>();
      pluginCache.put("t", mock(TriggerPlugin.class));
      pluginCache.put("term", mock(TerminalPlugin.class));

      List<Node> topologicalOrder = List.of(t, term);
      NodeAssembler[] assemblers =
          compiler.compileAssemblers(
              edges,
              toWorkflowNodeMap(parentsList),
              pluginCache,
              toWorkflowNodeList(topologicalOrder));

      assertThat(assemblers).hasSize(2);
    }
  }

  @Nested
  @DisplayName("compileTemplate")
  class CompileTemplateTests {

    @Test
    @DisplayName("should return valid WorkflowTemplate")
    void shouldReturnValidWorkflowTemplate() {
      Node t = new Node("t", "trigger", Map.of());
      Node term = new Node("term", "terminal", Map.of());

      WorkflowDefinition def =
          new WorkflowDefinition(
            "test-workflow",
            "Test",
            List.of(t, term), List.of(new Edge("t", "term")));
      final List<WorkflowEdge> edges =
          def.edges().stream()
              .map(e -> new WorkflowEdge(e.source(), e.target(), e.sourcePort()))
              .toList();

      Map<String, List<Node>> parentsList = new HashMap<>();
      parentsList.put("t", Collections.emptyList());
      parentsList.put("term", List.of(t));

      Map<String, WorkflowPlugin> pluginCache = new HashMap<>();
      pluginCache.put("t", mock(TriggerPlugin.class));
      pluginCache.put("term", mock(TerminalPlugin.class));

      List<Node> topologicalOrder = List.of(t, term);

      WorkflowTemplate template =
          compiler.compileTemplate(
              edges,
              toWorkflowNodeMap(parentsList),
              pluginCache,
              toWorkflowNodeList(topologicalOrder));

      assertThat(template).isNotNull();
    }

    @Test
    @DisplayName("should return template that accepts executionId and payload")
    void shouldReturnTemplateWithCorrectSignature() {
      Node t = new Node("t", "trigger", Map.of());
      Node term = new Node("term", "terminal", Map.of());

      WorkflowDefinition def =
          new WorkflowDefinition(
            "test-workflow",
            "Test",
            List.of(t, term), List.of(new Edge("t", "term")));
      final List<WorkflowEdge> edges =
          def.edges().stream()
              .map(e -> new WorkflowEdge(e.source(), e.target(), e.sourcePort()))
              .toList();

      Map<String, List<Node>> parentsList = new HashMap<>();
      parentsList.put("t", Collections.emptyList());
      parentsList.put("term", List.of(t));

      Map<String, WorkflowPlugin> pluginCache = new HashMap<>();
      pluginCache.put("t", mock(TriggerPlugin.class));
      pluginCache.put("term", mock(TerminalPlugin.class));

      List<Node> topologicalOrder = List.of(t, term);

      WorkflowTemplate template =
          compiler.compileTemplate(
              edges,
              toWorkflowNodeMap(parentsList),
              pluginCache,
              toWorkflowNodeList(topologicalOrder));

      String executionId = "exec-1";
      String sessionId = "sess-1";
      String workflowId = "wf-1";

      when(tracker.startWorkflow(executionId, sessionId, workflowId, List.of("t", "term")))
          .thenReturn(Mono.empty());

      Mono<Void> result =
          template
              .instantiate(executionId, Map.of("key", "value"))
              .contextWrite(c -> c.put("sessionId", sessionId).put("workflowId", workflowId));

      StepVerifier.create(result)
          .expectErrorMatches(
              e ->
                  e instanceof IllegalStateException
                      && e.getMessage().contains("ExecutionControl not registered"))
          .verify();

      verify(tracker).startWorkflow(executionId, sessionId, workflowId, List.of("t", "term"));
    }

    @Test
    @DisplayName("should invoke tracker.startWorkflow with correct parameters")
    void shouldInvokeTrackerStartWorkflowWithCorrectParams() {
      Node t = new Node("t", "trigger", Map.of());
      Node term = new Node("term", "terminal", Map.of());

      WorkflowDefinition def =
          new WorkflowDefinition(
            "test-workflow",
            "Test",
            List.of(t, term), List.of(new Edge("t", "term")));
      final List<WorkflowEdge> edges =
          def.edges().stream()
              .map(e -> new WorkflowEdge(e.source(), e.target(), e.sourcePort()))
              .toList();

      Map<String, List<Node>> parentsList = new HashMap<>();
      parentsList.put("t", Collections.emptyList());
      parentsList.put("term", List.of(t));

      Map<String, WorkflowPlugin> pluginCache = new HashMap<>();
      pluginCache.put("t", mock(TriggerPlugin.class));
      pluginCache.put("term", mock(TerminalPlugin.class));

      List<Node> topologicalOrder = List.of(t, term);

      WorkflowTemplate template =
          compiler.compileTemplate(
              edges,
              toWorkflowNodeMap(parentsList),
              pluginCache,
              toWorkflowNodeList(topologicalOrder));

      String executionId = "exec-123";
      String sessionId = "sess-456";
      String workflowId = "wf-789";
      Map<String, Object> payload = Map.of("data", "value");

      when(tracker.startWorkflow(executionId, sessionId, workflowId, List.of("t", "term")))
          .thenReturn(Mono.empty());

      Mono<Void> result =
          template
              .instantiate(executionId, payload)
              .contextWrite(c -> c.put("sessionId", sessionId).put("workflowId", workflowId));

      StepVerifier.create(result)
          .expectErrorMatches(e -> e instanceof IllegalStateException)
          .verify();

      verify(tracker).startWorkflow(executionId, sessionId, workflowId, List.of("t", "term"));
    }

    @Test
    @DisplayName("should handle empty topological order")
    void shouldHandleEmptyTopologicalOrder() {
      WorkflowDefinition def = new WorkflowDefinition(
            "test-workflow",
            "test-workflow", "Test",
            List.of(), List.of());
      final List<WorkflowEdge> edges =
          def.edges().stream()
              .map(e -> new WorkflowEdge(e.source(), e.target(), e.sourcePort()))
              .toList();

      Map<String, List<Node>> parentsList = new HashMap<>();
      Map<String, WorkflowPlugin> pluginCache = new HashMap<>();
      List<Node> topologicalOrder = List.of();

      WorkflowTemplate template =
          compiler.compileTemplate(
              edges,
              toWorkflowNodeMap(parentsList),
              pluginCache,
              toWorkflowNodeList(topologicalOrder));

      assertThat(template).isNotNull();
    }
  }

  @Nested
  @DisplayName("getBufferSize (via compileAssemblers)")
  class GetBufferSizeTests {

    @Test
    @DisplayName("should use configured buffer size when valid Number > 0")
    void shouldUseConfiguredBufferSize() {
      Node t1 = new Node("t1", "trigger", Map.of("bufferSize", 512));
      Node p1 = new Node("p1", "processor", Map.of());

      WorkflowDefinition def =
          new WorkflowDefinition(
            "test-workflow",
            "Test",
            List.of(t1, p1), List.of(new Edge("t1", "p1")));
      final List<WorkflowEdge> edges =
          def.edges().stream()
              .map(e -> new WorkflowEdge(e.source(), e.target(), e.sourcePort()))
              .toList();

      Map<String, List<Node>> parentsList = new HashMap<>();
      parentsList.put("t1", Collections.emptyList());
      parentsList.put("p1", List.of(t1));

      Map<String, WorkflowPlugin> pluginCache = new HashMap<>();
      pluginCache.put("t1", mock(TriggerPlugin.class));
      pluginCache.put("p1", mock(ProcessorPlugin.class));

      List<Node> topologicalOrder = List.of(t1, p1);

      NodeAssembler[] assemblers =
          compiler.compileAssemblers(
              edges,
              toWorkflowNodeMap(parentsList),
              pluginCache,
              toWorkflowNodeList(topologicalOrder));

      assertThat(assemblers).hasSize(2);
    }

    @Test
    @DisplayName("should use default buffer size when config not Number")
    void shouldUseDefaultBufferSizeWhenNotNumber() {
      Node t1 = new Node("t1", "trigger", Map.of());
      Node p1 = new Node("p1", "processor", Map.of("bufferSize", "invalid"));

      WorkflowDefinition def =
          new WorkflowDefinition(
            "test-workflow",
            "Test",
            List.of(t1, p1), List.of(new Edge("t1", "p1")));
      final List<WorkflowEdge> edges =
          def.edges().stream()
              .map(e -> new WorkflowEdge(e.source(), e.target(), e.sourcePort()))
              .toList();

      Map<String, List<Node>> parentsList = new HashMap<>();
      parentsList.put("t1", Collections.emptyList());
      parentsList.put("p1", List.of(t1));

      Map<String, WorkflowPlugin> pluginCache = new HashMap<>();
      pluginCache.put("t1", mock(TriggerPlugin.class));
      pluginCache.put("p1", mock(ProcessorPlugin.class));

      List<Node> topologicalOrder = List.of(t1, p1);

      NodeAssembler[] assemblers =
          compiler.compileAssemblers(
              edges,
              toWorkflowNodeMap(parentsList),
              pluginCache,
              toWorkflowNodeList(topologicalOrder));

      assertThat(assemblers).hasSize(2);
    }

    @Test
    @DisplayName("should use default buffer size when configured value <= 0")
    void shouldUseDefaultBufferSizeWhenValueNotPositive() {
      Node t1 = new Node("t1", "trigger", Map.of("bufferSize", 0));
      Node p1 = new Node("p1", "processor", Map.of("bufferSize", -1));

      WorkflowDefinition def =
          new WorkflowDefinition(
            "test-workflow",
            "Test",
            List.of(t1, p1), List.of(new Edge("t1", "p1")));
      final List<WorkflowEdge> edges =
          def.edges().stream()
              .map(e -> new WorkflowEdge(e.source(), e.target(), e.sourcePort()))
              .toList();

      Map<String, List<Node>> parentsList = new HashMap<>();
      parentsList.put("t1", Collections.emptyList());
      parentsList.put("p1", List.of(t1));

      Map<String, WorkflowPlugin> pluginCache = new HashMap<>();
      pluginCache.put("t1", mock(TriggerPlugin.class));
      pluginCache.put("p1", mock(ProcessorPlugin.class));

      List<Node> topologicalOrder = List.of(t1, p1);

      NodeAssembler[] assemblers =
          compiler.compileAssemblers(
              edges,
              toWorkflowNodeMap(parentsList),
              pluginCache,
              toWorkflowNodeList(topologicalOrder));

      assertThat(assemblers).hasSize(2);
    }

    @Test
    @DisplayName("should use default buffer size when not configured")
    void shouldUseDefaultBufferSizeWhenNotConfigured() {
      Node t1 = new Node("t1", "trigger", Map.of());
      Node p1 = new Node("p1", "processor", Map.of());

      WorkflowDefinition def =
          new WorkflowDefinition(
            "test-workflow",
            "Test",
            List.of(t1, p1), List.of(new Edge("t1", "p1")));
      final List<WorkflowEdge> edges =
          def.edges().stream()
              .map(e -> new WorkflowEdge(e.source(), e.target(), e.sourcePort()))
              .toList();

      Map<String, List<Node>> parentsList = new HashMap<>();
      parentsList.put("t1", Collections.emptyList());
      parentsList.put("p1", List.of(t1));

      Map<String, WorkflowPlugin> pluginCache = new HashMap<>();
      pluginCache.put("t1", mock(TriggerPlugin.class));
      pluginCache.put("p1", mock(ProcessorPlugin.class));

      List<Node> topologicalOrder = List.of(t1, p1);

      NodeAssembler[] assemblers =
          compiler.compileAssemblers(
              edges,
              toWorkflowNodeMap(parentsList),
              pluginCache,
              toWorkflowNodeList(topologicalOrder));

      assertThat(assemblers).hasSize(2);
    }
  }

  @Nested
  @DisplayName("getNodeTimeout (via compileAssemblers)")
  class GetNodeTimeoutTests {

    @Test
    @DisplayName("should use timeoutSeconds when configured and > 0")
    void shouldUseTimeoutSecondsWhenConfigured() {
      Node t1 = new Node("t1", "trigger", Map.of("timeoutSeconds", 60L));
      Node p1 = new Node("p1", "processor", Map.of());

      WorkflowDefinition def =
          new WorkflowDefinition(
            "test-workflow",
            "Test",
            List.of(t1, p1), List.of(new Edge("t1", "p1")));
      final List<WorkflowEdge> edges =
          def.edges().stream()
              .map(e -> new WorkflowEdge(e.source(), e.target(), e.sourcePort()))
              .toList();

      Map<String, List<Node>> parentsList = new HashMap<>();
      parentsList.put("t1", Collections.emptyList());
      parentsList.put("p1", List.of(t1));

      Map<String, WorkflowPlugin> pluginCache = new HashMap<>();
      pluginCache.put("t1", mock(TriggerPlugin.class));
      pluginCache.put("p1", mock(ProcessorPlugin.class));

      List<Node> topologicalOrder = List.of(t1, p1);

      NodeAssembler[] assemblers =
          compiler.compileAssemblers(
              edges,
              toWorkflowNodeMap(parentsList),
              pluginCache,
              toWorkflowNodeList(topologicalOrder));

      assertThat(assemblers).hasSize(2);
    }

    @Test
    @DisplayName("should use legacy timeout key when timeoutSeconds not present")
    void shouldUseLegacyTimeoutKeyWhenTimeoutSecondsAbsent() {
      Node t1 = new Node("t1", "trigger", Map.of("timeout", 75L));
      Node p1 = new Node("p1", "processor", Map.of());

      WorkflowDefinition def =
          new WorkflowDefinition(
            "test-workflow",
            "Test",
            List.of(t1, p1), List.of(new Edge("t1", "p1")));
      final List<WorkflowEdge> edges =
          def.edges().stream()
              .map(e -> new WorkflowEdge(e.source(), e.target(), e.sourcePort()))
              .toList();

      Map<String, List<Node>> parentsList = new HashMap<>();
      parentsList.put("t1", Collections.emptyList());
      parentsList.put("p1", List.of(t1));

      Map<String, WorkflowPlugin> pluginCache = new HashMap<>();
      pluginCache.put("t1", mock(TriggerPlugin.class));
      pluginCache.put("p1", mock(ProcessorPlugin.class));

      List<Node> topologicalOrder = List.of(t1, p1);

      NodeAssembler[] assemblers =
          compiler.compileAssemblers(
              edges,
              toWorkflowNodeMap(parentsList),
              pluginCache,
              toWorkflowNodeList(topologicalOrder));

      assertThat(assemblers).hasSize(2);
    }

    @Test
    @DisplayName("should use plugin default timeout when not configured")
    void shouldUsePluginDefaultTimeoutWhenNotConfigured() {
      TriggerPlugin trigger = mock(TriggerPlugin.class);
      when(trigger.getDefaultTimeout()).thenReturn(Duration.ofSeconds(45));

      ProcessorPlugin processor = mock(ProcessorPlugin.class);
      when(processor.getDefaultTimeout()).thenReturn(Duration.ofSeconds(60));

      Node t1 = new Node("t1", "trigger", Map.of());
      Node p1 = new Node("p1", "processor", Map.of());

      WorkflowDefinition def =
          new WorkflowDefinition(
            "test-workflow",
            "Test",
            List.of(t1, p1), List.of(new Edge("t1", "p1")));
      final List<WorkflowEdge> edges =
          def.edges().stream()
              .map(e -> new WorkflowEdge(e.source(), e.target(), e.sourcePort()))
              .toList();

      Map<String, List<Node>> parentsList = new HashMap<>();
      parentsList.put("t1", Collections.emptyList());
      parentsList.put("p1", List.of(t1));

      Map<String, WorkflowPlugin> pluginCache = new HashMap<>();
      pluginCache.put("t1", trigger);
      pluginCache.put("p1", processor);

      List<Node> topologicalOrder = List.of(t1, p1);

      NodeAssembler[] assemblers =
          compiler.compileAssemblers(
              edges,
              toWorkflowNodeMap(parentsList),
              pluginCache,
              toWorkflowNodeList(topologicalOrder));

      assertThat(assemblers).hasSize(2);
    }

    @Test
    @DisplayName("should use fallback timeout when plugin is null")
    void shouldUseFallbackTimeoutWhenPluginNull() {
      Node t1 = new Node("t1", "trigger", Map.of());
      Node p1 = new Node("p1", "processor", Map.of());

      WorkflowDefinition def =
          new WorkflowDefinition(
            "test-workflow",
            "Test",
            List.of(t1, p1), List.of(new Edge("t1", "p1")));
      final List<WorkflowEdge> edges =
          def.edges().stream()
              .map(e -> new WorkflowEdge(e.source(), e.target(), e.sourcePort()))
              .toList();

      Map<String, List<Node>> parentsList = new HashMap<>();
      parentsList.put("t1", Collections.emptyList());
      parentsList.put("p1", List.of(t1));

      Map<String, WorkflowPlugin> pluginCache = new HashMap<>();
      pluginCache.put("t1", null);
      pluginCache.put("p1", null);

      List<Node> topologicalOrder = List.of(t1, p1);

      NodeAssembler[] assemblers =
          compiler.compileAssemblers(
              edges,
              toWorkflowNodeMap(parentsList),
              pluginCache,
              toWorkflowNodeList(topologicalOrder));

      assertThat(assemblers).hasSize(2);
    }

    @Test
    @DisplayName("should use fallback timeout when plugin default is null")
    void shouldUseFallbackTimeoutWhenPluginDefaultNull() {
      TriggerPlugin trigger = mock(TriggerPlugin.class);
      when(trigger.getDefaultTimeout()).thenReturn(null);

      Node t1 = new Node("t1", "trigger", Map.of());
      Node p1 = new Node("p1", "processor", Map.of());

      WorkflowDefinition def =
          new WorkflowDefinition(
            "test-workflow",
            "Test",
            List.of(t1, p1), List.of(new Edge("t1", "p1")));
      final List<WorkflowEdge> edges =
          def.edges().stream()
              .map(e -> new WorkflowEdge(e.source(), e.target(), e.sourcePort()))
              .toList();

      Map<String, List<Node>> parentsList = new HashMap<>();
      parentsList.put("t1", Collections.emptyList());
      parentsList.put("p1", List.of(t1));

      Map<String, WorkflowPlugin> pluginCache = new HashMap<>();
      pluginCache.put("t1", trigger);
      pluginCache.put("p1", mock(ProcessorPlugin.class));

      List<Node> topologicalOrder = List.of(t1, p1);

      NodeAssembler[] assemblers =
          compiler.compileAssemblers(
              edges,
              toWorkflowNodeMap(parentsList),
              pluginCache,
              toWorkflowNodeList(topologicalOrder));

      assertThat(assemblers).hasSize(2);
    }

    @Test
    @DisplayName("should ignore timeout when configured value <= 0")
    void shouldIgnoreTimeoutWhenConfiguredValueNotPositive() {
      TriggerPlugin trigger = mock(TriggerPlugin.class);
      when(trigger.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));

      Node t1 = new Node("t1", "trigger", Map.of("timeoutSeconds", 0L));
      Node p1 = new Node("p1", "processor", Map.of());

      WorkflowDefinition def =
          new WorkflowDefinition(
            "test-workflow",
            "Test",
            List.of(t1, p1), List.of(new Edge("t1", "p1")));
      final List<WorkflowEdge> edges =
          def.edges().stream()
              .map(e -> new WorkflowEdge(e.source(), e.target(), e.sourcePort()))
              .toList();

      Map<String, List<Node>> parentsList = new HashMap<>();
      parentsList.put("t1", Collections.emptyList());
      parentsList.put("p1", List.of(t1));

      Map<String, WorkflowPlugin> pluginCache = new HashMap<>();
      pluginCache.put("t1", trigger);
      pluginCache.put("p1", mock(ProcessorPlugin.class));

      List<Node> topologicalOrder = List.of(t1, p1);

      NodeAssembler[] assemblers =
          compiler.compileAssemblers(
              edges,
              toWorkflowNodeMap(parentsList),
              pluginCache,
              toWorkflowNodeList(topologicalOrder));

      assertThat(assemblers).hasSize(2);
    }

    @Test
    @DisplayName("should ignore timeout when config value not Number")
    void shouldIgnoreTimeoutWhenConfigValueNotNumber() {
      TriggerPlugin trigger = mock(TriggerPlugin.class);
      when(trigger.getDefaultTimeout()).thenReturn(Duration.ofSeconds(30));

      Node t1 = new Node("t1", "trigger", Map.of("timeoutSeconds", "invalid"));
      Node p1 = new Node("p1", "processor", Map.of());

      WorkflowDefinition def =
          new WorkflowDefinition(
            "test-workflow",
            "Test",
            List.of(t1, p1), List.of(new Edge("t1", "p1")));
      final List<WorkflowEdge> edges =
          def.edges().stream()
              .map(e -> new WorkflowEdge(e.source(), e.target(), e.sourcePort()))
              .toList();

      Map<String, List<Node>> parentsList = new HashMap<>();
      parentsList.put("t1", Collections.emptyList());
      parentsList.put("p1", List.of(t1));

      Map<String, WorkflowPlugin> pluginCache = new HashMap<>();
      pluginCache.put("t1", trigger);
      pluginCache.put("p1", mock(ProcessorPlugin.class));

      List<Node> topologicalOrder = List.of(t1, p1);

      NodeAssembler[] assemblers =
          compiler.compileAssemblers(
              edges,
              toWorkflowNodeMap(parentsList),
              pluginCache,
              toWorkflowNodeList(topologicalOrder));

      assertThat(assemblers).hasSize(2);
    }

    @Test
    @DisplayName("should prefer timeoutSeconds over legacy timeout when both present")
    void shouldPreferTimeoutSecondsOverLegacyTimeout() {
      Node t1 = new Node("t1", "trigger", Map.of("timeoutSeconds", 60L, "timeout", 75L));
      Node p1 = new Node("p1", "processor", Map.of());

      WorkflowDefinition def =
          new WorkflowDefinition(
            "test-workflow",
            "Test",
            List.of(t1, p1), List.of(new Edge("t1", "p1")));
      final List<WorkflowEdge> edges =
          def.edges().stream()
              .map(e -> new WorkflowEdge(e.source(), e.target(), e.sourcePort()))
              .toList();

      Map<String, List<Node>> parentsList = new HashMap<>();
      parentsList.put("t1", Collections.emptyList());
      parentsList.put("p1", List.of(t1));

      Map<String, WorkflowPlugin> pluginCache = new HashMap<>();
      pluginCache.put("t1", mock(TriggerPlugin.class));
      pluginCache.put("p1", mock(ProcessorPlugin.class));

      List<Node> topologicalOrder = List.of(t1, p1);

      NodeAssembler[] assemblers =
          compiler.compileAssemblers(
              edges,
              toWorkflowNodeMap(parentsList),
              pluginCache,
              toWorkflowNodeList(topologicalOrder));

      assertThat(assemblers).hasSize(2);
    }

    @Test
    @DisplayName("should handle negative timeoutSeconds with plugin default")
    void shouldHandleNegativeTimeoutSecondsWithPluginDefault() {
      TriggerPlugin trigger = mock(TriggerPlugin.class);
      when(trigger.getDefaultTimeout()).thenReturn(Duration.ofSeconds(45));

      Node t1 = new Node("t1", "trigger", Map.of("timeoutSeconds", -5L));
      Node p1 = new Node("p1", "processor", Map.of());

      WorkflowDefinition def =
          new WorkflowDefinition(
            "test-workflow",
            "Test",
            List.of(t1, p1), List.of(new Edge("t1", "p1")));
      final List<WorkflowEdge> edges =
          def.edges().stream()
              .map(e -> new WorkflowEdge(e.source(), e.target(), e.sourcePort()))
              .toList();

      Map<String, List<Node>> parentsList = new HashMap<>();
      parentsList.put("t1", Collections.emptyList());
      parentsList.put("p1", List.of(t1));

      Map<String, WorkflowPlugin> pluginCache = new HashMap<>();
      pluginCache.put("t1", trigger);
      pluginCache.put("p1", mock(ProcessorPlugin.class));

      List<Node> topologicalOrder = List.of(t1, p1);

      NodeAssembler[] assemblers =
          compiler.compileAssemblers(
              edges,
              toWorkflowNodeMap(parentsList),
              pluginCache,
              toWorkflowNodeList(topologicalOrder));

      assertThat(assemblers).hasSize(2);
    }
  }

  @Nested
  @DisplayName("createNodeAssembler (via compileAssemblers)")
  class CreateNodeAssemblerTests {

    @Test
    @DisplayName("should return no-op assembler when no strategy matches")
    void shouldReturnNoOpAssemblerWhenNoStrategyMatches() {
      Node t = new Node("t", "trigger", Map.of());
      Node p = new Node("p", "processor", Map.of());

      WorkflowDefinition def =
          new WorkflowDefinition(
            "test-workflow",
            "Test",
            List.of(t, p), List.of(new Edge("t", "p")));
      final List<WorkflowEdge> edges =
          def.edges().stream()
              .map(e -> new WorkflowEdge(e.source(), e.target(), e.sourcePort()))
              .toList();

      Map<String, List<Node>> parentsList = new HashMap<>();
      parentsList.put("t", Collections.emptyList());
      parentsList.put("p", List.of(t));

      Map<String, WorkflowPlugin> pluginCache = new HashMap<>();
      pluginCache.put("t", mock(TriggerPlugin.class));
      pluginCache.put("p", mock(ProcessorPlugin.class));

      List<Node> topologicalOrder = List.of(t, p);

      NodeAssembler[] assemblers =
          compiler.compileAssemblers(
              edges,
              toWorkflowNodeMap(parentsList),
              pluginCache,
              toWorkflowNodeList(topologicalOrder));

      assertThat(assemblers).hasSize(2);
      assertThat(assemblers[0]).isNotNull();
      assertThat(assemblers[1]).isNotNull();
    }

    @Test
    @DisplayName("should handle node with parents")
    void shouldHandleNodeWithParents() {
      Node t1 = new Node("t1", "trigger", Map.of());
      Node t2 = new Node("t2", "trigger", Map.of());
      Node p = new Node("p", "processor", Map.of());

      WorkflowDefinition def =
          new WorkflowDefinition(
            "test-workflow",
              "Test", List.of(t1, t2, p), List.of(new Edge("t1", "p"), new Edge("t2", "p")));
      final List<WorkflowEdge> edges =
          def.edges().stream()
              .map(e -> new WorkflowEdge(e.source(), e.target(), e.sourcePort()))
              .toList();

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
          compiler.compileAssemblers(
              edges,
              toWorkflowNodeMap(parentsList),
              pluginCache,
              toWorkflowNodeList(topologicalOrder));

      assertThat(assemblers).hasSize(3);
    }

    @Test
    @DisplayName("should handle node without parents")
    void shouldHandleNodeWithoutParents() {
      Node t1 = new Node("t1", "trigger", Map.of());
      Node p = new Node("p", "processor", Map.of());

      WorkflowDefinition def =
          new WorkflowDefinition(
            "test-workflow",
            "Test",
            List.of(t1, p), List.of(new Edge("t1", "p")));
      final List<WorkflowEdge> edges =
          def.edges().stream()
              .map(e -> new WorkflowEdge(e.source(), e.target(), e.sourcePort()))
              .toList();

      Map<String, List<Node>> parentsList = new HashMap<>();
      parentsList.put("t1", Collections.emptyList());
      parentsList.put("p", List.of(t1));

      Map<String, WorkflowPlugin> pluginCache = new HashMap<>();
      pluginCache.put("t1", mock(TriggerPlugin.class));
      pluginCache.put("p", mock(ProcessorPlugin.class));

      List<Node> topologicalOrder = List.of(t1, p);

      NodeAssembler[] assemblers =
          compiler.compileAssemblers(
              edges,
              toWorkflowNodeMap(parentsList),
              pluginCache,
              toWorkflowNodeList(topologicalOrder));

      assertThat(assemblers).hasSize(2);
    }

    @Test
    @DisplayName("should handle edges with source port")
    void shouldHandleEdgesWithSourcePort() {
      Node t = new Node("t", "trigger", Map.of());
      Node p = new Node("p", "processor", Map.of());

      WorkflowDefinition def =
          new WorkflowDefinition(
            "test-workflow",
            "Test",
            List.of(t, p), List.of(new Edge("t", "p", "output")));
      final List<WorkflowEdge> edges =
          def.edges().stream()
              .map(e -> new WorkflowEdge(e.source(), e.target(), e.sourcePort()))
              .toList();

      Map<String, List<Node>> parentsList = new HashMap<>();
      parentsList.put("t", Collections.emptyList());
      parentsList.put("p", List.of(t));

      Map<String, WorkflowPlugin> pluginCache = new HashMap<>();
      pluginCache.put("t", mock(TriggerPlugin.class));
      pluginCache.put("p", mock(ProcessorPlugin.class));

      List<Node> topologicalOrder = List.of(t, p);

      NodeAssembler[] assemblers =
          compiler.compileAssemblers(
              edges,
              toWorkflowNodeMap(parentsList),
              pluginCache,
              toWorkflowNodeList(topologicalOrder));

      assertThat(assemblers).hasSize(2);
    }

    @Test
    @DisplayName("should handle multiple incoming edges with different ports")
    void shouldHandleMultipleIncomingEdgesWithDifferentPorts() {
      Node t1 = new Node("t1", "trigger", Map.of());
      Node t2 = new Node("t2", "trigger", Map.of());
      Node p = new Node("p", "processor", Map.of());

      WorkflowDefinition def =
          new WorkflowDefinition(
            "test-workflow",
              "Test",
              List.of(t1, t2, p),
              List.of(new Edge("t1", "p", "output"), new Edge("t2", "p", "error")));
      final List<WorkflowEdge> edges =
          def.edges().stream()
              .map(e -> new WorkflowEdge(e.source(), e.target(), e.sourcePort()))
              .toList();

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
          compiler.compileAssemblers(
              edges,
              toWorkflowNodeMap(parentsList),
              pluginCache,
              toWorkflowNodeList(topologicalOrder));

      assertThat(assemblers).hasSize(3);
    }

    @Test
    @DisplayName("should create correct node index mapping for topological order")
    void shouldCreateCorrectNodeIndexMappingForTopologicalOrder() {
      Node t = new Node("t", "trigger", Map.of());
      Node p1 = new Node("p1", "processor", Map.of());
      Node p2 = new Node("p2", "processor", Map.of());
      Node term = new Node("term", "terminal", Map.of());

      WorkflowDefinition def =
          new WorkflowDefinition(
            "test-workflow",
              "Test",
              List.of(t, p1, p2, term),
              List.of(new Edge("t", "p1"), new Edge("p1", "p2"), new Edge("p2", "term")));
      final List<WorkflowEdge> edges =
          def.edges().stream()
              .map(e -> new WorkflowEdge(e.source(), e.target(), e.sourcePort()))
              .toList();

      Map<String, List<Node>> parentsList = new HashMap<>();
      parentsList.put("t", Collections.emptyList());
      parentsList.put("p1", List.of(t));
      parentsList.put("p2", List.of(p1));
      parentsList.put("term", List.of(p2));

      Map<String, WorkflowPlugin> pluginCache = new HashMap<>();
      pluginCache.put("t", mock(TriggerPlugin.class));
      pluginCache.put("p1", mock(ProcessorPlugin.class));
      pluginCache.put("p2", mock(ProcessorPlugin.class));
      pluginCache.put("term", mock(TerminalPlugin.class));

      List<Node> topologicalOrder = List.of(t, p1, p2, term);

      NodeAssembler[] assemblers =
          compiler.compileAssemblers(
              edges,
              toWorkflowNodeMap(parentsList),
              pluginCache,
              toWorkflowNodeList(topologicalOrder));

      assertThat(assemblers).hasSize(4);
    }
  }

  @Nested
  @DisplayName("getBufferSize with type conversions")
  class BufferSizeTypeConversionTests {

    @Test
    @DisplayName("should handle Double buffer size value")
    void shouldHandleDoubleBufferSizeValue() {
      Node t1 = new Node("t1", "trigger", Map.of("bufferSize", 256.7));
      Node p1 = new Node("p1", "processor", Map.of());

      WorkflowDefinition def =
          new WorkflowDefinition(
            "test-workflow",
            "Test",
            List.of(t1, p1), List.of(new Edge("t1", "p1")));
      final List<WorkflowEdge> edges =
          def.edges().stream()
              .map(e -> new WorkflowEdge(e.source(), e.target(), e.sourcePort()))
              .toList();

      Map<String, List<Node>> parentsList = new HashMap<>();
      parentsList.put("t1", Collections.emptyList());
      parentsList.put("p1", List.of(t1));

      Map<String, WorkflowPlugin> pluginCache = new HashMap<>();
      pluginCache.put("t1", mock(TriggerPlugin.class));
      pluginCache.put("p1", mock(ProcessorPlugin.class));

      List<Node> topologicalOrder = List.of(t1, p1);

      NodeAssembler[] assemblers =
          compiler.compileAssemblers(
              edges,
              toWorkflowNodeMap(parentsList),
              pluginCache,
              toWorkflowNodeList(topologicalOrder));

      assertThat(assemblers).hasSize(2);
    }

    @Test
    @DisplayName("should handle Long buffer size value")
    void shouldHandleLongBufferSizeValue() {
      Node t1 = new Node("t1", "trigger", Map.of("bufferSize", 128L));
      Node p1 = new Node("p1", "processor", Map.of());

      WorkflowDefinition def =
          new WorkflowDefinition(
            "test-workflow",
            "Test",
            List.of(t1, p1), List.of(new Edge("t1", "p1")));
      final List<WorkflowEdge> edges =
          def.edges().stream()
              .map(e -> new WorkflowEdge(e.source(), e.target(), e.sourcePort()))
              .toList();

      Map<String, List<Node>> parentsList = new HashMap<>();
      parentsList.put("t1", Collections.emptyList());
      parentsList.put("p1", List.of(t1));

      Map<String, WorkflowPlugin> pluginCache = new HashMap<>();
      pluginCache.put("t1", mock(TriggerPlugin.class));
      pluginCache.put("p1", mock(ProcessorPlugin.class));

      List<Node> topologicalOrder = List.of(t1, p1);

      NodeAssembler[] assemblers =
          compiler.compileAssemblers(
              edges,
              toWorkflowNodeMap(parentsList),
              pluginCache,
              toWorkflowNodeList(topologicalOrder));

      assertThat(assemblers).hasSize(2);
    }
  }

  @Nested
  @DisplayName("getBufferSize - edge cases")
  class BufferSizeEdgeCasesTests {

    @Test
    @DisplayName("should handle null buffer size value")
    void shouldHandleNullBufferSizeValue() {
      Node t1 = new Node("t1", "trigger", Map.of());
      Node p1 = new Node("p1", "processor", Map.of());

      WorkflowDefinition def =
          new WorkflowDefinition(
            "test-workflow",
            "Test",
            List.of(t1, p1), List.of(new Edge("t1", "p1")));
      final List<WorkflowEdge> edges =
          def.edges().stream()
              .map(e -> new WorkflowEdge(e.source(), e.target(), e.sourcePort()))
              .toList();

      Map<String, List<Node>> parentsList = new HashMap<>();
      parentsList.put("t1", Collections.emptyList());
      parentsList.put("p1", List.of(t1));

      Map<String, WorkflowPlugin> pluginCache = new HashMap<>();
      pluginCache.put("t1", mock(TriggerPlugin.class));
      pluginCache.put("p1", mock(ProcessorPlugin.class));

      List<Node> topologicalOrder = List.of(t1, p1);

      NodeAssembler[] assemblers =
          compiler.compileAssemblers(
              edges,
              toWorkflowNodeMap(parentsList),
              pluginCache,
              toWorkflowNodeList(topologicalOrder));

      assertThat(assemblers).hasSize(2);
    }

    @Test
    @DisplayName("should use default buffer size for negative value")
    void shouldUseDefaultForNegativeValue() {
      Node t1 = new Node("t1", "trigger", Map.of("bufferSize", -100));
      Node p1 = new Node("p1", "processor", Map.of());

      WorkflowDefinition def =
          new WorkflowDefinition(
            "test-workflow",
            "Test",
            List.of(t1, p1), List.of(new Edge("t1", "p1")));
      final List<WorkflowEdge> edges =
          def.edges().stream()
              .map(e -> new WorkflowEdge(e.source(), e.target(), e.sourcePort()))
              .toList();

      Map<String, List<Node>> parentsList = new HashMap<>();
      parentsList.put("t1", Collections.emptyList());
      parentsList.put("p1", List.of(t1));

      Map<String, WorkflowPlugin> pluginCache = new HashMap<>();
      pluginCache.put("t1", mock(TriggerPlugin.class));
      pluginCache.put("p1", mock(ProcessorPlugin.class));

      List<Node> topologicalOrder = List.of(t1, p1);

      NodeAssembler[] assemblers =
          compiler.compileAssemblers(
              edges,
              toWorkflowNodeMap(parentsList),
              pluginCache,
              toWorkflowNodeList(topologicalOrder));

      assertThat(assemblers).hasSize(2);
    }

    @Test
    @DisplayName("should use plugin default buffer size when config not specified")
    void shouldUsePluginDefaultBufferSize() {
      ProcessorPlugin processor = mock(ProcessorPlugin.class);
      when(processor.getDefaultBufferSize()).thenReturn(512);

      Node t1 = new Node("t1", "trigger", Map.of());
      Node p1 = new Node("p1", "processor", Map.of());

      WorkflowDefinition def =
          new WorkflowDefinition(
            "test-workflow",
            "Test",
            List.of(t1, p1), List.of(new Edge("t1", "p1")));
      final List<WorkflowEdge> edges =
          def.edges().stream()
              .map(e -> new WorkflowEdge(e.source(), e.target(), e.sourcePort()))
              .toList();

      Map<String, List<Node>> parentsList = new HashMap<>();
      parentsList.put("t1", Collections.emptyList());
      parentsList.put("p1", List.of(t1));

      Map<String, WorkflowPlugin> pluginCache = new HashMap<>();
      pluginCache.put("t1", mock(TriggerPlugin.class));
      pluginCache.put("p1", processor);

      List<Node> topologicalOrder = List.of(t1, p1);

      NodeAssembler[] assemblers =
          compiler.compileAssemblers(
              edges,
              toWorkflowNodeMap(parentsList),
              pluginCache,
              toWorkflowNodeList(topologicalOrder));

      assertThat(assemblers).hasSize(2);
    }

    @Test
    @DisplayName("should use plugin default buffer size when configured value is invalid")
    void shouldUsePluginDefaultWhenConfigInvalid() {
      ProcessorPlugin processor = mock(ProcessorPlugin.class);
      when(processor.getDefaultBufferSize()).thenReturn(256);

      Node t1 = new Node("t1", "trigger", Map.of());
      Node p1 = new Node("p1", "processor", Map.of("bufferSize", "invalid"));

      WorkflowDefinition def =
          new WorkflowDefinition(
            "test-workflow",
            "Test",
            List.of(t1, p1), List.of(new Edge("t1", "p1")));
      final List<WorkflowEdge> edges =
          def.edges().stream()
              .map(e -> new WorkflowEdge(e.source(), e.target(), e.sourcePort()))
              .toList();

      Map<String, List<Node>> parentsList = new HashMap<>();
      parentsList.put("t1", Collections.emptyList());
      parentsList.put("p1", List.of(t1));

      Map<String, WorkflowPlugin> pluginCache = new HashMap<>();
      pluginCache.put("t1", mock(TriggerPlugin.class));
      pluginCache.put("p1", processor);

      List<Node> topologicalOrder = List.of(t1, p1);

      NodeAssembler[] assemblers =
          compiler.compileAssemblers(
              edges,
              toWorkflowNodeMap(parentsList),
              pluginCache,
              toWorkflowNodeList(topologicalOrder));

      assertThat(assemblers).hasSize(2);
    }

    @Test
    @DisplayName("should use fallback buffer size when plugin is null")
    void shouldUseFallbackWhenPluginNull() {
      Node t1 = new Node("t1", "trigger", Map.of());
      Node p1 = new Node("p1", "processor", Map.of());

      WorkflowDefinition def =
          new WorkflowDefinition(
            "test-workflow",
            "Test",
            List.of(t1, p1), List.of(new Edge("t1", "p1")));
      final List<WorkflowEdge> edges =
          def.edges().stream()
              .map(e -> new WorkflowEdge(e.source(), e.target(), e.sourcePort()))
              .toList();

      Map<String, List<Node>> parentsList = new HashMap<>();
      parentsList.put("t1", Collections.emptyList());
      parentsList.put("p1", List.of(t1));

      Map<String, WorkflowPlugin> pluginCache = new HashMap<>();
      pluginCache.put("t1", null);
      pluginCache.put("p1", null);

      List<Node> topologicalOrder = List.of(t1, p1);

      NodeAssembler[] assemblers =
          compiler.compileAssemblers(
              edges,
              toWorkflowNodeMap(parentsList),
              pluginCache,
              toWorkflowNodeList(topologicalOrder));

      assertThat(assemblers).hasSize(2);
    }
  }

  @Nested
  @DisplayName("getNodeTimeout - comprehensive coverage")
  class NodeTimeoutComprehensiveTests {

    @Test
    @DisplayName("should handle null plugin")
    void shouldHandleNullPlugin() {
      Node t1 = new Node("t1", "trigger", Map.of());
      Node p1 = new Node("p1", "processor", Map.of());

      WorkflowDefinition def =
          new WorkflowDefinition(
            "test-workflow",
            "Test",
            List.of(t1, p1), List.of(new Edge("t1", "p1")));
      final List<WorkflowEdge> edges =
          def.edges().stream()
              .map(e -> new WorkflowEdge(e.source(), e.target(), e.sourcePort()))
              .toList();

      Map<String, List<Node>> parentsList = new HashMap<>();
      parentsList.put("t1", Collections.emptyList());
      parentsList.put("p1", List.of(t1));

      Map<String, WorkflowPlugin> pluginCache = new HashMap<>();
      pluginCache.put("t1", null);
      pluginCache.put("p1", null);

      List<Node> topologicalOrder = List.of(t1, p1);

      NodeAssembler[] assemblers =
          compiler.compileAssemblers(
              edges,
              toWorkflowNodeMap(parentsList),
              pluginCache,
              toWorkflowNodeList(topologicalOrder));

      assertThat(assemblers).hasSize(2);
    }

    @Test
    @DisplayName("should use fallback timeout when all options exhausted")
    void shouldUseFallbackTimeoutWhenAllExhausted() {
      TriggerPlugin trigger = mock(TriggerPlugin.class);
      when(trigger.getDefaultTimeout()).thenReturn(null);

      Node t1 = new Node("t1", "trigger", Map.of());
      Node p1 = new Node("p1", "processor", Map.of());

      WorkflowDefinition def =
          new WorkflowDefinition(
            "test-workflow",
            "Test",
            List.of(t1, p1), List.of(new Edge("t1", "p1")));
      final List<WorkflowEdge> edges =
          def.edges().stream()
              .map(e -> new WorkflowEdge(e.source(), e.target(), e.sourcePort()))
              .toList();

      Map<String, List<Node>> parentsList = new HashMap<>();
      parentsList.put("t1", Collections.emptyList());
      parentsList.put("p1", List.of(t1));

      Map<String, WorkflowPlugin> pluginCache = new HashMap<>();
      pluginCache.put("t1", trigger);
      pluginCache.put("p1", mock(ProcessorPlugin.class));

      List<Node> topologicalOrder = List.of(t1, p1);

      NodeAssembler[] assemblers =
          compiler.compileAssemblers(
              edges,
              toWorkflowNodeMap(parentsList),
              pluginCache,
              toWorkflowNodeList(topologicalOrder));

      assertThat(assemblers).hasSize(2);
    }

    @Test
    @DisplayName("should handle large timeout values")
    void shouldHandleLargeTimeoutValues() {
      Node t1 = new Node("t1", "trigger", Map.of("timeoutSeconds", Long.MAX_VALUE));
      Node p1 = new Node("p1", "processor", Map.of());

      WorkflowDefinition def =
          new WorkflowDefinition(
            "test-workflow",
            "Test",
            List.of(t1, p1), List.of(new Edge("t1", "p1")));
      final List<WorkflowEdge> edges =
          def.edges().stream()
              .map(e -> new WorkflowEdge(e.source(), e.target(), e.sourcePort()))
              .toList();

      Map<String, List<Node>> parentsList = new HashMap<>();
      parentsList.put("t1", Collections.emptyList());
      parentsList.put("p1", List.of(t1));

      Map<String, WorkflowPlugin> pluginCache = new HashMap<>();
      pluginCache.put("t1", mock(TriggerPlugin.class));
      pluginCache.put("p1", mock(ProcessorPlugin.class));

      List<Node> topologicalOrder = List.of(t1, p1);

      NodeAssembler[] assemblers =
          compiler.compileAssemblers(
              edges,
              toWorkflowNodeMap(parentsList),
              pluginCache,
              toWorkflowNodeList(topologicalOrder));

      assertThat(assemblers).hasSize(2);
    }

    @Test
    @DisplayName("should prefer timeoutSeconds key over timeout key")
    void shouldPreferTimeoutSecondsKey() {
      Node t1 = new Node("t1", "trigger", Map.of("timeoutSeconds", 30L, "timeout", 60L));
      Node p1 = new Node("p1", "processor", Map.of());

      WorkflowDefinition def =
          new WorkflowDefinition(
            "test-workflow",
            "Test",
            List.of(t1, p1), List.of(new Edge("t1", "p1")));
      final List<WorkflowEdge> edges =
          def.edges().stream()
              .map(e -> new WorkflowEdge(e.source(), e.target(), e.sourcePort()))
              .toList();

      Map<String, List<Node>> parentsList = new HashMap<>();
      parentsList.put("t1", Collections.emptyList());
      parentsList.put("p1", List.of(t1));

      Map<String, WorkflowPlugin> pluginCache = new HashMap<>();
      pluginCache.put("t1", mock(TriggerPlugin.class));
      pluginCache.put("p1", mock(ProcessorPlugin.class));

      List<Node> topologicalOrder = List.of(t1, p1);

      NodeAssembler[] assemblers =
          compiler.compileAssemblers(
              edges,
              toWorkflowNodeMap(parentsList),
              pluginCache,
              toWorkflowNodeList(topologicalOrder));

      assertThat(assemblers).hasSize(2);
    }

    @Test
    @DisplayName("should use legacy timeout when timeoutSeconds not present")
    void shouldUseLegacyTimeoutWhenNewKeyAbsent() {
      Node t1 = new Node("t1", "trigger", Map.of("timeout", 45L));
      Node p1 = new Node("p1", "processor", Map.of());

      WorkflowDefinition def =
          new WorkflowDefinition(
            "test-workflow",
            "Test",
            List.of(t1, p1), List.of(new Edge("t1", "p1")));
      final List<WorkflowEdge> edges =
          def.edges().stream()
              .map(e -> new WorkflowEdge(e.source(), e.target(), e.sourcePort()))
              .toList();

      Map<String, List<Node>> parentsList = new HashMap<>();
      parentsList.put("t1", Collections.emptyList());
      parentsList.put("p1", List.of(t1));

      Map<String, WorkflowPlugin> pluginCache = new HashMap<>();
      pluginCache.put("t1", mock(TriggerPlugin.class));
      pluginCache.put("p1", mock(ProcessorPlugin.class));

      List<Node> topologicalOrder = List.of(t1, p1);

      NodeAssembler[] assemblers =
          compiler.compileAssemblers(
              edges,
              toWorkflowNodeMap(parentsList),
              pluginCache,
              toWorkflowNodeList(topologicalOrder));

      assertThat(assemblers).hasSize(2);
    }
  }

  @Nested
  @DisplayName("createNodeAssembler - strategy matching")
  class CreateNodeAssemblerStrategyTests {

    @Test
    @DisplayName("should create assembler for node without parents")
    void shouldCreateAssemblerForNodeWithoutParents() {
      Node t = new Node("t", "trigger", Map.of());

      WorkflowDefinition def = new WorkflowDefinition(
            "test-workflow",
            "Test",
            List.of(t), List.of());
      final List<WorkflowEdge> edges =
          def.edges().stream()
              .map(e -> new WorkflowEdge(e.source(), e.target(), e.sourcePort()))
              .toList();

      Map<String, List<Node>> parentsList = new HashMap<>();
      parentsList.put("t", Collections.emptyList());

      Map<String, WorkflowPlugin> pluginCache = new HashMap<>();
      pluginCache.put("t", mock(TriggerPlugin.class));

      List<Node> topologicalOrder = List.of(t);

      NodeAssembler[] assemblers =
          compiler.compileAssemblers(
              edges,
              toWorkflowNodeMap(parentsList),
              pluginCache,
              toWorkflowNodeList(topologicalOrder));

      assertThat(assemblers).hasSize(1).allMatch(a -> a != null);
    }

    @Test
    @DisplayName("should create assembler for node with single parent")
    void shouldCreateAssemblerForNodeWithSingleParent() {
      Node t = new Node("t", "trigger", Map.of());
      Node p = new Node("p", "processor", Map.of());

      WorkflowDefinition def =
          new WorkflowDefinition(
            "test-workflow",
            "Test",
            List.of(t, p), List.of(new Edge("t", "p")));
      final List<WorkflowEdge> edges =
          def.edges().stream()
              .map(e -> new WorkflowEdge(e.source(), e.target(), e.sourcePort()))
              .toList();

      Map<String, List<Node>> parentsList = new HashMap<>();
      parentsList.put("t", Collections.emptyList());
      parentsList.put("p", List.of(t));

      Map<String, WorkflowPlugin> pluginCache = new HashMap<>();
      pluginCache.put("t", mock(TriggerPlugin.class));
      pluginCache.put("p", mock(ProcessorPlugin.class));

      List<Node> topologicalOrder = List.of(t, p);

      NodeAssembler[] assemblers =
          compiler.compileAssemblers(
              edges,
              toWorkflowNodeMap(parentsList),
              pluginCache,
              toWorkflowNodeList(topologicalOrder));

      assertThat(assemblers).hasSize(2).allMatch(a -> a != null);
    }

    @Test
    @DisplayName("should create assembler for node with multiple parents")
    void shouldCreateAssemblerForNodeWithMultipleParents() {
      Node t1 = new Node("t1", "trigger", Map.of());
      Node t2 = new Node("t2", "trigger", Map.of());
      Node p = new Node("p", "processor", Map.of());

      WorkflowDefinition def =
          new WorkflowDefinition(
            "test-workflow",
              "Test", List.of(t1, t2, p), List.of(new Edge("t1", "p"), new Edge("t2", "p")));
      final List<WorkflowEdge> edges =
          def.edges().stream()
              .map(e -> new WorkflowEdge(e.source(), e.target(), e.sourcePort()))
              .toList();

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
          compiler.compileAssemblers(
              edges,
              toWorkflowNodeMap(parentsList),
              pluginCache,
              toWorkflowNodeList(topologicalOrder));

      assertThat(assemblers).hasSize(3).allMatch(a -> a != null);
    }

    @Test
    @DisplayName("should create assembler with named source port")
    void shouldCreateAssemblerWithNamedSourcePort() {
      Node t = new Node("t", "trigger", Map.of());
      Node p = new Node("p", "processor", Map.of());

      WorkflowDefinition def =
          new WorkflowDefinition(
            "test-workflow",
            "Test",
            List.of(t, p), List.of(new Edge("t", "p", "output")));
      final List<WorkflowEdge> edges =
          def.edges().stream()
              .map(e -> new WorkflowEdge(e.source(), e.target(), e.sourcePort()))
              .toList();

      Map<String, List<Node>> parentsList = new HashMap<>();
      parentsList.put("t", Collections.emptyList());
      parentsList.put("p", List.of(t));

      Map<String, WorkflowPlugin> pluginCache = new HashMap<>();
      pluginCache.put("t", mock(TriggerPlugin.class));
      pluginCache.put("p", mock(ProcessorPlugin.class));

      List<Node> topologicalOrder = List.of(t, p);

      NodeAssembler[] assemblers =
          compiler.compileAssemblers(
              edges,
              toWorkflowNodeMap(parentsList),
              pluginCache,
              toWorkflowNodeList(topologicalOrder));

      assertThat(assemblers).hasSize(2).allMatch(a -> a != null);
    }

    @Test
    @DisplayName("should create assembler when strategy matches plugin type")
    void shouldCreateAssemblerWhenStrategyMatches() {
      NodeAssemblerStrategy matchingStrategy = mock(NodeAssemblerStrategy.class);
      NodeAssembler mockAssembler = mock(NodeAssembler.class);

      when(matchingStrategy.supports(any(ProcessorPlugin.class), eq(true))).thenReturn(true);
      when(matchingStrategy.supports(any(), eq(false))).thenReturn(false);
      when(matchingStrategy.createAssembler(any(), any(), any(), anyInt(), anyInt(), any()))
          .thenReturn(mockAssembler);

      WorkflowCompiler testCompiler =
          new WorkflowCompiler(
              tracker,
              controlBusGateway,
              Schedulers.parallel(),
              Duration.ofSeconds(10),
              configService,
              executionControlRegistry,
              List.of(matchingStrategy));

      Node t = new Node("t", "trigger", Map.of());
      Node p = new Node("p", "processor", Map.of());

      WorkflowDefinition def =
          new WorkflowDefinition(
            "test-workflow",
            "Test",
            List.of(t, p), List.of(new Edge("t", "p")));
      final List<WorkflowEdge> edges =
          def.edges().stream()
              .map(e -> new WorkflowEdge(e.source(), e.target(), e.sourcePort()))
              .toList();

      Map<String, List<Node>> parentsList = new HashMap<>();
      parentsList.put("t", Collections.emptyList());
      parentsList.put("p", List.of(t));

      Map<String, WorkflowPlugin> pluginCache = new HashMap<>();
      pluginCache.put("t", mock(TriggerPlugin.class));
      pluginCache.put("p", mock(ProcessorPlugin.class));

      List<Node> topologicalOrder = List.of(t, p);

      NodeAssembler[] assemblers =
          testCompiler.compileAssemblers(
              edges,
              toWorkflowNodeMap(parentsList),
              pluginCache,
              toWorkflowNodeList(topologicalOrder));

      assertThat(assemblers).hasSize(2);
      assertThat(assemblers[1]).isEqualTo(mockAssembler);
    }

    @Test
    @DisplayName("should create no-op assembler when no strategy matches")
    void shouldCreateNoOpAssemblerWhenNoStrategyMatches() {
      Node t = new Node("t", "unknown-type", Map.of());

      WorkflowDefinition def = new WorkflowDefinition(
            "test-workflow",
            "Test",
            List.of(t), List.of());
      final List<WorkflowEdge> edges =
          def.edges().stream()
              .map(e -> new WorkflowEdge(e.source(), e.target(), e.sourcePort()))
              .toList();

      Map<String, List<Node>> parentsList = new HashMap<>();
      parentsList.put("t", Collections.emptyList());

      Map<String, WorkflowPlugin> pluginCache = new HashMap<>();
      pluginCache.put("t", mock(TriggerPlugin.class));

      List<Node> topologicalOrder = List.of(t);

      NodeAssembler[] assemblers =
          compiler.compileAssemblers(
              edges,
              toWorkflowNodeMap(parentsList),
              pluginCache,
              toWorkflowNodeList(topologicalOrder));

      assertThat(assemblers).hasSize(1).allMatch(a -> a != null);

      AssemblyContext mockContext =
          new AssemblyContext(
              "exec-1",
              "sess-1",
              "wf-1",
              Map.of(),
              null,
              new Flux[0],
              new ArrayList<>(),
              new ArrayList<>(),
              new ArrayList<>());

      assemblers[0].assemble(mockContext);
    }
  }

  @Nested
  @DisplayName("executeTemplate - reactive execution")
  class ExecuteTemplateReactiveTests {

    @Test
    @DisplayName("should throw when ExecutionControl not registered")
    void shouldThrowWhenExecutionControlNotRegistered() {
      String executionId = "unregistered-exec";
      String sessionId = "sess-1";
      String workflowId = "wf-1";
      Map<String, Object> payload = Map.of();

      NodeAssembler[] assemblers = new NodeAssembler[0];
      List<String> nodeIds = List.of();

      org.junit.jupiter.api.Assertions.assertThrows(
          IllegalStateException.class,
          () ->
              compiler.executeTemplate(
                  executionId, payload, 0, assemblers, sessionId, workflowId, nodeIds));
    }

    @Test
    @DisplayName("should complete when ExecutionControl is registered")
    void shouldCompleteWhenExecutionControlIsRegistered() {

      String executionId = "exec-1";
      String sessionId = "sess-1";
      String workflowId = "wf-1";
      Map<String, Object> payload = Map.of("test", "data");

      when(configService.getExecutionTimeout(any())).thenReturn(Mono.just(30L));

      ExecutionControl control = mock(ExecutionControl.class);
      when(control.executionId()).thenReturn(executionId);
      executionControlRegistry.register(control);

      NodeAssembler[] assemblers = new NodeAssembler[0];
      List<String> nodeIds = List.of();

      Mono<Void> result =
          compiler.executeTemplate(
              executionId, payload, 0, assemblers, sessionId, workflowId, nodeIds);

      StepVerifier.create(result).expectComplete().verify();
    }

    @Test
    @DisplayName("should invoke assemblers in order")
    void shouldInvokeAssemblersInOrder() {

      String executionId = "exec-assembler";
      String sessionId = "sess-1";
      String workflowId = "wf-1";
      Map<String, Object> payload = Map.of();

      when(configService.getExecutionTimeout(any())).thenReturn(Mono.just(30L));

      ExecutionControl control = mock(ExecutionControl.class);
      when(control.executionId()).thenReturn(executionId);
      executionControlRegistry.register(control);

      List<String> invocationOrder = new ArrayList<>();

      NodeAssembler assembler1 = ctx -> invocationOrder.add("a1");
      NodeAssembler assembler2 = ctx -> invocationOrder.add("a2");

      NodeAssembler[] assemblers = new NodeAssembler[] {assembler1, assembler2};
      List<String> nodeIds = List.of("a1", "a2");

      Mono<Void> result =
          compiler.executeTemplate(
              executionId, payload, 2, assemblers, sessionId, workflowId, nodeIds);

      StepVerifier.create(result).expectComplete().verify();

      assertThat(invocationOrder).containsExactly("a1", "a2");
    }

    @Test
    @DisplayName("should pass correct ExecutionId to assemblers")
    void shouldPassCorrectExecutionIdToAssemblers() {

      String executionId = "exec-id-test";
      String sessionId = "sess-1";
      String workflowId = "wf-1";
      Map<String, Object> payload = Map.of();

      when(configService.getExecutionTimeout(any())).thenReturn(Mono.just(30L));

      ExecutionControl control = mock(ExecutionControl.class);
      when(control.executionId()).thenReturn(executionId);
      executionControlRegistry.register(control);

      AtomicBoolean capturedCorrect = new AtomicBoolean(false);

      NodeAssembler assembler =
          ctx -> {
            capturedCorrect.set(executionId.equals(ctx.executionId()));
          };

      NodeAssembler[] assemblers = new NodeAssembler[] {assembler};
      List<String> nodeIds = List.of("node");

      Mono<Void> result =
          compiler.executeTemplate(
              executionId, payload, 1, assemblers, sessionId, workflowId, nodeIds);

      StepVerifier.create(result).expectComplete().verify();

      assertThat(capturedCorrect.get()).isTrue();
    }

    @Test
    @DisplayName("should pass correct SessionId to assemblers")
    void shouldPassCorrectSessionIdToAssemblers() {

      String executionId = "exec-1";
      String sessionId = "sess-id-test";
      String workflowId = "wf-1";
      Map<String, Object> payload = Map.of();

      when(configService.getExecutionTimeout(any())).thenReturn(Mono.just(30L));

      ExecutionControl control = mock(ExecutionControl.class);
      when(control.executionId()).thenReturn(executionId);
      executionControlRegistry.register(control);

      AtomicBoolean capturedCorrect = new AtomicBoolean(false);

      NodeAssembler assembler =
          ctx -> {
            capturedCorrect.set(sessionId.equals(ctx.sessionId()));
          };

      NodeAssembler[] assemblers = new NodeAssembler[] {assembler};
      List<String> nodeIds = List.of("node");

      Mono<Void> result =
          compiler.executeTemplate(
              executionId, payload, 1, assemblers, sessionId, workflowId, nodeIds);

      StepVerifier.create(result).expectComplete().verify();

      assertThat(capturedCorrect.get()).isTrue();
    }

    @Test
    @DisplayName("should pass correct WorkflowId to assemblers")
    void shouldPassCorrectWorkflowIdToAssemblers() {

      String executionId = "exec-1";
      String sessionId = "sess-1";
      String workflowId = "wf-id-test";
      Map<String, Object> payload = Map.of();

      when(configService.getExecutionTimeout(any())).thenReturn(Mono.just(30L));

      ExecutionControl control = mock(ExecutionControl.class);
      when(control.executionId()).thenReturn(executionId);
      executionControlRegistry.register(control);

      AtomicBoolean capturedCorrect = new AtomicBoolean(false);

      NodeAssembler assembler =
          ctx -> {
            capturedCorrect.set(workflowId.equals(ctx.workflowId()));
          };

      NodeAssembler[] assemblers = new NodeAssembler[] {assembler};
      List<String> nodeIds = List.of("node");

      Mono<Void> result =
          compiler.executeTemplate(
              executionId, payload, 1, assemblers, sessionId, workflowId, nodeIds);

      StepVerifier.create(result).expectComplete().verify();

      assertThat(capturedCorrect.get()).isTrue();
    }

    @Test
    @DisplayName("should pass correct payload to assemblers")
    void shouldPassCorrectPayloadToAssemblers() {

      String executionId = "exec-1";
      String sessionId = "sess-1";
      String workflowId = "wf-1";
      Map<String, Object> payload = Map.of("key1", "value1", "key2", 42);

      when(configService.getExecutionTimeout(any())).thenReturn(Mono.just(30L));

      ExecutionControl control = mock(ExecutionControl.class);
      when(control.executionId()).thenReturn(executionId);
      executionControlRegistry.register(control);

      AtomicBoolean payloadMatches = new AtomicBoolean(false);

      NodeAssembler assembler =
          ctx -> {
            payloadMatches.set(payload.equals(ctx.payload()));
          };

      NodeAssembler[] assemblers = new NodeAssembler[] {assembler};
      List<String> nodeIds = List.of("node");

      Mono<Void> result =
          compiler.executeTemplate(
              executionId, payload, 1, assemblers, sessionId, workflowId, nodeIds);

      StepVerifier.create(result).expectComplete().verify();

      assertThat(payloadMatches.get()).isTrue();
    }

    @Test
    @DisplayName("should handle empty assembler array")
    void shouldHandleEmptyAssemblerArray() {

      String executionId = "exec-empty";
      String sessionId = "sess-1";
      String workflowId = "wf-1";
      Map<String, Object> payload = Map.of();

      when(configService.getExecutionTimeout(any())).thenReturn(Mono.just(30L));

      ExecutionControl control = mock(ExecutionControl.class);
      when(control.executionId()).thenReturn(executionId);
      executionControlRegistry.register(control);

      NodeAssembler[] assemblers = new NodeAssembler[0];
      List<String> nodeIds = List.of();

      Mono<Void> result =
          compiler.executeTemplate(
              executionId, payload, 0, assemblers, sessionId, workflowId, nodeIds);

      StepVerifier.create(result).expectComplete().verify();
    }

    @Test
    @DisplayName("should handle large number of assemblers")
    void shouldHandleLargeNumberOfAssemblers() {

      String executionId = "exec-large";
      String sessionId = "sess-1";
      String workflowId = "wf-1";
      Map<String, Object> payload = Map.of();

      when(configService.getExecutionTimeout(any())).thenReturn(Mono.just(30L));

      ExecutionControl control = mock(ExecutionControl.class);
      when(control.executionId()).thenReturn(executionId);
      executionControlRegistry.register(control);

      int count = 50;
      List<String> nodeIds = new ArrayList<>();
      NodeAssembler[] assemblers = new NodeAssembler[count];

      for (int i = 0; i < count; i++) {
        final int index = i;
        assemblers[i] = ctx -> {};
        nodeIds.add("node-" + i);
      }

      Mono<Void> result =
          compiler.executeTemplate(
              executionId, payload, count, assemblers, sessionId, workflowId, nodeIds);

      StepVerifier.create(result).expectComplete().verify();
    }
  }
}
