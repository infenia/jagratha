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
package com.infenia.yukta.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infenia.yukta.model.api.WorkflowTriggerRequest;
import com.infenia.yukta.model.monitoring.WorkflowProgress;
import com.infenia.yukta.model.session.TaskResponse;
import com.infenia.yukta.model.workflow.WorkflowExecution;
import com.infenia.yukta.plugin.message.DefaultMessage;
import com.infenia.yukta.plugin.message.Message;
import com.infenia.yukta.service.LogRetrievalService;
import com.infenia.yukta.service.WorkflowService;
import com.infenia.yukta.service.control.gateway.ControlBusGateway;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class ExecutionManagementControllerTest {

  private WebTestClient webClient;
  private ControlBusGateway controlBusGateway;
  private WorkflowService workflowService;
  private LogRetrievalService logs;

  @BeforeEach
  void setUp() {
    controlBusGateway = mock(ControlBusGateway.class);
    workflowService = mock(WorkflowService.class);
    logs = mock(LogRetrievalService.class);
    ExecutionManagementController controller =
        new ExecutionManagementController(controlBusGateway, workflowService, logs);
    webClient = WebTestClient.bindToController(controller).build();
  }

  // --- Workflow Trigger Tests ---

  @Test
  void testTriggerWorkflowSuccess() {
    WorkflowTriggerRequest request = new WorkflowTriggerRequest("session-1", "w1", Map.of());
    TaskResponse response = new TaskResponse("SUCCESS", "Build successful");
    String executionId = "exec-123";
    WorkflowExecution execution = new WorkflowExecution(executionId, Mono.just(response));

    when(workflowService.validateAndTriggerWorkflow(anyString(), anyString(), any()))
        .thenReturn(Mono.just(execution));

    webClient
        .post()
        .uri("/api/workflow/trigger")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isAccepted()
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo(202)
        .jsonPath("$.message")
        .isEqualTo("Workflow trigger accepted")
        .jsonPath("$.data.executionId")
        .isEqualTo(executionId);
  }

  @Test
  void testGetWorkflowStatus() {
    WorkflowProgress progress =
        new WorkflowProgress(
            "exec-1", "sess-1", "wf-1", "RUNNING", List.of(), LocalDateTime.now(), null);
    when(controlBusGateway.getCurrentProgress("exec-1")).thenReturn(progress);

    webClient
        .get()
        .uri("/api/workflow/sess-1/status/exec-1")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.data.executionId")
        .isEqualTo("exec-1");
  }

  @Test
  void testGetWorkflowStatusNotFound() {
    when(controlBusGateway.getCurrentProgress("exec-1")).thenReturn(null);

    webClient
        .get()
        .uri("/api/workflow/sess-1/status/exec-1")
        .exchange()
        .expectStatus()
        .isNotFound();
  }

  @Test
  void testStreamWorkflowStatus() {
    WorkflowProgress progress =
        new WorkflowProgress(
            "exec-1", "sess-1", "wf-1", "RUNNING", List.of(), LocalDateTime.now(), null);
    when(controlBusGateway.watchExecution("exec-1")).thenReturn(Flux.just(progress));

    webClient
        .get()
        .uri("/api/workflow/sess-1/status/exec-1/stream")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM);
  }

  @Test
  void testGetWorkflowHistory() {
    when(controlBusGateway.getHistory("sess-1")).thenReturn(List.of());

    webClient.get().uri("/api/workflow/sess-1/history").exchange().expectStatus().isOk();
  }

  @Test
  void testListLogs() {
    when(logs.listLogs("sess-1")).thenReturn(Mono.just(List.of("test.log")));

    webClient
        .get()
        .uri("/api/logs/sess-1")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.data[0]")
        .isEqualTo("test.log");
  }

  @Test
  void testGetLogContent() {
    when(logs.getLogContent("sess-1", "test.log")).thenReturn(Mono.just("content"));

    webClient
        .get()
        .uri("/api/logs/sess-1/test.log")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.data")
        .isEqualTo("content");
  }

  @Test
  void testGetLogContentNotFound() {
    when(logs.getLogContent("sess-1", "test.log"))
        .thenReturn(Mono.error(new java.io.IOException()));

    webClient.get().uri("/api/logs/sess-1/test.log").exchange().expectStatus().isNotFound();
  }

  @Test
  void testGetRawLogContent() {
    when(logs.getLogContent("sess-1", "test.log")).thenReturn(Mono.just("content"));

    webClient
        .get()
        .uri("/api/logs/sess-1/test.log/raw")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .isEqualTo("content");
  }

  @Test
  void testGetRawLogContentNotFound() {
    when(logs.getLogContent("sess-1", "test.log"))
        .thenReturn(Mono.error(new java.io.IOException()));

    webClient.get().uri("/api/logs/sess-1/test.log/raw").exchange().expectStatus().isNotFound();
  }

  // --- Control Bus Tests ---

  @Test
  void testGetActiveNodes() {
    when(controlBusGateway.getActiveNodes()).thenReturn(List.of("node1", "node2"));
    webClient
        .get()
        .uri("/api/control/nodes")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.data[0]")
        .isEqualTo("node1")
        .jsonPath("$.message")
        .isEqualTo("Active nodes retrieved");
  }

  @Test
  void testGetLastHeartbeat() {
    final Message<?> hb = DefaultMessage.create(null, "ok").withControl(true);
    doReturn(hb).when(controlBusGateway).getLastHeartbeat("wf1", "n1");

    webClient
        .get()
        .uri("/api/control/workflows/wf1/nodes/n1/heartbeat")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.data.payload")
        .isEqualTo("ok");
  }

  @Test
  void testSendCommand() {
    final Message<?> resp = DefaultMessage.create(null, "done");
    when(controlBusGateway.sendCommand(eq("wf1"), eq("n1"), any())).thenReturn(Mono.just(resp));

    webClient
        .post()
        .uri("/api/control/workflows/wf1/nodes/n1/command")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(Map.of("cmd", "reset"))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.message")
        .isEqualTo("Command processed");
  }

  @Test
  void testStreamProgress() {
    WorkflowProgress progress = mock(WorkflowProgress.class);
    when(controlBusGateway.watchExecution("exec1")).thenReturn(Flux.just(progress));

    webClient
        .get()
        .uri("/api/control/executions/exec1/progress/stream")
        .accept(MediaType.TEXT_EVENT_STREAM)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM);
  }

  @Test
  void testGetActiveNodesInWorkflow() {
    when(controlBusGateway.getActiveNodes("wf1")).thenReturn(List.of("node1", "node2"));
    webClient
        .get()
        .uri("/api/control/workflows/wf1/nodes")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.data[0]")
        .isEqualTo("node1")
        .jsonPath("$.message")
        .isEqualTo("Active nodes retrieved");
  }

  @Test
  void testGetProgress() {
    WorkflowProgress progress = mock(WorkflowProgress.class);
    when(controlBusGateway.getCurrentProgress("exec1")).thenReturn(progress);

    webClient
        .get()
        .uri("/api/control/executions/exec1/progress")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.message")
        .isEqualTo("Progress retrieved");
  }

  @Test
  void testStreamLogs() {
    when(controlBusGateway.watchLogs("exec1")).thenReturn(Flux.just("log1", "log2"));

    webClient
        .get()
        .uri("/api/control/executions/exec1/logs/stream")
        .accept(MediaType.TEXT_EVENT_STREAM)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM);
  }

  @Test
  void testGetHistory() {
    when(controlBusGateway.getHistory("session1")).thenReturn(List.of());

    webClient
        .get()
        .uri("/api/control/sessions/session1/history")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.message")
        .isEqualTo("History retrieved");
  }
}
