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
package com.infenia.yukta.mcp;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infenia.yukta.model.monitoring.WorkflowExecutionSummary;
import com.infenia.yukta.service.SessionService;
import com.infenia.yukta.service.TaskTrackerService;
import com.infenia.yukta.service.WorkflowRegistry;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import tools.jackson.databind.ObjectMapper;

class AppMcpToolsStreamLogsTest {

  private AppMcpTools appMcpTools;
  private TaskTrackerService trackerService;

  @BeforeEach
  void setUp() {
    final SessionService sessionService = mock(SessionService.class);
    trackerService = mock(TaskTrackerService.class);
    final WorkflowRegistry registry = mock(WorkflowRegistry.class);
    final ObjectMapper objectMapper = new ObjectMapper();

    appMcpTools =
        new AppMcpTools(
            mock(), // workflowService
            sessionService,
            trackerService,
            registry,
            objectMapper);
  }

  @Test
  void testStreamSessionLogsSuccess() {
    final LocalDateTime now = LocalDateTime.now();
    final List<WorkflowExecutionSummary> history =
        List.of(
            new WorkflowExecutionSummary("exec-1", "workflow-1", "COMPLETED", now, now),
            new WorkflowExecutionSummary("exec-2", "workflow-2", "RUNNING", now, null));

    when(trackerService.getHistory("session-1")).thenReturn(history);

    Flux<String> result = appMcpTools.streamSessionLogs("session-1", null, null, null);

    StepVerifier.create(result).expectNextCount(2).verifyComplete();
  }

  @Test
  void testStreamSessionLogsFilterByExecutionId() {
    final LocalDateTime now = LocalDateTime.now();
    final List<WorkflowExecutionSummary> history =
        List.of(
            new WorkflowExecutionSummary("exec-1", "workflow-1", "COMPLETED", now, now),
            new WorkflowExecutionSummary("exec-2", "workflow-2", "RUNNING", now, null));

    when(trackerService.getHistory("session-1")).thenReturn(history);

    Flux<String> result = appMcpTools.streamSessionLogs("session-1", null, "exec-1", null);

    StepVerifier.create(result).expectNextCount(1).verifyComplete();
  }

  @Test
  void testStreamSessionLogsFilterByWorkflowId() {
    final LocalDateTime now = LocalDateTime.now();
    final List<WorkflowExecutionSummary> history =
        List.of(
            new WorkflowExecutionSummary("exec-1", "workflow-1", "COMPLETED", now, now),
            new WorkflowExecutionSummary("exec-2", "workflow-1", "RUNNING", now, null),
            new WorkflowExecutionSummary("exec-3", "workflow-2", "COMPLETED", now, now));

    when(trackerService.getHistory("session-1")).thenReturn(history);

    Flux<String> result = appMcpTools.streamSessionLogs("session-1", "workflow-1", null, null);

    StepVerifier.create(result).expectNextCount(2).verifyComplete();
  }

  @Test
  void testStreamSessionLogsApplyPattern() {
    final LocalDateTime now = LocalDateTime.now();
    final List<WorkflowExecutionSummary> history =
        List.of(
            new WorkflowExecutionSummary("exec-1", "workflow-1", "COMPLETED", now, now),
            new WorkflowExecutionSummary("exec-2", "workflow-2", "RUNNING", now, null));

    when(trackerService.getHistory("session-1")).thenReturn(history);

    Flux<String> result = appMcpTools.streamSessionLogs("session-1", null, null, "workflow-1");

    StepVerifier.create(result).expectNextCount(1).verifyComplete();
  }

  @Test
  void testStreamSessionLogsInvalidSession() {
    when(trackerService.getHistory("invalid-session"))
        .thenThrow(new RuntimeException("Session not found"));

    Flux<String> result = appMcpTools.streamSessionLogs("invalid-session", null, null, null);

    StepVerifier.create(result).expectError().verify();
  }

  @Test
  void testStreamSessionLogsInvalidRegex() {
    final LocalDateTime now = LocalDateTime.now();
    final List<WorkflowExecutionSummary> history =
        List.of(new WorkflowExecutionSummary("exec-1", "workflow-1", "COMPLETED", now, now));

    when(trackerService.getHistory("session-1")).thenReturn(history);

    Flux<String> result = appMcpTools.streamSessionLogs("session-1", null, null, "[invalid(regex");

    StepVerifier.create(result).expectError().verify();
  }
}
