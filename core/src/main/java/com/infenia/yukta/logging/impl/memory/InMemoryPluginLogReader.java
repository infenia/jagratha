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
package com.infenia.yukta.logging.impl.memory;

import com.infenia.yukta.logging.api.ExecutionSummary;
import com.infenia.yukta.logging.api.PluginLogEntry;
import com.infenia.yukta.logging.api.PluginLogReader;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * In-memory implementation of PluginLogReader.
 *
 * <p>Reads from shared ConcurrentHashMap storage. Requires InMemoryPluginLogWriter to be used for
 * persistence.
 */
@Slf4j
@RequiredArgsConstructor
public class InMemoryPluginLogReader implements PluginLogReader {

  /** Shared storage reference. */
  private final Map<String, List<PluginLogEntry>> storage;

  @Override
  public Flux<PluginLogEntry> readExecution(final String executionId) {
    return Mono.fromCallable(() -> storage.getOrDefault(executionId, List.of()))
        .subscribeOn(Schedulers.boundedElastic())
        .flatMapIterable(entries -> entries)
        .doOnNext(
            entry ->
                log.atTrace()
                    .addKeyValue("executionId", entry.executionId())
                    .addKeyValue("count", 1)
                    .log("Read execution log entry"));
  }

  @Override
  public Flux<PluginLogEntry> readSession(final String sessionId) {
    return Mono.fromCallable(
            () ->
                storage.values().stream()
                    .flatMap(List::stream)
                    .filter(entry -> sessionId.equals(entry.sessionId()))
                    .collect(Collectors.toList()))
        .subscribeOn(Schedulers.boundedElastic())
        .flatMapIterable(entries -> entries)
        .doOnNext(
            _ -> log.atTrace().addKeyValue("sessionId", sessionId).log("Read session log entry"));
  }

  @Override
  public Mono<List<ExecutionSummary>> listExecutions(final String sessionId) {
    return Mono.fromCallable(
            () -> {
              final Map<String, Builder> builders = new java.util.HashMap<>();

              for (final Map.Entry<String, List<PluginLogEntry>> entry : storage.entrySet()) {
                final List<PluginLogEntry> entries = entry.getValue();
                if (entries.isEmpty()) {
                  continue;
                }

                final PluginLogEntry first = entries.getFirst();
                if (!sessionId.equals(first.sessionId())) {
                  continue;
                }

                final Instant firstInstant = first.timestamp();
                final LocalDateTime startTime =
                    ZonedDateTime.ofInstant(firstInstant, ZoneId.systemDefault()).toLocalDateTime();

                final Builder builder =
                    new Builder()
                        .executionId(entry.getKey())
                        .sessionId(sessionId)
                        .startTime(startTime)
                        .entryCount(entries.size());

                final PluginLogEntry last = entries.getLast();
                final Instant lastInstant = last.timestamp();
                final LocalDateTime endTime =
                    ZonedDateTime.ofInstant(lastInstant, ZoneId.systemDefault()).toLocalDateTime();
                builder.endTime(endTime);

                builders.put(entry.getKey(), builder);
              }

              return builders.values().stream()
                  .map(Builder::build)
                  .sorted((a, b) -> b.startTime().compareTo(a.startTime()))
                  .collect(Collectors.toList());
            })
        .subscribeOn(Schedulers.boundedElastic())
        .doOnNext(
            summaries ->
                log.atDebug()
                    .addKeyValue("sessionId", sessionId)
                    .addKeyValue("executionCount", summaries.size())
                    .log("Listed executions"));
  }

  @Override
  public Mono<String> getRawContent(final String executionId) {
    return Mono.fromCallable(
            () ->
                storage.getOrDefault(executionId, List.of()).stream()
                    .map(
                        entry ->
                            String.format(
                                "%s | %s | %s | %s",
                                entry.timestamp(),
                                entry.stream(),
                                entry.pluginId(),
                                entry.message()))
                    .collect(Collectors.joining("\n")))
        .subscribeOn(Schedulers.boundedElastic())
        .doOnNext(
            content ->
                log.atDebug()
                    .addKeyValue("executionId", executionId)
                    .addKeyValue("contentLength", content.length())
                    .log("Retrieved raw log content"));
  }

  /** Helper builder class for ExecutionSummary. */
  public static class Builder {
    private String executionId;
    private String sessionId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private long entryCount;

    /** Set the execution ID. */
    public Builder executionId(final String executionId) {
      this.executionId = executionId;
      return this;
    }

    /** Set the session ID. */
    public Builder sessionId(final String sessionId) {
      this.sessionId = sessionId;
      return this;
    }

    /** Set the start time. */
    public Builder startTime(final LocalDateTime startTime) {
      this.startTime = startTime;
      return this;
    }

    /** Set the end time. */
    public Builder endTime(final LocalDateTime endTime) {
      this.endTime = endTime;
      return this;
    }

    /** Set the entry count. */
    public Builder entryCount(final long entryCount) {
      this.entryCount = entryCount;
      return this;
    }

    /** Build the ExecutionSummary. */
    public ExecutionSummary build() {
      return new ExecutionSummary(executionId, sessionId, startTime, endTime, entryCount);
    }
  }
}
