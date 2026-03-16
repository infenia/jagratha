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
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.codec.StringDecoder;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** Gateway for executing external processes with reactive streaming output. */
@Slf4j
@Service
public class ProcessExecutorGateway {

  /** Default constructor. */
  public ProcessExecutorGateway() {
    super();
  }

  /**
   * Execute a process with the given command, working directory, and timeout.
   *
   * @param command the command and arguments to execute
   * @param workingDir the working directory (null defaults to current directory)
   * @param timeoutSeconds the timeout in seconds
   * @return a Mono containing the process output
   */
  public Mono<String> execute(
      final List<String> command, final String workingDir, final long timeoutSeconds) {

    return Mono.fromCallable(
            () -> {
              final ProcessBuilder processBuilder = new ProcessBuilder(command);
              if (workingDir != null && !workingDir.isBlank()) {
                processBuilder.directory(new File(workingDir));
              }
              processBuilder.redirectErrorStream(true);
              return processBuilder.start();
            })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(
            process -> {
              final Flux<String> outputFlux =
                  DataBufferUtils.readInputStream(
                          process::getInputStream, DefaultDataBufferFactory.sharedInstance, 4096)
                      .transform(
                          flux -> StringDecoder.textPlainOnly().decode(flux, null, null, Map.of()));

              final Mono<Integer> exitCodeMono =
                  Mono.fromFuture(process.onExit())
                      .timeout(Duration.ofSeconds(timeoutSeconds))
                      .map(Process::exitValue);

              return outputFlux
                  .collectList()
                  .map(list -> String.join("", list))
                  .zipWith(exitCodeMono)
                  .map(
                      tuple -> {
                        final String output = tuple.getT1();
                        final Integer exitCode = tuple.getT2();
                        if (exitCode != 0) {
                          throw new WorkflowExecutionException(
                              "Process failed with exit code " + exitCode + ". Output: " + output);
                        }
                        return output;
                      })
                  .onErrorResume(
                      TimeoutException.class,
                      e -> {
                        process.destroyForcibly();
                        return Mono.error(
                            new WorkflowExecutionException(
                                "Process timed out after " + timeoutSeconds + "s", e));
                      });
            })
        .onErrorResume(
            IOException.class,
            e ->
                Mono.error(
                    new WorkflowExecutionException(
                        "Failed to start process: " + e.getMessage(), e)))
        .onErrorMap(
            e -> {
              if (e instanceof WorkflowExecutionException) {
                return e;
              }
              return new WorkflowExecutionException(
                  "Process execution failed: " + e.getMessage(), e);
            });
  }

  /**
   * Execute a process with metadata exported as environment variables.
   *
   * @param command the command and arguments to execute
   * @param workingDir the working directory (null defaults to current directory)
   * @param timeoutSeconds the timeout in seconds
   * @param metadata the metadata map to export as environment variables
   * @return a Mono containing the process output
   */
  public Mono<String> executeWithMetadata(
      final List<String> command,
      final String workingDir,
      final long timeoutSeconds,
      final Map<String, Object> metadata) {

    return Mono.fromCallable(
            () -> {
              final ProcessBuilder processBuilder = new ProcessBuilder(command);
              if (workingDir != null && !workingDir.isBlank()) {
                processBuilder.directory(new File(workingDir));
              }

              // Export metadata as environment variables
              if (metadata != null) {
                exportMetadata(metadata, processBuilder.environment());
              }

              processBuilder.redirectErrorStream(true);
              return processBuilder.start();
            })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(
            process -> {
              final Flux<String> outputFlux =
                  DataBufferUtils.readInputStream(
                          process::getInputStream, DefaultDataBufferFactory.sharedInstance, 4096)
                      .transform(
                          flux -> StringDecoder.textPlainOnly().decode(flux, null, null, Map.of()));

              final Mono<Integer> exitCodeMono =
                  Mono.fromFuture(process.onExit())
                      .timeout(Duration.ofSeconds(timeoutSeconds))
                      .map(Process::exitValue);

              return outputFlux
                  .collectList()
                  .map(list -> String.join("", list))
                  .zipWith(exitCodeMono)
                  .map(
                      tuple -> {
                        final String output = tuple.getT1();
                        final Integer exitCode = tuple.getT2();
                        if (exitCode != 0) {
                          throw new WorkflowExecutionException(
                              "Process failed with exit code " + exitCode + ". Output: " + output);
                        }
                        return output;
                      })
                  .onErrorResume(
                      TimeoutException.class,
                      e -> {
                        process.destroyForcibly();
                        return Mono.error(
                            new WorkflowExecutionException(
                                "Process timed out after " + timeoutSeconds + "s", e));
                      });
            })
        .onErrorResume(
            IOException.class,
            e ->
                Mono.error(
                    new WorkflowExecutionException(
                        "Failed to start process: " + e.getMessage(), e)))
        .onErrorMap(
            e -> {
              if (e instanceof WorkflowExecutionException) {
                return e;
              }
              return new WorkflowExecutionException(
                  "Process execution failed: " + e.getMessage(), e);
            });
  }

  /**
   * Export metadata as environment variables with YUKTA_METADATA_ prefix.
   *
   * @param metadata the metadata map
   * @param env the environment map to populate
   */
  private void exportMetadata(final Map<String, Object> metadata, final Map<String, String> env) {
    metadata.forEach(
        (key, value) -> {
          if (value != null) {
            final String metadataValue = value.toString();
            final String keyUpper = key.toUpperCase(java.util.Locale.ROOT);
            final String envKey = "YUKTA_METADATA_" + keyUpper.replace('.', '_');
            env.put(envKey, metadataValue);
          }
        });
  }
}
