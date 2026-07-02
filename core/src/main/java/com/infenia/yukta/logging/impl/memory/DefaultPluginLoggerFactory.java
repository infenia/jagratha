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

import com.infenia.yukta.logging.api.PluginLogWriter;
import com.infenia.yukta.logging.api.PluginLogger;
import com.infenia.yukta.logging.api.PluginLoggerFactory;
import lombok.extern.slf4j.Slf4j;

/**
 * Default implementation of PluginLoggerFactory.
 *
 * <p>Creates DefaultPluginLogger instances configured for specific execution contexts.
 */
@Slf4j
public class DefaultPluginLoggerFactory implements PluginLoggerFactory {

  /** The underlying log writer. */
  private final PluginLogWriter writer;

  /**
   * Create a new DefaultPluginLoggerFactory.
   *
   * @param writer the log writer
   */
  public DefaultPluginLoggerFactory(final PluginLogWriter writer) {
    this.writer = writer;
  }

  @Override
  public PluginLogger create(
      final String executionId,
      final String sessionId,
      final String pluginId,
      final String pluginName) {
    log.atDebug()
        .addKeyValue("executionId", executionId)
        .addKeyValue("pluginId", pluginId)
        .log("Creating PluginLogger instance");
    return new DefaultPluginLogger(executionId, sessionId, pluginId, pluginId, pluginName, writer);
  }
}
