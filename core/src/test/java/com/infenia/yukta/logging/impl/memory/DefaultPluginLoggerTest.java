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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infenia.yukta.logging.api.PluginLogEntry;
import com.infenia.yukta.logging.api.PluginLogWriter;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class DefaultPluginLoggerTest {

  private PluginLogWriter mockWriter;
  private DefaultPluginLogger logger;

  @BeforeEach
  void setUp() {
    mockWriter = mock(PluginLogWriter.class);
    when(mockWriter.write(any(PluginLogEntry.class))).thenReturn(Mono.empty());

    logger =
        new DefaultPluginLogger(
            "exec-123", "session-456", "process-executor", "Process Executor", mockWriter);
  }

  @Test
  void testLogStdout() {
    logger.logStdout("Hello World").block();

    verify(mockWriter).write(any(PluginLogEntry.class));
  }

  @Test
  void testLogStdoutWithMetadata() {
    final Map<String, Object> metadata = Map.of("key", "value");

    logger.logStdout("Message", metadata).block();

    verify(mockWriter).write(any(PluginLogEntry.class));
  }

  @Test
  void testLogStderr() {
    logger.logStderr("Error message").block();

    verify(mockWriter).write(any(PluginLogEntry.class));
  }

  @Test
  void testLogCustom() {
    logger.logCustom("CUSTOM", "Custom message").block();

    verify(mockWriter).write(any(PluginLogEntry.class));
  }

  @Test
  void testClose() {
    when(mockWriter.close()).thenReturn(Mono.empty());

    logger.close().block();

    verify(mockWriter).close();
  }
}
