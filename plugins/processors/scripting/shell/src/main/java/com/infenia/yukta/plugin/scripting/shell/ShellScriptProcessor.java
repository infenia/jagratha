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
package com.infenia.yukta.plugin.scripting.shell;

import com.infenia.yukta.plugin.exception.WorkflowExecutionException;
import com.infenia.yukta.plugin.message.Message;
import com.infenia.yukta.plugin.type.ProcessorPlugin;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.codec.StringDecoder;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * A processor plugin that executes shell scripts (bash).
 *
 * <p>Configuration:
 *
 * <ul>
 *   <li>script (required): The shell script content to execute.
 *   <li>executable (optional): The shell executable to use. Default is /bin/bash.
 *   <li>timeout (optional): Execution timeout in seconds. Default is 60.
 *   <li>workingDir (optional): The working directory for the script.
 * </ul>
 */
@Slf4j
@Component
public class ShellScriptProcessor implements ProcessorPlugin {

  private static final String TYPE = "SHELL_SCRIPT";
  private static final String CONFIG_SCRIPT = "script";
  private static final String CONFIG_EXECUTABLE = "executable";
  private static final String CONFIG_TIMEOUT = "timeout";
  private static final String CONFIG_WORKING_DIR = "workingDir";

  private static final String DEFAULT_EXECUTABLE = "/bin/bash";
  private static final long DEFAULT_TIMEOUT = 60L;

  @Override
  public String getType() {
    return TYPE;
  }

  @Override
  public String getDescription() {
    return "Executes a shell script (bash) and returns the output as a message payload.";
  }

  @Override
  public String getUsagePattern() {
    return "Configure with:\n"
        + "- script: The shell script content.\n"
        + "- executable: Optional path to shell (default: /bin/bash).\n"
        + "- timeout: Optional timeout in seconds (default: 60).\n"
        + "- workingDir: Optional working directory.";
  }

  @Override
  public Flux<Message<?>> process(final Flux<Message<?>> input, final Map<String, Object> config) {
    final String script = (String) config.get(CONFIG_SCRIPT);
    final String executable = (String) config.getOrDefault(CONFIG_EXECUTABLE, DEFAULT_EXECUTABLE);
    final long timeout =
        ((Number) config.getOrDefault(CONFIG_TIMEOUT, DEFAULT_TIMEOUT)).longValue();
    final String workingDir = (String) config.get(CONFIG_WORKING_DIR);

    if (script == null || script.isBlank()) {
      return Flux.error(new IllegalArgumentException("script configuration is mandatory"));
    }

    return input.flatMap(
        message -> executeScript(message, script, executable, timeout, workingDir));
  }

  private Mono<Message<?>> executeScript(
      final Message<?> message,
      final String script,
      final String executable,
      final long timeout,
      final String workingDir) {

    return Mono.fromCallable(
            () -> {
              final ProcessBuilder pb = new ProcessBuilder(executable, "-c", script);
              if (workingDir != null && !workingDir.isBlank()) {
                pb.directory(new File(workingDir));
              }

              // Export metadata as environment variables
              final Map<String, String> env = pb.environment();
              message
                  .getMetadata()
                  .forEach(
                      (k, v) -> {
                        if (v != null) {
                          env.put(
                              "YUKTA_METADATA_" + k.toUpperCase().replace('.', '_'), v.toString());
                        }
                      });

              pb.redirectErrorStream(true);
              return pb.start();
            })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(
            process -> {
              // Capture output
              final Flux<String> outputFlux =
                  DataBufferUtils.readInputStream(
                          process::getInputStream, DefaultDataBufferFactory.sharedInstance, 4096)
                      .transform(
                          flux -> StringDecoder.textPlainOnly().decode(flux, null, null, Map.of()));

              final Mono<Integer> exitCodeMono =
                  Mono.fromFuture(process.onExit()).map(Process::exitValue);

              final Mono<Message<?>> resultMono =
                  outputFlux
                      .collectList()
                      .map(list -> String.join("", list))
                      .zipWith(exitCodeMono)
                      .flatMap(
                          tuple -> {
                            final String output = tuple.getT1();
                            final int exitCode = tuple.getT2();
                            if (exitCode != 0) {
                              return Mono.error(
                                  new WorkflowExecutionException(
                                      "Shell script failed with exit code "
                                          + exitCode
                                          + ". Output: "
                                          + output));
                            }
                            return Mono.just((Message<?>) message.withPayload(output));
                          });

              return resultMono
                  .timeout(Duration.ofSeconds(timeout))
                  .onErrorResume(
                      TimeoutException.class,
                      e -> {
                        process.destroyForcibly();
                        return Mono.error(
                            new WorkflowExecutionException(
                                "Shell script execution timed out after " + timeout + "s", e));
                      });
            })
        .onErrorMap(
            IOException.class,
            e ->
                new WorkflowExecutionException(
                    "Failed to start shell script: " + e.getMessage(), e))
        .onErrorMap(
            e -> {
              if (e instanceof WorkflowExecutionException) {
                return e;
              }
              return new WorkflowExecutionException(
                  "Shell script execution failed: " + e.getMessage(), e);
            });
  }

  @Override
  public Mono<Void> validateConfig(final Map<String, Object> config) {
    final String script = (String) config.get(CONFIG_SCRIPT);
    if (script == null || script.isBlank()) {
      return Mono.error(new IllegalArgumentException("script is mandatory"));
    }
    return Mono.empty();
  }
}
