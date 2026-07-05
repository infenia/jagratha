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

import com.infenia.yukta.logging.api.ExecutionSummary;
import com.infenia.yukta.logging.api.LogLevel;
import com.infenia.yukta.logging.api.LogStream;
import com.infenia.yukta.logging.api.PluginLogEntry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryPluginLogReaderTest {

  private InMemoryPluginLogReader reader;
  private Map<String, List<PluginLogEntry>> storage;

  @BeforeEach
  void setUp() {
    storage = new ConcurrentHashMap<>();
    reader = new InMemoryPluginLogReader(storage);
  }

  private void addEntry(
      final String executionId,
      final String sessionId,
      final String pluginId,
      final String pluginName,
      final String message) {
    final PluginLogEntry entry =
        new PluginLogEntry(
            executionId,
            sessionId,
            pluginId,
            pluginName,
            LogStream.STDOUT,
            message,
            LogLevel.INFO,
            Instant.now());
    storage.computeIfAbsent(executionId, _ -> new ArrayList<>()).add(entry);
  }

  @Test
  void testReadExecution() {
    addEntry("exec-123", "session-456", "plugin-1", "Plugin 1", "Message 1");
    addEntry("exec-123", "session-456", "plugin-2", "Plugin 2", "Message 2");

    final List<PluginLogEntry> entries = reader.readExecution("exec-123").collectList().block();

    assertThat(entries).hasSize(2);
    assertThat(entries)
        .extracting(PluginLogEntry::message)
        .containsExactly("Message 1", "Message 2");
  }

  @Test
  void testReadSession() {
    addEntry("exec-001", "session-456", "plugin-1", "Plugin 1", "Exec 1");
    addEntry("exec-002", "session-456", "plugin-1", "Plugin 1", "Exec 2");
    addEntry("exec-003", "session-789", "plugin-1", "Plugin 1", "Other session");

    final List<PluginLogEntry> entries = reader.readSession("session-456").collectList().block();

    assertThat(entries).hasSize(2);
    assertThat(entries)
        .extracting(PluginLogEntry::executionId)
        .containsExactly("exec-001", "exec-002");
  }

  @Test
  void testListExecutions() {
    addEntry("exec-001", "session-456", "plugin-1", "Plugin 1", "Line 1");
    addEntry("exec-001", "session-456", "plugin-1", "Plugin 1", "Line 2");
    addEntry("exec-002", "session-456", "plugin-1", "Plugin 1", "Line 3");

    final List<ExecutionSummary> summaries = reader.listExecutions("session-456").block();

    assertThat(summaries).hasSize(2);
    assertThat(summaries)
        .extracting(ExecutionSummary::executionId)
        .containsExactly("exec-002", "exec-001"); // Most recent first
    assertThat(summaries).extracting(ExecutionSummary::entryCount).containsExactly(1L, 2L);
  }

  @Test
  void testGetRawContent() {
    addEntry("exec-123", "session-456", "plugin-1", "Plugin 1", "Line 1");
    addEntry("exec-123", "session-456", "plugin-1", "Plugin 1", "Line 2");

    final String content = reader.getRawContent("exec-123").block();

    assertThat(content).contains("Line 1");
    assertThat(content).contains("Line 2");
    assertThat(content).contains("|");
  }

  @Test
  void testReadNonExistentExecution() {
    final List<PluginLogEntry> entries = reader.readExecution("non-existent").collectList().block();

    assertThat(entries).isEmpty();
  }
}
