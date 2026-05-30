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
package com.infenia.yukta.service.control.processor;

import com.infenia.yukta.plugin.control.ControlSignalProcessor;
import com.infenia.yukta.plugin.control.WorkflowDirective;
import com.infenia.yukta.plugin.message.control.ControlCommand;
import com.infenia.yukta.plugin.message.control.ExecutionControlCommand.SkipNodeCommand;
import com.infenia.yukta.service.control.ExecutionControl;
import com.infenia.yukta.service.control.store.ExecutionControlRegistry;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Processor for skip node commands.
 *
 * <p>Marks a node as skipped (passes through without processing) or unskipped.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkipNodeCommandProcessor implements ControlSignalProcessor {

  private final ExecutionControlRegistry registry;

  @Override
  public boolean canProcess(final ControlCommand command) {
    return command instanceof SkipNodeCommand;
  }

  @Override
  public Mono<WorkflowDirective> process(final ControlCommand command) {
    final SkipNodeCommand skip = (SkipNodeCommand) command;
    return Mono.fromRunnable(
        () -> {
          final ExecutionControl control =
              registry
                  .findByExecutionId(skip.executionId())
                  .orElseThrow(
                      () ->
                          new IllegalArgumentException(
                              "Execution not found: " + skip.executionId()));

          final AtomicBoolean flag = control.nodeSkipFlags().get(skip.nodeId());
          if (flag == null) {
            throw new IllegalArgumentException("Node not found or not skippable: " + skip.nodeId());
          }

          flag.set(skip.skip());
          log.atDebug()
              .addKeyValue("executionId", skip.executionId())
              .addKeyValue("nodeId", skip.nodeId())
              .addKeyValue("skip", skip.skip())
              .log("Node skip flag set");
        });
  }

  @Override
  public int getPriority() {
    return 10;
  }
}
