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
package com.infenia.jagratha.plugin.gradle;

import com.infenia.jagratha.plugin.Message;
import com.infenia.jagratha.plugin.TriggerPlugin;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.codec.StringDecoder;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** Gradle implementation of a TriggerPlugin. */
@Slf4j
public class GradlePlugin implements TriggerPlugin {

  private Map<String, Object> config;
  private UUID traceId;

  /** Public constructor. */
  public GradlePlugin() {
    super();
  }

  @Override
  public String getType() {
    return "gradle";
  }

  @Override
  public Mono<Void> validateConfig(final Map<String, Object> config) {
    if (config == null) {
      return Mono.error(new IllegalArgumentException("Configuration is required"));
    }
    if (!config.containsKey("projectRoot")) {
      return Mono.error(new IllegalArgumentException("projectRoot is required"));
    }
    final Object tasks = config.get("tasks");
    if (tasks != null && !(tasks instanceof List)) {
      return Mono.error(new IllegalArgumentException("tasks must be a list of strings"));
    }
    return Mono.empty();
  }

  @Override
  public Mono<Void> initialize(final Map<String, Object> config) {
    this.config = Map.copyOf(config);
    this.traceId = UUID.randomUUID();
    return Mono.empty();
  }

  @Override
  public Flux<Message> start() {
    final String projectRoot = (String) config.get("projectRoot");
    @SuppressWarnings("unchecked")
    final List<String> tasks = (List<String>) config.getOrDefault("tasks", List.of("check"));
    final String gradlePath = (String) config.getOrDefault("gradlePath", "./gradlew");
    final Long timeout = ((Number) config.getOrDefault("timeout", 600L)).longValue();

    final File projectDir = new File(projectRoot);

    return Flux.fromIterable(tasks)
        .flatMap(
            task ->
                executeTask(projectDir, gradlePath, task, timeout)
                    .map(output -> Message.create(traceId, output)));
  }

  private Mono<String> executeTask(
      final File projectDir, final String gradlePath, final String task, final long timeout) {
    final List<String> command = new ArrayList<>();
    command.add(gradlePath);
    command.add(task);

    return Mono.fromCallable(
            () -> {
              final ProcessBuilder pb = new ProcessBuilder(command);
              pb.directory(projectDir);
              pb.redirectErrorStream(true);
              return pb.start();
            })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(
            process -> {
              final StringBuilder output = new StringBuilder();

              final Flux<String> outputFlux =
                  DataBufferUtils.readInputStream(
                          process::getInputStream, DefaultDataBufferFactory.sharedInstance, 4096)
                      .transform(
                          flux -> StringDecoder.textPlainOnly().decode(flux, null, null, Map.of()));

              final Mono<String> readOutputMono =
                  outputFlux.doOnNext(output::append).then(Mono.fromSupplier(output::toString));

              final Mono<Integer> exitCodeMono =
                  Mono.fromFuture(process.onExit())
                      .map(Process::exitValue)
                      .timeout(Duration.ofSeconds(timeout))
                      .onErrorResume(
                          TimeoutException.class,
                          e -> {
                            process.destroyForcibly();
                            return Mono.error(
                                new TimeoutException("Timeout running task: " + task));
                          });

              return Mono.zip(exitCodeMono, readOutputMono)
                  .map(
                      tuple -> {
                        if (tuple.getT1() != 0) {
                          log.warn("Task {} failed with exit code {}", task, tuple.getT1());
                        }
                        return tuple.getT2();
                      });
            })
        .onErrorResume(
            IOException.class,
            e ->
                Mono.error(
                    new RuntimeException("Error executing task " + task + ": " + e.getMessage())));
  }
}
