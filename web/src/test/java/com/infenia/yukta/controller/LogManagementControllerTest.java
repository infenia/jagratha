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
package com.infenia.yukta.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infenia.yukta.logging.api.LogStream;
import com.infenia.yukta.logging.api.PluginLogEntry;
import com.infenia.yukta.service.LogStreamingService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

/** Tests for LogManagementController with LogStreamingService. */
@SuppressWarnings({"PMD.LawOfDemeter", "PMD.UnitTestShouldIncludeAssert"})
@NoArgsConstructor
class LogManagementControllerTest {

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

  /** Web test client for testing controller endpoints. */
  private WebTestClient webClient;

  /** Mock log streaming service for testing. */
  private LogStreamingService mockService;

  @BeforeEach
  void setUp() {
    mockService = mock(LogStreamingService.class);
    final LogManagementController controller = new LogManagementController(mockService);
    webClient = WebTestClient.bindToController(controller).build();
  }

  @Test
  void testStreamExecutionLogs() {
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

    when(mockService.isConfigured()).thenReturn(true);
    when(mockService.streamExecutionLogs(EXEC_ID)).thenReturn(Flux.just(entry));

    webClient
        .get()
        .uri("/api/sessions/" + SESSION_ID + "/executions/" + EXEC_ID + "/logs")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBodyList(PluginLogEntry.class)
        .isEqualTo(List.of(entry));
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

    when(mockService.isConfigured()).thenReturn(true);
    when(mockService.streamExecutionLogs(EXEC_ID)).thenReturn(Flux.just(entry1, entry2));

    webClient
        .get()
        .uri("/api/sessions/" + SESSION_ID + "/executions/" + EXEC_ID + "/logs")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBodyList(PluginLogEntry.class)
        .hasSize(2);
  }

  @Test
  void testLogServiceNotConfigured() {
    when(mockService.isConfigured()).thenReturn(false);

    webClient
        .get()
        .uri("/api/sessions/" + SESSION_ID + "/executions/" + EXEC_ID + "/logs")
        .exchange()
        .expectStatus()
        .isEqualTo(503);
  }
}
