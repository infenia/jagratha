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

import jakarta.annotation.Nullable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Represents a single plugin log entry with execution context and stream information.
 *
 * @param executionId unique execution identifier
 * @param sessionId session identifier
 * @param nodeId the node identifier (used to resolve pluginId)
 * @param pluginId the plugin type/identifier
 * @param pluginName the plugin display name
 * @param stream the output stream type
 * @param message the log message
 * @param timestamp when the log was recorded
 * @param metadata additional context metadata
 */
public record PluginLogEntry(
    String executionId,
    String sessionId,
    String nodeId,
    String pluginId,
    @Nullable String pluginName,
    LogStream stream,
    String message,
    LocalDateTime timestamp,
    Map<String, Object> metadata) {

  /** Compact constructor to ensure metadata is immutable and validate required fields. */
  public PluginLogEntry {
    if (executionId == null) {
      throw new IllegalArgumentException("executionId cannot be null");
    }
    if (pluginId == null) {
      throw new IllegalArgumentException("pluginId cannot be null");
    }
    metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
  }
}
