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

/**
 * Factory for creating PluginLogger instances.
 *
 * <p>Implementations should create loggers configured for specific execution contexts.
 */
public interface PluginLoggerFactory {

  /**
   * Create a logger for a specific execution.
   *
   * @param executionId the execution identifier
   * @param sessionId the session identifier
   * @param pluginId the plugin type/identifier
   * @param pluginName the plugin display name (optional)
   * @return a new PluginLogger instance
   */
  PluginLogger create(String executionId, String sessionId, String pluginId, String pluginName);

  /**
   * Create a logger for a specific execution.
   *
   * @param executionId the execution identifier
   * @param sessionId the session identifier
   * @param pluginId the plugin type/identifier
   * @return a new PluginLogger instance
   */
  default PluginLogger create(String executionId, String sessionId, String pluginId) {
    return create(executionId, sessionId, pluginId, pluginId);
  }
}
