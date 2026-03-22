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

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Dependency engine for tracking in-degree (number of pending parent nodes) per node.
 *
 * <p>When a node completes, its in-degree is decremented for all children. Nodes that reach
 * in-degree zero are ready for execution.
 */
@Slf4j
@Service
@SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
public class DependencyEngine {

  private final Map<String, Map<String, AtomicInteger>> inDegrees = new ConcurrentHashMap<>();
  private final Map<String, Map<String, List<String>>> childrenMap = new ConcurrentHashMap<>();

  /**
   * Initialize dependency tracking for an execution.
   *
   * @param executionId the execution identifier
   * @param parentMap the parent map: nodeId → list of parent nodeIds
   */
  public void initExecution(
      @NotBlank final String executionId, @NotNull final Map<String, List<String>> parentMap) {
    final Map<String, AtomicInteger> nodeInDegrees = new ConcurrentHashMap<>();
    final Map<String, List<String>> childrensByNode = new ConcurrentHashMap<>();

    // Initialize in-degrees from parent counts
    for (final Map.Entry<String, List<String>> entry : parentMap.entrySet()) {
      final String nodeId = entry.getKey();
      final List<String> parents = entry.getValue();
      nodeInDegrees.put(nodeId, new AtomicInteger(parents.size()));

      // Build reverse adjacency list
      for (final String parent : parents) {
        childrensByNode.computeIfAbsent(parent, unused -> new ArrayList<>()).add(nodeId);
      }
    }

    inDegrees.put(executionId, nodeInDegrees);
    childrenMap.put(executionId, childrensByNode);
  }

  /**
   * Mark a node as completed and get all nodes that are now ready (in-degree = 0).
   *
   * @param executionId the execution identifier
   * @param nodeId the node that completed
   * @return a Flux emitting node IDs that reached in-degree zero
   */
  public Flux<String> nodeCompleted(
      @NotBlank final String executionId, @NotBlank final String nodeId) {
    return Flux.defer(
        () -> {
          final Map<String, AtomicInteger> nodeDegrees = inDegrees.get(executionId);
          final Map<String, List<String>> children = childrenMap.get(executionId);

          if (nodeDegrees == null || children == null) {
            return Flux.empty();
          }

          final List<String> readyNodes = new ArrayList<>();
          final List<String> childrenOfNode = children.getOrDefault(nodeId, List.of());

          for (final String child : childrenOfNode) {
            final AtomicInteger childDegree = nodeDegrees.get(child);
            if (childDegree != null && childDegree.decrementAndGet() == 0) {
              readyNodes.add(child);
            }
          }

          return Flux.fromIterable(readyNodes);
        });
  }

  /**
   * Get the current in-degree for a node.
   *
   * @param executionId the execution identifier
   * @param nodeId the node identifier
   * @return the current in-degree
   */
  public int getState(@NotBlank final String executionId, @NotBlank final String nodeId) {
    final Map<String, AtomicInteger> nodeDegrees = inDegrees.get(executionId);
    if (nodeDegrees == null) {
      return -1;
    }
    final AtomicInteger degree = nodeDegrees.get(nodeId);
    return degree != null ? degree.get() : -1;
  }

  /**
   * Clean up all tracking state for an execution.
   *
   * @param executionId the execution identifier
   */
  public void cleanupExecution(@NotBlank final String executionId) {
    inDegrees.remove(executionId);
    childrenMap.remove(executionId);
  }
}
