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
package com.infenia.yukta.service.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infenia.yukta.model.workflow.PreparedWorkflow;
import com.infenia.yukta.model.workflow.WorkflowDefinition;
import com.infenia.yukta.plugin.store.NodeCheckpointStore;
import com.infenia.yukta.service.control.store.ExecutionControlRegistry;
import com.infenia.yukta.service.control.store.InMemoryExecutionControlStore;
import com.infenia.yukta.service.control.valve.ReactiveControlValve;
import com.infenia.yukta.service.orchestrator.WorkflowOrchestrator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

class DefaultWorkflowControlApiTest {

  private WorkflowControlApi api;
  private ExecutionControlRegistry registry;
  private WorkflowOrchestrator orchestrator;
  private NodeCheckpointStore checkpointStore;

  @BeforeEach
  void setUp() {
    registry = new ExecutionControlRegistry(new InMemoryExecutionControlStore());
    orchestrator = mock(WorkflowOrchestrator.class);
    checkpointStore = mock(NodeCheckpointStore.class);
    api = new DefaultWorkflowControlApi(registry, orchestrator, checkpointStore);
  }

  private ExecutionControl createControl() {
    return new ExecutionControl(
            "session-1",
            "workflow-1",
            "exec-1",
        null,
        Map.of(),
        Sinks.one(),
        Sinks.one(),
        new ReactiveControlValve(),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of());
  }

  private ExecutionControl createControlWithNodeValve(
      final ReactiveControlValve nodeValve) {
    return new ExecutionControl(
            "session-1",
            "workflow-1",
            "exec-1",
        null,
        Map.of(),
        Sinks.one(),
        Sinks.one(),
        new ReactiveControlValve(),
        Map.of(),
        Map.of(),
        Map.of("node-1", nodeValve),
        Map.of(),
        Map.of(),
        Map.of());
  }

  private ExecutionControl createControlWithNodeSink(
      final Sinks.One<Void> nodeSink,
      final boolean isImmediateStop) {
    return new ExecutionControl(
            "session-1",
            "workflow-1",
            "exec-1",
        null,
        Map.of(),
        Sinks.one(),
        Sinks.one(),
        new ReactiveControlValve(),
        isImmediateStop ? Map.of("node-1", nodeSink) : Map.of(),
        isImmediateStop ? Map.of() : Map.of("node-1", nodeSink),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of());
  }

  @Test
  void testStopImmediately() {
    final String executionId = "exec-1";
    final ExecutionControl control = createControl();
    registry.register(control);

    StepVerifier.create(api.stopImmediately(executionId)).verifyComplete();

    // Sink should be completed
    StepVerifier.create(control.immediateStopSink().asMono()).expectComplete().verify();
  }

  @Test
  void testStopSafely() {
    final String executionId = "exec-1";
    final ExecutionControl control = createControl();
    registry.register(control);

    StepVerifier.create(api.stopSafely(executionId)).verifyComplete();

    StepVerifier.create(control.safeStopSink().asMono()).expectComplete().verify();
  }

  @Test
  void testStopImmediatelyExecutionNotFound() {
    StepVerifier.create(api.stopImmediately("non-existent"))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void testPauseWorkflow() {
    final String executionId = "exec-1";
    final ExecutionControl control = createControl();
    registry.register(control);

    assertThat(control.globalPauseValve().isPaused()).isFalse();

    StepVerifier.create(api.pauseWorkflow(executionId)).verifyComplete();

    assertThat(control.globalPauseValve().isPaused()).isTrue();
  }

  @Test
  void testUnpauseWorkflow() {
    final String executionId = "exec-1";
    final ExecutionControl control = createControl();
    registry.register(control);

    control.globalPauseValve().pause();
    assertThat(control.globalPauseValve().isPaused()).isTrue();

    StepVerifier.create(api.unpauseWorkflow(executionId)).verifyComplete();

    assertThat(control.globalPauseValve().isPaused()).isFalse();
  }

  @Test
  void testPauseWorkflowExecutionNotFound() {
    StepVerifier.create(api.pauseWorkflow("non-existent"))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void testPauseNode() {
    final String executionId = "exec-1";
    final String nodeId = "node-1";
    final ReactiveControlValve nodeValve = new ReactiveControlValve();
    final ExecutionControl control =
        createControlWithNodeValve(nodeValve);
    registry.register(control);

    assertThat(nodeValve.isPaused()).isFalse();

    StepVerifier.create(api.pauseNode(executionId, nodeId)).verifyComplete();

    assertThat(nodeValve.isPaused()).isTrue();
  }

  @Test
  void testUnpauseNode() {
    final String executionId = "exec-1";
    final String nodeId = "node-1";
    final ReactiveControlValve nodeValve = new ReactiveControlValve();
    final ExecutionControl control =
        createControlWithNodeValve(nodeValve);
    registry.register(control);

    nodeValve.pause();
    assertThat(nodeValve.isPaused()).isTrue();

    StepVerifier.create(api.unpauseNode(executionId, nodeId)).verifyComplete();

    assertThat(nodeValve.isPaused()).isFalse();
  }

  @Test
  void testPauseNodeNotFound() {
    final String executionId = "exec-1";
    final ExecutionControl control = createControl();
    registry.register(control);

    StepVerifier.create(api.pauseNode(executionId, "non-existent"))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void testStopNodeImmediately() {
    final String executionId = "exec-1";
    final String nodeId = "node-1";
    final Sinks.One<Void> nodeSink = Sinks.one();
    final ExecutionControl control =
        createControlWithNodeSink(nodeSink, true);
    registry.register(control);

    StepVerifier.create(api.stopNodeImmediately(executionId, nodeId)).verifyComplete();

    StepVerifier.create(nodeSink.asMono()).expectComplete().verify();
  }

  @Test
  void testStopNodeSafely() {
    final String executionId = "exec-1";
    final String nodeId = "node-1";
    final Sinks.One<Void> nodeSink = Sinks.one();
    final ExecutionControl control =
        createControlWithNodeSink(nodeSink, false);
    registry.register(control);

    StepVerifier.create(api.stopNodeSafely(executionId, nodeId)).verifyComplete();

    StepVerifier.create(nodeSink.asMono()).expectComplete().verify();
  }

  @Test
  void testSkipNode() {
    final String executionId = "exec-1";
    final String nodeId = "node-1";
    final AtomicBoolean skipFlag = new AtomicBoolean(false);
    final ExecutionControl control =
        new ExecutionControl(
            "session-1",
            "workflow-1",
            executionId,
            null,
            Map.of(),
            Sinks.one(),
            Sinks.one(),
            new ReactiveControlValve(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(nodeId, skipFlag),
            Map.of(),
            Map.of());
    registry.register(control);

    assertThat(skipFlag.get()).isFalse();

    StepVerifier.create(api.skipNode(executionId, nodeId, true)).verifyComplete();

    assertThat(skipFlag.get()).isTrue();
  }

  @Test
  void testUnskipNode() {
    final String executionId = "exec-1";
    final String nodeId = "node-1";
    final AtomicBoolean skipFlag = new AtomicBoolean(true);
    final ExecutionControl control =
        new ExecutionControl(
            "session-1",
            "workflow-1",
            executionId,
            null,
            Map.of(),
            Sinks.one(),
            Sinks.one(),
            new ReactiveControlValve(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(nodeId, skipFlag),
            Map.of(),
            Map.of());
    registry.register(control);

    assertThat(skipFlag.get()).isTrue();

    StepVerifier.create(api.skipNode(executionId, nodeId, false)).verifyComplete();

    assertThat(skipFlag.get()).isFalse();
  }

  @Test
  void testSkipNodeNotFound() {
    final String executionId = "exec-1";
    final ExecutionControl control = createControl();
    registry.register(control);

    StepVerifier.create(api.skipNode(executionId, "non-existent", true))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void testGetStatusFound() {
    final String executionId = "exec-1";
    final String nodeId = "node-1";
    final ReactiveControlValve nodeValve = new ReactiveControlValve();
    final AtomicBoolean skipFlag = new AtomicBoolean(true);

    nodeValve.pause();

    final ExecutionControl control =
        new ExecutionControl(
            "session-1",
            "workflow-1",
            executionId,
            null,
            Map.of(),
            Sinks.one(),
            Sinks.one(),
            new ReactiveControlValve(),
            Map.of(),
            Map.of(),
            Map.of(nodeId, nodeValve),
            Map.of(nodeId, skipFlag),
            Map.of(),
            Map.of());
    registry.register(control);

    StepVerifier.create(api.getStatus(executionId))
        .assertNext(
            snapshot -> {
              assertThat(snapshot.executionId()).isEqualTo(executionId);
              assertThat(snapshot.sessionId()).isEqualTo("session-1");
              assertThat(snapshot.workflowId()).isEqualTo("workflow-1");
              assertThat(snapshot.pausedNodes()).contains(nodeId);
              assertThat(snapshot.skippedNodes()).contains(nodeId);
              assertThat(snapshot.isGlobalPaused()).isFalse();
            })
        .verifyComplete();
  }

  @Test
  void testGetStatusMultipleNodes() {
    final String executionId = "exec-1";
    final String node1 = "node-1";
    final String node2 = "node-2";
    final String node3 = "node-3";

    final ReactiveControlValve valve1 = new ReactiveControlValve();
    final ReactiveControlValve valve2 = new ReactiveControlValve();
    final ReactiveControlValve valve3 = new ReactiveControlValve();

    final AtomicBoolean skip1 = new AtomicBoolean(true);
    final AtomicBoolean skip2 = new AtomicBoolean(false);

    valve1.pause();
    valve3.pause();

    final ExecutionControl control =
        new ExecutionControl(
            "session-1",
            "workflow-1",
            executionId,
            null,
            Map.of(),
            Sinks.one(),
            Sinks.one(),
            new ReactiveControlValve(),
            Map.of(),
            Map.of(),
            Map.of(node1, valve1, node2, valve2, node3, valve3),
            Map.of(node1, skip1, node2, skip2),
            Map.of(),
            Map.of());
    registry.register(control);

    StepVerifier.create(api.getStatus(executionId))
        .assertNext(
            snapshot -> {
              assertThat(snapshot.pausedNodes()).containsExactlyInAnyOrder(node1, node3);
              assertThat(snapshot.skippedNodes()).contains(node1);
            })
        .verifyComplete();
  }

  @Test
  void testEnableNodeStepMode() {
    final String executionId = "exec-1";
    final String nodeId = "node-1";
    final ReactiveControlValve nodeValve = new ReactiveControlValve();
    final ExecutionControl control =
        new ExecutionControl(
            "session-1",
            "workflow-1",
            executionId,
            null,
            Map.of(),
            Sinks.one(),
            Sinks.one(),
            new ReactiveControlValve(),
            Map.of(),
            Map.of(),
            Map.of(nodeId, nodeValve),
            Map.of(),
            Map.of(),
            Map.of());
    registry.register(control);

    assertThat(nodeValve.isStepMode()).isFalse();

    StepVerifier.create(api.enableNodeStepMode(executionId, nodeId)).verifyComplete();

    assertThat(nodeValve.isStepMode()).isTrue();
    assertThat(nodeValve.isPaused()).isTrue();
  }

  @Test
  void testDisableNodeStepMode() {
    final String executionId = "exec-1";
    final String nodeId = "node-1";
    final ReactiveControlValve nodeValve = new ReactiveControlValve();
    final ExecutionControl control =
        new ExecutionControl(
            "session-1",
            "workflow-1",
            executionId,
            null,
            Map.of(),
            Sinks.one(),
            Sinks.one(),
            new ReactiveControlValve(),
            Map.of(),
            Map.of(),
            Map.of(nodeId, nodeValve),
            Map.of(),
            Map.of(),
            Map.of());
    registry.register(control);

    nodeValve.enableStepMode();
    assertThat(nodeValve.isStepMode()).isTrue();

    StepVerifier.create(api.disableNodeStepMode(executionId, nodeId)).verifyComplete();

    assertThat(nodeValve.isStepMode()).isFalse();
  }

  @Test
  void testStepNode() {
    final String executionId = "exec-1";
    final String nodeId = "node-1";
    final ReactiveControlValve nodeValve = new ReactiveControlValve();
    final ExecutionControl control =
        new ExecutionControl(
            "session-1",
            "workflow-1",
            executionId,
            null,
            Map.of(),
            Sinks.one(),
            Sinks.one(),
            new ReactiveControlValve(),
            Map.of(),
            Map.of(),
            Map.of(nodeId, nodeValve),
            Map.of(),
            Map.of(),
            Map.of());
    registry.register(control);

    nodeValve.enableStepMode();

    StepVerifier.create(api.stepNode(executionId, nodeId)).verifyComplete();
  }

  @Test
  void testStepNodeNotInStepMode() {
    final String executionId = "exec-1";
    final String nodeId = "node-1";
    final ReactiveControlValve nodeValve = new ReactiveControlValve();
    final ExecutionControl control =
        new ExecutionControl(
            "session-1",
            "workflow-1",
            executionId,
            null,
            Map.of(),
            Sinks.one(),
            Sinks.one(),
            new ReactiveControlValve(),
            Map.of(),
            Map.of(),
            Map.of(nodeId, nodeValve),
            Map.of(),
            Map.of(),
            Map.of());
    registry.register(control);

    StepVerifier.create(api.stepNode(executionId, nodeId))
        .expectError(IllegalStateException.class)
        .verify();
  }

  @Test
  void testUnpauseNodeNotFound() {
    final String executionId = "exec-1";
    final ExecutionControl control = createControl();
    registry.register(control);

    StepVerifier.create(api.unpauseNode(executionId, "non-existent"))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void testStopNodeImmediatelyNotFound() {
    final String executionId = "exec-1";
    final ExecutionControl control = createControl();
    registry.register(control);

    StepVerifier.create(api.stopNodeImmediately(executionId, "non-existent"))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void testStopNodeSafelyNotFound() {
    final String executionId = "exec-1";
    final ExecutionControl control = createControl();
    registry.register(control);

    StepVerifier.create(api.stopNodeSafely(executionId, "non-existent"))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void testEnableNodeStepModeNotFound() {
    final String executionId = "exec-1";
    final ExecutionControl control = createControl();
    registry.register(control);

    StepVerifier.create(api.enableNodeStepMode(executionId, "non-existent"))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void testDisableNodeStepModeNotFound() {
    final String executionId = "exec-1";
    final ExecutionControl control = createControl();
    registry.register(control);

    StepVerifier.create(api.disableNodeStepMode(executionId, "non-existent"))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void testStepNodeNotFound() {
    final String executionId = "exec-1";
    final ExecutionControl control = createControl();
    registry.register(control);

    StepVerifier.create(api.stepNode(executionId, "non-existent"))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void testPauseWorkflowNullValve() {
    final String executionId = "exec-1";
    final ExecutionControl control =
        new ExecutionControl(
            "session-1",
            "workflow-1",
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
    registry.register(control);

    StepVerifier.create(api.pauseWorkflow(executionId))
        .expectError(IllegalStateException.class)
        .verify();
  }

  @Test
  void testUnpauseWorkflowNullValve() {
    final String executionId = "exec-1";
    final ExecutionControl control =
        new ExecutionControl(
            "session-1",
            "workflow-1",
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
    registry.register(control);

    StepVerifier.create(api.unpauseWorkflow(executionId))
        .expectError(IllegalStateException.class)
        .verify();
  }

  @Test
  void testGetStatusNotFound() {
    StepVerifier.create(api.getStatus("non-existent"))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void testStopSafelyExecutionNotFound() {
    StepVerifier.create(api.stopSafely("non-existent"))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void testPrepareWorkflow() {
    final WorkflowDefinition definition =
        new WorkflowDefinition("test workflow", List.of(), List.of());
    final PreparedWorkflow prepared =
        new PreparedWorkflow(definition, Map.of(), Map.of(), Map.of(), List.of(), null);

    when(orchestrator.prepareWorkflow(definition)).thenReturn(Mono.just(prepared));

    StepVerifier.create(api.prepareWorkflow(definition))
        .assertNext(
            result -> {
              assertThat(result.definition()).isEqualTo(definition);
            })
        .verifyComplete();
  }

  @Test
  void testExecuteWorkflow() {
    final String sessionId = "session-1";
    final String workflowId = "workflow-1";
    final String executionId = "exec-1";
    final WorkflowDefinition definition =
        new WorkflowDefinition("test workflow", List.of(), List.of());
    final PreparedWorkflow prepared =
        new PreparedWorkflow(definition, Map.of(), Map.of(), Map.of(), List.of(), null);
    final Map<String, Object> payload = Map.of("key", "value");

    when(orchestrator.execute(sessionId, workflowId, executionId, prepared, payload))
        .thenReturn(Mono.empty());

    StepVerifier.create(api.executeWorkflow(sessionId, workflowId, executionId, prepared, payload))
        .verifyComplete();
  }

  @Test
  void testRestartWorkflow() {
    final String executionId = "exec-1";
    final WorkflowDefinition definition =
        new WorkflowDefinition("test workflow", List.of(), List.of());
    final PreparedWorkflow prepared =
        new PreparedWorkflow(definition, Map.of(), Map.of(), Map.of(), List.of(), null);
    final Map<String, Object> payload = Map.of("key", "value");

    final ExecutionControl control =
        new ExecutionControl(
            "session-1",
            "workflow-1",
            executionId,
            prepared,
            payload,
            Sinks.one(),
            Sinks.one(),
            new ReactiveControlValve(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of());
    registry.register(control);

    when(orchestrator.execute(
            eq("session-1"), eq("workflow-1"), anyString(), eq(prepared), any()))
        .thenReturn(Mono.empty());

    StepVerifier.create(api.restartWorkflow(executionId))
        .assertNext(newExecutionId -> assertThat(newExecutionId).isNotBlank())
        .verifyComplete();
  }

  @Test
  void testRestartWorkflowNotFound() {
    StepVerifier.create(api.restartWorkflow("non-existent"))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void testRestartFromNode() {
    final String executionId = "exec-1";
    final String nodeId = "node-1";
    final WorkflowDefinition definition =
        new WorkflowDefinition("test workflow", List.of(), List.of());
    final Map<String, List<WorkflowDefinition.Node>> parentsList = Map.of();
    final PreparedWorkflow prepared =
        new PreparedWorkflow(definition, Map.of(), parentsList, Map.of(), List.of(), null);
    final Map<String, Object> payload = Map.of("key", "value");

    final ExecutionControl control =
        new ExecutionControl(
            "session-1",
            "workflow-1",
            executionId,
            prepared,
            payload,
            Sinks.one(),
            Sinks.one(),
            new ReactiveControlValve(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of());
    registry.register(control);

    when(orchestrator.restartFromNode(
            eq("session-1"),
            eq("workflow-1"),
            eq(executionId),
            anyString(),
            eq(prepared),
            eq(nodeId),
            any()))
        .thenReturn(Mono.empty());

    StepVerifier.create(api.restartFromNode(executionId, nodeId))
        .assertNext(newExecutionId -> assertThat(newExecutionId).isNotBlank())
        .verifyComplete();
  }

  @Test
  void testRestartFromNodeNotFound() {
    StepVerifier.create(api.restartFromNode("non-existent", "node-1"))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void testRestartFromNodeWithCheckpointFetchError() {
    final String executionId = "exec-1";
    final String nodeId = "node-2";
    final String parentNodeId = "node-1";

    final WorkflowDefinition definition =
        new WorkflowDefinition("test workflow", List.of(), List.of());
    final WorkflowDefinition.Node parentNode =
        new WorkflowDefinition.Node(parentNodeId, "parent-plugin", Map.of());
    final Map<String, List<WorkflowDefinition.Node>> parentsList =
        Map.of(nodeId, List.of(parentNode));
    final PreparedWorkflow prepared =
        new PreparedWorkflow(definition, Map.of(), parentsList, Map.of(), List.of(), null);
    final Map<String, Object> payload = Map.of("key", "value");

    final ExecutionControl control =
        new ExecutionControl(
            "session-1",
            "workflow-1",
            executionId,
            prepared,
            payload,
            Sinks.one(),
            Sinks.one(),
            new ReactiveControlValve(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of());
    registry.register(control);

    when(checkpointStore.get(executionId, parentNodeId))
        .thenReturn(Mono.error(new RuntimeException("Checkpoint not found")));
    when(orchestrator.restartFromNode(
            eq("session-1"),
            eq("workflow-1"),
            eq(executionId),
            anyString(),
            eq(prepared),
            eq(nodeId),
            any()))
        .thenReturn(Mono.empty());

    StepVerifier.create(api.restartFromNode(executionId, nodeId))
        .assertNext(newExecutionId -> assertThat(newExecutionId).isNotBlank())
        .verifyComplete();
  }

  @Test
  void testRestartFromNodeWithSuccessfulCheckpointFetch() {
    final String executionId = "exec-1";
    final String nodeId = "node-2";
    final String parentNodeId = "node-1";

    final WorkflowDefinition definition =
        new WorkflowDefinition("test workflow", List.of(), List.of());
    final WorkflowDefinition.Node parentNode =
        new WorkflowDefinition.Node(parentNodeId, "parent-plugin", Map.of());
    final Map<String, List<WorkflowDefinition.Node>> parentsList =
        Map.of(nodeId, List.of(parentNode));
    final PreparedWorkflow prepared =
        new PreparedWorkflow(definition, Map.of(), parentsList, Map.of(), List.of(), null);
    final Map<String, Object> payload = Map.of("key", "value");

    final ExecutionControl control =
        new ExecutionControl(
            "session-1",
            "workflow-1",
            executionId,
            prepared,
            payload,
            Sinks.one(),
            Sinks.one(),
            new ReactiveControlValve(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of());
    registry.register(control);

    final com.infenia.yukta.plugin.message.Message<?> checkpointMsg =
        mock(com.infenia.yukta.plugin.message.Message.class);
    when(checkpointStore.get(executionId, parentNodeId)).thenReturn(Mono.just(checkpointMsg));
    when(orchestrator.restartFromNode(
            eq("session-1"),
            eq("workflow-1"),
            eq(executionId),
            anyString(),
            eq(prepared),
            eq(nodeId),
            any()))
        .thenReturn(Mono.empty());

    StepVerifier.create(api.restartFromNode(executionId, nodeId))
        .assertNext(newExecutionId -> assertThat(newExecutionId).isNotBlank())
        .verifyComplete();
  }
}
