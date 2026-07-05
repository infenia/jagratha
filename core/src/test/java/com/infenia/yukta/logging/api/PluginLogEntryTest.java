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

import java.time.Instant;
import org.junit.jupiter.api.Test;

class PluginLogEntryTest {

  @Test
  void testCreatePluginLogEntry() {
    final Instant now = Instant.now();

    final PluginLogEntry entry =
        new PluginLogEntry(
            "exec-123",
            "session-456",
            "process-executor",
            "Process Executor",
            LogStream.STDOUT,
            "Hello World",
            LogLevel.INFO,
            now);

    assertThat(entry.executionId()).isEqualTo("exec-123");
    assertThat(entry.sessionId()).isEqualTo("session-456");
    assertThat(entry.pluginId()).isEqualTo("process-executor");
    assertThat(entry.pluginName()).isEqualTo("Process Executor");
    assertThat(entry.stream()).isEqualTo(LogStream.STDOUT);
    assertThat(entry.message()).isEqualTo("Hello World");
    assertThat(entry.logLevel()).isEqualTo(LogLevel.INFO);
    assertThat(entry.timestamp()).isEqualTo(now);
  }

  @Test
  void testFormat() {
    final Instant now = Instant.ofEpochMilli(1000000000000L);

    final PluginLogEntry entry =
        new PluginLogEntry(
            "exec-123",
            "session-456",
            "plugin-id",
            "Plugin Name",
            LogStream.STDERR,
            "Error message",
            LogLevel.ERROR,
            now);

    final String formatted = entry.format();
    assertThat(formatted).contains("ERROR");
    assertThat(formatted).contains("plugin-id");
    assertThat(formatted).contains("Plugin Name");
    assertThat(formatted).contains("Error message");
    assertThat(formatted).contains("STDERR");
  }

  @Test
  void testDifferentLogLevels() {
    final Instant now = Instant.now();

    final PluginLogEntry debugEntry =
        new PluginLogEntry(
            "exec-123",
            "session-456",
            "plugin-id",
            "Plugin Name",
            LogStream.STDOUT,
            "Debug message",
            LogLevel.DEBUG,
            now);

    final PluginLogEntry warnEntry =
        new PluginLogEntry(
            "exec-123",
            "session-456",
            "plugin-id",
            "Plugin Name",
            LogStream.STDOUT,
            "Warning message",
            LogLevel.WARN,
            now);

    assertThat(debugEntry.logLevel()).isEqualTo(LogLevel.DEBUG);
    assertThat(warnEntry.logLevel()).isEqualTo(LogLevel.WARN);
  }

  @Test
  void testAllFieldsAccessible() {
    final Instant now = Instant.now();
    final PluginLogEntry entry =
        new PluginLogEntry(
            "exec-123",
            "session-456",
            "plugin-id",
            "Plugin Name",
            LogStream.CUSTOM,
            "Custom message",
            LogLevel.WARN,
            now);

    assertThat(entry.executionId()).isEqualTo("exec-123");
    assertThat(entry.sessionId()).isEqualTo("session-456");
    assertThat(entry.pluginId()).isEqualTo("plugin-id");
    assertThat(entry.pluginName()).isEqualTo("Plugin Name");
    assertThat(entry.stream()).isEqualTo(LogStream.CUSTOM);
    assertThat(entry.message()).isEqualTo("Custom message");
    assertThat(entry.logLevel()).isEqualTo(LogLevel.WARN);
    assertThat(entry.timestamp()).isEqualTo(now);
  }

  @Test
  void testEqualsAndHashCode() {
    final Instant now = Instant.ofEpochMilli(1000000000000L);
    final PluginLogEntry entry1 =
        new PluginLogEntry(
            "exec-123",
            "session-456",
            "plugin-id",
            "Plugin Name",
            LogStream.STDOUT,
            "message",
            LogLevel.INFO,
            now);
    final PluginLogEntry entry2 =
        new PluginLogEntry(
            "exec-123",
            "session-456",
            "plugin-id",
            "Plugin Name",
            LogStream.STDOUT,
            "message",
            LogLevel.INFO,
            now);

    assertThat(entry1).isEqualTo(entry2);
    assertThat(entry1.hashCode()).isEqualTo(entry2.hashCode());
  }
}
