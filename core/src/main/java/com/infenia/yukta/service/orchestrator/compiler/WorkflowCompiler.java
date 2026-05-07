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
package com.infenia.yukta.service.orchestrator.compiler;

import com.infenia.yukta.model.workflow.NodeAssembler;
import com.infenia.yukta.model.workflow.ParentEdgeInfo;
import com.infenia.yukta.model.workflow.WorkflowTemplate;
import com.infenia.yukta.model.workflow.WorkflowEdge;
import com.infenia.yukta.model.workflow.WorkflowNode;
import com.infenia.yukta.plugin.core.WorkflowPlugin;
import com.infenia.yukta.plugin.gateway.ControlBusGateway;
import com.infenia.yukta.plugin.message.Message;
import com.infenia.yukta.service.TaskTrackerService;
import com.infenia.yukta.service.control.store.ExecutionControlRegistry;
import com.infenia.yukta.service.orchestrator.AssemblyContext;
import com.infenia.yukta.service.orchestrator.HeartbeatBuilder;
import com.infenia.yukta.service.orchestrator.ResourceManagementBuilder;
import com.infenia.yukta.service.orchestrator.strategy.NodeAssemblerStrategy;
import com.infenia.yukta.service.session.SessionConfigStore;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowCompiler {

  private static final int BUFFER_SIZE = 1024;
  private static final long REF_COUNT_TIMEOUT = 30L;

  private static final String CTX_SESSION_ID = "sessionId";
  private static final String CTX_WORKFLOW_ID = "workflowId";
  private static final String CTX_PAYLOAD = "payload";

  private static final String LOG_KEY_EXECUTION_ID = "executionId";
  private static final String LOG_KEY_NODE_COUNT = "nodeCount";
  private static final String LOG_KEY_NODE_IDS = "nodeIds";
  private static final String LOG_KEY_HEARTBEAT_INTERVAL = "heartbeatInterval";
  private static final String LOG_KEY_TERMINAL_COUNT = "terminalCount";
  private static final String LOG_KEY_NODE_ID = "nodeId";
  private static final String LOG_KEY_PLUGIN_TYPE = "pluginType";

  private final TaskTrackerService tracker;
  private final ControlBusGateway controlBusGateway;
  private final Scheduler virtualThreadScheduler;
  private final Duration heartbeatInterval;
  private final SessionConfigStore configService;
  private final ExecutionControlRegistry executionControlRegistry;
  private final List<NodeAssemblerStrategy> assemblerStrategies;

  /**
   * Compiles a workflow template for high-performance execution.
   *
   * @param edges the workflow edges
   * @param parentsList map of nodeId to parent nodes
   * @param pluginCache map of nodeId to initialized plugin instances
   * @param topologicalOrder list of nodes in topological order
   * @return the compiled workflow template
   */
  public WorkflowTemplate compileTemplate(
      final List<WorkflowEdge> edges,
      final Map<String, List<WorkflowNode>> parentsList,
      final Map<String, WorkflowPlugin> pluginCache,
      final List<WorkflowNode> topologicalOrder) {

    final int nodeCount = topologicalOrder.size();
    final NodeAssembler[] assemblers =
        compileAssemblers(edges, parentsList, pluginCache, topologicalOrder);

    final List<String> nodeIds = topologicalOrder.stream().map(WorkflowNode::nodeId).toList();

    return (executionId, payload) ->
        Mono.deferContextual(
            ctx ->
                tracker
                    .startWorkflow(
                        executionId, ctx.get(CTX_SESSION_ID), ctx.get(CTX_WORKFLOW_ID), nodeIds)
                    .then(
                        Mono.defer(
                            () ->
                                executeTemplate(
                                    executionId,
                                    payload,
                                    nodeCount,
                                    assemblers,
                                    ctx.get(CTX_SESSION_ID),
                                    ctx.get(CTX_WORKFLOW_ID),
                                    nodeIds)))
                    .contextWrite(c -> c.put(CTX_PAYLOAD, payload)));
  }

  /**
   * Builds an assembler array for the given workflow in topological order.
   *
   * @param edges the workflow edges
   * @param parentsList map of nodeId to parent nodes
   * @param pluginCache map of nodeId to initialized plugin instances
   * @param topologicalOrder list of nodes in topological order
   * @return the node assembler array, indexed by topological position
   */
  public NodeAssembler[] compileAssemblers(
      final List<WorkflowEdge> edges,
      final Map<String, List<WorkflowNode>> parentsList,
      final Map<String, WorkflowPlugin> pluginCache,
      final List<WorkflowNode> topologicalOrder) {

    final int nodeCount = topologicalOrder.size();
    final Map<String, Integer> nodeToIndex = new ConcurrentHashMap<>(nodeCount);
    for (int i = 0; i < nodeCount; i++) {
      nodeToIndex.put(topologicalOrder.get(i).nodeId(), i);
    }

    final NodeAssembler[] assemblers = new NodeAssembler[nodeCount];
    for (int i = 0; i < nodeCount; i++) {
      final WorkflowNode node = topologicalOrder.get(i);
      assemblers[i] = createNodeAssembler(edges, node, parentsList, pluginCache, nodeToIndex);
    }
    return assemblers;
  }

  /**
   * Internal execution logic for a compiled template.
   *
   * @param executionId the execution ID
   * @param payload the initial payload
   * @param nodeCount the number of nodes in the workflow
   * @param assemblers the node assemblers
   * @param sessionId the session ID
   * @param workflowId the workflow ID
   * @param nodeIds the list of node IDs
   * @return a Mono that completes when execution is finished
   */
  public Mono<Void> executeTemplate(
      final String executionId,
      final Map<String, Object> payload,
      final int nodeCount,
      final NodeAssembler[] assemblers,
      final String sessionId,
      final String workflowId,
      final List<String> nodeIds) {

    log.atDebug()
        .addKeyValue(LOG_KEY_EXECUTION_ID, executionId)
        .addKeyValue(LOG_KEY_NODE_COUNT, nodeCount)
        .addKeyValue(LOG_KEY_NODE_IDS, nodeIds)
        .log("Executing workflow template with {} nodes", nodeCount);

    @SuppressWarnings("unchecked")
    final Flux<Message<?>>[] streams = new Flux[nodeCount];
    final List<Mono<Void>> terminals = new ArrayList<>(nodeCount);
    final List<Disposable> disposables = new ArrayList<>(nodeCount);
    final List<Runnable> connectors = new ArrayList<>(nodeCount);

    final var control =
        executionControlRegistry
            .findByExecutionId(executionId)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "ExecutionControl not registered for execution: " + executionId));

    final AssemblyContext context =
        new AssemblyContext(
            executionId,
            sessionId,
            workflowId,
            payload,
            control,
            streams,
            terminals,
            disposables,
            connectors);

    for (final NodeAssembler assembler : assemblers) {
      assembler.assemble(context);
    }

    log.atTrace()
        .addKeyValue(LOG_KEY_EXECUTION_ID, executionId)
        .addKeyValue(LOG_KEY_TERMINAL_COUNT, terminals.size())
        .log("Node assembly complete");

    // Setup heartbeats and statistics
    log.atDebug()
        .addKeyValue(LOG_KEY_EXECUTION_ID, executionId)
        .addKeyValue(LOG_KEY_NODE_COUNT, nodeIds.size())
        .addKeyValue(LOG_KEY_HEARTBEAT_INTERVAL, heartbeatInterval.toMillis())
        .log("Starting heartbeat and statistics emission for {} nodes", nodeIds.size());

    final HeartbeatBuilder heartbeatBuilder =
        new HeartbeatBuilder(controlBusGateway, heartbeatInterval, virtualThreadScheduler);
    final List<Disposable> heartbeatDisposables =
        heartbeatBuilder
            .forNodes(nodeIds)
            .withHeartbeatInterval(heartbeatInterval)
            .withStatisticsInterval(heartbeatInterval.multipliedBy(2))
            .build();
    disposables.addAll(heartbeatDisposables);

    // Execute with resource management
    return new ResourceManagementBuilder(tracker, configService, virtualThreadScheduler)
        .withDisposables(disposables)
        .withTerminals(terminals)
        .withConnectors(connectors)
        .withExecutionTimeout(sessionId, executionId)
        .build();
  }

  /**
   * Creates a NodeAssembler for a specific node.
   *
   * @param edges the workflow edges
   * @param node the node to assemble
   * @param parentsList map of nodeId to parent nodes
   * @param pluginCache map of nodeId to initialized plugin instances
   * @param nodeToIndex map of nodeId to stream array index
   * @return the NodeAssembler
   */
  private NodeAssembler createNodeAssembler(
      final List<WorkflowEdge> edges,
      final WorkflowNode node,
      final Map<String, List<WorkflowNode>> parentsList,
      final Map<String, WorkflowPlugin> pluginCache,
      final Map<String, Integer> nodeToIndex) {

    final WorkflowPlugin plugin = pluginCache.get(node.nodeId());
    final Duration nodeTimeout = getNodeTimeout(node, plugin);
    final int bufferSize = getBufferSize(node, plugin);
    final boolean hasParents = !parentsList.get(node.nodeId()).isEmpty();
    final int nodeIndex = nodeToIndex.get(node.nodeId());

    final ParentEdgeInfo[] parentEdges =
        edges.stream()
            .filter(e -> e.target().equals(node.nodeId()))
            .map(e -> new ParentEdgeInfo(nodeToIndex.get(e.source()), e.source(), e.sourcePort()))
            .toArray(ParentEdgeInfo[]::new);

    for (final NodeAssemblerStrategy strategy : assemblerStrategies) {
      if (strategy.supports(plugin, hasParents)) {
        return strategy.createAssembler(
            node, plugin, nodeTimeout, nodeIndex, bufferSize, parentEdges);
      }
    }

    log.atWarn()
        .addKeyValue(LOG_KEY_NODE_ID, node.nodeId())
        .addKeyValue(LOG_KEY_PLUGIN_TYPE, node.type())
        .log("Unknown plugin type, creating no-op assembler");
    return context -> {};
  }

  /**
   * Gets the buffer size for a node.
   *
   * @param node the node
   * @param plugin the plugin
   * @return the buffer size
   */
  private int getBufferSize(final WorkflowNode node, final WorkflowPlugin plugin) {
    final Object bufferVal = node.config().get("bufferSize");
    final int result;
    if (bufferVal instanceof Number numValue && numValue.intValue() > 0) {
      result = numValue.intValue();
    } else {
        result = plugin != null ? plugin.getDefaultBufferSize() : BUFFER_SIZE;
    }
    return result;
  }

  /**
   * Gets the timeout for a node.
   *
   * @param node the node
   * @param plugin the plugin
   * @return the timeout duration
   */
  private Duration getNodeTimeout(final WorkflowNode node, final WorkflowPlugin plugin) {
    final Object timeoutVal = node.config().get("timeoutSeconds");

    final Duration result;
    if (timeoutVal instanceof Number numValue && numValue.longValue() > 0) {
      result = Duration.ofSeconds(numValue.longValue());
    } else {
      final Duration defaultTimeout = plugin != null ? plugin.getDefaultTimeout() : null;
      if (defaultTimeout != null) {
        result = defaultTimeout;
      } else {
        result = Duration.ofSeconds(REF_COUNT_TIMEOUT);
      }
    }
    return result;
  }
}
