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
package com.infenia.yukta.service.orchestrator.strategy;

import com.infenia.yukta.model.workflow.NodeAssembler;
import com.infenia.yukta.model.workflow.ParentEdgeInfo;
import com.infenia.yukta.model.workflow.api.WorkflowDefinition.Node;
import com.infenia.yukta.plugin.core.WorkflowPlugin;
import com.infenia.yukta.plugin.gateway.ControlBusGateway;
import com.infenia.yukta.plugin.gateway.ResultCollector;
import com.infenia.yukta.plugin.message.DefaultMessage;
import com.infenia.yukta.plugin.message.Message;
import com.infenia.yukta.plugin.message.control.ControlError;
import com.infenia.yukta.plugin.type.TerminalPlugin;
import com.infenia.yukta.service.TaskTrackerService;
import com.infenia.yukta.service.orchestrator.ExecutionContextBuilder;
import com.infenia.yukta.service.orchestrator.stream.StreamTopologyDecorator;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

@Slf4j
@Component
@Order(3)
@RequiredArgsConstructor
public class TerminalNodeAssemblerStrategy implements NodeAssemblerStrategy {

  private static final int BUFFER_SIZE = 1024;
  private static final String DEFAULT_TASK_ID = "default";
  private static final String STATUS_RUNNING = "RUNNING";
  private static final String STATUS_SUCCESS = "SUCCESS";
  private static final String STATUS_FAILURE = "FAILURE";
  private static final String LOG_KEY_NODE_ID = "nodeId";
  private static final String LOG_KEY_PARENT_EDGE_COUNT = "parentEdgeCount";

  private final TaskTrackerService tracker;
  private final ControlBusGateway controlBusGateway;

  private final Scheduler virtualThreadScheduler;

  private final StreamTopologyDecorator streamTopologyDecorator;

  @Override
  public boolean supports(final WorkflowPlugin plugin, final boolean hasParents) {
    return plugin instanceof TerminalPlugin;
  }

  @Override
  public NodeAssembler createAssembler(
      final Node node,
      final WorkflowPlugin plugin,
      final Duration timeout,
      final int index,
      final int bufferSize,
      final ParentEdgeInfo[] parentEdges) {
    final TerminalPlugin terminal = (TerminalPlugin) plugin;

    log.atTrace()
        .addKeyValue(LOG_KEY_NODE_ID, node.nodeId())
        .addKeyValue(LOG_KEY_PARENT_EDGE_COUNT, parentEdges.length)
        .log("Creating terminal node assembler");

    return context -> {
      final var control = context.control();
      final Flux<Message<?>> mergedInput =
          streamTopologyDecorator.mergeParentStreams(context.streams(), parentEdges);

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
                                    e.getMessage())))
                        .subscribe();
                  });

      context.terminals().add(completion);
    };
  }
}
