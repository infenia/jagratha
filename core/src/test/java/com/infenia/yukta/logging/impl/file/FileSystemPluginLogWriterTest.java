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
package com.infenia.yukta.logging.impl.file;

import static org.assertj.core.api.Assertions.assertThat;

import com.infenia.yukta.logging.api.LogLevel;
import com.infenia.yukta.logging.api.LogStream;
import com.infenia.yukta.logging.api.PluginLogEntry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemPluginLogWriterTest {

  @TempDir private Path tempDir;

  private FileSystemPluginLogWriter writer;

  @BeforeEach
  void setUp() {
    writer = new FileSystemPluginLogWriter(tempDir.toString());
  }

  @Test
  void testWriteSingleEntry() throws IOException {
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

    final Path logFile = tempDir.resolve("session-456").resolve("exec-123.log");
    assertThat(logFile).exists();
    final String content = Files.readString(logFile);
    assertThat(content).contains("Test message");
    assertThat(content).contains("plugin-id");
  }

  @Test
  void testWriteBatch() throws IOException {
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

    final Path logFile = tempDir.resolve("session-456").resolve("exec-123.log");
    assertThat(logFile).exists();
    final String content = Files.readString(logFile);
    assertThat(content).contains("Message 1");
    assertThat(content).contains("Message 2");
  }

  @Test
  void testClose() {
    final var result = writer.close();
    assertThat(result).isNotNull();
  }

  @Test
  void testWithDefaultDir() {
    final var defaultWriter = FileSystemPluginLogWriter.withDefaultDir();
    assertThat(defaultWriter).isNotNull();
  }

  @Test
  void testEmptyBatch() {
    final var result = writer.writeBatch(List.of()).block();
    assertThat(result).isNull();
  }

  @Test
  void testMultipleExecutions() throws IOException {
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

    assertThat(tempDir.resolve("session-456").resolve("exec-001.log")).exists();
    assertThat(tempDir.resolve("session-456").resolve("exec-002.log")).exists();
  }
}
