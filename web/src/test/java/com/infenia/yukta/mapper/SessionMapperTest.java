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
package com.infenia.yukta.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.infenia.yukta.dto.request.ConfigRequest;
import com.infenia.yukta.dto.request.WorkflowDefinitionRequest;
import com.infenia.yukta.dto.request.WorkflowDefinitionRequest.EdgeRequest;
import com.infenia.yukta.dto.request.WorkflowDefinitionRequest.NodeRequest;
import com.infenia.yukta.model.session.SessionConfigData;
import com.infenia.yukta.model.workflow.WorkflowDefinition;
import com.infenia.yukta.model.workflow.WorkflowDefinition.Edge;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SessionMapperTest {

  @Autowired private SessionMapper mapper;

  @Test
  void testConfigRequestToSessionConfigData() {
    ConfigRequest request =
        new ConfigRequest(
            "session-123",
            "Test Session",
            "test-user",
            Map.of("env", "test"),
            "/home/user/project",
            Map.of(
                "workflow-1",
                new WorkflowDefinitionRequest(
                    "workflow-1",
                    "Test Workflow",
                    List.of(new NodeRequest("node-1", "ProcessorPlugin", Map.of("param", "value"))),
                    List.of(new EdgeRequest("node-1", "node-1", null)))));

    SessionConfigData result = mapper.configRequestToSessionConfigData(request);

    assertThat(result).isNotNull();
    assertThat(result.sessionId()).isEqualTo("session-123");
    assertThat(result.description()).isEqualTo("Test Session");
    assertThat(result.initiator()).isEqualTo("test-user");
    assertThat(result.projectPath()).isEqualTo("/home/user/project");
  }

  @Test
  void testWorkflowDefinitionRequestToWorkflowDefinition() {
    WorkflowDefinitionRequest request =
        new WorkflowDefinitionRequest(
            "workflow-1",
            "Test Workflow",
            List.of(
                new NodeRequest("node-1", "ProcessorPlugin", Map.of()),
                new NodeRequest("node-2", "TerminalPlugin", Map.of())),
            List.of(new EdgeRequest("node-1", "node-2", null)));

    WorkflowDefinition result = mapper.workflowDefinitionRequestToWorkflowDefinition(request);

    assertThat(result).isNotNull();
    assertThat(result.nodes()).hasSize(2);
    assertThat(result.edges()).hasSize(1);
  }

  @Test
  void testEdgeRequestToEdgeWithNullSourcePort() {
    EdgeRequest edgeRequest = new EdgeRequest("node-1", "node-2", null);

    Edge result = mapper.edgeRequestToEdge(edgeRequest);

    assertThat(result).isNotNull();
    assertThat(result.sourcePort()).isEqualTo("default");
    assertThat(result.source()).isEqualTo("node-1");
    assertThat(result.target()).isEqualTo("node-2");
  }

  @Test
  void testEdgeRequestToEdgeWithSourcePort() {
    EdgeRequest edgeRequest = new EdgeRequest("node-1", "node-2", "output");

    Edge result = mapper.edgeRequestToEdge(edgeRequest);

    assertThat(result).isNotNull();
    assertThat(result.sourcePort()).isEqualTo("output");
    assertThat(result.source()).isEqualTo("node-1");
    assertThat(result.target()).isEqualTo("node-2");
  }

  @Test
  void testConfigRequestToSessionConfigDataWithEmptyWorkflows() {
    ConfigRequest request =
        new ConfigRequest(
            "session-456", "Empty Session", "system", null, "/home/user/project2", Map.of());

    SessionConfigData result = mapper.configRequestToSessionConfigData(request);

    assertThat(result).isNotNull();
    assertThat(result.sessionId()).isEqualTo("session-456");
  }

  @Test
  void testWorkflowDefinitionRequestToWorkflowDefinitionWithMultipleNodes() {
    WorkflowDefinitionRequest request =
        new WorkflowDefinitionRequest(
            "workflow-complex",
            "Complex Workflow",
            List.of(
                new NodeRequest("node-1", "TriggerPlugin", Map.of()),
                new NodeRequest("node-2", "ProcessorPlugin", Map.of("step", "1")),
                new NodeRequest("node-3", "ProcessorPlugin", Map.of("step", "2")),
                new NodeRequest("node-4", "TerminalPlugin", Map.of())),
            List.of(
                new EdgeRequest("node-1", "node-2", "success"),
                new EdgeRequest("node-2", "node-3", null),
                new EdgeRequest("node-3", "node-4", "default")));

    WorkflowDefinition result = mapper.workflowDefinitionRequestToWorkflowDefinition(request);

    assertThat(result).isNotNull();
    assertThat(result.nodes()).hasSize(4);
    assertThat(result.edges()).hasSize(3);
  }

  @Test
  void testEdgeRequestToEdgeWithoutConfig() {
    EdgeRequest edgeRequest1 = new EdgeRequest("node-1", "node-2", "");
    Edge result1 = mapper.edgeRequestToEdge(edgeRequest1);
    assertThat(result1).isNotNull();

    EdgeRequest edgeRequest2 = new EdgeRequest("node-A", "node-B", "");
    Edge result2 = mapper.edgeRequestToEdge(edgeRequest2);
    assertThat(result2).isNotNull();
  }

  @Test
  void testConfigRequestWithManyWorkflows() {
    Map<String, WorkflowDefinitionRequest> workflows = new java.util.HashMap<>();
    for (int i = 0; i < 5; i++) {
      workflows.put(
          "workflow-" + i,
          new WorkflowDefinitionRequest(
              "workflow-" + i,
              "Workflow " + i,
              List.of(new NodeRequest("node-" + i, "ProcessorPlugin", Map.of())),
              List.of()));
    }

    ConfigRequest request =
        new ConfigRequest(
            "session-many",
            "Many Workflows",
            "test-user",
            Map.of("key1", "val1", "key2", "val2"),
            "/home/user/project",
            workflows);

    SessionConfigData result = mapper.configRequestToSessionConfigData(request);

    assertThat(result).isNotNull();
    assertThat(result.sessionId()).isEqualTo("session-many");
  }

  @Test
  void testWorkflowWithEmptyEdges() {
    WorkflowDefinitionRequest request =
        new WorkflowDefinitionRequest(
            "workflow-no-edges",
            "No Edges Workflow",
            List.of(
                new NodeRequest("node-1", "TriggerPlugin", Map.of()),
                new NodeRequest("node-2", "TerminalPlugin", Map.of())),
            List.of());

    WorkflowDefinition result = mapper.workflowDefinitionRequestToWorkflowDefinition(request);

    assertThat(result).isNotNull();
    assertThat(result.nodes()).hasSize(2);
    assertThat(result.edges()).isEmpty();
  }

  @Test
  void testConfigRequestToSessionConfigDataWithNull() {
    SessionConfigData result = mapper.configRequestToSessionConfigData(null);

    assertThat(result).isNull();
  }

  @Test
  void testWorkflowDefinitionRequestToWorkflowDefinitionWithNull() {
    WorkflowDefinition result = mapper.workflowDefinitionRequestToWorkflowDefinition(null);

    assertThat(result).isNull();
  }

  @Test
  void testEdgeRequestToEdgeWithNull() {
    Edge result = mapper.edgeRequestToEdge(null);

    assertThat(result).isNull();
  }
}
