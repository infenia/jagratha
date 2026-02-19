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
import com.infenia.jagratha.plugin.Message;
import com.infenia.jagratha.plugin.PluginCategory;
import com.infenia.jagratha.plugin.ProcessorPlugin;
import com.infenia.jagratha.plugin.TerminalPlugin;
import com.infenia.jagratha.plugin.TriggerPlugin;
import com.infenia.jagratha.plugin.WorkflowPlugin;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Orchestrator for executing reactive workflow DAGs. */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowOrchestrator {

  private final WorkflowRegistry registry;
  private final TaskTrackerService tracker;

  /**
   * Validate and initialize all plugins in the workflow.
   *
   * @param def the workflow definition
   * @return a Mono that completes if preparation is successful
   */
  public Mono<Void> prepareWorkflow(final WorkflowDefinition def) {
    return validateStructuralIntegrity(def)
        .thenMany(Flux.fromIterable(def.nodes()))
        .flatMap(
            node -> {
              final WorkflowPlugin plugin = registry.get(node.type());
              if (plugin == null) {
                return Mono.error(
                    new IllegalArgumentException("Plugin not found for type: " + node.type()));
              }
              return plugin.validateConfig(node.config()).then(plugin.initialize(node.config()));
            })
        .then();
  }

  /**
   * Execute the workflow.
   *
   * @param sessionId the session identifier
   * @param def the workflow definition
   * @return a Mono that completes when all branches of the workflow have finished
   */
  public Mono<Void> execute(final String sessionId, final WorkflowDefinition def) {
    final List<Node> triggers =
        def.nodes().stream()
            .filter(n -> registry.get(n.type()).getCategory() == PluginCategory.TRIGGER)
            .collect(Collectors.toList());

    final List<String> nodeIds = def.nodes().stream().map(Node::id).collect(Collectors.toList());
    tracker.startWorkflow(sessionId, nodeIds);

    return Flux.fromIterable(triggers)
        .flatMap(
            triggerNode -> {
              final TriggerPlugin trigger = (TriggerPlugin) registry.get(triggerNode.type());
              tracker.updateTaskStatus(sessionId, triggerNode.id(), "", "RUNNING");
              final Flux<Message> stream =
                  trigger
                      .start()
                      .doOnComplete(
                          () -> tracker.updateTaskStatus(sessionId, triggerNode.id(), "", "SUCCESS"))
                      .doOnError(
                          e -> tracker.updateTaskStatus(sessionId, triggerNode.id(), "", "FAILURE"));

              return chain(sessionId, stream, triggerNode, def);
            })
        .then()
        .doOnTerminate(() -> tracker.finishWorkflow(sessionId, "COMPLETED"));
  }

  private Mono<Void> chain(
      final String sessionId,
      final Flux<Message> stream,
      final Node currentNode,
      final WorkflowDefinition def) {
    final List<Node> children = getChildrenOf(currentNode.id(), def);

    if (children.isEmpty()) {
      return Mono.empty();
    }

    // Use publish().autoConnect(n) to broadcast the stream if there are multiple children
    final Flux<Message> broadcastStream = stream.publish().autoConnect(children.size());

    return Flux.fromIterable(children)
        .flatMap(
            child -> {
              final WorkflowPlugin plugin = registry.get(child.type());
              tracker.updateTaskStatus(sessionId, child.id(), "", "RUNNING");

              if (plugin instanceof ProcessorPlugin p) {
                final Flux<Message> processedStream =
                    p.process(broadcastStream)
                        .doOnComplete(
                            () -> tracker.updateTaskStatus(sessionId, child.id(), "", "SUCCESS"))
                        .doOnError(
                            e -> tracker.updateTaskStatus(sessionId, child.id(), "", "FAILURE"));
                return chain(sessionId, processedStream, child, def);
              } else if (plugin instanceof TerminalPlugin t) {
                return t.consume(broadcastStream)
                    .doOnSuccess(
                        v -> tracker.updateTaskStatus(sessionId, child.id(), "", "SUCCESS"))
                    .doOnError(e -> tracker.updateTaskStatus(sessionId, child.id(), "", "FAILURE"));
              }
              return Mono.empty();
            })
        .then();
  }

  private List<Node> getChildrenOf(final String nodeId, final WorkflowDefinition def) {
    final Set<String> childrenIds =
        def.edges().stream()
            .filter(e -> e.source().equals(nodeId))
            .map(WorkflowDefinition.Edge::target)
            .collect(Collectors.toSet());

    return def.nodes().stream().filter(n -> childrenIds.contains(n.id())).collect(Collectors.toList());
  }

  private Mono<Void> validateStructuralIntegrity(final WorkflowDefinition def) {
    // 1. Entry Points: All nodes with 0 incoming edges must be TRIGGER types.
    final Set<String> targetIds =
        def.edges().stream().map(WorkflowDefinition.Edge::target).collect(Collectors.toSet());
    for (final Node node : def.nodes()) {
      if (!targetIds.contains(node.id())) {
        final WorkflowPlugin plugin = registry.get(node.type());
        if (plugin != null && plugin.getCategory() != PluginCategory.TRIGGER) {
          return Mono.error(
              new IllegalArgumentException(
                  "Node " + node.id() + " is an entry point but not a TRIGGER"));
        }
      }
    }

    // 2. Continuity: A PROCESSOR must have at least one incoming and one outgoing edge.
    final Set<String> sourceIds =
        def.edges().stream().map(WorkflowDefinition.Edge::source).collect(Collectors.toSet());
    for (final Node node : def.nodes()) {
      final WorkflowPlugin plugin = registry.get(node.type());
      if (plugin != null && plugin.getCategory() == PluginCategory.PROCESSOR) {
        if (!targetIds.contains(node.id()) || !sourceIds.contains(node.id())) {
          return Mono.error(
              new IllegalArgumentException(
                  "Processor node " + node.id() + " must have both incoming and outgoing edges"));
        }
      }
    }

    // 3. Endpoints: Nodes with 0 outgoing edges must be TERMINAL types.
    for (final Node node : def.nodes()) {
      if (!sourceIds.contains(node.id())) {
        final WorkflowPlugin plugin = registry.get(node.type());
        if (plugin != null && plugin.getCategory() != PluginCategory.TERMINAL) {
          return Mono.error(
              new IllegalArgumentException(
                  "Node " + node.id() + " is an endpoint but not a TERMINAL"));
        }
      }
    }

    // 4. No Loops: Circular references are prohibited.
    if (hasCycles(def)) {
      return Mono.error(new IllegalArgumentException("Workflow DAG contains cycles"));
    }

    return Mono.empty();
  }

  private boolean hasCycles(final WorkflowDefinition def) {
    final Map<String, List<String>> adjacencyList = new HashMap<>();
    def.nodes().forEach(n -> adjacencyList.put(n.id(), new ArrayList<>()));
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
      final String i,
      final Set<String> visited,
      final Set<String> recStack,
      final Map<String, List<String>> adj) {
    if (recStack.contains(i)) {
      return true;
    }
    if (visited.contains(i)) {
      return false;
    }

    visited.add(i);
    recStack.add(i);

    final List<String> children = adj.get(i);
    for (final String c : children) {
      if (isCyclicUtil(c, visited, recStack, adj)) {
        return true;
      }
    }

    recStack.remove(i);
    return false;
  }
}
