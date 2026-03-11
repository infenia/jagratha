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
package com.infenia.yukta.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class PreparedWorkflowTest {

  @Test
  void testPreparedWorkflowTopologicalSort() {
    WorkflowDefinition.Node n1 = new WorkflowDefinition.Node("n1", "t", null);
    WorkflowDefinition.Node n2 = new WorkflowDefinition.Node("n2", "t", null);
    WorkflowDefinition.Edge e1 = new WorkflowDefinition.Edge("n1", "n2");
    WorkflowDefinition def = new WorkflowDefinition("d", List.of(n1, n2), List.of(e1));

    Map<String, List<WorkflowDefinition.Node>> adj = Map.of("n1", List.of(n2), "n2", List.of());
    Map<String, List<WorkflowDefinition.Node>> parents = Map.of("n1", List.of(), "n2", List.of(n1));

    List<WorkflowDefinition.Node> order =
        PreparedWorkflow.computeTopologicalOrder(def, adj, parents);
    assertEquals(2, order.size());
    assertEquals("n1", order.get(0).nodeId());
    assertEquals("n2", order.get(1).nodeId());

    // Test with missing key in adj (null children)
    Map<String, List<WorkflowDefinition.Node>> adjNull = new java.util.HashMap<>();
    adjNull.put("n1", List.of(n2));
    // n2 is missing
    order = PreparedWorkflow.computeTopologicalOrder(def, adjNull, parents);
    assertEquals(2, order.size());

    PreparedWorkflow prepared =
        new PreparedWorkflow(def, adj, parents, Map.of(), order, (id, p) -> Mono.empty());
    assertEquals(def, prepared.definition());
  }

  @Test
  void testPreparedWorkflowCycle() {
    WorkflowDefinition.Node n1 = new WorkflowDefinition.Node("n1", "t", null);
    WorkflowDefinition.Node n2 = new WorkflowDefinition.Node("n2", "t", null);
    WorkflowDefinition.Edge e1 = new WorkflowDefinition.Edge("n1", "n2");
    WorkflowDefinition.Edge e2 = new WorkflowDefinition.Edge("n2", "n1");
    WorkflowDefinition def = new WorkflowDefinition("d", List.of(n1, n2), List.of(e1, e2));

    Map<String, List<WorkflowDefinition.Node>> adj = Map.of("n1", List.of(n2), "n2", List.of(n1));
    Map<String, List<WorkflowDefinition.Node>> parents =
        Map.of("n1", List.of(n2), "n2", List.of(n1));

    assertThrows(
        IllegalArgumentException.class,
        () -> PreparedWorkflow.computeTopologicalOrder(def, adj, parents));
  }
}
