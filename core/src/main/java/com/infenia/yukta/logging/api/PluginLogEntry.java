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
    Instant timestamp) {

  /**
   * Format this log entry as a human-readable string.
   *
   * @return formatted log line
   */
  public String format() {
    return String.format(
        "[%s] [%s] [%s/%s] %s: %s", timestamp, logLevel, pluginId, pluginName, stream, message);
  }
}
