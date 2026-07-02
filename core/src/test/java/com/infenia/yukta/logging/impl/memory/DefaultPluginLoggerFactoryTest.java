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

import static org.assertj.core.api.Assertions.assertThat;

import com.infenia.yukta.logging.api.PluginLogWriter;
import com.infenia.yukta.logging.api.PluginLogger;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DefaultPluginLoggerFactoryTest {

  @Test
  void testCreateLogger() {
    final PluginLogWriter mockWriter = Mockito.mock(PluginLogWriter.class);
    final DefaultPluginLoggerFactory factory = new DefaultPluginLoggerFactory(mockWriter);

    final PluginLogger logger =
        factory.create("exec-123", "session-456", "plugin-id", "Plugin Name");

    assertThat(logger).isNotNull();
    assertThat(logger).isInstanceOf(DefaultPluginLogger.class);
  }

  @Test
  void testCreateLoggerWithThreeParams() {
    final PluginLogWriter mockWriter = Mockito.mock(PluginLogWriter.class);
    final DefaultPluginLoggerFactory factory = new DefaultPluginLoggerFactory(mockWriter);

    final PluginLogger logger = factory.create("exec-123", "session-456", "plugin-id");

    assertThat(logger).isNotNull();
    assertThat(logger).isInstanceOf(DefaultPluginLogger.class);
  }

  @Test
  void testMultipleLoggersCanBeCreated() {
    final PluginLogWriter mockWriter = Mockito.mock(PluginLogWriter.class);
    final DefaultPluginLoggerFactory factory = new DefaultPluginLoggerFactory(mockWriter);

    final PluginLogger logger1 = factory.create("exec-001", "session-456", "plugin-1", "Plugin 1");
    final PluginLogger logger2 = factory.create("exec-002", "session-456", "plugin-2", "Plugin 2");

    assertThat(logger1).isNotNull();
    assertThat(logger2).isNotNull();
    assertThat(logger1).isNotSameAs(logger2);
  }
}
