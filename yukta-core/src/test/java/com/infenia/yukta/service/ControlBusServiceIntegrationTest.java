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
package com.infenia.yukta.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.infenia.yukta.plugin.message.DefaultMessage;
import com.infenia.yukta.plugin.message.Message;
import com.infenia.yukta.plugin.message.control.ControlHeartbeat;
import com.infenia.yukta.plugin.message.control.ControlStatistics;
import com.infenia.yukta.service.control.ControlHeartbeatHandler;
import com.infenia.yukta.service.control.ControlSignalHandler;
import com.infenia.yukta.service.control.ControlStatisticsHandler;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class ControlBusServiceIntegrationTest {

  private ControlBusService controlBusService;
  private ControlHeartbeatHandler heartbeatHandler;
  private ControlStatisticsHandler statisticsHandler;

  @BeforeEach
  void setUp() {
    heartbeatHandler = new ControlHeartbeatHandler();
    statisticsHandler = new ControlStatisticsHandler();

    final List<ControlSignalHandler> handlers = List.of(heartbeatHandler, statisticsHandler);
    controlBusService = new ControlBusService(100, 50, 256, handlers);
    controlBusService.init();
  }

  @Test
  void testHeartbeatSignalDispatch() {
    final Message<?> hb =
        DefaultMessage.create(null, new ControlHeartbeat("node1", 1000L))
            .withSourceNodeId("node1")
            .withPriority(5);

    StepVerifier.create(controlBusService.emit(hb)).verifyComplete();

    try {
      Thread.sleep(100);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    assertNotNull(controlBusService.getLastHeartbeat("node1"));
    assertEquals("node1", controlBusService.getActiveNodes().get(0));
  }

  @Test
  void testStatisticsSignalDispatch() {
    final Message<?> stats =
        DefaultMessage.create(null, new ControlStatistics("node2", 100.0, 50.0))
            .withSourceNodeId("node2")
            .withPriority(5);

    StepVerifier.create(controlBusService.emit(stats)).verifyComplete();

    try {
      Thread.sleep(100);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    assertNotNull(controlBusService.getLastStatistics("node2"));
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

    try {
      Thread.sleep(100);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    assertNotNull(controlBusService.getLastHeartbeat("node3"));
    assertNotNull(controlBusService.getLastStatistics("node3"));

    controlBusService.unregisterPlugin("node3");

    assertNull(controlBusService.getLastHeartbeat("node3"));
    assertNull(controlBusService.getLastStatistics("node3"));
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

    try {
      Thread.sleep(100);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    final List<String> activeNodes = controlBusService.getActiveNodes();
    assertEquals(2, activeNodes.size());
    assertTrue(activeNodes.contains("node1"));
    assertTrue(activeNodes.contains("node2"));

    controlBusService.unregisterPlugin("node1");

    assertEquals(1, controlBusService.getActiveNodes().size());
    assertNull(controlBusService.getLastHeartbeat("node1"));
    assertNotNull(controlBusService.getLastHeartbeat("node2"));
  }
}
