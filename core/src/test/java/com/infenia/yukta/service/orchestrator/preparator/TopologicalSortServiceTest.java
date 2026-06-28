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
package com.infenia.yukta.service.orchestrator.preparator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infenia.yukta.model.workflow.WorkflowNode;
import java.util.List;
import java.util.Map;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link TopologicalSortService}. */
@NoArgsConstructor
@SuppressWarnings("PMD.ShortVariable")
class TopologicalSortServiceTest {

  /** Instance under test. */
  private TopologicalSortService sortService;

  @BeforeEach
  void setUp() {
    sortService = new TopologicalSortService();
  }

  @Test
  void testSimpleLinearSort() {
    final WorkflowNode n1 = new WorkflowNode("n1", "t", Map.of());
    final WorkflowNode n2 = new WorkflowNode("n2", "t", Map.of());

    final List<WorkflowNode> nodes = List.of(n1, n2);
    final Map<String, List<WorkflowNode>> adj = Map.of("n1", List.of(n2), "n2", List.of());
    final Map<String, List<WorkflowNode>> parents = Map.of("n1", List.of(), "n2", List.of(n1));

    final List<WorkflowNode> order = sortService.computeTopologicalOrder(nodes, adj, parents);

    assertThat(order).hasSize(2);
    assertThat(order.get(0).nodeId()).isEqualTo("n1");
    assertThat(order.get(1).nodeId()).isEqualTo("n2");
  }

  @Test
  void testCycleDetection() {
    final WorkflowNode n1 = new WorkflowNode("n1", "t", Map.of());
    final WorkflowNode n2 = new WorkflowNode("n2", "t", Map.of());

    final List<WorkflowNode> nodes = List.of(n1, n2);
    final Map<String, List<WorkflowNode>> adj = Map.of("n1", List.of(n2), "n2", List.of(n1));
    final Map<String, List<WorkflowNode>> parents = Map.of("n1", List.of(n2), "n2", List.of(n1));

    assertThatThrownBy(() -> sortService.computeTopologicalOrder(nodes, adj, parents))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void testBranchedSort() {
    final WorkflowNode n1 = new WorkflowNode("n1", "t", Map.of());
    final WorkflowNode n2 = new WorkflowNode("n2", "t", Map.of());
    final WorkflowNode n3 = new WorkflowNode("n3", "t", Map.of());

    final List<WorkflowNode> nodes = List.of(n1, n2, n3);
    final Map<String, List<WorkflowNode>> adj =
        Map.of("n1", List.of(n2, n3), "n2", List.of(), "n3", List.of());
    final Map<String, List<WorkflowNode>> parents =
        Map.of("n1", List.of(), "n2", List.of(n1), "n3", List.of(n1));

    final List<WorkflowNode> order = sortService.computeTopologicalOrder(nodes, adj, parents);

    assertThat(order).hasSize(3);
    assertThat(order.get(0).nodeId()).isEqualTo("n1");
  }
}
