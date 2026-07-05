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

import com.infenia.yukta.logging.api.LogLevel;
import com.infenia.yukta.logging.api.LogStream;
import com.infenia.yukta.logging.api.PluginLogEntry;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryPluginLogWriterTest {

  private InMemoryPluginLogWriter writer;

  @BeforeEach
  void setUp() {
    final ConcurrentHashMap<String, List<PluginLogEntry>> storage = new ConcurrentHashMap<>();
    writer = new InMemoryPluginLogWriter(storage);
  }

  @Test
  void testWriteSingleEntry() {
    final PluginLogEntry entry =
        new PluginLogEntry(
            "exec-123",
            "session-456",
            "plugin-id",
            "Plugin",
            LogStream.STDOUT,
            "Test message",
            LogLevel.INFO,
            Instant.now());

    writer.write(entry).block();

    final List<PluginLogEntry> stored = writer.getStorage().get("exec-123");
    assertThat(stored).hasSize(1);
    assertThat(stored.getFirst()).isEqualTo(entry);
  }

  @Test
  void testWriteBatch() {
    final PluginLogEntry entry1 =
        new PluginLogEntry(
            "exec-123",
            "session-456",
            "plugin-1",
            "Plugin 1",
            LogStream.STDOUT,
            "Message 1",
            LogLevel.INFO,
            Instant.now());
    final PluginLogEntry entry2 =
        new PluginLogEntry(
            "exec-123",
            "session-456",
            "plugin-2",
            "Plugin 2",
            LogStream.STDERR,
            "Message 2",
            LogLevel.ERROR,
            Instant.now());

    writer.writeBatch(List.of(entry1, entry2)).block();

    final List<PluginLogEntry> stored = writer.getStorage().get("exec-123");
    assertThat(stored).hasSize(2);
    assertThat(stored).containsExactly(entry1, entry2);
  }

  @Test
  void testMultipleExecutions() {
    final PluginLogEntry entry1 =
        new PluginLogEntry(
            "exec-001",
            "session-456",
            "plugin-id",
            "Plugin",
            LogStream.STDOUT,
            "Exec 1",
            LogLevel.INFO,
            Instant.now());
    final PluginLogEntry entry2 =
        new PluginLogEntry(
            "exec-002",
            "session-456",
            "plugin-id",
            "Plugin",
            LogStream.STDOUT,
            "Exec 2",
            LogLevel.INFO,
            Instant.now());

    writer.write(entry1).block();
    writer.write(entry2).block();

    assertThat(writer.getStorage()).hasSize(2);
    assertThat(writer.getStorage().get("exec-001")).hasSize(1);
    assertThat(writer.getStorage().get("exec-002")).hasSize(1);
  }

  @Test
  void testClose() {
    writer.close().block();
    // Should complete without error
  }
}
