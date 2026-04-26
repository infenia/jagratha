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
package com.infenia.yukta.service.control;

import com.infenia.yukta.plugin.control.ControlSignalProcessor;
import com.infenia.yukta.plugin.control.WorkflowDirective;
import com.infenia.yukta.plugin.message.control.ControlCommand;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Default processor for {@link ControlCommand.CommandType#RESTART_FROM_NODE} commands.
 *
 * <p>Validates that a {@code targetNodeId} is present, then produces a {@link
 * WorkflowDirective.RestartFromNode}. The dispatcher uses the node ID to locate the checkpoint
 * messages stored by the node's direct parents and replays the workflow from that point.
 */
@Component
@NoArgsConstructor
public class DefaultRestartFromNodeProcessor implements ControlSignalProcessor {

  @Override
  public boolean canProcess(final ControlCommand command) {
    return command.type() == ControlCommand.CommandType.RESTART_FROM_NODE;
  }

  @Override
  public Mono<WorkflowDirective> process(final ControlCommand command) {
    final Mono<WorkflowDirective> result;
    if (command.targetNodeId() == null || command.targetNodeId().isBlank()) {
      result =
          Mono.error(
              new IllegalArgumentException(
                  "RESTART_FROM_NODE command requires a non-blank targetNodeId"));
    } else {
      result = Mono.just(new WorkflowDirective.RestartFromNode(command.targetNodeId()));
    }
    return result;
  }
}
