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
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.infenia.yukta.model.WorkflowDefinition;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TopologicalSortServiceTest {

  private final TopologicalSortService service = new TopologicalSortService();

  @Test
  void testComputeTopologicalOrder() {
    WorkflowDefinition.Node n1 = new WorkflowDefinition.Node("n1", "t", null);
    WorkflowDefinition.Node n2 = new WorkflowDefinition.Node("n2", "t", null);
    WorkflowDefinition.Edge e1 = new WorkflowDefinition.Edge("n1", "n2");
    WorkflowDefinition def = new WorkflowDefinition("d", List.of(n1, n2), List.of(e1));

    Map<String, List<WorkflowDefinition.Node>> adj = Map.of("n1", List.of(n2), "n2", List.of());
    Map<String, List<WorkflowDefinition.Node>> parents = Map.of("n1", List.of(), "n2", List.of(n1));

    List<WorkflowDefinition.Node> order = service.computeTopologicalOrder(def, adj, parents);
    assertEquals(2, order.size());
    assertEquals("n1", order.get(0).nodeId());
    assertEquals("n2", order.get(1).nodeId());
  }

  @Test
  void testComputeTopologicalOrderWithMissingAdjacencyKey() {
    WorkflowDefinition.Node n1 = new WorkflowDefinition.Node("n1", "t", null);
    WorkflowDefinition.Node n2 = new WorkflowDefinition.Node("n2", "t", null);
    WorkflowDefinition.Edge e1 = new WorkflowDefinition.Edge("n1", "n2");
    WorkflowDefinition def = new WorkflowDefinition("d", List.of(n1, n2), List.of(e1));

    Map<String, List<WorkflowDefinition.Node>> adjNull = new HashMap<>();
    adjNull.put("n1", List.of(n2));
    // n2 is missing in adjacency list
    Map<String, List<WorkflowDefinition.Node>> parents = Map.of("n1", List.of(), "n2", List.of(n1));

    List<WorkflowDefinition.Node> order = service.computeTopologicalOrder(def, adjNull, parents);
    assertEquals(2, order.size());
    assertEquals("n1", order.get(0).nodeId());
    assertEquals("n2", order.get(1).nodeId());
  }

  @Test
  void testComputeTopologicalOrderWithCycle() {
    WorkflowDefinition.Node n1 = new WorkflowDefinition.Node("n1", "t", null);
    WorkflowDefinition.Node n2 = new WorkflowDefinition.Node("n2", "t", null);
    WorkflowDefinition.Edge e1 = new WorkflowDefinition.Edge("n1", "n2");
    WorkflowDefinition.Edge e2 = new WorkflowDefinition.Edge("n2", "n1");
    WorkflowDefinition def = new WorkflowDefinition("d", List.of(n1, n2), List.of(e1, e2));

    Map<String, List<WorkflowDefinition.Node>> adj = Map.of("n1", List.of(n2), "n2", List.of(n1));
    Map<String, List<WorkflowDefinition.Node>> parents =
        Map.of("n1", List.of(n2), "n2", List.of(n1));

    assertThrows(
        IllegalArgumentException.class, () -> service.computeTopologicalOrder(def, adj, parents));
  }

  @Test
  void testComputeTopologicalOrderMultipleNodes() {
    WorkflowDefinition.Node n1 = new WorkflowDefinition.Node("n1", "t", null);
    WorkflowDefinition.Node n2 = new WorkflowDefinition.Node("n2", "t", null);
    WorkflowDefinition.Node n3 = new WorkflowDefinition.Node("n3", "t", null);
    WorkflowDefinition.Edge e1 = new WorkflowDefinition.Edge("n1", "n2");
    WorkflowDefinition.Edge e2 = new WorkflowDefinition.Edge("n2", "n3");
    WorkflowDefinition def = new WorkflowDefinition("d", List.of(n1, n2, n3), List.of(e1, e2));

    Map<String, List<WorkflowDefinition.Node>> adj =
        Map.of("n1", List.of(n2), "n2", List.of(n3), "n3", List.of());
    Map<String, List<WorkflowDefinition.Node>> parents =
        Map.of("n1", List.of(), "n2", List.of(n1), "n3", List.of(n2));

    List<WorkflowDefinition.Node> order = service.computeTopologicalOrder(def, adj, parents);
    assertEquals(3, order.size());
    assertEquals("n1", order.get(0).nodeId());
    assertEquals("n2", order.get(1).nodeId());
    assertEquals("n3", order.get(2).nodeId());
  }
}
