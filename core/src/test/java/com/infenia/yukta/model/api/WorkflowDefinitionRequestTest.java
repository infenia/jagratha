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
package com.infenia.yukta.model.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WorkflowDefinitionRequestTest {

  private static Validator validator;

  @BeforeAll
  static void setUp() {
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  // WorkflowDefinitionRequest Constructor Tests

  @Test
  void constructor_validInput_createsInstance() {
    // Given
    String workflowId = "test-workflow";
    String description = "Test workflow definition";
    var node = new WorkflowDefinitionRequest.NodeRequest("node1", "gradle", Map.of());
    var edge = new WorkflowDefinitionRequest.EdgeRequest("node1", "node2", "default");
    var nodes = List.of(node);
    var edges = List.of(edge);

    // When
    var request = new WorkflowDefinitionRequest(workflowId, description, nodes, edges);

    // Then
    assertEquals("test-workflow", request.workflowId());
    assertEquals("Test workflow definition", request.description());
    assertNotNull(request.nodes());
    assertNotNull(request.edges());
    assertEquals(1, request.nodes().size());
    assertEquals(1, request.edges().size());
  }

  @Test
  void constructor_nullNodes_defaultsToEmptyList() {
    // Given
    String workflowId = "test-workflow";
    String description = "Test workflow";
    var edge = new WorkflowDefinitionRequest.EdgeRequest("n1", "n2", "default");

    // When
    var request = new WorkflowDefinitionRequest(workflowId, description, null, List.of(edge));

    // Then
    assertNotNull(request.nodes());
    assertTrue(request.nodes().isEmpty());
  }

  @Test
  void constructor_nullEdges_defaultsToEmptyList() {
    // Given
    String workflowId = "test-workflow";
    String description = "Test workflow";
    var node = new WorkflowDefinitionRequest.NodeRequest("node1", "gradle", Map.of());

    // When
    var request = new WorkflowDefinitionRequest(workflowId, description, List.of(node), null);

    // Then
    assertNotNull(request.edges());
    assertTrue(request.edges().isEmpty());
  }

  @Test
  void constructor_immutableNodes_preventsMutation() {
    // Given
    var mutableNodeList = new java.util.ArrayList<WorkflowDefinitionRequest.NodeRequest>();
    var node = new WorkflowDefinitionRequest.NodeRequest("node1", "gradle", Map.of());
    mutableNodeList.add(node);
    var request =
        new WorkflowDefinitionRequest("test-workflow", "desc", mutableNodeList, List.of());

    // When-Then
    assertTrue(request.nodes() instanceof java.util.List);
    // Verify that modifying original list doesn't affect request's nodes
    mutableNodeList.clear();
    assertEquals(1, request.nodes().size());
  }

  @Test
  void constructor_immutableEdges_preventsMutation() {
    // Given
    var mutableEdgeList = new java.util.ArrayList<WorkflowDefinitionRequest.EdgeRequest>();
    var edge = new WorkflowDefinitionRequest.EdgeRequest("n1", "n2", "default");
    mutableEdgeList.add(edge);
    var node = new WorkflowDefinitionRequest.NodeRequest("node1", "gradle", Map.of());
    var request =
        new WorkflowDefinitionRequest("test-workflow", "desc", List.of(node), mutableEdgeList);

    // When-Then
    assertTrue(request.edges() instanceof java.util.List);
    // Verify that modifying original list doesn't affect request's edges
    mutableEdgeList.clear();
    assertEquals(1, request.edges().size());
  }

  // WorkflowDefinitionRequest Validation Tests

  @Test
  void validation_validInput_passesValidation() {
    // Given
    var node = new WorkflowDefinitionRequest.NodeRequest("node1", "gradle", Map.of());
    var edge = new WorkflowDefinitionRequest.EdgeRequest("node1", "node2", "default");
    var request =
        new WorkflowDefinitionRequest("test-workflow", "description", List.of(node), List.of(edge));

    // When
    Set<ConstraintViolation<WorkflowDefinitionRequest>> violations = validator.validate(request);

    // Then
    assertTrue(violations.isEmpty());
  }

  @Test
  void validation_blankWorkflowId_failsValidation() {
    // Given
    var node = new WorkflowDefinitionRequest.NodeRequest("node1", "gradle", Map.of());
    var request = new WorkflowDefinitionRequest("", "description", List.of(node), List.of());

    // When
    Set<ConstraintViolation<WorkflowDefinitionRequest>> violations = validator.validate(request);

    // Then
    assertFalse(violations.isEmpty());
    assertTrue(
        violations.stream().anyMatch(v -> v.getMessage().contains("Workflow ID is mandatory")));
  }

  @Test
  void validation_blankDescription_failsValidation() {
    // Given
    var node = new WorkflowDefinitionRequest.NodeRequest("node1", "gradle", Map.of());
    var request = new WorkflowDefinitionRequest("test-workflow", "   ", List.of(node), List.of());

    // When
    Set<ConstraintViolation<WorkflowDefinitionRequest>> violations = validator.validate(request);

    // Then
    assertFalse(violations.isEmpty());
    assertTrue(
        violations.stream()
            .anyMatch(v -> v.getMessage().contains("Workflow description is mandatory")));
  }

  @Test
  void validation_descriptionExceedsMaxLength_failsValidation() {
    // Given
    String longDescription = "a".repeat(257);
    var node = new WorkflowDefinitionRequest.NodeRequest("node1", "gradle", Map.of());
    var request =
        new WorkflowDefinitionRequest("test-workflow", longDescription, List.of(node), List.of());

    // When
    Set<ConstraintViolation<WorkflowDefinitionRequest>> violations = validator.validate(request);

    // Then
    assertFalse(violations.isEmpty());
    assertTrue(
        violations.stream()
            .anyMatch(
                v ->
                    v.getMessage()
                        .contains("Workflow description must be at most 256 characters")));
  }

  @Test
  void validation_descriptionExactlyMaxLength_passesValidation() {
    // Given
    String maxDescription = "a".repeat(256);
    var node = new WorkflowDefinitionRequest.NodeRequest("node1", "gradle", Map.of());
    var request =
        new WorkflowDefinitionRequest("test-workflow", maxDescription, List.of(node), List.of());

    // When
    Set<ConstraintViolation<WorkflowDefinitionRequest>> violations = validator.validate(request);

    // Then
    assertTrue(violations.isEmpty());
  }

  @Test
  void validation_emptyNodesList_failsValidation() {
    // Given
    var request =
        new WorkflowDefinitionRequest("test-workflow", "description", List.of(), List.of());

    // When
    Set<ConstraintViolation<WorkflowDefinitionRequest>> violations = validator.validate(request);

    // Then
    assertFalse(violations.isEmpty());
    assertTrue(
        violations.stream()
            .anyMatch(v -> v.getMessage().contains("Workflow must contain at least one node")));
  }

  @Test
  void validation_nullEdgesHandledByConstructor_passesValidation() {
    // Given
    var node = new WorkflowDefinitionRequest.NodeRequest("node1", "gradle", Map.of());
    // Constructor converts null edges to empty list
    var request =
        new WorkflowDefinitionRequest("test-workflow", "description", List.of(node), null);

    // When
    Set<ConstraintViolation<WorkflowDefinitionRequest>> violations = validator.validate(request);

    // Then
    assertTrue(violations.isEmpty());
  }

  @Test
  void validation_invalidNodeInList_failsValidation() {
    // Given - node with null nodeId
    var invalidNode = new WorkflowDefinitionRequest.NodeRequest(null, "gradle", Map.of());
    var request =
        new WorkflowDefinitionRequest(
            "test-workflow", "description", List.of(invalidNode), List.of());

    // When
    Set<ConstraintViolation<WorkflowDefinitionRequest>> violations = validator.validate(request);

    // Then
    assertFalse(violations.isEmpty());
    assertTrue(
        violations.stream().anyMatch(v -> v.getMessage().contains("Node ID cannot be null")));
  }

  @Test
  void validation_invalidEdgeInList_failsValidation() {
    // Given - edge with blank source
    var invalidEdge = new WorkflowDefinitionRequest.EdgeRequest("", "node2", "default");
    var node = new WorkflowDefinitionRequest.NodeRequest("node1", "gradle", Map.of());
    var request =
        new WorkflowDefinitionRequest(
            "test-workflow", "description", List.of(node), List.of(invalidEdge));

    // When
    Set<ConstraintViolation<WorkflowDefinitionRequest>> violations = validator.validate(request);

    // Then
    assertFalse(violations.isEmpty());
    assertTrue(
        violations.stream()
            .anyMatch(v -> v.getMessage().contains("Source node ID cannot be blank")));
  }

  // NodeRequest Constructor Tests

  @Test
  void nodeRequest_validInput_createsInstance() {
    // Given
    String nodeId = "node1";
    String type = "gradle";
    Map<String, Object> config = Map.of("key", "value");

    // When
    var nodeRequest = new WorkflowDefinitionRequest.NodeRequest(nodeId, type, config);

    // Then
    assertEquals("node1", nodeRequest.nodeId());
    assertEquals("gradle", nodeRequest.type());
    assertNotNull(nodeRequest.config());
    assertEquals(1, nodeRequest.config().size());
  }

  @Test
  void nodeRequest_nullConfig_defaultsToEmptyMap() {
    // Given
    String nodeId = "node1";
    String type = "gradle";

    // When
    var nodeRequest = new WorkflowDefinitionRequest.NodeRequest(nodeId, type, null);

    // Then
    assertNotNull(nodeRequest.config());
    assertTrue(nodeRequest.config().isEmpty());
  }

  @Test
  void nodeRequest_immutableConfig_preventsMutation() {
    // Given
    var mutableConfig = new java.util.HashMap<String, Object>();
    mutableConfig.put("key", "value");
    var nodeRequest = new WorkflowDefinitionRequest.NodeRequest("node1", "gradle", mutableConfig);

    // When-Then
    // Modifying original map doesn't affect nodeRequest's config
    mutableConfig.clear();
    assertEquals(1, nodeRequest.config().size());
  }

  // NodeRequest Validation Tests

  @Test
  void nodeRequest_validInput_passesValidation() {
    // Given
    var nodeRequest =
        new WorkflowDefinitionRequest.NodeRequest("node1", "gradle", Map.of("key", "value"));

    // When
    Set<ConstraintViolation<WorkflowDefinitionRequest.NodeRequest>> violations =
        validator.validate(nodeRequest);

    // Then
    assertTrue(violations.isEmpty());
  }

  @Test
  void nodeRequest_blankNodeId_failsValidation() {
    // Given
    var nodeRequest = new WorkflowDefinitionRequest.NodeRequest("", "gradle", Map.of());

    // When
    Set<ConstraintViolation<WorkflowDefinitionRequest.NodeRequest>> violations =
        validator.validate(nodeRequest);

    // Then
    assertFalse(violations.isEmpty());
    assertTrue(
        violations.stream().anyMatch(v -> v.getMessage().contains("Node ID cannot be blank")));
  }

  @Test
  void nodeRequest_nullNodeId_failsValidation() {
    // Given
    var nodeRequest = new WorkflowDefinitionRequest.NodeRequest(null, "gradle", Map.of());

    // When
    Set<ConstraintViolation<WorkflowDefinitionRequest.NodeRequest>> violations =
        validator.validate(nodeRequest);

    // Then
    assertFalse(violations.isEmpty());
    assertTrue(
        violations.stream().anyMatch(v -> v.getMessage().contains("Node ID cannot be null")));
  }

  @Test
  void nodeRequest_blankType_failsValidation() {
    // Given
    var nodeRequest = new WorkflowDefinitionRequest.NodeRequest("node1", "", Map.of());

    // When
    Set<ConstraintViolation<WorkflowDefinitionRequest.NodeRequest>> violations =
        validator.validate(nodeRequest);

    // Then
    assertFalse(violations.isEmpty());
    assertTrue(
        violations.stream().anyMatch(v -> v.getMessage().contains("Plugin type cannot be blank")));
  }

  @Test
  void nodeRequest_nullType_failsValidation() {
    // Given
    var nodeRequest = new WorkflowDefinitionRequest.NodeRequest("node1", null, Map.of());

    // When
    Set<ConstraintViolation<WorkflowDefinitionRequest.NodeRequest>> violations =
        validator.validate(nodeRequest);

    // Then
    assertFalse(violations.isEmpty());
    assertTrue(
        violations.stream().anyMatch(v -> v.getMessage().contains("Plugin type cannot be null")));
  }

  // EdgeRequest Constructor Tests

  @Test
  void edgeRequest_validInput_createsInstance() {
    // Given
    String source = "node1";
    String target = "node2";
    String sourcePort = "output";

    // When
    var edgeRequest = new WorkflowDefinitionRequest.EdgeRequest(source, target, sourcePort);

    // Then
    assertEquals("node1", edgeRequest.source());
    assertEquals("node2", edgeRequest.target());
    assertEquals("output", edgeRequest.sourcePort());
  }

  @Test
  void edgeRequest_twoParameterConstructor_createsInstanceWithNullPort() {
    // Given
    String source = "node1";
    String target = "node2";

    // When
    var edgeRequest = new WorkflowDefinitionRequest.EdgeRequest(source, target, "default");

    // Then
    assertEquals("node1", edgeRequest.source());
    assertEquals("node2", edgeRequest.target());
    assertEquals("default", edgeRequest.sourcePort());
  }

  @Test
  void edgeRequest_nullSourcePort_createsInstance() {
    // Given
    String source = "node1";
    String target = "node2";

    // When
    var edgeRequest = new WorkflowDefinitionRequest.EdgeRequest(source, target, null);

    // Then
    assertEquals("node1", edgeRequest.source());
    assertEquals("node2", edgeRequest.target());
    assertEquals(null, edgeRequest.sourcePort());
  }

  // EdgeRequest Validation Tests

  @Test
  void edgeRequest_validInput_passesValidation() {
    // Given
    var edgeRequest = new WorkflowDefinitionRequest.EdgeRequest("node1", "node2", "output");

    // When
    Set<ConstraintViolation<WorkflowDefinitionRequest.EdgeRequest>> violations =
        validator.validate(edgeRequest);

    // Then
    assertTrue(violations.isEmpty());
  }

  @Test
  void edgeRequest_blankSource_failsValidation() {
    // Given
    var edgeRequest = new WorkflowDefinitionRequest.EdgeRequest("", "node2", "default");

    // When
    Set<ConstraintViolation<WorkflowDefinitionRequest.EdgeRequest>> violations =
        validator.validate(edgeRequest);

    // Then
    assertFalse(violations.isEmpty());
    assertTrue(
        violations.stream()
            .anyMatch(v -> v.getMessage().contains("Source node ID cannot be blank")));
  }

  @Test
  void edgeRequest_nullSource_failsValidation() {
    // Given
    var edgeRequest = new WorkflowDefinitionRequest.EdgeRequest(null, "node2", "default");

    // When
    Set<ConstraintViolation<WorkflowDefinitionRequest.EdgeRequest>> violations =
        validator.validate(edgeRequest);

    // Then
    assertFalse(violations.isEmpty());
    assertTrue(
        violations.stream()
            .anyMatch(v -> v.getMessage().contains("Source node ID cannot be null")));
  }

  @Test
  void edgeRequest_blankTarget_failsValidation() {
    // Given
    var edgeRequest = new WorkflowDefinitionRequest.EdgeRequest("node1", "", "default");

    // When
    Set<ConstraintViolation<WorkflowDefinitionRequest.EdgeRequest>> violations =
        validator.validate(edgeRequest);

    // Then
    assertFalse(violations.isEmpty());
    assertTrue(
        violations.stream()
            .anyMatch(v -> v.getMessage().contains("Target node ID cannot be blank")));
  }

  @Test
  void edgeRequest_nullTarget_failsValidation() {
    // Given
    var edgeRequest = new WorkflowDefinitionRequest.EdgeRequest("node1", null, "default");

    // When
    Set<ConstraintViolation<WorkflowDefinitionRequest.EdgeRequest>> violations =
        validator.validate(edgeRequest);

    // Then
    assertFalse(violations.isEmpty());
    assertTrue(
        violations.stream()
            .anyMatch(v -> v.getMessage().contains("Target node ID cannot be null")));
  }

  // Record Equality and Hash Code Tests

  @Test
  void recordEquality_sameValues_returnsTrue() {
    // Given
    var node = new WorkflowDefinitionRequest.NodeRequest("node1", "gradle", Map.of());
    var request1 = new WorkflowDefinitionRequest("test", "desc", List.of(node), List.of());
    var request2 = new WorkflowDefinitionRequest("test", "desc", List.of(node), List.of());

    // When-Then
    assertEquals(request1, request2);
  }

  @Test
  void recordEquality_differentWorkflowId_returnsFalse() {
    // Given
    var node = new WorkflowDefinitionRequest.NodeRequest("node1", "gradle", Map.of());
    var request1 = new WorkflowDefinitionRequest("test1", "desc", List.of(node), List.of());
    var request2 = new WorkflowDefinitionRequest("test2", "desc", List.of(node), List.of());

    // When-Then
    assertFalse(request1.equals(request2));
  }

  @Test
  void recordEquality_differentDescription_returnsFalse() {
    // Given
    var node = new WorkflowDefinitionRequest.NodeRequest("node1", "gradle", Map.of());
    var request1 = new WorkflowDefinitionRequest("test", "desc1", List.of(node), List.of());
    var request2 = new WorkflowDefinitionRequest("test", "desc2", List.of(node), List.of());

    // When-Then
    assertFalse(request1.equals(request2));
  }

  @Test
  void recordHashCode_sameValues_sameHashCode() {
    // Given
    var node = new WorkflowDefinitionRequest.NodeRequest("node1", "gradle", Map.of());
    var request1 = new WorkflowDefinitionRequest("test", "desc", List.of(node), List.of());
    var request2 = new WorkflowDefinitionRequest("test", "desc", List.of(node), List.of());

    // When-Then
    assertEquals(request1.hashCode(), request2.hashCode());
  }

  @Test
  void recordToString_validInstance_returnsStringRepresentation() {
    // Given
    var node = new WorkflowDefinitionRequest.NodeRequest("node1", "gradle", Map.of());
    var request = new WorkflowDefinitionRequest("test", "desc", List.of(node), List.of());

    // When
    String toString = request.toString();

    // Then
    assertNotNull(toString);
    assertTrue(toString.contains("test"));
    assertTrue(toString.contains("desc"));
  }

  @Test
  void nodeRequestEquality_sameValues_returnsTrue() {
    // Given
    var node1 =
        new WorkflowDefinitionRequest.NodeRequest("node1", "gradle", Map.of("key", "value"));
    var node2 =
        new WorkflowDefinitionRequest.NodeRequest("node1", "gradle", Map.of("key", "value"));

    // When-Then
    assertEquals(node1, node2);
  }

  @Test
  void edgeRequestEquality_sameValues_returnsTrue() {
    // Given
    var edge1 = new WorkflowDefinitionRequest.EdgeRequest("node1", "node2", "output");
    var edge2 = new WorkflowDefinitionRequest.EdgeRequest("node1", "node2", "output");

    // When-Then
    assertEquals(edge1, edge2);
  }

  @Test
  void edgeRequestEquality_differentSourcePort_returnsFalse() {
    // Given
    var edge1 = new WorkflowDefinitionRequest.EdgeRequest("node1", "node2", "output1");
    var edge2 = new WorkflowDefinitionRequest.EdgeRequest("node1", "node2", "output2");

    // When-Then
    assertFalse(edge1.equals(edge2));
  }
}
