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
package com.infenia.yukta.plugin.process;

import com.infenia.yukta.plugin.exception.WorkflowExecutionException;
import com.infenia.yukta.plugin.message.Message;
import com.infenia.yukta.plugin.type.ProcessorPlugin;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * A processor plugin that executes an arbitrary process with configurable command, arguments,
 * working directory, and timeout.
 *
 * <p>Configuration:
 *
 * <ul>
 *   <li>executable (required): Path or name of the executable to run.
 *   <li>args (optional): List of string arguments. Default is empty list.
 *   <li>workingDir (optional): Working directory for the process.
 *   <li>timeout (optional): Execution timeout in seconds. Default is 600.
 * </ul>
 */
@Slf4j
@Component
public class ProcessExecutorPlugin implements ProcessorPlugin {

  private static final String TYPE = "PROCESS_EXECUTOR";
  private static final String CONFIG_EXECUTABLE = "executable";
  private static final String CONFIG_ARGS = "args";
  private static final String CONFIG_WORK_DIR = "workingDir";
  private static final String CONFIG_TIMEOUT = "timeout";

  private static final long DEFAULT_TIMEOUT = 600L;

  @Autowired(required = false)
  private ProcessExecutorGateway gateway;

  /** Default constructor. */
  public ProcessExecutorPlugin() {
    super();
  }

  /**
   * Set the process executor gateway.
   *
   * @param gateway the gateway
   */
  public void setGateway(final ProcessExecutorGateway gateway) {
    this.gateway = gateway;
  }

  @Override
  public String getType() {
    return TYPE;
  }

  @Override
  public String getDescription() {
    return "Executes an arbitrary process and returns the output as a message payload.";
  }

  @Override
  public String getUsagePattern() {
    return "Configure with:\n"
        + "- executable: Path or name of the executable (required).\n"
        + "- args: List of string arguments (optional, default: []).\n"
        + "- workingDir: Optional working directory.\n"
        + "- timeout: Optional timeout in seconds (default: 600).";
  }

  @Override
  public Duration getDefaultTimeout() {
    return Duration.ofSeconds(600);
  }

  @Override
  public Flux<Message<?>> process(final Flux<Message<?>> input, final Map<String, Object> config) {
    return input.onBackpressureDrop().flatMap(message -> executeProcess(message, config), 1);
  }

  private Mono<Message<?>> executeProcess(
      final Message<?> message, final Map<String, Object> config) {
    return gateway
        .executeWithMetadata(
            buildCommand(config),
            (String) config.get(CONFIG_WORK_DIR),
            ((Number) config.getOrDefault(CONFIG_TIMEOUT, DEFAULT_TIMEOUT)).longValue(),
            message.getMetadata())
        .<Message<?>>map(output -> message.withPayload(output))
        .onErrorMap(
            e -> {
              if (e instanceof WorkflowExecutionException) {
                return e;
              }
              return new WorkflowExecutionException(
                  "Process execution failed: " + e.getMessage(), e);
            });
  }

  private List<String> buildCommand(final Map<String, Object> config) {
    final String executable = (String) config.get(CONFIG_EXECUTABLE);
    @SuppressWarnings("unchecked")
    final List<String> args = (List<String>) config.getOrDefault(CONFIG_ARGS, List.of());

    final List<String> command = new ArrayList<>();
    command.add(executable);
    command.addAll(args);
    return command;
  }

  @Override
  public Mono<Void> validateConfig(final Map<String, Object> config) {
    final String executable = (String) config.get(CONFIG_EXECUTABLE);
    final Mono<Void> result;
    if (executable == null || executable.isBlank()) {
      result = Mono.error(new IllegalArgumentException("executable is mandatory"));
    } else {
      result = Mono.empty();
    }
    return result;
  }
}
