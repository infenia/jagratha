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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infenia.yukta.model.api.ControlBusStatus;
import com.infenia.yukta.model.monitoring.WorkflowExecutionSummary;
import com.infenia.yukta.service.SessionService;
import com.infenia.yukta.service.TaskTrackerService;
import com.infenia.yukta.service.WorkflowRegistry;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AppMcpToolsControlBusTest {

  private AppMcpTools appMcpTools;
  private TaskTrackerService trackerService;
  private WorkflowRegistry registry;

  @BeforeEach
  void setUp() {
    final SessionService sessionService = mock(SessionService.class);
    trackerService = mock(TaskTrackerService.class);
    registry = mock(WorkflowRegistry.class);
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
  void testGetControlBusStatusFull() {
    final LocalDateTime now = LocalDateTime.now();
    final List<WorkflowExecutionSummary> history =
        List.of(new WorkflowExecutionSummary("exec-1", "workflow-1", "COMPLETED", now, now));

    when(trackerService.getHistory("session-1")).thenReturn(history);
    when(registry.listPlugins()).thenReturn(List.of());

    ControlBusStatus status = appMcpTools.getControlBusStatus(null);

    assertNotNull(status);
    assertNotNull(status.activeSessions());
    assertNotNull(status.pluginRegistry());
    assertNotNull(status.systemHealth());
    assertNotNull(status.recentExecutions());
  }

  @Test
  void testGetControlBusStatusFilterSessions() {
    final LocalDateTime now = LocalDateTime.now();
    final List<WorkflowExecutionSummary> history =
        List.of(new WorkflowExecutionSummary("exec-1", "workflow-1", "COMPLETED", now, now));

    when(trackerService.getHistory("session-1")).thenReturn(history);

    ControlBusStatus status = appMcpTools.getControlBusStatus("sessions");

    assertNotNull(status);
    assertNotNull(status.activeSessions());
  }

  @Test
  void testGetControlBusStatusFilterPlugins() {
    when(registry.listPlugins()).thenReturn(List.of());

    ControlBusStatus status = appMcpTools.getControlBusStatus("plugins");

    assertNotNull(status);
    assertNotNull(status.pluginRegistry());
  }

  @Test
  void testGetControlBusStatusFilterHealth() {
    ControlBusStatus status = appMcpTools.getControlBusStatus("health");

    assertNotNull(status);
    assertNotNull(status.systemHealth());
  }

  @Test
  void testGetControlBusStatusFilterExecutions() {
    final LocalDateTime now = LocalDateTime.now();
    final List<WorkflowExecutionSummary> history =
        List.of(new WorkflowExecutionSummary("exec-1", "workflow-1", "COMPLETED", now, now));

    when(trackerService.getHistory("session-1")).thenReturn(history);

    ControlBusStatus status = appMcpTools.getControlBusStatus("executions");

    assertNotNull(status);
    assertNotNull(status.recentExecutions());
  }

  @Test
  void testGetControlBusStatusEmptyData() {
    when(registry.listPlugins()).thenReturn(List.of());

    ControlBusStatus status = appMcpTools.getControlBusStatus(null);

    assertNotNull(status);
    assertNotNull(status.activeSessions());
    assertNotNull(status.pluginRegistry());
    assertNotNull(status.recentExecutions());
  }

  @Test
  void testGetControlBusStatusHasSystemHealth() {
    ControlBusStatus status = appMcpTools.getControlBusStatus(null);

    assertNotNull(status.systemHealth());
    assertNotNull(status.systemHealth().uptime());
    assertFalse(status.systemHealth().uptime().isEmpty());
  }
}
