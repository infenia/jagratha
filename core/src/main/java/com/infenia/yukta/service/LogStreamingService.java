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
package com.infenia.yukta.service;

import com.infenia.yukta.logging.api.ExecutionSummary;
import com.infenia.yukta.logging.api.PluginLogEntry;
import com.infenia.yukta.logging.api.PluginLogReader;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service for streaming plugin execution logs from configured readers.
 *
 * <p>Delegates to pluggable PluginLogReader implementations for storage-agnostic log access.
 * Provides a service layer abstraction between controllers and logging backends.
 */
@Slf4j
@Service
@Validated
@RequiredArgsConstructor
public class LogStreamingService {

  /** The plugin log reader service (optional - may not be configured). */
  private final ObjectProvider<PluginLogReader> logReader;

  /**
   * Stream log entries for a specific execution.
   *
   * @param executionId the execution identifier
   * @return flux of log entries in chronological order
   */
  public Flux<PluginLogEntry> streamExecutionLogs(final String executionId) {
    return logReader.stream()
        .findFirst()
        .map(reader -> reader.readExecution(executionId))
        .orElseGet(
            () -> {
              log.warn("No PluginLogReader configured, returning empty stream");
              return Flux.empty();
            });
  }

  /**
   * Stream log entries for a specific session.
   *
   * @param sessionId the session identifier
   * @return flux of log entries
   */
  public Flux<PluginLogEntry> streamSessionLogs(final String sessionId) {
    return logReader.stream()
        .findFirst()
        .map(reader -> reader.readSession(sessionId))
        .orElseGet(
            () -> {
              log.warn("No PluginLogReader configured, returning empty stream");
              return Flux.empty();
            });
  }

  /**
   * List all executions with log data for a session.
   *
   * @param sessionId the session identifier
   * @return mono containing list of execution summaries
   */
  public Mono<List<ExecutionSummary>> listExecutions(final String sessionId) {
    return logReader.stream()
        .findFirst()
        .map(reader -> reader.listExecutions(sessionId))
        .orElseGet(
            () -> {
              log.warn("No PluginLogReader configured, returning empty list");
              return Mono.just(List.of());
            });
  }

  /**
   * Get raw log content for an execution.
   *
   * @param executionId the execution identifier
   * @return mono containing raw log content
   */
  public Mono<String> getRawLogContent(final String executionId) {
    return logReader.stream()
        .findFirst()
        .map(reader -> reader.getRawContent(executionId))
        .orElseGet(
            () -> {
              log.warn("No PluginLogReader configured, returning empty content");
              return Mono.empty();
            });
  }

  /**
   * Check if a log reader is configured.
   *
   * @return true if a PluginLogReader implementation is available
   */
  public boolean isConfigured() {
    return logReader.stream().findFirst().isPresent();
  }
}
