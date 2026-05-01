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
package com.infenia.yukta.service.control.directive;

import com.infenia.yukta.plugin.control.ControlSignalProcessor;
import com.infenia.yukta.plugin.control.WorkflowDirective;
import com.infenia.yukta.plugin.message.control.ControlCommand;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Default processor for {@link ControlCommand.CommandType#STOP} commands.
 *
 * <p>Extracts an optional {@code reason} string from the command parameters and produces a {@link
 * WorkflowDirective.Stop}. Custom processors with a higher {@link #getPriority()} can intercept
 * stop commands before this one runs.
 */
@Component
@NoArgsConstructor
public class DefaultStopProcessor implements ControlSignalProcessor {

  private static final String PARAM_REASON = "reason";
  private static final String DEFAULT_REASON = "User requested stop";

  @Override
  public boolean canProcess(final ControlCommand command) {
    return command.type() == ControlCommand.CommandType.STOP;
  }

  @Override
  public Mono<WorkflowDirective> process(final ControlCommand command) {
    final String reason = command.params().getOrDefault(PARAM_REASON, DEFAULT_REASON).toString();
    return Mono.just(new WorkflowDirective.Stop(reason));
  }
}
