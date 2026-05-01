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

import com.infenia.yukta.plugin.message.Message;
import com.infenia.yukta.service.control.valve.ReactiveControlValve;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

class ExecutionControlTest {

  private ExecutionControl control;
  private String nodeId;

  private Message<?> mockMessage() {
    return Mockito.mock(Message.class);
  }

  @BeforeEach
  void setUp() {
    nodeId = "node-1";
    control =
        new ExecutionControl(
            "session-1",
            "workflow-1",
            "exec-1",
            null,
            Map.of("key", "value"),
            Sinks.one(),
            Sinks.one(),
            new ReactiveControlValve(),
            Map.of(nodeId, Sinks.one()),
            Map.of(nodeId, Sinks.one()),
            Map.of(nodeId, new ReactiveControlValve()),
            Map.of(nodeId, new AtomicBoolean(false)),
            Map.of(nodeId, new AtomicBoolean(false)),
            Map.of(nodeId, Sinks.many().multicast().onBackpressureBuffer()));
  }

  @Test
  void testCompactConstructorImmutabilityPayload() {
    final Map<String, Object> payload = Map.of("a", "b", "c", "d");
    final ExecutionControl ctrl =
        new ExecutionControl(
            "s1",
            "w1",
            "e1",
            null,
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

    assertThat(ctrl.payload()).isNotSameAs(payload);
    assertThat(ctrl.payload()).containsAllEntriesOf(payload);
  }

  @Test
  void testApplyPreProcessingControlsWithoutSkip() {
    final Flux<Message<?>> input = Flux.range(1, 3).map(i -> mockMessage());

    StepVerifier.create(control.applyPreProcessingControls(nodeId, input))
        .expectNextCount(3)
        .verifyComplete();
  }

  @Test
  void testApplyPreProcessingControlsWithSkipEnabled() {
    control.nodeSkipFlags().get(nodeId).set(true);

    final Flux<Message<?>> input = Flux.range(1, 3).map(i -> mockMessage());

    StepVerifier.create(control.applyPreProcessingControls(nodeId, input))
        .expectNextCount(3)
        .verifyComplete();
  }

  @Test
  void testApplyPreProcessingControlsWithSafeStopSink() {
    final Sinks.One<Void> safeSink = control.nodeSafeStopSinks().get(nodeId);
    safeSink.emitEmpty(Sinks.EmitFailureHandler.FAIL_FAST);

    final Flux<Message<?>> input = Flux.range(1, 3).map(i -> mockMessage());

    StepVerifier.create(control.applyPreProcessingControls(nodeId, input)).verifyComplete();
  }

  @Test
  void testApplyPreProcessingControlsNoNodeControls() {
    final String otherNodeId = "other-node";
    final Flux<Message<?>> input = Flux.range(1, 2).map(i -> mockMessage());

    StepVerifier.create(control.applyPreProcessingControls(otherNodeId, input))
        .expectNextCount(2)
        .verifyComplete();
  }

  @Test
  void testApplyPostProcessingControlsWithoutPause() {
    final Flux<Message<?>> output = Flux.range(1, 3).map(i -> mockMessage());

    StepVerifier.create(control.applyPostProcessingControls(nodeId, output))
        .expectNextCount(3)
        .verifyComplete();
  }

  @Test
  void testApplyPostProcessingControlsWithNodePausedThenResumed() {
    final ReactiveControlValve nodeValve = control.nodePauseValves().get(nodeId);
    nodeValve.pause();

    final Flux<Message<?>> output = Flux.range(1, 2).map(i -> mockMessage());

    nodeValve.resume();

    StepVerifier.create(control.applyPostProcessingControls(nodeId, output))
        .expectNextCount(2)
        .verifyComplete();
  }

  @Test
  void testApplyPostProcessingControlsWithGlobalPausedThenResumed() {
    final ReactiveControlValve globalValve = control.globalPauseValve();
    globalValve.pause();

    final Flux<Message<?>> output = Flux.range(1, 2).map(i -> mockMessage());

    globalValve.resume();

    StepVerifier.create(control.applyPostProcessingControls(nodeId, output))
        .expectNextCount(2)
        .verifyComplete();
  }

  @Test
  void testApplyPostProcessingControlsWithImmediateStop() {
    final Sinks.One<Void> immediateSink = control.nodeImmediateStopSinks().get(nodeId);
    immediateSink.emitEmpty(Sinks.EmitFailureHandler.FAIL_FAST);

    final Flux<Message<?>> output = Flux.range(1, 3).map(i -> mockMessage());

    StepVerifier.create(control.applyPostProcessingControls(nodeId, output)).verifyComplete();
  }

  @Test
  void testApplyPostProcessingControlsNoNodeControls() {
    final String otherNodeId = "other-node";
    final Flux<Message<?>> output = Flux.range(1, 2).map(i -> mockMessage());

    StepVerifier.create(control.applyPostProcessingControls(otherNodeId, output))
        .expectNextCount(2)
        .verifyComplete();
  }

  @Test
  void testControlHierarchyAccessors() {
    assertThat(control.sessionId()).isEqualTo("session-1");
    assertThat(control.workflowId()).isEqualTo("workflow-1");
    assertThat(control.executionId()).isEqualTo("exec-1");
    assertThat(control.payload()).containsEntry("key", "value");

    assertThat(control.immediateStopSink()).isNotNull();
    assertThat(control.safeStopSink()).isNotNull();
    assertThat(control.globalPauseValve()).isNotNull();

    assertThat(control.nodeImmediateStopSinks()).containsKey(nodeId);
    assertThat(control.nodeSafeStopSinks()).containsKey(nodeId);
    assertThat(control.nodePauseValves()).containsKey(nodeId);
    assertThat(control.nodeSkipFlags()).containsKey(nodeId);
  }

  @Test
  void testMultipleNodesControl() {
    final String node1 = "node-1";
    final String node2 = "node-2";
    final String node3 = "node-3";

    final ReactiveControlValve valve1 = new ReactiveControlValve();
    final ReactiveControlValve valve2 = new ReactiveControlValve();
    final ReactiveControlValve valve3 = new ReactiveControlValve();

    final AtomicBoolean skip1 = new AtomicBoolean(false);
    final AtomicBoolean skip2 = new AtomicBoolean(true);
    final AtomicBoolean skip3 = new AtomicBoolean(false);

    valve1.pause();
    valve2.pause();

    assertThat(valve1.isPaused()).isTrue();
    assertThat(valve2.isPaused()).isTrue();
    assertThat(valve3.isPaused()).isFalse();

    assertThat(skip1.get()).isFalse();
    assertThat(skip2.get()).isTrue();
    assertThat(skip3.get()).isFalse();

    valve1.resume();
    assertThat(valve1.isPaused()).isFalse();

    skip2.set(false);
    assertThat(skip2.get()).isFalse();
  }

  @Test
  void testPayloadImmutability() {
    final Map<String, Object> originalPayload = Map.of("data", "value");
    final ExecutionControl ctrl =
        new ExecutionControl(
            "s1",
            "w1",
            "e1",
            null,
            originalPayload,
            Sinks.one(),
            Sinks.one(),
            new ReactiveControlValve(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of());

    assertThat(ctrl.payload()).isUnmodifiable();
    assertThat(ctrl.payload()).containsEntry("data", "value");
  }
}
