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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infenia.yukta.model.session.SessionConfigData;
import com.infenia.yukta.service.SessionService;
import com.infenia.yukta.service.TaskTrackerService;
import com.infenia.yukta.service.WorkflowRegistry;
import com.infenia.yukta.service.WorkflowService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.ObjectMapper;

class AppMcpToolsCreateSessionTest {

  private AppMcpTools mcpTools;
  private WorkflowService workflowService;
  private SessionService sessionService;
  private TaskTrackerService trackerService;
  private WorkflowRegistry registry;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    workflowService = mock(WorkflowService.class);
    sessionService = mock(SessionService.class);
    trackerService = mock(TaskTrackerService.class);
    registry = mock(WorkflowRegistry.class);
    objectMapper = new ObjectMapper();
    mcpTools =
        new AppMcpTools(workflowService, sessionService, trackerService, registry, objectMapper);
  }

  @Test
  void testCreateSessionSuccessfulWithValidJson() {
    // Arrange
    String sessionConfigJson =
        """
        {
          "sessionId": "test-session",
          "description": "Test session for unit tests",
          "initiator": "test-user",
          "projectPath": "/tmp/test-project",
          "tags": {"env": "test"},
          "workflows": {
            "test-workflow": {
              "nodes": [
                {"id": "node-1", "type": "trigger", "config": {}}
              ],
              "edges": []
            }
          }
        }
        """;

    when(sessionService.applyConfig(any(SessionConfigData.class))).thenReturn(Mono.empty());

    // Act & Assert
    StepVerifier.create(mcpTools.createSession(sessionConfigJson))
        .expectNextMatches(
            response ->
                response.sessionId().equals("test-session")
                    && response.createdWorkflows().contains("test-workflow")
                    && response.success()
                    && response.warnings().isEmpty())
        .verifyComplete();
  }

  @Test
  void testCreateSessionWithMultipleWorkflows() {
    // Arrange
    String sessionConfigJson =
        """
        {
          "sessionId": "multi-workflow-session",
          "description": "Session with multiple workflows",
          "initiator": "test-user",
          "projectPath": "/tmp/test-project",
          "workflows": {
            "workflow-1": {
              "nodes": [{"id": "n1", "type": "trigger", "config": {}}],
              "edges": []
            },
            "workflow-2": {
              "nodes": [{"id": "n2", "type": "processor", "config": {}}],
              "edges": []
            },
            "workflow-3": {
              "nodes": [{"id": "n3", "type": "terminal", "config": {}}],
              "edges": []
            }
          }
        }
        """;

    when(sessionService.applyConfig(any(SessionConfigData.class))).thenReturn(Mono.empty());

    // Act & Assert
    StepVerifier.create(mcpTools.createSession(sessionConfigJson))
        .expectNextMatches(
            response ->
                response.sessionId().equals("multi-workflow-session")
                    && response.createdWorkflows().size() == 3
                    && response.createdWorkflows().contains("workflow-1")
                    && response.createdWorkflows().contains("workflow-2")
                    && response.createdWorkflows().contains("workflow-3")
                    && response.success())
        .verifyComplete();
  }

  @Test
  void testCreateSessionInvalidJsonHandling() {
    // Arrange
    String invalidJson = "{invalid json}";

    // Act & Assert
    StepVerifier.create(mcpTools.createSession(invalidJson))
        .expectNextMatches(
            response ->
                !response.success()
                    && !response.warnings().isEmpty()
                    && response.warnings().stream()
                        .anyMatch(w -> w.contains("Invalid JSON") || w.contains("JSON")))
        .verifyComplete();
  }

  @Test
  void testCreateSessionMissingRequiredFields() {
    // Arrange - missing required fields like description
    String incompleteJson =
        """
        {
          "sessionId": "incomplete-session",
          "initiator": "test-user",
          "workflows": {}
        }
        """;

    // Act & Assert
    StepVerifier.create(mcpTools.createSession(incompleteJson))
        .expectNextMatches(response -> !response.success() && !response.warnings().isEmpty())
        .verifyComplete();
  }

  @Test
  void testCreateSessionEmptyWorkflows() {
    // Arrange - empty workflows map which violates @NotEmpty
    String emptyWorkflowsJson =
        """
        {
          "sessionId": "empty-workflows",
          "description": "Session with empty workflows",
          "initiator": "test-user",
          "projectPath": "/tmp/test",
          "workflows": {}
        }
        """;

    // Act & Assert
    StepVerifier.create(mcpTools.createSession(emptyWorkflowsJson))
        .expectNextMatches(response -> !response.success() && !response.warnings().isEmpty())
        .verifyComplete();
  }

  @Test
  void testCreateSessionServiceError() {
    // Arrange
    String sessionConfigJson =
        """
        {
          "sessionId": "failing-session",
          "description": "Session that will fail to create",
          "initiator": "test-user",
          "projectPath": "/tmp/test-project",
          "workflows": {
            "workflow": {
              "nodes": [{"id": "n1", "type": "trigger", "config": {}}],
              "edges": []
            }
          }
        }
        """;

    when(sessionService.applyConfig(any(SessionConfigData.class)))
        .thenReturn(Mono.error(new RuntimeException("Database connection failed")));

    // Act & Assert
    StepVerifier.create(mcpTools.createSession(sessionConfigJson))
        .expectNextMatches(
            response ->
                !response.success()
                    && !response.warnings().isEmpty()
                    && response.warnings().stream()
                        .anyMatch(w -> w.contains("Database") || w.contains("connection")))
        .verifyComplete();
  }

  @Test
  void testCreateSessionExtractsWorkflowNamesCorrectly() {
    // Arrange
    String sessionConfigJson =
        """
        {
          "sessionId": "extraction-test",
          "description": "Test workflow extraction",
          "initiator": "test-user",
          "projectPath": "/tmp/test-project",
          "workflows": {
            "quality-checks": {
              "nodes": [{"id": "n1", "type": "trigger", "config": {}}],
              "edges": []
            },
            "integration-tests": {
              "nodes": [{"id": "n2", "type": "processor", "config": {}}],
              "edges": []
            }
          }
        }
        """;

    when(sessionService.applyConfig(any(SessionConfigData.class))).thenReturn(Mono.empty());

    // Act & Assert
    StepVerifier.create(mcpTools.createSession(sessionConfigJson))
        .expectNextMatches(
            response -> {
              List<String> workflows = response.createdWorkflows();
              return workflows.size() == 2
                  && workflows.contains("quality-checks")
                  && workflows.contains("integration-tests");
            })
        .verifyComplete();
  }

  @Test
  void testCreateSessionWarningsListInitiallyEmpty() {
    // Arrange
    String sessionConfigJson =
        """
        {
          "sessionId": "warning-test",
          "description": "Test warnings list",
          "initiator": "test-user",
          "projectPath": "/tmp/test-project",
          "workflows": {
            "test-wf": {
              "nodes": [{"id": "n1", "type": "trigger", "config": {}}],
              "edges": []
            }
          }
        }
        """;

    when(sessionService.applyConfig(any(SessionConfigData.class))).thenReturn(Mono.empty());

    // Act & Assert
    StepVerifier.create(mcpTools.createSession(sessionConfigJson))
        .expectNextMatches(response -> response.warnings().isEmpty() && response.success())
        .verifyComplete();
  }

  @Test
  void testCreateSessionMalformedJsonDetailed() {
    // Arrange
    String malformedJson = "{\"sessionId\": \"test\", \"broken\": ";

    // Act & Assert
    StepVerifier.create(mcpTools.createSession(malformedJson))
        .expectNextMatches(response -> !response.success() && !response.warnings().isEmpty())
        .verifyComplete();
  }
}
