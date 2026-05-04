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
import com.infenia.yukta.model.workflow.WorkflowDefinition.Node;
import com.infenia.yukta.plugin.core.WorkflowPlugin;
import com.infenia.yukta.plugin.gateway.ControlBusGateway;
import com.infenia.yukta.plugin.message.Message;
import com.infenia.yukta.plugin.type.ProcessorPlugin;
import com.infenia.yukta.service.TaskTrackerService;
import com.infenia.yukta.service.orchestrator.ExecutionContextBuilder;
import com.infenia.yukta.service.orchestrator.StreamBuilder;
import com.infenia.yukta.service.orchestrator.stream.StreamTopologyDecorator;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;

@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class ProcessorNodeAssemblerStrategy implements NodeAssemblerStrategy {

  private final TaskTrackerService tracker;
  private final ControlBusGateway controlBusGateway;

  @Qualifier("virtualThreadScheduler")
  private final Scheduler virtualThreadScheduler;

  private final StreamTopologyDecorator streamTopologyDecorator;

  private static final String LOG_KEY_NODE_ID = "nodeId";
  private static final String LOG_KEY_PARENT_EDGE_COUNT = "parentEdgeCount";

  @Override
  public boolean supports(final WorkflowPlugin plugin, final boolean hasParents) {
    return plugin instanceof ProcessorPlugin;
  }

  @Override
  public NodeAssembler createAssembler(
      final Node node,
      final WorkflowPlugin plugin,
      final Duration timeout,
      final int index,
      final int bufferSize,
      final ParentEdgeInfo[] parentEdges) {
    final ProcessorPlugin processor = (ProcessorPlugin) plugin;

    log.atTrace()
        .addKeyValue(LOG_KEY_NODE_ID, node.nodeId())
        .addKeyValue(LOG_KEY_PARENT_EDGE_COUNT, parentEdges.length)
        .log("Creating processor node assembler");

    return context -> {
      final var control = context.control();
      final Flux<Message<?>> mergedInput =
          streamTopologyDecorator.mergeParentStreams(context.streams(), parentEdges);

      Flux<Message<?>> safeInput = control.applyPreProcessingControls(node.nodeId(), mergedInput);

      Flux<Message<?>> stream;
      final AtomicBoolean skipFlag = control.nodeSkipFlags().get(node.nodeId());

      if (skipFlag != null && skipFlag.get()) {
        log.atDebug().addKeyValue(LOG_KEY_NODE_ID, node.nodeId()).log("Node marked for skip");
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

      built = control.applyPostProcessingControls(node.nodeId(), built);
      built = contextBuilder.applyContextTo(built);
      context.streams()[index] =
          streamTopologyDecorator.applyLoggingAndBroadcasting(
              context.executionId(),
              node.nodeId(),
              built,
              bufferSize,
              context.disposables(),
              context.connectors());
    };
  }
}
