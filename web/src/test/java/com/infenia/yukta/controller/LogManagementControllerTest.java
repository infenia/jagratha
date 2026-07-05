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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infenia.yukta.logging.api.PluginLogStore;
import com.infenia.yukta.service.control.gateway.ControlBusGateway;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

/** Tests for LogManagementController with ControlBusGateway. */
@SuppressWarnings({"PMD.LawOfDemeter", "PMD.UnitTestShouldIncludeAssert"})
@NoArgsConstructor
class LogManagementControllerTest {

  /** Test execution ID. */
  private static final String EXEC_ID = "exec-123";

  /** Test session ID. */
  private static final String SESSION_ID = "session-456";

  /** Web test client for testing controller endpoints. */
  private WebTestClient webClient;

  /** Mock control bus gateway for testing. */
  private ControlBusGateway mockControlBus;

  /** Mock plugin log store for testing. */
  private PluginLogStore mockLogStore;

  @BeforeEach
  void setUp() {
    mockControlBus = mock(ControlBusGateway.class);
    mockLogStore = mock(PluginLogStore.class);
    final LogManagementController controller =
        new LogManagementController(mockControlBus, mockLogStore);
    webClient = WebTestClient.bindToController(controller).build();
  }

  @Test
  void testStreamExecutionLogs() {
    when(mockControlBus.watchLogs(SESSION_ID, EXEC_ID))
        .thenReturn(Flux.just("log line 1", "log line 2"));

    final var result =
        webClient
            .get()
            .uri("/api/sessions/" + SESSION_ID + "/executions/" + EXEC_ID + "/logs")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus()
            .isOk()
            .returnResult();
    assertThat(result.getStatus().value()).isEqualTo(200);
  }

  @Test
  void testStreamExecutionLogsMultipleLines() {
    when(mockControlBus.watchLogs(SESSION_ID, EXEC_ID))
        .thenReturn(Flux.just("line 1", "line 2", "line 3", "line 4", "line 5"));

    final var result =
        webClient
            .get()
            .uri("/api/sessions/" + SESSION_ID + "/executions/" + EXEC_ID + "/logs")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
            .returnResult();
    assertThat(result.getStatus().value()).isEqualTo(200);
  }
}
