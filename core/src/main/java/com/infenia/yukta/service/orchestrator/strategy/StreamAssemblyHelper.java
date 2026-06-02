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

import com.infenia.yukta.model.workflow.WorkflowNode;
import com.infenia.yukta.plugin.message.Message;
import com.infenia.yukta.service.control.ExecutionControl;
import com.infenia.yukta.service.execution.status.ExecutionStatusPublisher;
import com.infenia.yukta.service.orchestrator.assembly.AssemblyContext;
import com.infenia.yukta.service.orchestrator.assembly.ExecutionContextBuilder;
import com.infenia.yukta.service.orchestrator.stream.StreamBuilder;
import com.infenia.yukta.service.orchestrator.tracker.TaskTrackerService;
import java.time.Duration;
import lombok.experimental.UtilityClass;
import reactor.core.publisher.Flux;

@UtilityClass
class StreamAssemblyHelper {

  /**
   * Builds a stream with execution context and post-processing controls applied.
   *
   * @param node the workflow node
   * @param stream the source stream
   * @param timeout the node timeout duration
   * @param tracker the task tracker service
   * @param statusPublisher the execution status publisher
   * @param context the assembly context
   * @return the built stream with context applied
   */
  static Flux<Message<?>> buildStreamWithContext(
      final WorkflowNode node,
      final Flux<Message<?>> stream,
      final Duration timeout,
      final TaskTrackerService tracker,
      final ExecutionStatusPublisher statusPublisher,
      final AssemblyContext context) {

    final ExecutionControl control = context.control();
    final ExecutionContextBuilder contextBuilder =
        new ExecutionContextBuilder()
            .sessionId(context.sessionId())
            .workflowId(context.workflowId())
            .executionId(context.executionId())
            .nodeId(node.nodeId())
            .payload(context.payload());

    Flux<Message<?>> built =
        new StreamBuilder(node, timeout, tracker, statusPublisher)
            .withSource(stream)
            .withTimeout()
            .withTaskTracking(context.executionId())
            .withErrorHandling(context.executionId())
            .build();

    built = control.applyPostProcessingControls(node.nodeId(), built);
    return contextBuilder.applyContextTo(built);
  }
}
