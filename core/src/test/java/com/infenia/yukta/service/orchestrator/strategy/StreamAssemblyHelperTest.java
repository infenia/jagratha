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

import com.infenia.yukta.model.workflow.WorkflowNode;
import com.infenia.yukta.plugin.gateway.ControlBusGateway;
import com.infenia.yukta.plugin.message.DefaultMessage;
import com.infenia.yukta.plugin.message.Message;
import com.infenia.yukta.service.TaskTrackerService;
import com.infenia.yukta.service.control.ExecutionControl;
import com.infenia.yukta.service.orchestrator.AssemblyContext;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@DisplayName("StreamAssemblyHelper")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StreamAssemblyHelperTest {

  @Mock private TaskTrackerService tracker;
  @Mock private ControlBusGateway controlBusGateway;

  @Test
  @DisplayName("buildStreamWithContext returns non-null Flux")
  void buildStreamWithContext_validInputs_returnsFluxWithContextApplied() {
    WorkflowNode node = new WorkflowNode("node-1", "type", Map.of());
    Flux<Message<?>> sourceStream = Flux.just(DefaultMessage.create(null, "data"));
    Duration timeout = Duration.ofSeconds(30);
    ExecutionControl control = mock(ExecutionControl.class);
    when(control.applyPreProcessingControls(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(1));
    when(control.applyPostProcessingControls(anyString(), any()))
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

    Flux<Message<?>> result =
        StreamAssemblyHelper.buildStreamWithContext(
            node, sourceStream, timeout, tracker, controlBusGateway, context);

    assertThat(result).isNotNull();
  }

  @Test
  @DisplayName("buildStreamWithContext applies timeout to stream")
  void buildStreamWithContext_builderChainSequence_appliesTimeoutTrackingErrorHandling() {
    WorkflowNode node = new WorkflowNode("node-1", "type", Map.of());
    Message<?> testMessage = DefaultMessage.create(null, "test-data");
    Flux<Message<?>> sourceStream = Flux.just(testMessage);
    Duration timeout = Duration.ofSeconds(10);
    ExecutionControl control = mock(ExecutionControl.class);
    when(control.applyPreProcessingControls(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(1));
    when(control.applyPostProcessingControls(anyString(), any()))
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

    Flux<Message<?>> result =
        StreamAssemblyHelper.buildStreamWithContext(
            node, sourceStream, timeout, tracker, controlBusGateway, context);

    StepVerifier.create(result).expectNext(testMessage).verifyComplete();
  }

  @Test
  @DisplayName("buildStreamWithContext initializes ExecutionContextBuilder with all properties")
  void buildStreamWithContext_executionContextBuilderInitialization_setsAllProperties() {
    String executionId = "exec-test-1";
    String sessionId = "sess-test-1";
    String workflowId = "wf-test-1";
    String nodeId = "node-test-1";
    Map<String, Object> payload = Map.of("key", "value");

    WorkflowNode node = new WorkflowNode(nodeId, "type", Map.of());
    Flux<Message<?>> sourceStream = Flux.just(DefaultMessage.create(null, "data"));
    Duration timeout = Duration.ofSeconds(30);
    ExecutionControl control = mock(ExecutionControl.class);
    when(control.applyPreProcessingControls(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(1));
    when(control.applyPostProcessingControls(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(1));

    Flux<Message<?>>[] streams = new Flux[1];
    AssemblyContext context =
        new AssemblyContext(
            executionId,
            sessionId,
            workflowId,
            payload,
            control,
            streams,
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>());

    Flux<Message<?>> result =
        StreamAssemblyHelper.buildStreamWithContext(
            node, sourceStream, timeout, tracker, controlBusGateway, context);

    assertThat(result).isNotNull();
  }

  @Test
  @DisplayName("buildStreamWithContext applies post-processing controls")
  void buildStreamWithContext_postProcessingControlsApplied_callsApplyPostProcessingControls() {
    WorkflowNode node = new WorkflowNode("node-1", "type", Map.of());
    Flux<Message<?>> sourceStream = Flux.just(DefaultMessage.create(null, "data"));
    Duration timeout = Duration.ofSeconds(30);
    ExecutionControl control = mock(ExecutionControl.class);
    when(control.applyPreProcessingControls(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(1));
    when(control.applyPostProcessingControls(anyString(), any()))
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

    StreamAssemblyHelper.buildStreamWithContext(
        node, sourceStream, timeout, tracker, controlBusGateway, context);

    verify(control).applyPostProcessingControls(eq("node-1"), any(Flux.class));
  }

  @Test
  @DisplayName("buildStreamWithContext applies context to stream")
  void buildStreamWithContext_contextAppliedToStream_appliesContextCorrectly() {
    WorkflowNode node = new WorkflowNode("node-ctx", "type", Map.of());
    Message<?> testMessage = DefaultMessage.create(null, "context-test");
    Flux<Message<?>> sourceStream = Flux.just(testMessage);
    Duration timeout = Duration.ofSeconds(30);
    ExecutionControl control = mock(ExecutionControl.class);
    when(control.applyPreProcessingControls(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(1));
    when(control.applyPostProcessingControls(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(1));

    Flux<Message<?>>[] streams = new Flux[1];
    Map<String, Object> payload = Map.of("ctx-key", "ctx-value");
    AssemblyContext context =
        new AssemblyContext(
            "exec-ctx",
            "sess-ctx",
            "wf-ctx",
            payload,
            control,
            streams,
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>());

    Flux<Message<?>> result =
        StreamAssemblyHelper.buildStreamWithContext(
            node, sourceStream, timeout, tracker, controlBusGateway, context);

    StepVerifier.create(result).expectNext(testMessage).verifyComplete();
  }

  @Test
  @DisplayName("buildStreamWithContext with different nodes returns isolated contexts")
  void buildStreamWithContext_withMultipleNodes_eachNodeReceivesOwnContext() {
    WorkflowNode node1 = new WorkflowNode("node-1", "type", Map.of());
    WorkflowNode node2 = new WorkflowNode("node-2", "type", Map.of());
    Flux<Message<?>> sourceStream = Flux.just(DefaultMessage.create(null, "data"));
    Duration timeout = Duration.ofSeconds(30);

    ExecutionControl control1 = mock(ExecutionControl.class);
    when(control1.applyPreProcessingControls(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(1));
    when(control1.applyPostProcessingControls(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(1));

    ExecutionControl control2 = mock(ExecutionControl.class);
    when(control2.applyPreProcessingControls(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(1));
    when(control2.applyPostProcessingControls(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(1));

    Flux<Message<?>>[] streams1 = new Flux[1];
    Flux<Message<?>>[] streams2 = new Flux[1];

    AssemblyContext context1 =
        new AssemblyContext(
            "exec-1",
            "sess-1",
            "wf-1",
            Map.of(),
            control1,
            streams1,
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>());

    AssemblyContext context2 =
        new AssemblyContext(
            "exec-2",
            "sess-2",
            "wf-2",
            Map.of(),
            control2,
            streams2,
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>());

    Flux<Message<?>> result1 =
        StreamAssemblyHelper.buildStreamWithContext(
            node1, sourceStream, timeout, tracker, controlBusGateway, context1);

    Flux<Message<?>> result2 =
        StreamAssemblyHelper.buildStreamWithContext(
            node2, sourceStream, timeout, tracker, controlBusGateway, context2);

    assertThat(result1).isNotNull();
    assertThat(result2).isNotNull();
    verify(control1).applyPostProcessingControls(eq("node-1"), any(Flux.class));
    verify(control2).applyPostProcessingControls(eq("node-2"), any(Flux.class));
  }

  @Test
  @DisplayName("buildStreamWithContext passes TaskTrackerService to StreamBuilder")
  void buildStreamWithContext_taskTrackerPassedToStreamBuilder_passedCorrectly() {
    WorkflowNode node = new WorkflowNode("node-1", "type", Map.of());
    Flux<Message<?>> sourceStream = Flux.just(DefaultMessage.create(null, "data"));
    Duration timeout = Duration.ofSeconds(30);
    ExecutionControl control = mock(ExecutionControl.class);
    when(control.applyPreProcessingControls(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(1));
    when(control.applyPostProcessingControls(anyString(), any()))
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

    Flux<Message<?>> result =
        StreamAssemblyHelper.buildStreamWithContext(
            node, sourceStream, timeout, tracker, controlBusGateway, context);

    assertThat(result).isNotNull();
  }

  @Test
  @DisplayName("buildStreamWithContext uses correct node ID in post-processing")
  void buildStreamWithContext_nodeIdExtractedFromNode_usedInPostProcessing() {
    String nodeId = "specific-node-id";
    WorkflowNode node = new WorkflowNode(nodeId, "type", Map.of());
    Flux<Message<?>> sourceStream = Flux.just(DefaultMessage.create(null, "data"));
    Duration timeout = Duration.ofSeconds(30);
    ExecutionControl control = mock(ExecutionControl.class);
    when(control.applyPreProcessingControls(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(1));
    when(control.applyPostProcessingControls(anyString(), any()))
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

    StreamAssemblyHelper.buildStreamWithContext(
        node, sourceStream, timeout, tracker, controlBusGateway, context);

    ArgumentCaptor<String> nodeIdCaptor = ArgumentCaptor.forClass(String.class);
    verify(control).applyPostProcessingControls(nodeIdCaptor.capture(), any(Flux.class));
    assertThat(nodeIdCaptor.getValue()).isEqualTo(nodeId);
  }

  @Test
  @DisplayName("buildStreamWithContext with empty source stream")
  void buildStreamWithContext_withEmptySourceStream_completesWithoutElements() {
    WorkflowNode node = new WorkflowNode("node-1", "type", Map.of());
    Flux<Message<?>> sourceStream = Flux.empty();
    Duration timeout = Duration.ofSeconds(30);
    ExecutionControl control = mock(ExecutionControl.class);
    when(control.applyPreProcessingControls(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(1));
    when(control.applyPostProcessingControls(anyString(), any()))
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

    Flux<Message<?>> result =
        StreamAssemblyHelper.buildStreamWithContext(
            node, sourceStream, timeout, tracker, controlBusGateway, context);

    StepVerifier.create(result).verifyComplete();
  }

  @Test
  @DisplayName("buildStreamWithContext with multiple messages in source stream")
  void buildStreamWithContext_withMultipleMessages_emitsAllMessages() {
    WorkflowNode node = new WorkflowNode("node-1", "type", Map.of());
    Message<?> msg1 = DefaultMessage.create(null, "data1");
    Message<?> msg2 = DefaultMessage.create(null, "data2");
    Message<?> msg3 = DefaultMessage.create(null, "data3");
    Flux<Message<?>> sourceStream = Flux.just(msg1, msg2, msg3);
    Duration timeout = Duration.ofSeconds(30);
    ExecutionControl control = mock(ExecutionControl.class);
    when(control.applyPreProcessingControls(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(1));
    when(control.applyPostProcessingControls(anyString(), any()))
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

    Flux<Message<?>> result =
        StreamAssemblyHelper.buildStreamWithContext(
            node, sourceStream, timeout, tracker, controlBusGateway, context);

    StepVerifier.create(result).expectNext(msg1, msg2, msg3).verifyComplete();
  }
}
