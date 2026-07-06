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

import com.infenia.yukta.logging.api.LogLevel;
import com.infenia.yukta.logging.api.LogStream;
import com.infenia.yukta.logging.api.PluginLogEntry;
import com.infenia.yukta.logging.api.PluginLogWriter;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.NoArgsConstructor;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

/** Tests for {@link DefaultPluginLogger}. */
@NoArgsConstructor
@SuppressWarnings({
  "PMD.TooManyMethods",
  "PMD.LocalVariableCouldBeFinal",
  "PMD.AvoidAccessibilityAlteration"
})
class DefaultPluginLoggerTest {

  /** Mock PluginLogWriter for test verification. */
  private PluginLogWriter mockWriter;

  /** DefaultPluginLogger instance under test. */
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
  void testLogStdoutWithMetadata_capturesMetadata() {
    final ArgumentCaptor<PluginLogEntry> captor = ArgumentCaptor.forClass(PluginLogEntry.class);
    final Map<String, Object> metadata = Map.of("userId", "user-123", "requestId", "req-456");

    logger.logStdout("User action logged", metadata).block();

    verify(mockWriter).write(captor.capture());
    final PluginLogEntry entry = captor.getValue();
    Assertions.assertThat(entry.metadata()).containsExactlyInAnyOrderEntriesOf(metadata);
  }

  @Test
  void testLogStderr() {
    logger.logStderr("Error message").block();

    verify(mockWriter).write(any(PluginLogEntry.class));
  }

  @Test
  void testLogCustom() {
    logger.logCustom("docker-build", "Custom message").block();

    verify(mockWriter).write(any(PluginLogEntry.class));
  }

  @Test
  void testLogCustom_preservesCustomStreamName() {
    final ArgumentCaptor<PluginLogEntry> captor = ArgumentCaptor.forClass(PluginLogEntry.class);

    logger.logCustom("docker-build", "Building image").block();

    verify(mockWriter).write(captor.capture());
    final PluginLogEntry entry = captor.getValue();
    Assertions.assertThat(entry.customStreamName()).isEqualTo("docker-build");
  }

  @Test
  void testLogCustomWithMetadata() {
    final Map<String, Object> metadata = Map.of("key", "value");

    logger.logCustom("custom-stream", "Custom message", metadata).block();

    verify(mockWriter).write(any(PluginLogEntry.class));
  }

  @Test
  void testLogCustomWithMetadata_capturesMetadataAndStreamName() {
    final ArgumentCaptor<PluginLogEntry> captor = ArgumentCaptor.forClass(PluginLogEntry.class);
    final Map<String, Object> metadata = Map.of("layer", "final", "size", "2GB");

    logger.logCustom("docker-build", "Layer completed", metadata).block();

    verify(mockWriter).write(captor.capture());
    final PluginLogEntry entry = captor.getValue();
    Assertions.assertThat(entry.customStreamName()).isEqualTo("docker-build");
    Assertions.assertThat(entry.metadata()).containsExactlyInAnyOrderEntriesOf(metadata);
  }

  @Test
  void testLogStderrWithMetadata() {
    final Map<String, Object> metadata = Map.of("error", "details");

    logger.logStderr("Error with metadata", metadata).block();

    verify(mockWriter).write(any(PluginLogEntry.class));
  }

  @Test
  void testClose() {
    when(mockWriter.close()).thenReturn(Mono.empty());

    logger.close().block();

    verify(mockWriter).close();
  }

  @Test
  void adapter_writeBatch_delegatesToUnderlyingWriter() throws Exception {
    // The writeBatch method of ImmutablePluginLogWriterAdapter in DefaultPluginLogger
    // must be tested. Since the adapter is private, we access it via reflection to ensure
    // all paths are covered.

    // Given
    final List<PluginLogEntry> entries = List.of();
    when(mockWriter.writeBatch(any(List.class))).thenReturn(Mono.empty());

    // When - access the adapter via reflection through the logger's writer field
    java.lang.reflect.Field writerField = DefaultPluginLogger.class.getDeclaredField("writer");
    writerField.setAccessible(true);
    PluginLogWriter adapter = (PluginLogWriter) writerField.get(logger);

    // Then - invoke writeBatch on the adapter
    adapter.writeBatch(entries).block();
    verify(mockWriter).writeBatch(entries);
  }

  @Test
  void adapter_write_delegatesToUnderlyingWriter_viaReflection() throws Exception {
    // Verify write() method delegation through direct reflection access to ensure
    // all paths in DefaultPluginLogger's ImmutablePluginLogWriterAdapter are covered.

    // Given
    final Instant now = Instant.now();
    final PluginLogEntry entry =
        new PluginLogEntry(
            "exec-123",
            "session-456",
            "process-executor",
            "Process Executor",
            LogStream.STDERR,
            "Test message",
            LogLevel.ERROR,
            now,
            null,
            null);
    when(mockWriter.write(any(PluginLogEntry.class))).thenReturn(Mono.empty());

    // When - access the adapter via reflection and invoke write
    java.lang.reflect.Field writerField = DefaultPluginLogger.class.getDeclaredField("writer");
    writerField.setAccessible(true);
    PluginLogWriter adapter = (PluginLogWriter) writerField.get(logger);
    adapter.write(entry).block();

    // Then
    verify(mockWriter).write(entry);
  }

  @Test
  void adapter_close_delegatesToUnderlyingWriter_viaReflection() throws Exception {
    // Verify close() method delegation through direct reflection access to ensure
    // all paths in DefaultPluginLogger's ImmutablePluginLogWriterAdapter are covered.

    // Given
    when(mockWriter.close()).thenReturn(Mono.empty());

    // When - access the adapter via reflection and invoke close
    java.lang.reflect.Field writerField = DefaultPluginLogger.class.getDeclaredField("writer");
    writerField.setAccessible(true);
    PluginLogWriter adapter = (PluginLogWriter) writerField.get(logger);
    adapter.close().block();

    // Then
    verify(mockWriter).close();
  }

  @Test
  void pluginLogEntry_format_includesMetadata() {
    final Map<String, Object> metadata =
        Map.of("requestId", "req-123", "status", 200, "duration", "1.5s");
    final Instant timestamp = Instant.parse("2026-07-07T10:30:00Z");
    final PluginLogEntry entry =
        new PluginLogEntry(
            "exec-123",
            "session-456",
            "process-executor",
            "Process Executor",
            LogStream.STDOUT,
            "Request completed",
            LogLevel.INFO,
            timestamp,
            "stdout",
            metadata);

    final String formatted = entry.format();

    Assertions.assertThat(formatted)
        .contains("2026-07-07T10:30:00Z")
        .contains("INFO")
        .contains("process-executor")
        .contains("Process Executor")
        .contains("STDOUT")
        .contains("Request completed")
        .contains("requestId=req-123")
        .contains("status=200")
        .contains("duration=1.5s");
  }

  @Test
  void pluginLogEntry_format_includesCustomStreamName() {
    final Instant timestamp = Instant.parse("2026-07-07T10:35:00Z");
    final PluginLogEntry entry =
        new PluginLogEntry(
            "exec-123",
            "session-456",
            "docker-plugin",
            "Docker Plugin",
            LogStream.CUSTOM,
            "Image built successfully",
            LogLevel.INFO,
            timestamp,
            "docker-build",
            Map.of());

    final String formatted = entry.format();

    Assertions.assertThat(formatted)
        .contains("docker-build")
        .contains("Image built successfully")
        .doesNotContain("CUSTOM");
  }

  @Test
  void pluginLogEntry_format_stdoutWithoutMetadata() {
    final Instant timestamp = Instant.parse("2026-07-07T11:00:00Z");
    final PluginLogEntry entry =
        new PluginLogEntry(
            "exec-123",
            "session-456",
            "test-plugin",
            "Test Plugin",
            LogStream.STDOUT,
            "Simple message",
            LogLevel.INFO,
            timestamp,
            null,
            null);

    final String formatted = entry.format();

    Assertions.assertThat(formatted)
        .contains("2026-07-07T11:00:00Z")
        .contains("INFO")
        .contains("test-plugin")
        .contains("Test Plugin")
        .contains("STDOUT")
        .contains("Simple message")
        .doesNotContain("{");
  }
}
