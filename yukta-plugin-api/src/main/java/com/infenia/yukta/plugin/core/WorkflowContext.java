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
package com.infenia.yukta.plugin.core;

import java.util.List;

/**
 * Minimal graph context passed to plugins for workflow-level validation. Contains the edges
 * adjacent to a given node, allowing plugins to validate DAG constraints without direct access to
 * the full WorkflowDefinition.
 */
public record WorkflowContext(
    String nodeId, List<WorkflowEdge> outgoingEdges, List<WorkflowEdge> incomingEdges) {

  /**
   * Represents a single edge in the workflow graph.
   *
   * @param source source node ID
   * @param target target node ID
   * @param sourcePort source port name (optional)
   */
  public record WorkflowEdge(String source, String target, String sourcePort) {
    /** Compact constructor for immutability. */
    public WorkflowEdge {}
  }
}
