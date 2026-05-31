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
package com.infenia.yukta.service.control.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infenia.yukta.model.monitoring.WorkflowProgress;
import com.infenia.yukta.plugin.core.WorkflowPlugin;
import com.infenia.yukta.plugin.message.DefaultMessage;
import com.infenia.yukta.plugin.message.Message;
import com.infenia.yukta.service.control.ControlBusService;
import com.infenia.yukta.service.orchestrator.tracker.DefaultTaskTrackerServiceService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class DefaultControlBusGatewayTest {

  private ControlBusService controlBusService;
  private DefaultTaskTrackerServiceService defaultTaskTrackerService;
  private DefaultControlBusGateway gateway;

  @BeforeEach
  void setUp() {
    controlBusService = mock(ControlBusService.class);
    defaultTaskTrackerService = mock(DefaultTaskTrackerServiceService.class);
    gateway = new DefaultControlBusGateway(controlBusService, defaultTaskTrackerService);
  }

  @Test
  void testEmit() {
    Message<String> message = DefaultMessage.create(UUID.randomUUID(), "test");
    when(controlBusService.emit(message)).thenReturn(Mono.empty());

    StepVerifier.create(gateway.emit(message)).verifyComplete();

    verify(controlBusService).emit(message);
  }

  @Test
  void testRegisterPlugin() {
    String workflowId = "wf-1";
    String nodeId = "node1";
    WorkflowPlugin plugin = mock(WorkflowPlugin.class);

    gateway.registerPlugin(workflowId, nodeId, plugin);

    verify(controlBusService).registerPlugin(workflowId, nodeId, plugin);
  }

  @Test
  void testUnregisterPlugin() {
    String workflowId = "wf-1";
    String nodeId = "node1";

    gateway.unregisterPlugin(workflowId, nodeId);

    verify(controlBusService).unregisterPlugin(workflowId, nodeId);
  }

  @Test
  void testSendCommand() {
    String workflowId = "wf-1";
    String nodeId = "node1";
    Message<String> command = DefaultMessage.create(UUID.randomUUID(), "cmd");
    Message<String> response = DefaultMessage.create(UUID.randomUUID(), "resp");
    when(controlBusService.sendCommand(eq(workflowId), eq(nodeId), any(Message.class)))
        .thenReturn(Mono.just(response));

    StepVerifier.create(gateway.sendCommand(workflowId, nodeId, command))
        .expectNext(response)
        .verifyComplete();

    verify(controlBusService).sendCommand(eq(workflowId), eq(nodeId), any(Message.class));
  }

  @Test
  void testGetLastHeartbeat() {
    String workflowId = "wf-1";
    String nodeId = "node1";
    Message<?> heartbeat = mock(Message.class);
    doReturn(heartbeat).when(controlBusService).getLastHeartbeat(workflowId, nodeId);

    Message<?> result = gateway.getLastHeartbeat(workflowId, nodeId);

    assertEquals(heartbeat, result);
    verify(controlBusService).getLastHeartbeat(workflowId, nodeId);
  }

  @Test
  void testGetLastStatistics() {
    String workflowId = "wf-1";
    String nodeId = "node1";
    Message<?> stats = mock(Message.class);
    doReturn(stats).when(controlBusService).getLastStatistics(workflowId, nodeId);

    Message<?> result = gateway.getLastStatistics(workflowId, nodeId);

    assertEquals(stats, result);
    verify(controlBusService).getLastStatistics(workflowId, nodeId);
  }

  @Test
  void testGetActiveNodes() {
    String workflowId = "wf-1";
    List<String> activeNodes = List.of("node1", "node2");
    when(controlBusService.getActiveNodes(workflowId)).thenReturn(activeNodes);

    List<String> result = gateway.getActiveNodes(workflowId);

    assertEquals(activeNodes, result);
    verify(controlBusService).getActiveNodes(workflowId);
  }

  @Test
  void testGetAllActiveNodes() {
    List<String> allActiveNodes = List.of("wf-1 node1", "wf-2 node2");
    when(controlBusService.getActiveNodes()).thenReturn(allActiveNodes);

    List<String> result = gateway.getActiveNodes();

    assertEquals(allActiveNodes, result);
    verify(controlBusService).getActiveNodes();
  }

  @Test
  void testPauseNode() {
    String executionId = "exec-1";
    String nodeId = "node1";
    when(controlBusService.emit(any())).thenReturn(Mono.empty());

    StepVerifier.create(gateway.pauseNode(executionId, nodeId)).verifyComplete();

    verify(controlBusService).emit(any());
  }

  @Test
  void testWatchExecution() {
    String executionId = "exec-1";
    WorkflowProgress progress = mock(WorkflowProgress.class);
    when(defaultTaskTrackerService.getStatusStream(executionId)).thenReturn(Flux.just(progress));

    StepVerifier.create(gateway.watchExecution(executionId)).expectNext(progress).verifyComplete();

    verify(defaultTaskTrackerService).getStatusStream(executionId);
  }

  @Test
  void testGetCurrentProgress() {
    String executionId = "exec-1";
    WorkflowProgress progress = mock(WorkflowProgress.class);
    doReturn(progress).when(defaultTaskTrackerService).getProgressByExecutionId(executionId);

    WorkflowProgress result = gateway.getCurrentProgress(executionId);

    assertEquals(progress, result);
    verify(defaultTaskTrackerService).getProgressByExecutionId(executionId);
  }
}
