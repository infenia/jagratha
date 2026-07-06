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

  /** The execution identifier. */
  private final String executionId;

  /** The session identifier. */
  private final String sessionId;

  /** The plugin identifier. */
  private final String pluginId;

  /** The plugin display name. */
  private final String pluginName;

  /** The log writer for persisting log entries. */
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
    this.writer = new ImmutablePluginLogWriterAdapter(writer);
  }

  @Override
  public Mono<Void> logStdout(final String message) {
    return logToStream(LogStream.STDOUT, null, message, LogLevel.INFO, null);
  }

  @Override
  public Mono<Void> logStdout(final String message, final Map<String, Object> metadata) {
    return logToStream(LogStream.STDOUT, null, message, LogLevel.INFO, metadata);
  }

  @Override
  public Mono<Void> logStderr(final String message) {
    return logToStream(LogStream.STDERR, null, message, LogLevel.ERROR, null);
  }

  @Override
  public Mono<Void> logStderr(final String message, final Map<String, Object> metadata) {
    return logToStream(LogStream.STDERR, null, message, LogLevel.ERROR, metadata);
  }

  @Override
  public Mono<Void> logCustom(final String stream, final String message) {
    return logToStream(LogStream.CUSTOM, stream, message, LogLevel.INFO, null);
  }

  @Override
  public Mono<Void> logCustom(
      final String stream, final String message, final Map<String, Object> metadata) {
    return logToStream(LogStream.CUSTOM, stream, message, LogLevel.INFO, metadata);
  }

  @Override
  public Mono<Void> close() {
    return writer.close();
  }

  private Mono<Void> logToStream(
      final LogStream stream,
      final String customStreamName,
      final String message,
      final LogLevel logLevel,
      final Map<String, Object> metadata) {
    return writer.write(
        new PluginLogEntry(
            executionId,
            sessionId,
            pluginId,
            pluginName,
            stream,
            message,
            logLevel,
            Instant.now(Clock.systemUTC()),
            customStreamName,
            metadata));
  }

  /** Immutable adapter for PluginLogWriter to prevent EI2 exposure violations. */
  private static final class ImmutablePluginLogWriterAdapter implements PluginLogWriter {
    /** The underlying writer delegate. */
    private final PluginLogWriter delegate;

    /**
     * Package-private constructor to prevent external instantiation outside this class hierarchy.
     */
    /* default */ ImmutablePluginLogWriterAdapter(final PluginLogWriter delegate) {
      this.delegate = delegate;
    }

    @Override
    public Mono<Void> write(final PluginLogEntry entry) {
      return delegate.write(entry);
    }

    @Override
    public Mono<Void> writeBatch(final java.util.List<PluginLogEntry> entries) {
      return delegate.writeBatch(entries);
    }

    @Override
    public Mono<Void> close() {
      return delegate.close();
    }
  }
}
