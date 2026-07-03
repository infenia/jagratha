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
package com.infenia.yukta.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infenia.yukta.logging.api.ExecutionSummary;
import com.infenia.yukta.logging.api.LogStream;
import com.infenia.yukta.logging.api.PluginLogEntry;
import com.infenia.yukta.logging.api.PluginLogReader;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Tests for LogStreamingService. */
@SuppressWarnings({
  "PMD.LawOfDemeter",
  "PMD.UnitTestShouldIncludeAssert",
  "PMD.JUnitTestsShouldIncludeAssert",
  "PMD.AvoidDuplicateLiterals"
})
@NoArgsConstructor
class LogStreamingServiceTest {

  /** Test execution ID. */
  private static final String EXEC_ID = "exec-123";

  /** Test session ID. */
  private static final String SESSION_ID = "session-456";

  /** Test node ID. */
  private static final String NODE_ID = "node-001";

  /** Test plugin ID. */
  private static final String PLUGIN_ID = "process-executor";

  /** Test plugin name. */
  private static final String PLUGIN_NAME = "Process Executor";

  /** Mock log reader. */
  private PluginLogReader mockReader;

  /** Service under test. */
  private LogStreamingService service;

  @BeforeEach
  void setUp() {
    mockReader = mock(PluginLogReader.class);
    @SuppressWarnings("unchecked")
    final ObjectProvider<PluginLogReader> provider = mock(ObjectProvider.class);
    when(provider.stream()).thenReturn(java.util.stream.Stream.of(mockReader));
    service = new LogStreamingService(provider);
  }

  @Test
  void testStreamExecutionLogsWithConfiguredReader() {
    final PluginLogEntry entry =
        new PluginLogEntry(
            EXEC_ID,
            SESSION_ID,
            NODE_ID,
            PLUGIN_ID,
            PLUGIN_NAME,
            LogStream.STDOUT,
            "Test message",
            LocalDateTime.now(ZoneId.systemDefault()),
            java.util.Map.of());
    when(mockReader.readExecution(EXEC_ID)).thenReturn(Flux.just(entry));

    StepVerifier.create(service.streamExecutionLogs(EXEC_ID)).expectNext(entry).verifyComplete();
  }

  @Test
  void testStreamExecutionLogsMultipleEntries() {
    final PluginLogEntry entry1 =
        new PluginLogEntry(
            EXEC_ID,
            SESSION_ID,
            NODE_ID,
            "plugin-1",
            "Plugin 1",
            LogStream.STDOUT,
            "Message 1",
            LocalDateTime.now(ZoneId.systemDefault()),
            java.util.Map.of());
    final PluginLogEntry entry2 =
        new PluginLogEntry(
            EXEC_ID,
            SESSION_ID,
            NODE_ID,
            "plugin-2",
            "Plugin 2",
            LogStream.STDERR,
            "Message 2",
            LocalDateTime.now(ZoneId.systemDefault()),
            java.util.Map.of());
    when(mockReader.readExecution(EXEC_ID)).thenReturn(Flux.just(entry1, entry2));

    StepVerifier.create(service.streamExecutionLogs(EXEC_ID))
        .expectNext(entry1, entry2)
        .verifyComplete();
  }

  @Test
  void testStreamExecutionLogsNoReaderConfigured() {
    @SuppressWarnings("unchecked")
    final ObjectProvider<PluginLogReader> emptyProvider = mock(ObjectProvider.class);
    when(emptyProvider.stream()).thenReturn(java.util.stream.Stream.empty());
    final LogStreamingService serviceNoReader = new LogStreamingService(emptyProvider);

    StepVerifier.create(serviceNoReader.streamExecutionLogs(EXEC_ID)).verifyComplete();
  }

  @Test
  void testStreamSessionLogs() {
    final PluginLogEntry entry =
        new PluginLogEntry(
            EXEC_ID,
            SESSION_ID,
            NODE_ID,
            PLUGIN_ID,
            PLUGIN_NAME,
            LogStream.STDOUT,
            "Test message",
            LocalDateTime.now(ZoneId.systemDefault()),
            java.util.Map.of());
    when(mockReader.readSession(SESSION_ID)).thenReturn(Flux.just(entry));

    StepVerifier.create(service.streamSessionLogs(SESSION_ID)).expectNext(entry).verifyComplete();
  }

  @Test
  void testListExecutions() {
    final ExecutionSummary summary =
        new ExecutionSummary(
            EXEC_ID,
            SESSION_ID,
            LocalDateTime.now(ZoneId.systemDefault()),
            LocalDateTime.now(ZoneId.systemDefault()).plusSeconds(10),
            5);
    when(mockReader.listExecutions(SESSION_ID)).thenReturn(Mono.just(List.of(summary)));

    StepVerifier.create(service.listExecutions(SESSION_ID))
        .expectNext(List.of(summary))
        .verifyComplete();
  }

  @Test
  void testListExecutionsNoReaderConfigured() {
    @SuppressWarnings("unchecked")
    final ObjectProvider<PluginLogReader> emptyProvider = mock(ObjectProvider.class);
    when(emptyProvider.stream()).thenReturn(java.util.stream.Stream.empty());
    final LogStreamingService serviceNoReader = new LogStreamingService(emptyProvider);

    StepVerifier.create(serviceNoReader.listExecutions(SESSION_ID))
        .expectNext(List.of())
        .verifyComplete();
  }

  @Test
  void testGetRawLogContent() {
    final String content = "Raw log content";
    when(mockReader.getRawContent(EXEC_ID)).thenReturn(Mono.just(content));

    StepVerifier.create(service.getRawLogContent(EXEC_ID)).expectNext(content).verifyComplete();
  }

  @Test
  void testGetRawLogContentNoReaderConfigured() {
    @SuppressWarnings("unchecked")
    final ObjectProvider<PluginLogReader> emptyProvider = mock(ObjectProvider.class);
    when(emptyProvider.stream()).thenReturn(java.util.stream.Stream.empty());
    final LogStreamingService serviceNoReader = new LogStreamingService(emptyProvider);

    StepVerifier.create(serviceNoReader.getRawLogContent(EXEC_ID)).verifyComplete();
  }

  @Test
  void testIsConfigured() {
    assertThat(service.isConfigured()).isTrue();
  }

  @Test
  void testIsConfiguredNoReader() {
    @SuppressWarnings("unchecked")
    final ObjectProvider<PluginLogReader> emptyProvider = mock(ObjectProvider.class);
    when(emptyProvider.stream()).thenReturn(java.util.stream.Stream.empty());
    final LogStreamingService serviceNoReader = new LogStreamingService(emptyProvider);

    assertThat(serviceNoReader.isConfigured()).isFalse();
  }
}
