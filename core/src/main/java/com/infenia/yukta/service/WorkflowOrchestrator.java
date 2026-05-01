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

import com.infenia.yukta.model.workflow.NodeAssembler;
import com.infenia.yukta.model.workflow.ParentEdgeInfo;
import com.infenia.yukta.model.workflow.PreparedWorkflow;
import com.infenia.yukta.model.workflow.WorkflowDefinition;
import com.infenia.yukta.model.workflow.WorkflowDefinition.Node;
import com.infenia.yukta.model.workflow.WorkflowTemplate;
import com.infenia.yukta.plugin.core.PluginCategory;
import com.infenia.yukta.plugin.core.WorkflowPlugin;
import com.infenia.yukta.plugin.gateway.ControlBusGateway;
import com.infenia.yukta.plugin.gateway.ResultCollector;
import com.infenia.yukta.plugin.message.DefaultMessage;
import com.infenia.yukta.plugin.message.Message;
import com.infenia.yukta.plugin.message.control.ControlError;
import com.infenia.yukta.plugin.store.MessageStore;
import com.infenia.yukta.plugin.store.NodeCheckpointStore;
import com.infenia.yukta.plugin.type.ProcessorPlugin;
import com.infenia.yukta.plugin.type.TerminalPlugin;
import com.infenia.yukta.plugin.type.TriggerPlugin;
import com.infenia.yukta.service.control.ExecutionControl;
import com.infenia.yukta.service.control.factory.ExecutionControlFactory;
import com.infenia.yukta.service.control.store.ExecutionControlRegistry;
import com.infenia.yukta.service.orchestrator.AssemblyContext;
import com.infenia.yukta.service.orchestrator.ExecutionContextBuilder;
import com.infenia.yukta.service.orchestrator.HeartbeatBuilder;
import com.infenia.yukta.service.orchestrator.ResourceManagementBuilder;
import com.infenia.yukta.service.orchestrator.StreamBuilder;
import com.infenia.yukta.service.orchestrator.compiler.WorkflowCompiler;
import com.infenia.yukta.service.orchestrator.stream.StreamTopologyDecorator;
import com.infenia.yukta.service.orchestrator.strategy.NodeAssemblerStrategy;
import com.infenia.yukta.service.session.SessionConfigStore;
import com.infenia.yukta.validation.SessionId;
import com.infenia.yukta.validation.WorkflowId;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Scheduler;
import reactor.util.context.Context;

/** Orchestrator for executing reactive workflow DAGs. */
@Slf4j
@Service
@Validated
@SuppressWarnings({
  "PMD.ExcessiveImports",
  "PMD.CouplingBetweenObjects",
  "PMD.LawOfDemeter",
  "PMD.TooManyMethods",
  "PMD.LongVariable",
  "PMD.ExcessiveParameterList"
})
public class WorkflowOrchestrator {

  private static final int BUFFER_SIZE = 1024;
  private static final Sinks.EmitFailureHandler RETRY_HANDLER =
      (signalType, emitResult) -> {
        if (emitResult == Sinks.EmitResult.FAIL_NON_SERIALIZED) {
          // Yield carrier thread for 10 microseconds to allow concurrent emission to complete
          LockSupport.parkNanos(10_000);
          return true;
        }
        return false;
      };
  private static final long GLOBAL_TIMEOUT = 3600L;
  private static final long REF_COUNT_TIMEOUT = 30L;
  private static final String STATUS_RUNNING = "RUNNING";
  private static final String STATUS_SUCCESS = "SUCCESS";
  private static final String STATUS_FAILURE = "FAILURE";
  private static final String STATUS_ERROR = "ERROR";
  private static final String DEFAULT_TASK_ID = "default";

  private static final String CTX_SESSION_ID = "sessionId";
  private static final String CTX_WORKFLOW_ID = "workflowId";
  private static final String CTX_EXECUTION_ID = "executionId";
  private static final String CTX_PAYLOAD = "payload";
  private static final String CTX_NODE_ID = "nodeId";

  // Logging key constants
  private static final String LOG_KEY_SESSION_ID = "sessionId";
  private static final String LOG_KEY_WORKFLOW_ID = "workflowId";
  private static final String LOG_KEY_EXECUTION_ID = "executionId";
  private static final String LOG_KEY_NODE_ID = "nodeId";
  private static final String LOG_KEY_PLUGIN_TYPE = "pluginType";
  private static final String LOG_KEY_NUM_NODES = "numNodes";
  private static final String LOG_KEY_NODE_IDS = "nodeIds";
  private static final String LOG_KEY_SOURCE = "source";
  private static final String LOG_KEY_TARGET = "target";
  private static final String LOG_KEY_PORT = "port";
  private static final String LOG_KEY_PLUGIN_COUNT = "pluginCount";
  private static final String LOG_KEY_NODE_CATEGORY = "nodeCategory";
  private static final String LOG_KEY_PARENT_EDGE_COUNT = "parentEdgeCount";
  private static final String LOG_KEY_NODE_COUNT = "nodeCount";
  private static final String LOG_KEY_TIMEOUT_SECONDS = "timeoutSeconds";
  private static final String LOG_KEY_TERMINAL_COUNT = "terminalCount";
  private static final String LOG_KEY_HEARTBEAT_INTERVAL = "heartbeatInterval";
  private static final String LOG_KEY_CONNECTOR_COUNT = "connectorCount";
  private static final String LOG_KEY_DISPOSABLE_COUNT = "disposableCount";

  private final WorkflowRegistry registry;
  private final TaskTrackerService tracker;
  private final WorkflowValidator validator;
  private final TopologicalSortService topologicalSortService;
  private final SessionConfigStore configService;
  private final MessageStore messageStore;
  private final ControlBusGateway controlBusGateway;
  private final Scheduler virtualThreadScheduler;
  private final ExecutionControlRegistry executionControlRegistry;
  private final ExecutionControlFactory executionControlFactory;
  private final NodeCheckpointStore checkpointStore;
  private final StreamTopologyDecorator streamTopologyDecorator;
  private final WorkflowCompiler compiler;

  /**
   * Constructs a new WorkflowOrchestrator.
   *
   * @param registry the workflow registry
   * @param tracker the task tracker service
   * @param validator the workflow validator
   * @param topologicalSortService the topological sort service
   * @param configService the session config store
   * @param messageStore the message store for auditing
   * @param controlBusGateway the control bus gateway
   * @param virtualThreadScheduler the scheduler for virtual threads
   * @param executionControlRegistry the registry for live executions
   * @param executionControlFactory the factory for creating execution controls
   * @param checkpointStore the node checkpoint store for restart support
   * @param streamTopologyDecorator the stream topology decorator for message flow management
   * @param compiler the workflow compiler for template and assembler compilation
   */
  @Autowired
  @SuppressFBWarnings("EI_EXPOSE_REP2")
  public WorkflowOrchestrator(
      final WorkflowRegistry registry,
      final TaskTrackerService tracker,
      final WorkflowValidator validator,
      final TopologicalSortService topologicalSortService,
      final SessionConfigStore configService,
      @Nullable final MessageStore messageStore,
      final ControlBusGateway controlBusGateway,
      @Qualifier("virtualThreadScheduler") final Scheduler virtualThreadScheduler,
      final ExecutionControlRegistry executionControlRegistry,
      final ExecutionControlFactory executionControlFactory,
      final NodeCheckpointStore checkpointStore,
      final StreamTopologyDecorator streamTopologyDecorator,
      final WorkflowCompiler compiler) {
    this.registry = registry;
    this.tracker = tracker;
    this.validator = validator;
    this.topologicalSortService = topologicalSortService;
    this.configService = configService;
    this.messageStore = messageStore;
    this.controlBusGateway = controlBusGateway;
    this.virtualThreadScheduler = virtualThreadScheduler;
    this.executionControlRegistry = executionControlRegistry;
    this.executionControlFactory = executionControlFactory;
    this.checkpointStore = checkpointStore;
    this.streamTopologyDecorator = streamTopologyDecorator;
    this.compiler = compiler;
  }

  /**
   * Prepares a workflow for execution.
   *
   * @param def the workflow definition
   * @return a Mono containing the prepared workflow
   */
  public Mono<PreparedWorkflow> prepareWorkflow(@NotNull @Valid final WorkflowDefinition def) {
    final int numNodes = def.nodes().size();
    log.atDebug()
        .addKeyValue(LOG_KEY_NUM_NODES, numNodes)
        .addKeyValue(LOG_KEY_NODE_IDS, def.nodes().stream().map(Node::nodeId).toList())
        .log("Preparing workflow with {} nodes", numNodes);

    final Map<String, List<Node>> adjacencyList = new ConcurrentHashMap<>(numNodes);
    final Map<String, List<Node>> parentsList = new ConcurrentHashMap<>(numNodes);
    final Map<String, WorkflowPlugin> pluginCache = new ConcurrentHashMap<>(numNodes);
    final Map<String, Node> nodeMap = new ConcurrentHashMap<>(numNodes);

    def.nodes()
        .forEach(
            node -> {
              nodeMap.put(node.nodeId(), node);
              adjacencyList.put(node.nodeId(), new ArrayList<>());
              parentsList.put(node.nodeId(), new ArrayList<>());
              final WorkflowPlugin plugin = registry.get(node.type());
              if (plugin != null) {
                pluginCache.put(node.nodeId(), plugin);
                log.atTrace()
                    .addKeyValue(LOG_KEY_NODE_ID, node.nodeId())
                    .addKeyValue(LOG_KEY_PLUGIN_TYPE, node.type())
                    .log("Cached plugin for node");
              } else {
                log.atWarn()
                    .addKeyValue(LOG_KEY_NODE_ID, node.nodeId())
                    .addKeyValue(LOG_KEY_PLUGIN_TYPE, node.type())
                    .log("Plugin not found for node type");
              }
            });

    def.edges()
        .forEach(
            edge -> {
              adjacencyList.get(edge.source()).add(nodeMap.get(edge.target()));
              parentsList.get(edge.target()).add(nodeMap.get(edge.source()));
              log.atTrace()
                  .addKeyValue(LOG_KEY_SOURCE, edge.source())
                  .addKeyValue(LOG_KEY_TARGET, edge.target())
                  .addKeyValue(LOG_KEY_PORT, edge.sourcePort())
                  .log("Added workflow edge");
            });

    return validator
        .validate(def)
        .doOnError(e -> log.atError().setCause(e).log("Workflow validation failed"))
        .then(
            Flux.fromIterable(def.nodes())
                .flatMap(
                    node -> {
                      final WorkflowPlugin plugin = pluginCache.get(node.nodeId());
                      if (plugin == null) {
                        log.atError()
                            .addKeyValue(LOG_KEY_NODE_ID, node.nodeId())
                            .addKeyValue(LOG_KEY_PLUGIN_TYPE, node.type())
                            .log("Plugin not found during initialization");
                        return Mono.error(
                            new IllegalArgumentException("Plugin not found: " + node.type()));
                      }
                      return plugin
                          .initialize(node.config())
                          .doOnSuccess(
                              v -> {
                                log.atDebug()
                                    .addKeyValue(LOG_KEY_NODE_ID, node.nodeId())
                                    .addKeyValue(LOG_KEY_PLUGIN_TYPE, node.type())
                                    .log("Plugin initialized successfully");
                                controlBusGateway.registerPlugin(node.nodeId(), plugin);
                              })
                          .then(
                              controlBusGateway.emit(
                                  DefaultMessage.create(null, "Node Online")
                                      .withSourceNodeId(node.nodeId())
                                      .withControl(true)));
                    })
                .then())
        .then(
            Mono.fromCallable(
                () -> {
                  final List<Node> topologicalOrder =
                      topologicalSortService.computeTopologicalOrder(
                          def, adjacencyList, parentsList);
                  final WorkflowTemplate template =
                      compiler.compileTemplate(def, parentsList, pluginCache, topologicalOrder);
                  return new PreparedWorkflow(
                      def, adjacencyList, parentsList, pluginCache, topologicalOrder, template);
                }))
        .onErrorResume(
            e -> {
              log.atError()
                  .setCause(e)
                  .addKeyValue(LOG_KEY_PLUGIN_COUNT, pluginCache.size())
                  .log("Workflow preparation failed, shutting down plugins");
              return Flux.fromIterable(pluginCache.entrySet())
                  .flatMap(
                      entry -> {
                        final String nodeId = entry.getKey();
                        final WorkflowPlugin plugin = entry.getValue();

                        log.atDebug()
                            .addKeyValue(LOG_KEY_NODE_ID, nodeId)
                            .log("Unregistering and shutting down plugin");
                        controlBusGateway.unregisterPlugin(nodeId);

                        final Node node = nodeMap.get(nodeId);
                        final Mono<Void> shutdown = plugin.shutdown(node.config());

                        return (shutdown != null ? shutdown : Mono.<Void>empty())
                            .onErrorResume(
                                ex -> {
                                  log.atWarn()
                                      .setCause(ex)
                                      .addKeyValue(LOG_KEY_NODE_ID, nodeId)
                                      .log("Error during plugin shutdown");
                                  return Mono.empty();
                                });
                      })
                  .then(Mono.error(e));
            });
  }

  /**
   * Executes a workflow.
   *
   * @param sessionId the session ID
   * @param workflowId the workflow ID
   * @param executionId the execution ID
   * @param prepared the prepared workflow
   * @param payload the initial payload
   * @return a Mono that completes when the workflow execution is finished
   */
  public Mono<Void> execute(
      @SessionId final String sessionId,
      @WorkflowId final String workflowId,
      @NotBlank final String executionId,
      @NotNull @Valid final PreparedWorkflow prepared,
      @NotEmpty final Map<String, Object> payload) {

    log.atInfo()
        .addKeyValue(LOG_KEY_SESSION_ID, sessionId)
        .addKeyValue(LOG_KEY_WORKFLOW_ID, workflowId)
        .addKeyValue(LOG_KEY_EXECUTION_ID, executionId)
        .addKeyValue(LOG_KEY_NODE_COUNT, prepared.topologicalOrder().size())
        .log("Starting workflow execution");

    final ExecutionControl control =
        executionControlFactory.create(sessionId, workflowId, executionId, prepared, payload);
    executionControlRegistry.register(control);

    final Mono<Void> execution = prepared.template().instantiate(executionId, payload);

    return Mono.firstWithSignal(
            execution, control.immediateStopSink().asMono(), control.safeStopSink().asMono())
        .doFinally(
            signal -> {
              executionControlRegistry.unregister(executionId);
              checkpointStore.clear(executionId);
            })
        .doOnSuccess(
            v ->
                log.atInfo()
                    .addKeyValue(LOG_KEY_SESSION_ID, sessionId)
                    .addKeyValue(LOG_KEY_WORKFLOW_ID, workflowId)
                    .addKeyValue(LOG_KEY_EXECUTION_ID, executionId)
                    .log("Workflow execution completed successfully"))
        .doOnError(
            e ->
                log.atError()
                    .setCause(e)
                    .addKeyValue(LOG_KEY_SESSION_ID, sessionId)
                    .addKeyValue(LOG_KEY_WORKFLOW_ID, workflowId)
                    .addKeyValue(LOG_KEY_EXECUTION_ID, executionId)
                    .log("Workflow execution failed"))
        .contextWrite(
            Context.of(
                CTX_SESSION_ID, sessionId,
                CTX_WORKFLOW_ID, workflowId,
                CTX_EXECUTION_ID, executionId));
  }

  /**
   * Executes a workflow starting from a specific node, replaying checkpoint messages from its
   * direct parents.
   *
   * <p>Nodes that appear before {@code restartNodeId} in topological order are replaced with bypass
   * assemblers. Direct parents of the restart node emit their stored checkpoint message; all other
   * predecessors emit an empty stream. The restart node and its successors run normally.
   *
   * @param sessionId the session identifier
   * @param workflowId the workflow identifier
   * @param previousExecutionId the execution whose checkpoints to replay
   * @param newExecutionId the new execution identifier
   * @param prepared the prepared workflow
   * @param restartNodeId the node from which to resume execution
   * @param parentCheckpoints map of parentNodeId to the checkpoint message to replay
   * @return a Mono that completes when the restarted execution finishes
   */
  @SuppressWarnings({"PMD.UseConcurrentHashMap", "PMD.UseObjectForClearerAPI"})
  public Mono<Void> restartFromNode(
      final String sessionId,
      final String workflowId,
      final String previousExecutionId,
      final String newExecutionId,
      final PreparedWorkflow prepared,
      final String restartNodeId,
      final Map<String, Message<?>> parentCheckpoints) {

    final List<Node> topologicalOrder = prepared.topologicalOrder();
    final int nodeCount = topologicalOrder.size();
    final Map<String, Integer> nodeToIndex = new HashMap<>(nodeCount);
    for (int i = 0; i < nodeCount; i++) {
      nodeToIndex.put(topologicalOrder.get(i).nodeId(), i);
    }

    final int restartIndex = nodeToIndex.getOrDefault(restartNodeId, 0);
    final NodeAssembler[] assemblers =
        compiler.compileAssemblers(
            prepared.definition(),
            prepared.parentsList(),
            prepared.pluginCache(),
            topologicalOrder);

    // Replace pre-restart assemblers with bypass or checkpoint-replay variants
    for (int i = 0; i < restartIndex; i++) {
      final Node node = topologicalOrder.get(i);
      final int idx = i;
      final Message<?> checkpoint = parentCheckpoints.get(node.nodeId());
      if (checkpoint != null) {
        assemblers[idx] = context -> context.streams()[idx] = Flux.just(checkpoint);
      } else {
        assemblers[idx] = context -> context.streams()[idx] = Flux.empty();
      }
    }

    final List<String> nodeIds = topologicalOrder.stream().map(Node::nodeId).toList();

    final ExecutionControl control =
        executionControlFactory.create(sessionId, workflowId, newExecutionId, prepared, Map.of());
    executionControlRegistry.register(control);

    return tracker
        .startWorkflow(newExecutionId, sessionId, workflowId, nodeIds)
        .then(
            compiler.executeTemplate(
                newExecutionId, Map.of(), nodeCount, assemblers, sessionId, workflowId, nodeIds))
        .as(mono -> Mono.firstWithSignal(mono, control.safeStopSink().asMono()))
        .doFinally(
            signal -> {
              executionControlRegistry.unregister(newExecutionId);
              checkpointStore.clear(newExecutionId);
            })
        .doOnSuccess(
            v ->
                log.atInfo()
                    .addKeyValue(LOG_KEY_SESSION_ID, sessionId)
                    .addKeyValue(LOG_KEY_WORKFLOW_ID, workflowId)
                    .addKeyValue(LOG_KEY_EXECUTION_ID, newExecutionId)
                    .log("RestartFromNode execution completed"))
        .doOnError(
            e ->
                log.atError()
                    .setCause(e)
                    .addKeyValue(LOG_KEY_SESSION_ID, sessionId)
                    .addKeyValue(LOG_KEY_WORKFLOW_ID, workflowId)
                    .addKeyValue(LOG_KEY_EXECUTION_ID, newExecutionId)
                    .log("RestartFromNode execution failed"));
  }

}
