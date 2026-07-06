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
package com.infenia.yukta.logging.api;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable log entry for plugin execution logs.
 *
 * <p>Captures a single log message with full execution context and metadata.
 */
public record PluginLogEntry(
    String executionId,
    String sessionId,
    String pluginId,
    String pluginName,
    LogStream stream,
    String message,
    LogLevel logLevel,
    Instant timestamp,
    String customStreamName,
    Map<String, Object> metadata) {

  /**
   * Compact constructor to ensure metadata defensively copied to prevent mutation after creation.
   * Also recursively freezes nested mutable collections to prevent external mutation leaks.
   */
  public PluginLogEntry {
    if (metadata == null) {
      metadata = Map.of();
    } else {
      metadata = freezeMetadata(metadata);
    }
  }

  @SuppressWarnings("PMD.UseConcurrentHashMap")
  private static Map<String, Object> freezeMetadata(final Map<String, Object> original) {
    final Map<String, Object> frozen = new LinkedHashMap<>();
    original.forEach((key, value) -> frozen.put(key, freezeValue(value)));
    return Collections.unmodifiableMap(frozen);
  }

  @SuppressWarnings("PMD.OnlyOneReturn")
  private static Object freezeValue(final Object value) {
    if (value instanceof List) {
      return List.copyOf((List<?>) value);
    }
    if (value instanceof Set) {
      return Set.copyOf((Set<?>) value);
    }
    if (value instanceof Map) {
      return Map.copyOf((Map<?, ?>) value);
    }
    return value;
  }

  /**
   * Format this log entry as a human-readable string.
   *
   * @return formatted log line
   */
  public String format() {
    final String streamDisplay =
        stream == LogStream.CUSTOM && customStreamName != null
            ? customStreamName
            : stream.toString();
    final StringBuilder stringBuilder =
        new StringBuilder()
            .append('[')
            .append(timestamp)
            .append("] [")
            .append(logLevel)
            .append("] [")
            .append(pluginId)
            .append('/')
            .append(pluginName)
            .append("] ")
            .append(streamDisplay)
            .append(": ")
            .append(message);

    if (!metadata.isEmpty()) {
      stringBuilder.append(" {");
      metadata.forEach(
          (key, value) -> stringBuilder.append(key).append('=').append(value).append(", "));
      stringBuilder.setLength(stringBuilder.length() - 2);
      stringBuilder.append('}');
    }

    return stringBuilder.toString();
  }
}
