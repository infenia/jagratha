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
import com.infenia.yukta.service.control.store.ExecutionControlRegistry;
import com.infenia.yukta.service.control.valve.ReactiveControlValve;
import com.infenia.yukta.service.orchestrator.AssemblyContext;
import com.infenia.yukta.service.orchestrator.ExecutionContextBuilder;
import com.infenia.yukta.service.orchestrator.HeartbeatBuilder;
import com.infenia.yukta.service.orchestrator.ResourceManagementBuilder;
import com.infenia.yukta.service.orchestrator.StreamBuilder;
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
  private final Duration heartbeatInterval;
  private final Scheduler virtualThreadScheduler;
  private final ExecutionControlRegistry executionControlRegistry;
  private final NodeCheckpointStore checkpointStore;

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
   * @param heartbeatInterval the heartbeat interval duration
   * @param virtualThreadScheduler the scheduler for virtual threads
   * @param executionControlRegistry the registry for live executions
   * @param checkpointStore the node checkpoint store for restart support
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
      final Duration heartbeatInterval,
      @Qualifier("virtualThreadScheduler") final Scheduler virtualThreadScheduler,
      final ExecutionControlRegistry executionControlRegistry,
      final NodeCheckpointStore checkpointStore) {
    this.registry = registry;
    this.tracker = tracker;
    this.validator = validator;
    this.topologicalSortService = topologicalSortService;
    this.configService = configService;
    this.messageStore = messageStore;
    this.controlBusGateway = controlBusGateway;
    this.heartbeatInterval = heartbeatInterval;
    this.virtualThreadScheduler = virtualThreadScheduler;
    this.executionControlRegistry = executionControlRegistry;
    this.checkpointStore = checkpointStore;
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
                      compileTemplate(def, parentsList, pluginCache, topologicalOrder);
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
        createExecutionControl(sessionId, workflowId, executionId, prepared, payload);
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
   * Builds an assembler array for the given workflow in topological order.
   *
   * @param def the workflow definition
   * @param parentsList map of nodeId to parent nodes
   * @param pluginCache map of nodeId to initialized plugin instances
   * @param topologicalOrder list of nodes in topological order
   * @return the node assembler array, indexed by topological position
   */
  @SuppressWarnings("PMD.UseConcurrentHashMap")
  /* package */ NodeAssembler[] compileAssemblers(
      final WorkflowDefinition def,
      final Map<String, List<Node>> parentsList,
      final Map<String, WorkflowPlugin> pluginCache,
      final List<Node> topologicalOrder) {

    final int nodeCount = topologicalOrder.size();
    final Map<String, Integer> nodeToIndex = new HashMap<>(nodeCount);
    for (int i = 0; i < nodeCount; i++) {
      nodeToIndex.put(topologicalOrder.get(i).nodeId(), i);
    }

    final NodeAssembler[] assemblers = new NodeAssembler[nodeCount];
    for (int i = 0; i < nodeCount; i++) {
      final Node node = topologicalOrder.get(i);
      assemblers[i] = createNodeAssembler(def, node, parentsList, pluginCache, nodeToIndex);
    }
    return assemblers;
  }

  /**
   * Compiles a workflow template for high-performance execution.
   *
   * @param def the workflow definition
   * @param parentsList map of nodeId to parent nodes
   * @param pluginCache map of nodeId to initialized plugin instances
   * @param topologicalOrder list of nodes in topological order
   * @return the compiled workflow template
   */
  private WorkflowTemplate compileTemplate(
      final WorkflowDefinition def,
      final Map<String, List<Node>> parentsList,
      final Map<String, WorkflowPlugin> pluginCache,
      final List<Node> topologicalOrder) {

    final int nodeCount = topologicalOrder.size();
    final NodeAssembler[] assemblers =
        compileAssemblers(def, parentsList, pluginCache, topologicalOrder);

    final List<String> nodeIds = topologicalOrder.stream().map(Node::nodeId).toList();

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
  private Mono<Void> executeTemplate(
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

    final ExecutionControl control =
        executionControlRegistry.findByExecutionId(executionId).orElseThrow(
            () ->
                new IllegalStateException(
                    "ExecutionControl not registered for execution: " + executionId));

    final AssemblyContext context =
        new AssemblyContext(
            executionId, sessionId, workflowId, payload, control, streams, terminals, disposables,
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
   * @param def the workflow definition
   * @param node the node to assemble
   * @param parentsList map of nodeId to parent nodes
   * @param pluginCache map of nodeId to initialized plugin instances
   * @param nodeToIndex map of nodeId to stream array index
   * @return the NodeAssembler
   */
  private NodeAssembler createNodeAssembler(
      final WorkflowDefinition def,
      final Node node,
      final Map<String, List<Node>> parentsList,
      final Map<String, WorkflowPlugin> pluginCache,
      final Map<String, Integer> nodeToIndex) {

    final WorkflowPlugin plugin = pluginCache.get(node.nodeId());
    final Duration nodeTimeout = getNodeTimeout(node, plugin);
    final int bufferSize = getBufferSize(node);
    final boolean hasParents = !parentsList.get(node.nodeId()).isEmpty();
    final int nodeIndex = nodeToIndex.get(node.nodeId());

    final NodeAssembler result;
    if (plugin instanceof TriggerPlugin trigger
        && (plugin.getCategory() == PluginCategory.TRIGGER || !hasParents)) {
      log.atTrace()
          .addKeyValue(LOG_KEY_NODE_ID, node.nodeId())
          .addKeyValue(LOG_KEY_PLUGIN_TYPE, node.type())
          .addKeyValue(LOG_KEY_NODE_CATEGORY, "TRIGGER")
          .log("Creating trigger node assembler");
      result = createTriggerAssembler(node, trigger, nodeTimeout, nodeIndex, bufferSize);
    } else {
      final ParentEdgeInfo[] parentEdges =
          def.edges().stream()
              .filter(e -> e.target().equals(node.nodeId()))
              .map(e -> new ParentEdgeInfo(nodeToIndex.get(e.source()), e.source(), e.sourcePort()))
              .toArray(ParentEdgeInfo[]::new);

      if (plugin instanceof ProcessorPlugin processor) {
        log.atTrace()
            .addKeyValue(LOG_KEY_NODE_ID, node.nodeId())
            .addKeyValue(LOG_KEY_PLUGIN_TYPE, node.type())
            .addKeyValue(LOG_KEY_NODE_CATEGORY, "PROCESSOR")
            .addKeyValue(LOG_KEY_PARENT_EDGE_COUNT, parentEdges.length)
            .log("Creating processor node assembler");
        result =
            createProcessorAssembler(
                node, processor, nodeTimeout, nodeIndex, bufferSize, parentEdges);
      } else if (plugin instanceof TerminalPlugin terminal) {
        log.atTrace()
            .addKeyValue(LOG_KEY_NODE_ID, node.nodeId())
            .addKeyValue(LOG_KEY_PLUGIN_TYPE, node.type())
            .addKeyValue(LOG_KEY_NODE_CATEGORY, "TERMINAL")
            .addKeyValue(LOG_KEY_PARENT_EDGE_COUNT, parentEdges.length)
            .log("Creating terminal node assembler");
        result = createTerminalAssembler(node, terminal, nodeTimeout, parentEdges);
      } else {
        log.atWarn()
            .addKeyValue(LOG_KEY_NODE_ID, node.nodeId())
            .addKeyValue(LOG_KEY_PLUGIN_TYPE, node.type())
            .log("Unknown plugin type, creating no-op assembler");
        result = context -> {};
      }
    }

    return result;
  }

  private NodeAssembler createTriggerAssembler(
      final Node node,
      final TriggerPlugin trigger,
      final Duration timeout,
      final int index,
      final int bufferSize) {

    return context -> {
      final ExecutionControl control = context.control();

      Flux<Message<?>> stream = trigger.start(node.config());
      if (trigger.isBlocking()) {
        stream = stream.subscribeOn(virtualThreadScheduler);
      }

      final ExecutionContextBuilder contextBuilder =
          new ExecutionContextBuilder()
              .sessionId(context.sessionId())
              .workflowId(context.workflowId())
              .executionId(context.executionId())
              .nodeId(node.nodeId())
              .payload(context.payload());

      Flux<Message<?>> built =
          new StreamBuilder(node, timeout, tracker, controlBusGateway)
              .withSource(stream)
              .withTimeout()
              .withTaskTracking(context.executionId())
              .withErrorHandling(context.executionId())
              .build();

      final Sinks.One<Void> nodeSafeSink = control.nodeSafeStopSinks().get(node.nodeId());
      if (nodeSafeSink != null) {
        built = built.takeUntilOther(nodeSafeSink.asMono());
      }

      built = control.applyPostProcessingControls(node.nodeId(), built);
      built = contextBuilder.applyContextTo(built);
      context.streams()[index] =
          applyLoggingAndBroadcasting(
              context.executionId(),
              node.nodeId(),
              built,
              bufferSize,
              context.disposables(),
              context.connectors());
    };
  }

  @SuppressWarnings("PMD.UseVarargs")
  private NodeAssembler createProcessorAssembler(
      final Node node,
      final ProcessorPlugin processor,
      final Duration timeout,
      final int index,
      final int bufferSize,
      final ParentEdgeInfo[] parentEdges) {

    return context -> {
      final ExecutionControl control = context.control();
      final Flux<Message<?>> mergedInput = mergeParentStreams(context.streams(), parentEdges);

      // 1. Apply Pre-Processing Controls (Safe Stop & Skip Detection)
      Flux<Message<?>> safeInput = control.applyPreProcessingControls(node.nodeId(), mergedInput);

      // 2. Execute processor (or skip if flagged)
      Flux<Message<?>> stream;
      final AtomicBoolean skipFlag = control.nodeSkipFlags().get(node.nodeId());

      if (skipFlag != null && skipFlag.get()) {
        log.atDebug()
            .addKeyValue(LOG_KEY_NODE_ID, node.nodeId())
            .log("Node marked for skip, bypassing processor");
        stream = safeInput;
      } else {
        stream = processor.process(safeInput, node.config());
        if (processor.isBlocking()) {
          stream = stream.subscribeOn(virtualThreadScheduler);
        }
      }

      final ExecutionContextBuilder contextBuilder =
          new ExecutionContextBuilder()
              .sessionId(context.sessionId())
              .workflowId(context.workflowId())
              .executionId(context.executionId())
              .nodeId(node.nodeId())
              .payload(context.payload());

      Flux<Message<?>> built =
          new StreamBuilder(node, timeout, tracker, controlBusGateway)
              .withSource(stream)
              .withTimeout()
              .withTaskTracking(context.executionId())
              .withErrorHandling(context.executionId())
              .build();

      // 3. Apply Post-Processing Controls (Immediate Stop & Pauses)
      built = control.applyPostProcessingControls(node.nodeId(), built);
      built = contextBuilder.applyContextTo(built);
      context.streams()[index] =
          applyLoggingAndBroadcasting(
              context.executionId(),
              node.nodeId(),
              built,
              bufferSize,
              context.disposables(),
              context.connectors());
    };
  }

  @SuppressWarnings("PMD.UseVarargs")
  private NodeAssembler createTerminalAssembler(
      final Node node,
      final TerminalPlugin terminal,
      final Duration timeout,
      final ParentEdgeInfo[] parentEdges) {

    return context -> {
      final ExecutionControl control = context.control();
      final Flux<Message<?>> mergedInput =
          mergeParentStreams(context.streams(), parentEdges);

      // 1. Apply Pre-Processing Controls (Safe Stop & Skip)
      final Flux<Message<?>> safeInput =
          control.applyPreProcessingControls(node.nodeId(), mergedInput);

      final Flux<Message<?>> inputToTerminal =
          safeInput
              .flatMap(
                  msg ->
                      Mono.<Message<?>>just(msg)
                          .timeout(timeout, virtualThreadScheduler)
                          .onErrorMap(TimeoutException.class, e -> e),
                  BUFFER_SIZE)
              .transformDeferredContextual(
                  (flux, ctx) -> {
                    final ResultCollector collector = ctx.getOrDefault("resultCollector", null);
                    return collector != null ? flux.doOnNext(collector::add) : flux;
                  });

      final ExecutionContextBuilder contextBuilder =
          new ExecutionContextBuilder()
              .sessionId(context.sessionId())
              .workflowId(context.workflowId())
              .executionId(context.executionId())
              .nodeId(node.nodeId())
              .payload(context.payload());

      Mono<Void> completion = terminal.consume(inputToTerminal, node.config());
      if (terminal.isBlocking()) {
        completion = completion.subscribeOn(virtualThreadScheduler);
      }

      completion =
          completion
              .doOnSubscribe(
                  s ->
                      tracker.emitTaskStatusEvent(
                          context.executionId(),
                          node.nodeId(),
                          DEFAULT_TASK_ID,
                          STATUS_RUNNING,
                          Collections.emptyMap()))
              .doOnSuccess(
                  v ->
                      tracker.emitTaskStatusEvent(
                          context.executionId(),
                          node.nodeId(),
                          DEFAULT_TASK_ID,
                          STATUS_SUCCESS,
                          Collections.emptyMap()))
              .doOnError(
                  e -> {
                    tracker.emitTaskStatusEvent(
                        context.executionId(),
                        node.nodeId(),
                        DEFAULT_TASK_ID,
                        STATUS_FAILURE,
                        Collections.emptyMap());
                    controlBusGateway
                        .emit(
                            DefaultMessage.create(
                                    null,
                                    new ControlError(
                                        node.nodeId(),
                                        context.executionId(),
                                        "Node Failure",
                                        e.getMessage()))
                                .withSourceNodeId(node.nodeId())
                                .withControl(true)
                                .withPriority(10))
                        .subscribe();
                  })
              .then();

      completion = contextBuilder.applyContextTo(completion);
      context.terminals().add(completion);
    };
  }

  /**
   * Merges parent streams.
   *
   * @param streams the array of streams
   * @param parentEdges the parent edges
   * @return the merged stream
   */
  @SuppressWarnings({"PMD.UseVarargs", "PMD.OnlyOneReturn", "PMD.AvoidLiteralsInIfCondition"})
  private Flux<Message<?>> mergeParentStreams(
      final Flux<Message<?>>[] streams, final ParentEdgeInfo[] parentEdges) {
    if (parentEdges.length == 0) {
      return Flux.empty();
    }
    if (parentEdges.length == 1) {
      return applyEdgeRouting(streams, parentEdges[0]);
    }

    final List<Flux<Message<?>>> parentFluxes = new ArrayList<>(parentEdges.length);
    for (final ParentEdgeInfo edge : parentEdges) {
      parentFluxes.add(applyEdgeRouting(streams, edge));
    }
    return Flux.merge(parentFluxes);
  }

  private Flux<Message<?>> applyEdgeRouting(
      final Flux<Message<?>>[] streams, final ParentEdgeInfo edge) {
    Flux<Message<?>> stream =
        streams[edge.parentIndex()].map(msg -> msg.withSourceNodeId(edge.sourceNodeId()));

    if (edge.sourcePort() != null) {
      stream = stream.filter(msg -> edge.sourcePort().equals(msg.getSourcePort()));
    }
    return stream;
  }

  private record ParentEdgeInfo(int parentIndex, String sourceNodeId, String sourcePort) {}

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
        compileAssemblers(
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
        createExecutionControl(sessionId, workflowId, newExecutionId, prepared, Map.of());
    executionControlRegistry.register(control);

    return tracker
        .startWorkflow(newExecutionId, sessionId, workflowId, nodeIds)
        .then(
            executeTemplate(
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

  /**
   * Gets the buffer size for a node.
   *
   * @param node the node
   * @return the buffer size
   */
  private int getBufferSize(final Node node) {
    final Object bufferVal = node.config().get("bufferSize");
    final int result;
    if (bufferVal instanceof Number numValue && numValue.intValue() > 0) {
      result = numValue.intValue();
    } else {
      result = BUFFER_SIZE;
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
  private Duration getNodeTimeout(final Node node, final WorkflowPlugin plugin) {
    final Object timeoutVal =
        node.config().getOrDefault("timeoutSeconds", node.config().get("timeout"));

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

  /**
   * Applies logging and broadcasting (Sinks) to a stream.
   *
   * @param executionId the execution ID
   * @param nodeId the node ID
   * @param stream the stream to process
   * @param bufferSize the buffer size
   * @param disposables the list of disposables to manage resource lifecycle
   * @param connectors the list of tasks to connect upstreams to sinks
   * @return the processed stream
   */
  private Flux<Message<?>> applyLoggingAndBroadcasting(
      final String executionId,
      final String nodeId,
      final Flux<Message<?>> stream,
      final int bufferSize,
      final List<Disposable> disposables,
      final List<Runnable> connectors) {
    final Flux<Message<?>> historiedStream = stream.map(msg -> msg.withAddedHistory(nodeId));
    final Flux<Message<?>> processedStream = getMessageFlux(executionId, nodeId, historiedStream);

    final Sinks.Many<Message<?>> sink = Sinks.many().multicast().onBackpressureBuffer(bufferSize);

    connectors.add(
        () ->
            disposables.add(
                processedStream.subscribe(
                    msg -> sink.emitNext(msg, RETRY_HANDLER),
                    err -> sink.emitError(err, RETRY_HANDLER),
                    () -> sink.emitComplete(RETRY_HANDLER))));

    return sink.asFlux();
  }

  private Flux<Message<?>> getMessageFlux(
      final String executionId, final String nodeId, final Flux<Message<?>> stream) {
    Flux<Message<?>> logStream = stream;
    // 1. Conditional Reactor Logging: Only active if DEBUG level is set for this class
    if (log.isDebugEnabled()) {
      logStream = logStream.log("Node-" + nodeId);
    }

    // 2. Wire Tap: Send to MessageStore if available
    if (messageStore != null) {
      logStream = logStream.flatMap(msg -> messageStore.store(msg).thenReturn(msg));
    }

    // 3. Checkpoint: Save the last output of each node for restart-from-node support
    logStream =
        logStream.flatMap(msg -> checkpointStore.save(executionId, nodeId, msg).thenReturn(msg));

    final Flux<Message<?>> processedStream;
    if (log.isTraceEnabled()) {
      processedStream =
          logStream.doOnNext(
              msg -> tracker.emitLogEvent(executionId, String.valueOf(msg.getPayload())));
    } else {
      processedStream = logStream;
    }
    return processedStream;
  }

  private ExecutionControl createExecutionControl(
      final String sessionId,
      final String workflowId,
      final String executionId,
      final PreparedWorkflow prepared,
      final Map<String, Object> payload) {
    final List<String> nodeIds = prepared.topologicalOrder().stream().map(Node::nodeId).toList();

    final Map<String, Sinks.One<Void>> nodeImmediateStopSinks = new ConcurrentHashMap<>();
    final Map<String, Sinks.One<Void>> nodeSafeStopSinks = new ConcurrentHashMap<>();
    final Map<String, ReactiveControlValve> nodePauseValves = new ConcurrentHashMap<>();
    final Map<String, AtomicBoolean> nodeSkipFlags = new ConcurrentHashMap<>();
    final Map<String, AtomicBoolean> nodeStepModes = new ConcurrentHashMap<>();
    final Map<String, Sinks.Many<Void>> nodeStepSinks = new ConcurrentHashMap<>();

    nodeIds.forEach(
        nodeId -> {
          nodeImmediateStopSinks.put(nodeId, Sinks.one());
          nodeSafeStopSinks.put(nodeId, Sinks.one());
          nodePauseValves.put(nodeId, new ReactiveControlValve());
          nodeSkipFlags.put(nodeId, new AtomicBoolean(false));
          nodeStepModes.put(nodeId, new AtomicBoolean(false));
          nodeStepSinks.put(nodeId, Sinks.many().multicast().onBackpressureBuffer());
        });

    return new ExecutionControl(
        sessionId,
        workflowId,
        executionId,
        prepared,
        payload,
        Sinks.one(),
        Sinks.one(),
        new ReactiveControlValve(),
        nodeImmediateStopSinks,
        nodeSafeStopSinks,
        nodePauseValves,
        nodeSkipFlags,
        nodeStepModes,
        nodeStepSinks);
  }
}
