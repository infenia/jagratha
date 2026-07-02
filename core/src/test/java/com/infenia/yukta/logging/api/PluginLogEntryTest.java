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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PluginLogEntryTest {

  @Test
  void testCreatePluginLogEntry() {
    final LocalDateTime now = LocalDateTime.now();
    final Map<String, Object> metadata = Map.of("version", "1.0");

    final PluginLogEntry entry =
        new PluginLogEntry(
            "exec-123",
            "session-456",
            "node-001",
            "process-executor",
            "Process Executor",
            LogStream.STDOUT,
            "Hello World",
            now,
            metadata);

    assertThat(entry.executionId()).isEqualTo("exec-123");
    assertThat(entry.sessionId()).isEqualTo("session-456");
    assertThat(entry.nodeId()).isEqualTo("node-001");
    assertThat(entry.pluginId()).isEqualTo("process-executor");
    assertThat(entry.pluginName()).isEqualTo("Process Executor");
    assertThat(entry.stream()).isEqualTo(LogStream.STDOUT);
    assertThat(entry.message()).isEqualTo("Hello World");
    assertThat(entry.timestamp()).isEqualTo(now);
    assertThat(entry.metadata()).containsEntry("version", "1.0");
  }

  @Test
  void testMetadataImmutable() {
    final Map<String, Object> metadata = new HashMap<>();
    metadata.put("key", "value");

    final PluginLogEntry entry =
        new PluginLogEntry(
            "exec-123",
            "session-456",
            "node-001",
            "plugin-id",
            "Plugin Name",
            LogStream.STDERR,
            "Error message",
            LocalDateTime.now(),
            metadata);

    metadata.put("key2", "value2");

    assertThat(entry.metadata()).doesNotContainKey("key2");
  }

  @Test
  void testNullMetadataBecomesEmpty() {
    final PluginLogEntry entry =
        new PluginLogEntry(
            "exec-123",
            "session-456",
            "node-001",
            "plugin-id",
            "Plugin Name",
            LogStream.CUSTOM,
            "Custom message",
            LocalDateTime.now(),
            null);

    assertThat(entry.metadata()).isEmpty();
  }

  @Test
  void testExecutionIdRequired() {
    assertThatThrownBy(
            () ->
                new PluginLogEntry(
                    null,
                    "session-456",
                    "node-001",
                    "plugin-id",
                    "Plugin Name",
                    LogStream.STDOUT,
                    "message",
                    LocalDateTime.now(),
                    Map.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void testPluginIdRequired() {
    assertThatThrownBy(
            () ->
                new PluginLogEntry(
                    "exec-123",
                    "session-456",
                    "node-001",
                    null,
                    "Plugin Name",
                    LogStream.STDOUT,
                    "message",
                    LocalDateTime.now(),
                    Map.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
