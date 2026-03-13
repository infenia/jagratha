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
import reactor.test.StepVerifier;
import tools.jackson.databind.ObjectMapper;

class AppMcpToolsExecutionLogsTest {

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
  void testGetWorkflowExecutionLogsSuccess() {
    final LocalDateTime now = LocalDateTime.now();
    final List<WorkflowExecutionSummary> history =
        List.of(
            new WorkflowExecutionSummary("exec-1", "workflow-1", "COMPLETED", now, now),
            new WorkflowExecutionSummary("exec-2", "workflow-2", "RUNNING", now, null));

    when(trackerService.getHistory("session-1")).thenReturn(history);

    var result = appMcpTools.getWorkflowExecutionLogs("session-1", "exec-1", null);

    StepVerifier.create(result)
        .expectNextMatches(logs -> logs.contains("exec-1") && logs.contains("workflow-1"))
        .verifyComplete();
  }

  @Test
  void testGetWorkflowExecutionLogsWithFilter() {
    final LocalDateTime now = LocalDateTime.now();
    final List<WorkflowExecutionSummary> history =
        List.of(
            new WorkflowExecutionSummary("exec-1", "workflow-1", "COMPLETED", now, now),
            new WorkflowExecutionSummary("exec-2", "workflow-2", "RUNNING", now, null));

    when(trackerService.getHistory("session-1")).thenReturn(history);

    var result = appMcpTools.getWorkflowExecutionLogs("session-1", "exec-1", "COMPLETED");

    StepVerifier.create(result)
        .expectNextMatches(logs -> logs.contains("COMPLETED"))
        .verifyComplete();
  }

  @Test
  void testGetWorkflowExecutionLogsNotFound() {
    final LocalDateTime now = LocalDateTime.now();
    final List<WorkflowExecutionSummary> history =
        List.of(new WorkflowExecutionSummary("exec-1", "workflow-1", "COMPLETED", now, now));

    when(trackerService.getHistory("session-1")).thenReturn(history);

    var result = appMcpTools.getWorkflowExecutionLogs("session-1", "exec-999", null);

    StepVerifier.create(result).expectError(IllegalArgumentException.class).verify();
  }

  @Test
  void testGetWorkflowExecutionLogsInvalidRegex() {
    final LocalDateTime now = LocalDateTime.now();
    final List<WorkflowExecutionSummary> history =
        List.of(new WorkflowExecutionSummary("exec-1", "workflow-1", "COMPLETED", now, now));

    when(trackerService.getHistory("session-1")).thenReturn(history);

    var result = appMcpTools.getWorkflowExecutionLogs("session-1", "exec-1", "[invalid(regex");

    StepVerifier.create(result).expectError(IllegalArgumentException.class).verify();
  }

  @Test
  void testGetWorkflowExecutionLogsFiltersCorrectly() {
    final LocalDateTime now = LocalDateTime.now();
    final List<WorkflowExecutionSummary> history =
        List.of(
            new WorkflowExecutionSummary("exec-1", "workflow-1", "COMPLETED", now, now),
            new WorkflowExecutionSummary("exec-2", "workflow-2", "RUNNING", now, null));

    when(trackerService.getHistory("session-1")).thenReturn(history);

    var result = appMcpTools.getWorkflowExecutionLogs("session-1", "exec-1", "workflow");

    StepVerifier.create(result)
        .expectNextMatches(logs -> logs.contains("exec-1") && logs.contains("workflow-1"))
        .verifyComplete();
  }
}
