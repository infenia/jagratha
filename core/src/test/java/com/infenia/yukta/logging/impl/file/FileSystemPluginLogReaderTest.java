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

import com.infenia.yukta.logging.api.PluginLogEntry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemPluginLogReaderTest {

  @TempDir private Path tempDir;

  private FileSystemPluginLogReader reader;

  @BeforeEach
  void setUp() {
    reader = new FileSystemPluginLogReader(tempDir.toString());
  }

  @Test
  void testReadExecution() throws IOException {
    final Path sessionDir = Files.createDirectories(tempDir.resolve("session-456"));
    Files.writeString(
        sessionDir.resolve("exec-123.log"),
        "2026-07-02T10:00:00 | STDOUT | plugin-1 | Message 1\n"
            + "2026-07-02T10:00:01 | STDERR | plugin-2 | Message 2\n");

    final List<PluginLogEntry> entries = reader.readExecution("exec-123").collectList().block();

    assertThat(entries).hasSize(2);
    assertThat(entries.get(0).message()).isEqualTo("Message 1");
    assertThat(entries.get(1).message()).isEqualTo("Message 2");
  }

  @Test
  void testReadSession() throws IOException {
    final Path sessionDir = Files.createDirectories(tempDir.resolve("session-456"));
    Files.writeString(
        sessionDir.resolve("exec-001.log"), "2026-07-02T10:00:00 | STDOUT | plugin-1 | Exec 1\n");
    Files.writeString(
        sessionDir.resolve("exec-002.log"), "2026-07-02T10:00:01 | STDOUT | plugin-1 | Exec 2\n");

    final List<PluginLogEntry> entries = reader.readSession("session-456").collectList().block();

    assertThat(entries).hasSize(2);
  }

  @Test
  void testReadNonExistentExecution() {
    final List<PluginLogEntry> entries = reader.readExecution("non-existent").collectList().block();

    assertThat(entries).isEmpty();
  }

  @Test
  void testGetRawContent() throws IOException {
    final Path sessionDir = Files.createDirectories(tempDir.resolve("session-456"));
    Files.writeString(
        sessionDir.resolve("exec-123.log"),
        "2026-07-02T10:00:00 | STDOUT | plugin-1 | Message 1\n"
            + "2026-07-02T10:00:01 | STDOUT | plugin-1 | Message 2\n");

    final String content = reader.getRawContent("exec-123").block();

    assertThat(content).contains("Message 1");
    assertThat(content).contains("Message 2");
  }
}
