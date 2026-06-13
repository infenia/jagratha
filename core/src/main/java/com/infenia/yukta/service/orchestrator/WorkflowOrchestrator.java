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
package com.infenia.yukta.service.orchestrator;

import com.infenia.yukta.model.workflow.NodeAssembler;
import com.infenia.yukta.model.workflow.PreparedWorkflow;
import com.infenia.yukta.model.workflow.WorkflowDefinition;
import com.infenia.yukta.model.workflow.WorkflowNode;
import com.infenia.yukta.plugin.message.Message;
import com.infenia.yukta.plugin.store.NodeCheckpointStore;
import com.infenia.yukta.service.control.ExecutionControl;
import com.infenia.yukta.service.control.factory.ExecutionControlFactory;
import com.infenia.yukta.service.control.store.ExecutionControlRegistry;
import com.infenia.yukta.service.orchestrator.compiler.WorkflowCompiler;
import com.infenia.yukta.service.orchestrator.preparator.WorkflowPreparator;
import com.infenia.yukta.service.orchestrator.tracker.TaskTrackerService;
import com.infenia.yukta.validation.SessionId;
import com.infenia.yukta.validation.WorkflowId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/** Orchestrator for executing reactive workflow DAGs. */
@Slf4j
@Service
@Validated
@RequiredArgsConstructor
public class WorkflowOrchestrator {

  private static final String CTX_SESSION_ID = "sessionId";
  private static final String CTX_WORKFLOW_ID = "workflowId";
  private static final String CTX_EXECUTION_ID = "executionId";

  private static final String LOG_KEY_SESSION_ID = "sessionId";
  private static final String LOG_KEY_WORKFLOW_ID = "workflowId";
  private static final String LOG_KEY_EXECUTION_ID = "executionId";
  private static final String LOG_KEY_NODE_COUNT = "nodeCount";

  private final TaskTrackerService tracker;
  private final ExecutionControlRegistry executionControlRegistry;
  private final ExecutionControlFactory executionControlFactory;
  private final NodeCheckpointStore checkpointStore;
  private final WorkflowCompiler compiler;
  private final WorkflowPreparator preparator;

  /**
   * Prepares a workflow for execution.
   *
   * @param def the workflow definition (includes workflowId)
   * @return a Mono containing the prepared workflow
   */
  public Mono<PreparedWorkflow> prepareWorkflow(@NotNull @Valid final WorkflowDefinition def) {
    return preparator.prepareWorkflow(def);
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

    final List<String> nodeIds =
        prepared.topologicalOrder().stream().map(WorkflowNode::nodeId).toList();

    final Mono<Void> execution =
        tracker
            .startWorkflow(executionId, sessionId, workflowId, nodeIds)
            .then(prepared.template().instantiate(executionId, payload));

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

    final List<WorkflowNode> topologicalOrder = prepared.topologicalOrder();
    final int nodeCount = topologicalOrder.size();
    final Map<String, Integer> nodeToIndex = new HashMap<>(nodeCount);
    for (int i = 0; i < nodeCount; i++) {
      nodeToIndex.put(topologicalOrder.get(i).nodeId(), i);
    }

    final int restartIndex = nodeToIndex.getOrDefault(restartNodeId, 0);
    final NodeAssembler[] assemblers =
        compiler.compileAssemblers(
            prepared.edges(), prepared.parentsList(), prepared.pluginCache(), topologicalOrder);

    // Replace pre-restart assemblers with bypass or checkpoint-replay variants
    for (int i = 0; i < restartIndex; i++) {
      final WorkflowNode node = topologicalOrder.get(i);
      final int idx = i;
      final Message<?> checkpoint = parentCheckpoints.get(node.nodeId());
      if (checkpoint != null) {
        assemblers[idx] = context -> context.streams()[idx] = Flux.just(checkpoint);
      } else {
        assemblers[idx] = context -> context.streams()[idx] = Flux.empty();
      }
    }

    final List<String> nodeIds = topologicalOrder.stream().map(WorkflowNode::nodeId).toList();

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
