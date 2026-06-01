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
import com.infenia.yukta.model.workflow.WorkflowNode;
import com.infenia.yukta.plugin.core.PluginCategory;
import com.infenia.yukta.plugin.core.WorkflowPlugin;
import com.infenia.yukta.plugin.message.Message;
import com.infenia.yukta.plugin.type.TriggerPlugin;
import com.infenia.yukta.service.control.gateway.ControlBusGateway;
import com.infenia.yukta.service.orchestrator.stream.StreamTopologyDecorator;
import com.infenia.yukta.service.orchestrator.tracker.TaskTrackerService;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;

/** Strategy for assembling trigger nodes that initiate workflow execution. */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class TriggerNodeAssemblerStrategy implements NodeAssemblerStrategy {

  private final TaskTrackerService tracker;
  private final ControlBusGateway controlBusGateway;

  private final Scheduler virtualThreadScheduler;

  private final StreamTopologyDecorator streamTopologyDecorator;

  @Override
  public boolean supports(final WorkflowPlugin plugin, final boolean hasParents) {
    return plugin instanceof TriggerPlugin
        && (plugin.getCategory() == PluginCategory.TRIGGER || !hasParents);
  }

  @Override
  public NodeAssembler createAssembler(
      final WorkflowNode node,
      final WorkflowPlugin plugin,
      final Duration timeout,
      final int index,
      final int bufferSize,
      final ParentEdgeInfo[] parentEdges) {
    final TriggerPlugin trigger = (TriggerPlugin) plugin;

    return context -> {
      final var control = context.control();

      Flux<Message<?>> stream = trigger.start(node.config());
      if (trigger.isBlocking()) {
        stream = stream.subscribeOn(virtualThreadScheduler);
      }

      Flux<Message<?>> built =
          StreamAssemblyHelper.buildStreamWithContext(
              node, stream, timeout, tracker, controlBusGateway, context);

      final var nodeSafeSink = control.nodeSafeStopSinks().get(node.nodeId());
      if (nodeSafeSink != null) {
        built = built.takeUntilOther(nodeSafeSink.asMono());
      }

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
