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
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Tests for LogManagementController with PluginLogReader. */
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

  /** Mock log reader for testing. */
  private PluginLogReader mockReader;

  @BeforeEach
  void setUp() {
    mockReader = mock(PluginLogReader.class);
    @SuppressWarnings("unchecked")
    final ObjectProvider<PluginLogReader> provider = mock(ObjectProvider.class);
    when(provider.stream()).thenReturn(java.util.stream.Stream.of(mockReader));

    final LogManagementController controller = new LogManagementController(provider);
    webClient = WebTestClient.bindToController(controller).build();
  }

  @Test
  void testGetExecutionLogs() {
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

    webClient
        .get()
        .uri("/api/executions/" + EXEC_ID + "/logs")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBodyList(PluginLogEntry.class)
        .isEqualTo(List.of(entry));
  }

  @Test
  void testGetRawExecutionLogs() {
    when(mockReader.getRawContent(EXEC_ID)).thenReturn(Mono.just("Raw log content"));

    webClient
        .get()
        .uri("/api/executions/" + EXEC_ID + "/logs/raw")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .isEqualTo("Raw log content");
  }

  @Test
  void testGetSessionLogs() {
    final PluginLogEntry entry1 =
        new PluginLogEntry(
            "exec-001",
            SESSION_ID,
            NODE_ID,
            "plugin-id",
            "Plugin",
            LogStream.STDOUT,
            "Message 1",
            LocalDateTime.now(ZoneId.systemDefault()),
            java.util.Map.of());
    final PluginLogEntry entry2 =
        new PluginLogEntry(
            "exec-002",
            SESSION_ID,
            NODE_ID,
            "plugin-id",
            "Plugin",
            LogStream.STDOUT,
            "Message 2",
            LocalDateTime.now(ZoneId.systemDefault()),
            java.util.Map.of());

    when(mockReader.readSession(SESSION_ID)).thenReturn(Flux.just(entry1, entry2));

    webClient
        .get()
        .uri("/api/executions/session/" + SESSION_ID + "/logs")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBodyList(PluginLogEntry.class)
        .hasSize(2);
  }

  @Test
  void testListSessionExecutions() {
    final ExecutionSummary summary =
        new ExecutionSummary(EXEC_ID, SESSION_ID, LocalDateTime.now(), LocalDateTime.now(), 5L);

    when(mockReader.listExecutions(SESSION_ID)).thenReturn(Mono.just(List.of(summary)));

    webClient
        .get()
        .uri("/api/executions/session/" + SESSION_ID + "/executions")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBodyList(ExecutionSummary.class)
        .hasSize(1);
  }

  @Test
  void testLogServiceNotConfigured() {
    @SuppressWarnings("unchecked")
    final ObjectProvider<PluginLogReader> emptyProvider = mock(ObjectProvider.class);
    when(emptyProvider.stream()).thenReturn(java.util.stream.Stream.empty());

    final LogManagementController controllerNoService = new LogManagementController(emptyProvider);
    final WebTestClient clientNoService =
        WebTestClient.bindToController(controllerNoService).build();

    clientNoService
        .get()
        .uri("/api/executions/" + EXEC_ID + "/logs")
        .exchange()
        .expectStatus()
        .is5xxServerError();
  }
}
