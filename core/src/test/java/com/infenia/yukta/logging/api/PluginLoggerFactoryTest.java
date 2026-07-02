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

import static org.assertj.core.api.Assertions.assertThat;

import com.infenia.yukta.logging.impl.memory.DefaultPluginLoggerFactory;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PluginLoggerFactoryTest {

  @Test
  void testDefaultMethodUsesFourParamMethod() {
    final var mockWriter = Mockito.mock(PluginLogWriter.class);
    final PluginLoggerFactory factory = new DefaultPluginLoggerFactory(mockWriter);

    final PluginLogger logger1 = factory.create("exec-123", "session-456", "plugin-id");
    final PluginLogger logger2 =
        factory.create("exec-123", "session-456", "plugin-id", "plugin-id");

    assertThat(logger1).isNotNull();
    assertThat(logger2).isNotNull();
  }
}
