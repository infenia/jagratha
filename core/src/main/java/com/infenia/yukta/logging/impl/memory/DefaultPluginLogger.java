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

import com.infenia.yukta.logging.api.LogLevel;
import com.infenia.yukta.logging.api.LogStream;
import com.infenia.yukta.logging.api.PluginLogEntry;
import com.infenia.yukta.logging.api.PluginLogWriter;
import com.infenia.yukta.logging.api.PluginLogger;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Default implementation of PluginLogger that writes to a PluginLogWriter.
 *
 * <p>Provides fluent logging methods for stdout, stderr, and custom streams with optional metadata.
 */
@Slf4j
public class DefaultPluginLogger implements PluginLogger {

  private final String executionId;
  private final String sessionId;
  private final String pluginId;
  private final String pluginName;
  private final PluginLogWriter writer;

  /**
   * Create a new DefaultPluginLogger.
   *
   * @param executionId the execution identifier
   * @param sessionId the session identifier
   * @param pluginId the plugin identifier
   * @param pluginName the plugin display name
   * @param writer the log writer
   */
  public DefaultPluginLogger(
      final String executionId,
      final String sessionId,
      final String pluginId,
      final String pluginName,
      final PluginLogWriter writer) {
    this.executionId = executionId;
    this.sessionId = sessionId;
    this.pluginId = pluginId;
    this.pluginName = pluginName;
    this.writer = writer;
  }

  @Override
  public Mono<Void> logStdout(final String message) {
    return logToStream(LogStream.STDOUT, message, LogLevel.INFO);
  }

  @Override
  public Mono<Void> logStdout(final String message, final Map<String, Object> metadata) {
    return logToStream(LogStream.STDOUT, message, LogLevel.INFO);
  }

  @Override
  public Mono<Void> logStderr(final String message) {
    return logToStream(LogStream.STDERR, message, LogLevel.ERROR);
  }

  @Override
  public Mono<Void> logStderr(final String message, final Map<String, Object> metadata) {
    return logToStream(LogStream.STDERR, message, LogLevel.ERROR);
  }

  @Override
  public Mono<Void> logCustom(final String stream, final String message) {
    return logToStream(LogStream.CUSTOM, message, LogLevel.INFO);
  }

  @Override
  public Mono<Void> logCustom(
      final String stream, final String message, final Map<String, Object> metadata) {
    return logToStream(LogStream.CUSTOM, message, LogLevel.INFO);
  }

  @Override
  public Mono<Void> close() {
    return writer.close();
  }

  private Mono<Void> logToStream(
      final LogStream stream, final String message, final LogLevel logLevel) {
    return writer.write(
        new PluginLogEntry(
            executionId,
            sessionId,
            pluginId,
            pluginName,
            stream,
            message,
            logLevel,
            Instant.now(Clock.systemUTC())));
  }
}
