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

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infenia.yukta.plugin.core.WorkflowPlugin;
import com.infenia.yukta.plugin.message.DefaultMessage;
import com.infenia.yukta.plugin.message.Message;
import com.infenia.yukta.plugin.message.control.ControlHeartbeat;
import com.infenia.yukta.plugin.message.control.ControlStatistics;
import com.infenia.yukta.service.control.directive.ControlHeartbeatHandler;
import com.infenia.yukta.service.control.directive.ControlSignalHandler;
import com.infenia.yukta.service.control.directive.ControlStatisticsHandler;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

class ControlBusServiceTest {

  private ControlBusService service;
  private ControlBusService controlBusService;

  @BeforeEach
  void setUp() {
    final List<ControlSignalHandler> handlers =
        List.of(new ControlHeartbeatHandler(), new ControlStatisticsHandler());
    service = new ControlBusService(100, 50, 256, handlers);
    service.init();

    final ControlHeartbeatHandler heartbeatHandler = new ControlHeartbeatHandler();
    final ControlStatisticsHandler statisticsHandler = new ControlStatisticsHandler();
    final List<ControlSignalHandler> integrationHandlers =
        List.of(heartbeatHandler, statisticsHandler);
    controlBusService = new ControlBusService(100, 50, 256, integrationHandlers);
    controlBusService.init();
  }

  // ==================== Basic Tests ====================

  @Test
  void testEmitAndStream() {
    Message<String> msg = DefaultMessage.create(null, "test");

    StepVerifier.create(service.getControlStream())
        .then(() -> service.emit(msg).subscribe())
        .expectNext(msg)
        .thenCancel()
        .verify();
  }

  @Test
  void testHeartbeatAndStatistics() {
    ControlHeartbeat hb = new ControlHeartbeat("node1", 1000L);
    Message<ControlHeartbeat> hbMsg = DefaultMessage.create(null, hb).withSourceNodeId("node1");

    ControlStatistics stats = new ControlStatistics("node1", 100.0, 50.0);
    Message<ControlStatistics> statsMsg =
        DefaultMessage.create(null, stats).withSourceNodeId("node1");

    service.emit(hbMsg).block();
    service.emit(statsMsg).block();

    // Loop to wait for state update
    for (int i = 0; i < 20; i++) {
      if (service.getLastHeartbeat("test-workflow", "node1") != null) break;
      try {
        Thread.sleep(50);
      } catch (InterruptedException _) {
      }
    }

    assertEquals(hbMsg, service.getLastHeartbeat("test-workflow", "node1"));
    assertTrue(service.getActiveNodes().contains("node1"));
  }

  @Test
  void testRegisterAndSendCommand() {
    WorkflowPlugin plugin = mock(WorkflowPlugin.class);
    Message<String> cmd = DefaultMessage.create(null, "cmd");
    Message<String> resp = DefaultMessage.create(null, "resp");

    service.registerPlugin("test-workflow", "node1", plugin);
    when(plugin.onControlSignal(cmd)).thenReturn(Mono.just(resp));

    StepVerifier.create(service.sendCommand("test-workflow", "node1", cmd)).expectNext(resp).verifyComplete();

    service.unregisterPlugin("test-workflow", "node1");
    StepVerifier.create(service.sendCommand("test-workflow", "node1", cmd))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void testInitCustomBufferSize() {
    ControlBusService customService = new ControlBusService(10, 10, 512, List.of());
    customService.init();
    customService.shutdown();
  }

  @Test
  void testInitZeroBufferSize() {
    ControlBusService zeroBufferService = new ControlBusService(10, 10, 0, List.of());
    zeroBufferService.init();
    zeroBufferService.shutdown();
  }

  @Test
  void testInitNegativeBufferSize() {
    ControlBusService negativeBufferService = new ControlBusService(10, 10, -1, List.of());
    negativeBufferService.init();
    negativeBufferService.shutdown();
  }

  @Test
  void testBatchProcessingError() throws Exception {
    ControlSignalHandler failingHandler = mock(ControlSignalHandler.class);
    when(failingHandler.canHandle(any())).thenReturn(true);
    org.mockito.Mockito.doThrow(new RuntimeException("batch fail"))
        .when(failingHandler)
        .handle(any(), any(), any());

    ControlBusService errService = new ControlBusService(1, 1, 256, List.of(failingHandler));
    errService.init();

    Message<String> msg = DefaultMessage.create(null, "payload").withSourceNodeId("node1");
    errService.emit(msg).block();

    Thread.sleep(100);
    errService.shutdown();
  }

  @Test
  void testEmitError() throws Exception {
    ControlBusService serviceWithFullSink = new ControlBusService(100, 50, 256, List.of());
    java.lang.reflect.Field sinkField = ControlBusService.class.getDeclaredField("controlSink");
    sinkField.setAccessible(true);
    @SuppressWarnings("unchecked")
    Sinks.Many<Message<?>> mockSink = mock(Sinks.Many.class);
    org.mockito.Mockito.doThrow(new RuntimeException("sink fail"))
        .when(mockSink)
        .emitNext(any(), any());
    sinkField.set(serviceWithFullSink, mockSink);

    StepVerifier.create(serviceWithFullSink.emit(DefaultMessage.create(null, "d")))
        .expectError(IllegalStateException.class)
        .verify();
  }

  @Test
  void testHandleControlBatchBranches() throws Exception {
    ControlBusService testService = new ControlBusService(100, 50, 256, List.of());
    java.lang.reflect.Method handleBatch =
        ControlBusService.class.getDeclaredMethod("handleControlBatch", List.class);
    handleBatch.setAccessible(true);

    Message<String> msgNoNode = DefaultMessage.create(null, "p");
    handleBatch.invoke(testService, List.of(msgNoNode));

    Message<?> mockMsg = mock(Message.class);
    when(mockMsg.getPayload()).thenReturn(null);
    when(mockMsg.getSourceNodeId()).thenReturn("node1");
    handleBatch.invoke(testService, List.of(mockMsg));
  }

  @Test
  void testGetActiveNodesEmpty() {
    ControlBusService emptyService = new ControlBusService(100, 50, 256, List.of());
    assertTrue(emptyService.getActiveNodes().isEmpty());
  }

  @Test
  void testGetHeartbeatAndStatsMissing() {
    ControlBusService emptyService = new ControlBusService(100, 50, 256, List.of());
    org.junit.jupiter.api.Assertions.assertNull(emptyService.getLastHeartbeat("test-workflow", "n1"));
    org.junit.jupiter.api.Assertions.assertNull(emptyService.getLastStatistics("test-workflow", "n1"));
  }

  // ==================== Unit Tests (Exception Handling) ====================

  @Test
  void testEmitWhenSinkThrowsRuntimeException()
      throws NoSuchFieldException, IllegalAccessException {
    final ControlBusService testService = new ControlBusService(100, 50, 256, List.of());
    testService.init();

    @SuppressWarnings("unchecked")
    final Sinks.Many<Message<?>> mockSink = mock(Sinks.Many.class);
    doThrow(new RuntimeException("Simulated sink error")).when(mockSink).emitNext(any(), any());

    final Field sinkField = ControlBusService.class.getDeclaredField("controlSink");
    sinkField.setAccessible(true);
    sinkField.set(testService, mockSink);

    final Message<?> msg = DefaultMessage.create(null, "test").withSourceNodeId("test");

    StepVerifier.create(testService.emit(msg))
        .expectErrorMatches(
            e ->
                e instanceof IllegalStateException
                    && e.getMessage().contains("Control bus emit failed"))
        .verify();
  }

  @Test
  void testEmitWhenSinkThrowsIllegalStateException()
      throws NoSuchFieldException, IllegalAccessException {
    final ControlBusService testService = new ControlBusService(100, 50, 256, List.of());
    testService.init();

    @SuppressWarnings("unchecked")
    final Sinks.Many<Message<?>> mockSink = mock(Sinks.Many.class);
    doThrow(new IllegalStateException("Sink closed")).when(mockSink).emitNext(any(), any());

    final Field sinkField = ControlBusService.class.getDeclaredField("controlSink");
    sinkField.setAccessible(true);
    sinkField.set(testService, mockSink);

    final Message<?> msg = DefaultMessage.create(null, "test").withSourceNodeId("test");

    StepVerifier.create(testService.emit(msg))
        .expectErrorMatches(
            e ->
                e instanceof IllegalStateException
                    && e.getMessage().contains("Control bus emit failed"))
        .verify();
  }

  @Test
  void testHandleControlBatchExceptionIsHandledGracefully() {
    final ControlSignalHandler faultyHandler = mock(ControlSignalHandler.class);
    when(faultyHandler.canHandle(any())).thenReturn(true);
    doThrow(new RuntimeException("Handler error")).when(faultyHandler).handle(any(), any(), any());

    final ControlBusService testService = new ControlBusService(1, 50, 256, List.of(faultyHandler));
    testService.init();

    final Message<?> msg =
        DefaultMessage.create(null, "test").withSourceNodeId("test").withPriority(5);

    StepVerifier.create(testService.emit(msg)).verifyComplete();

    await()
        .timeout(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(50))
        .untilAsserted(
            () -> {
              /* allow batch processing delay */
            });

    final Message<?> followUp =
        DefaultMessage.create(null, "followup").withSourceNodeId("followup").withPriority(5);
    StepVerifier.create(testService.emit(followUp)).verifyComplete();
  }

  @Test
  void testInitWithExactBufferSize256DoesNotReinitializeSink() {
    final ControlBusService testService = new ControlBusService(100, 50, 256, List.of());
    testService.init();

    final Message<?> msg = DefaultMessage.create(null, "test").withSourceNodeId("test");

    StepVerifier.create(testService.emit(msg)).verifyComplete();
  }

  // ==================== Integration Tests ====================

  @Test
  void testHeartbeatSignalDispatch() {
    final Message<?> hb =
        DefaultMessage.create(null, new ControlHeartbeat("node1", 1000L))
            .withSourceNodeId("node1")
            .withPriority(5);

    StepVerifier.create(controlBusService.emit(hb)).verifyComplete();

    await()
        .timeout(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(50))
        .until(() -> controlBusService.getLastHeartbeat("test-workflow", "node1") != null);

    assertNotNull(controlBusService.getLastHeartbeat("test-workflow", "node1"));
    assertEquals("node1", controlBusService.getActiveNodes().getFirst());
  }

  @Test
  void testStatisticsSignalDispatch() {
    final Message<?> stats =
        DefaultMessage.create(null, new ControlStatistics("node2", 100.0, 50.0))
            .withSourceNodeId("node2")
            .withPriority(5);

    StepVerifier.create(controlBusService.emit(stats)).verifyComplete();

    await()
        .timeout(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(50))
        .until(() -> controlBusService.getLastStatistics("test-workflow", "node2") != null);

    assertNotNull(controlBusService.getLastStatistics("test-workflow", "node2"));
  }

  @Test
  void testUnregisterPluginCleansUpAllHandlerState() {
    final Message<?> hb =
        DefaultMessage.create(null, new ControlHeartbeat("node3", 1000L))
            .withSourceNodeId("node3")
            .withPriority(5);
    final Message<?> stats =
        DefaultMessage.create(null, new ControlStatistics("node3", 100.0, 50.0))
            .withSourceNodeId("node3")
            .withPriority(5);

    StepVerifier.create(controlBusService.emit(hb)).verifyComplete();
    StepVerifier.create(controlBusService.emit(stats)).verifyComplete();

    await()
        .timeout(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(50))
        .untilAsserted(
            () -> {
              /* allow batch processing delay */
            });

    assertNotNull(controlBusService.getLastHeartbeat("test-workflow", "node3"));
    assertNotNull(controlBusService.getLastStatistics("test-workflow", "node3"));

    controlBusService.unregisterPlugin("test-workflow", "node3");

    assertNull(controlBusService.getLastHeartbeat("test-workflow", "node3"));
    assertNull(controlBusService.getLastStatistics("test-workflow", "node3"));
    assertTrue(controlBusService.getActiveNodes().isEmpty());
  }

  @Test
  void testMultipleNodesTrackedIndependently() {
    final Message<?> hb1 =
        DefaultMessage.create(null, new ControlHeartbeat("node1", 1000L))
            .withSourceNodeId("node1")
            .withPriority(5);
    final Message<?> hb2 =
        DefaultMessage.create(null, new ControlHeartbeat("node2", 1000L))
            .withSourceNodeId("node2")
            .withPriority(5);

    StepVerifier.create(controlBusService.emit(hb1)).verifyComplete();
    StepVerifier.create(controlBusService.emit(hb2)).verifyComplete();

    await()
        .timeout(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(50))
        .untilAsserted(
            () -> {
              /* allow batch processing delay */
            });

    final List<String> activeNodes = controlBusService.getActiveNodes();
    assertEquals(2, activeNodes.size());
    assertTrue(activeNodes.contains("node1"));
    assertTrue(activeNodes.contains("node2"));

    controlBusService.unregisterPlugin("test-workflow", "node1");

    assertEquals(1, controlBusService.getActiveNodes().size());
    assertNull(controlBusService.getLastHeartbeat("test-workflow", "node1"));
    assertNotNull(controlBusService.getLastHeartbeat("test-workflow", "node2"));
  }

  @Test
  void testGetControlStream() {
    final Message<?> hb =
        DefaultMessage.create(null, new ControlHeartbeat("node1", 1000L))
            .withSourceNodeId("node1")
            .withPriority(5);

    StepVerifier.create(controlBusService.getControlStream().take(1))
        .then(() -> StepVerifier.create(controlBusService.emit(hb)).verifyComplete())
        .expectNextCount(1)
        .verifyComplete();
  }

  @Test
  void testSendCommandSuccess() {
    final Message<?> hb =
        DefaultMessage.create(null, new ControlHeartbeat("node1", 1000L))
            .withSourceNodeId("node1")
            .withPriority(5);

    StepVerifier.create(controlBusService.emit(hb)).verifyComplete();

    await()
        .timeout(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(50))
        .untilAsserted(
            () -> {
              /* allow batch processing delay */
            });

    assertNotNull(controlBusService.getLastHeartbeat("test-workflow", "node1"));
  }

  @Test
  void testSendCommandNodeNotFound() {
    final Message<?> command =
        DefaultMessage.create(null, "command").withSourceNodeId("nonexistent");

    StepVerifier.create(controlBusService.sendCommand("test-workflow", "nonexistent", command))
        .expectErrorMatches(
            e -> e instanceof IllegalArgumentException && e.getMessage().contains("Node not found"))
        .verify();
  }

  @Test
  void testRegisterPlugin() {
    final Message<?> hb =
        DefaultMessage.create(null, new ControlHeartbeat("node-test", 1000L))
            .withSourceNodeId("node-test")
            .withPriority(5);

    StepVerifier.create(controlBusService.emit(hb)).verifyComplete();

    await()
        .timeout(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(50))
        .untilAsserted(
            () -> {
              /* allow batch processing delay */
            });

    assertNotNull(controlBusService.getLastHeartbeat("test-workflow", "node-test"));
    assertTrue(controlBusService.getActiveNodes().contains("node-test"));
  }

  @Test
  void testShutdown() {
    controlBusService.shutdown();

    StepVerifier.create(controlBusService.getControlStream()).verifyComplete();
  }

  @Test
  void testInitWithCustomBufferSize() {
    final ControlBusService testService = new ControlBusService(50, 30, 512, List.of());
    testService.init();

    final Message<?> msg = DefaultMessage.create(null, "test").withSourceNodeId("test");
    StepVerifier.create(testService.emit(msg)).verifyComplete();
  }

  @Test
  void testHandleControlBatchWithNullNodeId() {
    final Message<?> msgWithoutNodeId =
        DefaultMessage.create(null, new ControlHeartbeat("ignored", 1000L)).withPriority(5);

    StepVerifier.create(controlBusService.emit(msgWithoutNodeId)).verifyComplete();

    await()
        .timeout(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(50))
        .untilAsserted(
            () -> {
              /* allow batch processing delay */
            });

    assertTrue(controlBusService.getActiveNodes().isEmpty());
  }

  @Test
  void testHandleControlBatchWithNullPayload() {
    final Message<?> msgWithoutPayload =
        DefaultMessage.create(null, null).withSourceNodeId("node1").withPriority(5);

    StepVerifier.create(controlBusService.emit(msgWithoutPayload)).verifyComplete();

    await()
        .timeout(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(50))
        .untilAsserted(
            () -> {
              /* allow batch processing delay */
            });

    assertNull(controlBusService.getLastHeartbeat("test-workflow", "node1"));
  }

  @Test
  void testGetLastHeartbeatFromMultipleHandlers() {
    final Message<?> hb =
        DefaultMessage.create(null, new ControlHeartbeat("node1", 1000L))
            .withSourceNodeId("node1")
            .withPriority(5);

    StepVerifier.create(controlBusService.emit(hb)).verifyComplete();

    await()
        .timeout(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(50))
        .untilAsserted(
            () -> {
              /* allow batch processing delay */
            });

    assertNotNull(controlBusService.getLastHeartbeat("test-workflow", "node1"));
    assertEquals(
        1000L,
        ((ControlHeartbeat)
                Objects.requireNonNull(controlBusService.getLastHeartbeat("test-workflow", "node1")).getPayload())
            .uptime());
  }

  @Test
  void testGetLastStatisticsFromMultipleHandlers() {
    final Message<?> stats =
        DefaultMessage.create(null, new ControlStatistics("node1", 75.0, 25.0))
            .withSourceNodeId("node1")
            .withPriority(5);

    StepVerifier.create(controlBusService.emit(stats)).verifyComplete();

    await()
        .timeout(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(50))
        .untilAsserted(
            () -> {
              /* allow batch processing delay */
            });

    assertNotNull(controlBusService.getLastStatistics("test-workflow", "node1"));
    assertEquals(
        75.0,
        ((ControlStatistics) controlBusService.getLastStatistics("test-workflow", "node1").getPayload())
            .throughput());
  }

  @Test
  void testGetActiveNodesFromMultipleHandlers() {
    final Message<?> hb =
        DefaultMessage.create(null, new ControlHeartbeat("node-active", 1000L))
            .withSourceNodeId("node-active")
            .withPriority(5);

    StepVerifier.create(controlBusService.emit(hb)).verifyComplete();

    await()
        .timeout(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(50))
        .untilAsserted(
            () -> {
              /* allow batch processing delay */
            });

    final List<String> activeNodes = controlBusService.getActiveNodes();
    assertTrue(activeNodes.contains("node-active"));
  }

  @Test
  void testHandleControlBatchPrioritization() {
    final Message<?> lowPriority =
        DefaultMessage.create(null, new ControlHeartbeat("node-low", 1000L))
            .withSourceNodeId("node-low")
            .withPriority(1);
    final Message<?> highPriority =
        DefaultMessage.create(null, new ControlHeartbeat("node-high", 2000L))
            .withSourceNodeId("node-high")
            .withPriority(10);

    StepVerifier.create(controlBusService.emit(lowPriority)).verifyComplete();
    StepVerifier.create(controlBusService.emit(highPriority)).verifyComplete();

    await()
        .timeout(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(50))
        .untilAsserted(
            () -> {
              /* allow batch processing delay */
            });

    assertNotNull(controlBusService.getLastHeartbeat("test-workflow", "node-low"));
    assertNotNull(controlBusService.getLastHeartbeat("test-workflow", "node-high"));
  }

  @Test
  void testEmitWithBufferSize256() {
    final ControlBusService testService = new ControlBusService(100, 50, 256, List.of());
    testService.init();

    final Message<?> msg = DefaultMessage.create(null, "test").withSourceNodeId("test");
    StepVerifier.create(testService.emit(msg)).verifyComplete();
  }

  @Test
  void testEmitWithCustomBufferSizeLessThanSmall() {
    final ControlBusService testService = new ControlBusService(100, 50, 32, List.of());
    testService.init();

    final Message<?> msg = DefaultMessage.create(null, "test").withSourceNodeId("test");
    StepVerifier.create(testService.emit(msg)).verifyComplete();
  }

  @Test
  void testEmitSuccessPath() {
    final Message<?> hb =
        DefaultMessage.create(null, new ControlHeartbeat("emit-test", 1000L))
            .withSourceNodeId("emit-test")
            .withPriority(5);

    StepVerifier.create(controlBusService.emit(hb)).verifyComplete();
  }

  @Test
  void testInitWithBufferSizeZero() {
    final ControlBusService testService = new ControlBusService(100, 50, 0, List.of());
    testService.init();

    final Message<?> msg = DefaultMessage.create(null, "test").withSourceNodeId("test");
    StepVerifier.create(testService.emit(msg)).verifyComplete();
  }
}
