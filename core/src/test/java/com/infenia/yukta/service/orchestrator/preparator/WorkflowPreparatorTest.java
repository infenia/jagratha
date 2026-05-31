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
package com.infenia.yukta.service.orchestrator.preparator;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infenia.yukta.api.WorkflowDefinition;
import com.infenia.yukta.api.WorkflowDefinition.Edge;
import com.infenia.yukta.api.WorkflowDefinition.Node;
import com.infenia.yukta.model.workflow.PreparedWorkflow;
import com.infenia.yukta.model.workflow.WorkflowTemplate;
import com.infenia.yukta.plugin.core.WorkflowPlugin;
import com.infenia.yukta.service.control.gateway.ControlBusGateway;
import com.infenia.yukta.service.orchestrator.compiler.WorkflowCompiler;
import com.infenia.yukta.service.orchestrator.validator.WorkflowValidator;
import com.infenia.yukta.service.registry.WorkflowRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@MockitoSettings
class WorkflowPreparatorTest {

  @Mock private WorkflowRegistry registry;
  @Mock private WorkflowValidator validator;
  @Mock private TopologicalSortService topologicalSortService;
  @Mock private ControlBusGateway controlBusGateway;
  @Mock private WorkflowCompiler compiler;

  @BeforeEach
  void setUp() {}

  @Test
  void testPrepareWorkflowHappyPath() {
    doNothing()
        .when(controlBusGateway)
        .registerPlugin(anyString(), anyString(), any(WorkflowPlugin.class));
    when(topologicalSortService.computeTopologicalOrder(any(), any(), any())).thenReturn(List.of());
    when(controlBusGateway.emit(any())).thenReturn(Mono.empty());

    Node node1 = new Node("node-1", "trigger", Map.of());
    Node node2 = new Node("node-2", "terminal", Map.of());
    Edge edge = new Edge("node-1", "node-2", "output");
    WorkflowDefinition definition =
        new WorkflowDefinition(
            "test-workflow", "Happy path workflow", List.of(node1, node2), List.of(edge));

    WorkflowPlugin triggerPlugin = mock(WorkflowPlugin.class);
    WorkflowPlugin terminalPlugin = mock(WorkflowPlugin.class);
    WorkflowTemplate template = mock(WorkflowTemplate.class);

    when(registry.get("trigger")).thenReturn(triggerPlugin);
    when(registry.get("terminal")).thenReturn(terminalPlugin);
    when(validator.validate(definition)).thenReturn(Mono.empty());
    when(triggerPlugin.initialize(Map.of())).thenReturn(Mono.empty());
    when(terminalPlugin.initialize(Map.of())).thenReturn(Mono.empty());
    when(compiler.compileTemplate(any(), any(), any(), any())).thenReturn(template);

    WorkflowPreparator preparator =
        new WorkflowPreparator(
            registry, validator, topologicalSortService, controlBusGateway, compiler);

    StepVerifier.create(preparator.prepareWorkflow(definition))
        .assertNext(result -> assertInstanceOf(PreparedWorkflow.class, result))
        .verifyComplete();
  }

  @Test
  void testPrepareWorkflowSingleNode() {
    doNothing()
        .when(controlBusGateway)
        .registerPlugin(anyString(), anyString(), any(WorkflowPlugin.class));
    when(topologicalSortService.computeTopologicalOrder(any(), any(), any())).thenReturn(List.of());
    when(controlBusGateway.emit(any())).thenReturn(Mono.empty());

    Node node = new Node("node-1", "processor", Map.of());
    WorkflowDefinition definition =
        new WorkflowDefinition("test-workflow", "Single node", List.of(node), List.of());

    WorkflowPlugin plugin = mock(WorkflowPlugin.class);
    WorkflowTemplate template = mock(WorkflowTemplate.class);

    when(registry.get("processor")).thenReturn(plugin);
    when(validator.validate(definition)).thenReturn(Mono.empty());
    when(plugin.initialize(Map.of())).thenReturn(Mono.empty());
    when(compiler.compileTemplate(any(), any(), any(), any())).thenReturn(template);

    WorkflowPreparator preparator =
        new WorkflowPreparator(
            registry, validator, topologicalSortService, controlBusGateway, compiler);

    StepVerifier.create(preparator.prepareWorkflow(definition))
        .assertNext(Assertions::assertNotNull)
        .verifyComplete();
  }

  @Test
  void testPrepareWorkflowWithConfiguration() {
    doNothing()
        .when(controlBusGateway)
        .registerPlugin(anyString(), anyString(), any(WorkflowPlugin.class));
    when(topologicalSortService.computeTopologicalOrder(any(), any(), any())).thenReturn(List.of());
    when(controlBusGateway.emit(any())).thenReturn(Mono.empty());

    Map<String, Object> config = Map.of("timeout", 5000, "retries", 3);
    Node node = new Node("node-1", "processor", config);
    WorkflowDefinition definition =
        new WorkflowDefinition("test-workflow", "With config", List.of(node), List.of());

    WorkflowPlugin plugin = mock(WorkflowPlugin.class);
    WorkflowTemplate template = mock(WorkflowTemplate.class);

    when(registry.get("processor")).thenReturn(plugin);
    when(validator.validate(definition)).thenReturn(Mono.empty());
    when(plugin.initialize(config)).thenReturn(Mono.empty());
    when(compiler.compileTemplate(any(), any(), any(), any())).thenReturn(template);

    WorkflowPreparator preparator =
        new WorkflowPreparator(
            registry, validator, topologicalSortService, controlBusGateway, compiler);

    StepVerifier.create(preparator.prepareWorkflow(definition))
        .assertNext(Assertions::assertNotNull)
        .verifyComplete();
  }

  @Test
  void testPrepareWorkflowComplexDAG() {
    doNothing()
        .when(controlBusGateway)
        .registerPlugin(anyString(), anyString(), any(WorkflowPlugin.class));
    when(topologicalSortService.computeTopologicalOrder(any(), any(), any())).thenReturn(List.of());
    when(controlBusGateway.emit(any())).thenReturn(Mono.empty());

    Node node1 = new Node("node-1", "trigger", Map.of());
    Node node2 = new Node("node-2", "processor", Map.of());
    Node node3 = new Node("node-3", "terminal", Map.of());
    Edge edge1 = new Edge("node-1", "node-2", "output");
    Edge edge2 = new Edge("node-2", "node-3", "output");

    WorkflowDefinition definition =
        new WorkflowDefinition(
            "test-workflow", "Complex DAG", List.of(node1, node2, node3), List.of(edge1, edge2));

    WorkflowPlugin triggerPlugin = mock(WorkflowPlugin.class);
    WorkflowPlugin processorPlugin = mock(WorkflowPlugin.class);
    WorkflowPlugin terminalPlugin = mock(WorkflowPlugin.class);
    WorkflowTemplate template = mock(WorkflowTemplate.class);

    when(registry.get("trigger")).thenReturn(triggerPlugin);
    when(registry.get("processor")).thenReturn(processorPlugin);
    when(registry.get("terminal")).thenReturn(terminalPlugin);
    when(validator.validate(definition)).thenReturn(Mono.empty());
    when(triggerPlugin.initialize(Map.of())).thenReturn(Mono.empty());
    when(processorPlugin.initialize(Map.of())).thenReturn(Mono.empty());
    when(terminalPlugin.initialize(Map.of())).thenReturn(Mono.empty());
    when(compiler.compileTemplate(any(), any(), any(), any())).thenReturn(template);

    WorkflowPreparator preparator =
        new WorkflowPreparator(
            registry, validator, topologicalSortService, controlBusGateway, compiler);

    StepVerifier.create(preparator.prepareWorkflow(definition))
        .assertNext(Assertions::assertNotNull)
        .verifyComplete();
  }

  @Test
  void testPrepareWorkflowWithNullPlugin() {

    Node node = new Node("node-1", "unknown-type", Map.of());
    WorkflowDefinition definition =
        new WorkflowDefinition("test-workflow", "Null plugin test", List.of(node), List.of());

    when(registry.get("unknown-type")).thenReturn(null);
    when(validator.validate(definition)).thenReturn(Mono.empty());

    WorkflowPreparator preparator =
        new WorkflowPreparator(
            registry, validator, topologicalSortService, controlBusGateway, compiler);

    StepVerifier.create(preparator.prepareWorkflow(definition))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void testPrepareWorkflowWithValidationFailure() {
    doNothing().when(controlBusGateway).unregisterPlugin(anyString(), anyString());

    Node node = new Node("node-1", "processor", Map.of());
    WorkflowDefinition definition =
        new WorkflowDefinition("test-workflow", "Validation error test", List.of(node), List.of());

    WorkflowPlugin plugin = mock(WorkflowPlugin.class);
    when(registry.get("processor")).thenReturn(plugin);
    when(validator.validate(definition))
        .thenReturn(Mono.error(new IllegalStateException("Validation failed")));
    when(plugin.shutdown(Map.of())).thenReturn(Mono.empty());

    WorkflowPreparator preparator =
        new WorkflowPreparator(
            registry, validator, topologicalSortService, controlBusGateway, compiler);

    StepVerifier.create(preparator.prepareWorkflow(definition))
        .expectError(IllegalStateException.class)
        .verify();
  }

  @Test
  void testPrepareWorkflowWithPluginInitializationError() {
    doNothing().when(controlBusGateway).unregisterPlugin(anyString(), anyString());
    when(controlBusGateway.emit(any())).thenReturn(Mono.empty());

    Node node = new Node("node-1", "processor", Map.of());
    WorkflowDefinition definition =
        new WorkflowDefinition("test-workflow", "Plugin init error test", List.of(node), List.of());

    WorkflowPlugin plugin = mock(WorkflowPlugin.class);
    when(registry.get("processor")).thenReturn(plugin);
    when(validator.validate(definition)).thenReturn(Mono.empty());
    when(plugin.initialize(Map.of()))
        .thenReturn(Mono.error(new RuntimeException("Plugin initialization failed")));
    when(plugin.shutdown(Map.of())).thenReturn(Mono.empty());

    WorkflowPreparator preparator =
        new WorkflowPreparator(
            registry, validator, topologicalSortService, controlBusGateway, compiler);

    StepVerifier.create(preparator.prepareWorkflow(definition))
        .expectError(RuntimeException.class)
        .verify();
  }

  @Test
  void testPrepareWorkflowWithPluginShutdownErrorDuringCleanup() {
    doNothing()
        .when(controlBusGateway)
        .registerPlugin(anyString(), anyString(), any(WorkflowPlugin.class));
    doNothing().when(controlBusGateway).unregisterPlugin(anyString(), anyString());
    when(controlBusGateway.emit(any())).thenReturn(Mono.empty());

    Node node = new Node("node-1", "processor", Map.of());
    WorkflowDefinition definition =
        new WorkflowDefinition(
            "test-workflow", "Plugin shutdown error during cleanup test", List.of(node), List.of());

    WorkflowPlugin plugin = mock(WorkflowPlugin.class);
    when(registry.get("processor")).thenReturn(plugin);
    when(validator.validate(definition)).thenReturn(Mono.empty());
    when(plugin.initialize(Map.of())).thenReturn(Mono.empty());
    when(plugin.shutdown(Map.of())).thenReturn(Mono.error(new RuntimeException("Shutdown error")));
    when(controlBusGateway.emit(any())).thenReturn(Mono.error(new RuntimeException("Emit failed")));

    WorkflowPreparator preparator =
        new WorkflowPreparator(
            registry, validator, topologicalSortService, controlBusGateway, compiler);

    StepVerifier.create(preparator.prepareWorkflow(definition))
        .expectError(RuntimeException.class)
        .verify();
  }

  @Test
  void testPrepareWorkflowWithNullShutdownMono() {
    doNothing()
        .when(controlBusGateway)
        .registerPlugin(anyString(), anyString(), any(WorkflowPlugin.class));
    doNothing().when(controlBusGateway).unregisterPlugin(anyString(), anyString());
    when(controlBusGateway.emit(any())).thenReturn(Mono.empty());

    Node node = new Node("node-1", "processor", Map.of());
    WorkflowDefinition definition =
        new WorkflowDefinition(
            "test-workflow", "Null shutdown mono test", List.of(node), List.of());

    WorkflowPlugin plugin = mock(WorkflowPlugin.class);
    when(registry.get("processor")).thenReturn(plugin);
    when(validator.validate(definition)).thenReturn(Mono.empty());
    when(plugin.initialize(Map.of())).thenReturn(Mono.empty());
    when(plugin.shutdown(Map.of())).thenReturn(null);
    when(controlBusGateway.emit(any())).thenReturn(Mono.error(new RuntimeException("Emit failed")));

    WorkflowPreparator preparator =
        new WorkflowPreparator(
            registry, validator, topologicalSortService, controlBusGateway, compiler);

    StepVerifier.create(preparator.prepareWorkflow(definition))
        .expectError(RuntimeException.class)
        .verify();
  }
}
