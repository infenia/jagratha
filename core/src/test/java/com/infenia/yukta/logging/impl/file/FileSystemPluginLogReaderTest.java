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

import com.infenia.yukta.logging.api.ExecutionSummary;
import com.infenia.yukta.logging.api.PluginLogEntry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for FileSystemPluginLogReader. */
@NoArgsConstructor
@SuppressWarnings({"PMD.TooManyMethods"})
class FileSystemPluginLogReaderTest {

  /** Temporary directory for test files. */
  @TempDir private Path tempDir;

  /** Log reader instance for testing. */
  private FileSystemPluginLogReader reader;

  /** Session ID used in test scenarios. */
  private static final String SESSION_ID = "session-456";

  @BeforeEach
  void setUp() {
    reader = new FileSystemPluginLogReader(tempDir.toString());
  }

  @Test
  void testReadExecution() throws IOException {
    final Path sessionDir = Files.createDirectories(tempDir.resolve(SESSION_ID));
    Files.writeString(
        sessionDir.resolve("exec-123.log"),
        """
        2026-07-02T10:00:00 | STDOUT | plugin-1 | Message 1
        2026-07-02T10:00:01 | STDERR | plugin-2 | Message 2
        """);

    final List<PluginLogEntry> entries = reader.readExecution("exec-123").collectList().block();

    assertThat(entries).hasSize(2);
    assertThat(entries.get(0).message()).isEqualTo("Message 1");
    assertThat(entries.get(1).message()).isEqualTo("Message 2");
  }

  @Test
  void testReadSession() throws IOException {
    final Path sessionDir = Files.createDirectories(tempDir.resolve(SESSION_ID));
    Files.writeString(
        sessionDir.resolve("exec-001.log"), "2026-07-02T10:00:00 | STDOUT | plugin-1 | Exec 1\n");
    Files.writeString(
        sessionDir.resolve("exec-002.log"), "2026-07-02T10:00:01 | STDOUT | plugin-1 | Exec 2\n");

    final List<PluginLogEntry> entries = reader.readSession(SESSION_ID).collectList().block();

    assertThat(entries).hasSize(2);
  }

  @Test
  void testReadNonExistentExecution() {
    final List<PluginLogEntry> entries = reader.readExecution("non-existent").collectList().block();

    assertThat(entries).isEmpty();
  }

  @Test
  void testGetRawContent() throws IOException {
    final Path sessionDir = Files.createDirectories(tempDir.resolve(SESSION_ID));
    Files.writeString(
        sessionDir.resolve("exec-123.log"),
        """
        2026-07-02T10:00:00 | STDOUT | plugin-1 | Message 1
        2026-07-02T10:00:01 | STDOUT | plugin-1 | Message 2
        """);

    final String content = reader.getRawContent("exec-123").block();

    assertThat(content).contains("Message 1");
    assertThat(content).contains("Message 2");
  }

  @Test
  void testListExecutions() throws IOException {
    final Path sessionDir = Files.createDirectories(tempDir.resolve(SESSION_ID));
    Files.writeString(
        sessionDir.resolve("exec-001.log"),
        "2026-07-02T10:00:00+00:00 | STDOUT | plugin-1 | Message 1\n");
    Files.writeString(
        sessionDir.resolve("exec-002.log"),
        "2026-07-02T10:00:05+00:00 | STDOUT | plugin-1 | Message 2\n");

    final List<ExecutionSummary> summaries = reader.listExecutions(SESSION_ID).block();

    assertThat(summaries).hasSize(2);
    assertThat(summaries.get(0).executionId()).isIn("exec-001", "exec-002");
  }

  @Test
  void testListExecutionsNonExistentSession() {
    final List<ExecutionSummary> summaries = reader.listExecutions("non-existent").block();

    assertThat(summaries).isEmpty();
  }

  @Test
  void testReadNonExistentSession() {
    final List<PluginLogEntry> entries = reader.readSession("non-existent").collectList().block();

    assertThat(entries).isEmpty();
  }

  @Test
  void testReadExecutionNonExistentBaseDir() {
    final FileSystemPluginLogReader readerWithBadDir =
        new FileSystemPluginLogReader("/non/existent/path");
    final List<PluginLogEntry> entries =
        readerWithBadDir.readExecution("exec-123").collectList().block();

    assertThat(entries).isEmpty();
  }

  @Test
  void testGetRawContentNonExistentExecution() {
    final String content = reader.getRawContent("non-existent").block();

    assertThat(content).isEmpty();
  }

  @Test
  void testReadExecutionWithMultipleDelimiters() throws IOException {
    final Path sessionDir = Files.createDirectories(tempDir.resolve(SESSION_ID));
    Files.writeString(
        sessionDir.resolve("exec-123.log"),
        "2026-07-02T10:00:00 | STDOUT | plugin-1 | Message with | delimiter\n");

    final List<PluginLogEntry> entries = reader.readExecution("exec-123").collectList().block();

    assertThat(entries).hasSize(1);
    assertThat(entries.get(0).message()).isEqualTo("Message with | delimiter");
  }

  @Test
  void testReadExecutionWithInvalidLogLine() throws IOException {
    final Path sessionDir = Files.createDirectories(tempDir.resolve(SESSION_ID));
    Files.writeString(
        sessionDir.resolve("exec-123.log"),
        """
        invalid line without delimiters
        2026-07-02T10:00:00 | STDOUT | plugin-1 | Valid message
        """);

    final List<PluginLogEntry> entries = reader.readExecution("exec-123").collectList().block();

    assertThat(entries).hasSize(1);
    assertThat(entries.get(0).message()).isEqualTo("Valid message");
  }

  @Test
  void testListExecutionsSorted() throws IOException {
    final Path sessionDir = Files.createDirectories(tempDir.resolve(SESSION_ID));
    Files.writeString(
        sessionDir.resolve("exec-001.log"),
        "2026-07-02T10:00:00+00:00 | STDOUT | plugin-1 | Message 1\n");
    Files.writeString(
        sessionDir.resolve("exec-002.log"),
        "2026-07-02T10:00:10+00:00 | STDOUT | plugin-1 | Message 2\n");

    final List<ExecutionSummary> summaries = reader.listExecutions(SESSION_ID).block();

    assertThat(summaries).hasSize(2);
    assertThat(summaries.get(0).executionId()).isEqualTo("exec-002");
    assertThat(summaries.get(1).executionId()).isEqualTo("exec-001");
  }

  @Test
  void testGetRawContentNonExistentBaseDir() {
    final FileSystemPluginLogReader readerWithBadDir =
        new FileSystemPluginLogReader("/non/existent/path");
    final String content = readerWithBadDir.getRawContent("exec-123").block();

    assertThat(content).isEmpty();
  }

  @Test
  void testReadExecutionEmptyLog() throws IOException {
    final Path sessionDir = Files.createDirectories(tempDir.resolve(SESSION_ID));
    Files.writeString(sessionDir.resolve("exec-empty.log"), "");

    final List<PluginLogEntry> entries = reader.readExecution("exec-empty").collectList().block();

    assertThat(entries).isEmpty();
  }

  @Test
  void testReadSessionWithoutLogExtension() throws IOException {
    final Path sessionDir = Files.createDirectories(tempDir.resolve(SESSION_ID));
    Files.writeString(
        sessionDir.resolve("exec-123.txt"), "2026-07-02T10:00:00 | STDOUT | plugin-1 | Message\n");

    final List<PluginLogEntry> entries = reader.readSession(SESSION_ID).collectList().block();

    assertThat(entries).isEmpty();
  }

  @Test
  void testReadExecutionStreamParsing() throws IOException {
    final Path sessionDir = Files.createDirectories(tempDir.resolve(SESSION_ID));
    Files.writeString(
        sessionDir.resolve("exec-123.log"),
        "2026-07-02T10:00:00 | STDERR | plugin-1 | Error message\n");

    final List<PluginLogEntry> entries = reader.readExecution("exec-123").collectList().block();

    assertThat(entries).hasSize(1);
  }
}
