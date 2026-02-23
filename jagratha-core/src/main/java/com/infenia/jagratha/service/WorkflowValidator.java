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
package com.infenia.jagratha.service;

import com.infenia.jagratha.model.WorkflowDefinition;
import com.infenia.jagratha.model.WorkflowDefinition.Node;
import com.infenia.jagratha.plugin.PluginCategory;
import com.infenia.jagratha.plugin.WorkflowPlugin;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Service for validating workflow structural integrity and plugin configurations. */
@Service
@Validated
@RequiredArgsConstructor
public class WorkflowValidator {

  private final WorkflowRegistry registry;

  /**
   * Validate the structural integrity of a workflow definition.
   *
   * @param def the workflow definition
   * @return a Mono that completes if validation is successful
   */
  public Mono<Void> validate(@NotNull @Valid final WorkflowDefinition def) {
    final Set<String> targetIds =
        def.edges().stream().map(WorkflowDefinition.Edge::target).collect(Collectors.toSet());
    final Set<String> sourceIds =
        def.edges().stream().map(WorkflowDefinition.Edge::source).collect(Collectors.toSet());

    return validateEntryPoints(def, targetIds)
        .then(validateProcessors(def, targetIds, sourceIds))
        .then(validateEndpoints(def, sourceIds))
        .then(validateNoCycles(def))
        .then(validateNoOrphans(def, targetIds))
        .then(validatePluginConfigs(def));
  }

  private Mono<Void> validateEntryPoints(
      final WorkflowDefinition def, final Set<String> targetIds) {
    for (final Node node : def.nodes()) {
      final WorkflowPlugin plugin = registry.get(node.type());
      if (plugin == null) {
        return Mono.error(
            new IllegalArgumentException("Plugin not found for type: " + node.type()));
      }
      final boolean isEntryPoint = !targetIds.contains(node.nodeId());
      final boolean isTrigger = plugin.getCategory() == PluginCategory.TRIGGER;

      if (isEntryPoint) {
        if (!isTrigger) {
          return Mono.error(
              new IllegalArgumentException(
                  "Node " + node.nodeId() + " is an entry point but not a TRIGGER"));
        }
      } else if (isTrigger) {
        return Mono.error(
            new IllegalArgumentException(
                "Trigger node " + node.nodeId() + " cannot have incoming edges"));
      }
    }
    return Mono.empty();
  }

  private Mono<Void> validateProcessors(
      final WorkflowDefinition def, final Set<String> targetIds, final Set<String> sourceIds) {
    for (final Node node : def.nodes()) {
      final WorkflowPlugin plugin = registry.get(node.type());
      if (plugin != null
          && plugin.getCategory() == PluginCategory.PROCESSOR
          && (!targetIds.contains(node.nodeId()) || !sourceIds.contains(node.nodeId()))) {
        return Mono.error(
            new IllegalArgumentException(
                "Processor node " + node.nodeId() + " must have both incoming and outgoing edges"));
      }
    }
    return Mono.empty();
  }

  private Mono<Void> validateEndpoints(final WorkflowDefinition def, final Set<String> sourceIds) {
    for (final Node node : def.nodes()) {
      final WorkflowPlugin plugin = registry.get(node.type());
      if (plugin != null) {
        final boolean isEndpoint = !sourceIds.contains(node.nodeId());
        final boolean isTerminal = plugin.getCategory() == PluginCategory.TERMINAL;

        if (isEndpoint) {
          if (!isTerminal) {
            return Mono.error(
                new IllegalArgumentException(
                    "Node " + node.nodeId() + " is an endpoint but not a TERMINAL"));
          }
        } else if (isTerminal) {
          return Mono.error(
              new IllegalArgumentException(
                  "Terminal node " + node.nodeId() + " cannot have outgoing edges"));
        }
      }
    }
    return Mono.empty();
  }

  private Mono<Void> validateNoCycles(final WorkflowDefinition def) {
    if (hasCycles(def)) {
      return Mono.error(new IllegalArgumentException("Workflow DAG contains cycles"));
    }
    return Mono.empty();
  }

  private Mono<Void> validateNoOrphans(final WorkflowDefinition def, final Set<String> targetIds) {
    final Set<String> triggerIds =
        def.nodes().stream()
            .filter(
                n -> {
                  final WorkflowPlugin p = registry.get(n.type());
                  return p != null && p.getCategory() == PluginCategory.TRIGGER;
                })
            .map(Node::nodeId)
            .collect(Collectors.toSet());

    final Map<String, List<String>> adj = new ConcurrentHashMap<>();
    def.nodes().forEach(n -> adj.put(n.nodeId(), new ArrayList<>()));
    def.edges().forEach(e -> adj.get(e.source()).add(e.target()));

    final Set<String> reachable = new HashSet<>();
    for (final String triggerId : triggerIds) {
      dfs(triggerId, adj, reachable);
    }

    for (final Node node : def.nodes()) {
      if (!reachable.contains(node.nodeId())) {
        return Mono.error(
            new IllegalArgumentException(
                "Node " + node.nodeId() + " is not reachable from any trigger"));
      }
    }
    return Mono.empty();
  }

  private void dfs(
      final String nodeId, final Map<String, List<String>> adj, final Set<String> reachable) {
    if (reachable.contains(nodeId)) {
      return;
    }
    reachable.add(nodeId);
    final List<String> children = adj.get(nodeId);
    if (children != null) {
      for (final String child : children) {
        dfs(child, adj, reachable);
      }
    }
  }

  private Mono<Void> validatePluginConfigs(final WorkflowDefinition def) {
    return Flux.fromIterable(def.nodes())
        .flatMap(
            node -> {
              final WorkflowPlugin plugin = registry.get(node.type());
              return plugin.validateConfig(node.config());
            })
        .then();
  }

  private boolean hasCycles(final WorkflowDefinition def) {
    final Map<String, List<String>> adjacencyList = new ConcurrentHashMap<>();
    def.nodes().forEach(n -> adjacencyList.put(n.nodeId(), new ArrayList<>()));
    def.edges().forEach(e -> adjacencyList.get(e.source()).add(e.target()));

    final Set<String> visited = new HashSet<>();
    final Set<String> recStack = new HashSet<>();

    for (final String nodeId : adjacencyList.keySet()) {
      if (isCyclicUtil(nodeId, visited, recStack, adjacencyList)) {
        return true;
      }
    }
    return false;
  }

  private boolean isCyclicUtil(
      final String nodeId,
      final Set<String> visited,
      final Set<String> recStack,
      final Map<String, List<String>> adj) {
    if (recStack.contains(nodeId)) {
      return true;
    }
    if (visited.contains(nodeId)) {
      return false;
    }

    visited.add(nodeId);
    recStack.add(nodeId);

    final List<String> children = adj.get(nodeId);
    if (children != null) {
      for (final String child : children) {
        if (isCyclicUtil(child, visited, recStack, adj)) {
          return true;
        }
      }
    }

    recStack.remove(nodeId);
    return false;
  }
}
