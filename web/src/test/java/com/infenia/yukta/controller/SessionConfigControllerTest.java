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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infenia.yukta.dto.request.ConfigRequest;
import com.infenia.yukta.dto.request.WorkflowDefinitionRequest;
import com.infenia.yukta.dto.request.WorkflowDefinitionRequest.NodeRequest;
import com.infenia.yukta.mapper.SessionMapper;
import com.infenia.yukta.model.session.SessionConfigData;
import com.infenia.yukta.model.workflow.WorkflowDefinition;
import com.infenia.yukta.service.session.SessionService;
import java.util.List;
import java.util.Map;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Tests for SessionConfigController. */
@WebFluxTest(SessionConfigController.class)
@NoArgsConstructor
@SuppressWarnings({"PMD.LawOfDemeter", "PMD.TooManyStaticImports", "PMD.TooManyMethods"})
class SessionConfigControllerTest {

  /** Web test client for testing controller endpoints. */
  @Autowired private WebTestClient webTestClient;

  /** Mock service for session operations. */
  @MockitoBean private SessionService sessionService;

  /** Mock mapper for session data transformation. */
  @MockitoBean private SessionMapper sessionMapper;

  @Test
  void testGetSessionDetails() {
    final Map<String, Object> config = Map.of("workflows", Map.of("wf1", Map.of()));
    when(sessionService.getSessionConfig("s1")).thenReturn(Mono.just(config));

    webTestClient
        .get()
        .uri("/api/sessions/s1")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.data")
        .exists()
        .consumeWith(
            response -> {
              assertThat(response.getStatus().value()).isEqualTo(200);
            });

    when(sessionService.getSessionConfig("unknown")).thenReturn(Mono.empty());
    final var result =
        webTestClient
            .get()
            .uri("/api/sessions/unknown")
            .exchange()
            .expectStatus()
            .isNotFound()
            .returnResult();
    assertThat(result.getStatus().value()).isEqualTo(404);
  }

  @Test
  void testGetWorkflow() {
    final WorkflowDefinition def = new WorkflowDefinition("wf1", "desc", List.of(), List.of());
    when(sessionService.getSessionWorkflow("s1", "wf1")).thenReturn(Mono.just(def));

    webTestClient
        .get()
        .uri("/api/sessions/s1/workflows/wf1")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.data")
        .exists()
        .consumeWith(
            response -> {
              assertThat(response.getStatus().value()).isEqualTo(200);
            });

    when(sessionService.getSessionWorkflow("s1", "unknown")).thenReturn(Mono.empty());
    final var result =
        webTestClient
            .get()
            .uri("/api/sessions/s1/workflows/unknown")
            .exchange()
            .expectStatus()
            .isNotFound()
            .returnResult();
    assertThat(result.getStatus().value()).isEqualTo(404);
  }

  @Test
  void testApplyConfig() {
    final WorkflowDefinitionRequest workflow =
        new WorkflowDefinitionRequest(
            "w1", "Test workflow", List.of(new NodeRequest("n1", "gradle", Map.of())), List.of());
    final ConfigRequest request =
        new ConfigRequest(
            "session-1", "desc", "initiator-1", Map.of(), "/new/path", Map.of("w1", workflow));

    lenient()
        .when(sessionMapper.configRequestToSessionConfigData(any()))
        .thenReturn(
            new SessionConfigData(
                "session-1", "desc", "initiator-1", Map.of(), "/new/path", Map.of()));
    when(sessionService.applyConfig(any())).thenReturn(Mono.empty());

    final var result =
        webTestClient
            .post()
            .uri("/api/sessions")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.status")
            .isEqualTo(200)
            .jsonPath("$.message")
            .isEqualTo("Configuration applied successfully")
            .returnResult();
    assertThat(result.getStatus().value()).isEqualTo(200);

    verify(sessionMapper).configRequestToSessionConfigData(any());
    verify(sessionService).applyConfig(any());
  }

  @Test
  void testGetSessionDetailsWithNullSessionId() {
    final var result =
        webTestClient
            .get()
            .uri("/api/sessions/")
            .exchange()
            .expectStatus()
            .isNotFound()
            .returnResult();
    assertThat(result.getStatus().value()).isEqualTo(404);
  }

  @Test
  void testGetWorkflowWithNonExistentSession() {
    when(sessionService.getSessionWorkflow("nonexistent", "wf1")).thenReturn(Mono.empty());

    final var result =
        webTestClient
            .get()
            .uri("/api/sessions/nonexistent/workflows/wf1")
            .exchange()
            .expectStatus()
            .isNotFound()
            .returnResult();
    assertThat(result.getStatus().value()).isEqualTo(404);
  }

  @Test
  void testApplyConfigWithEmptyWorkflows() {
    final ConfigRequest request =
        new ConfigRequest("session-1", "desc", "initiator-1", Map.of(), "/new/path", Map.of());

    final var result =
        webTestClient
            .post()
            .uri("/api/sessions")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isBadRequest()
            .expectBody()
            .jsonPath("$.status")
            .isEqualTo(400)
            .jsonPath("$.message")
            .isEqualTo("Validation failed")
            .returnResult();
    assertThat(result.getStatus().value()).isEqualTo(400);
  }

  @Test
  void testApplyConfigWithMissingSessionId() {
    final WorkflowDefinitionRequest workflow =
        new WorkflowDefinitionRequest(
            "w1", "Test workflow", List.of(new NodeRequest("n1", "gradle", Map.of())), List.of());
    final ConfigRequest request =
        new ConfigRequest(
            null, "desc", "initiator-1", Map.of(), "/new/path", Map.of("w1", workflow));

    final var result =
        webTestClient
            .post()
            .uri("/api/sessions")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isBadRequest()
            .expectBody()
            .jsonPath("$.status")
            .isEqualTo(400)
            .returnResult();
    assertThat(result.getStatus().value()).isEqualTo(400);
  }

  @Test
  void testApplyConfigWithMissingProjectPath() {
    final WorkflowDefinitionRequest workflow =
        new WorkflowDefinitionRequest(
            "w1", "Test workflow", List.of(new NodeRequest("n1", "gradle", Map.of())), List.of());
    final ConfigRequest request =
        new ConfigRequest(
            "session-1", "desc", "initiator-1", Map.of(), null, Map.of("w1", workflow));

    final var result =
        webTestClient
            .post()
            .uri("/api/sessions")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isBadRequest()
            .expectBody()
            .jsonPath("$.status")
            .isEqualTo(400)
            .returnResult();
    assertThat(result.getStatus().value()).isEqualTo(400);
  }

  @Test
  void testGetSessionDetailsWithEmptyWorkflows() {
    final Map<String, Object> config = Map.of("workflows", Map.of());
    when(sessionService.getSessionConfig("s1")).thenReturn(Mono.just(config));

    final var result =
        webTestClient
            .get()
            .uri("/api/sessions/s1")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.data")
            .exists()
            .returnResult();
    assertThat(result.getStatus().value()).isEqualTo(200);
  }

  @Test
  void testGetSessionDetailsSuccess() {
    final Map<String, Object> config =
        Map.of("workflows", Map.of("wf1", Map.of(), "wf2", Map.of()));
    when(sessionService.getSessionConfig("s2")).thenReturn(Mono.just(config));

    final var result =
        webTestClient
            .get()
            .uri("/api/sessions/s2")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.message")
            .isEqualTo("Session details retrieved")
            .jsonPath("$.status")
            .isEqualTo(200)
            .returnResult();
    assertThat(result.getStatus().value()).isEqualTo(200);
  }

  @Test
  void testGetWorkflowSuccess() {
    final WorkflowDefinition def =
        new WorkflowDefinition("wf2", "another desc", List.of(), List.of());
    when(sessionService.getSessionWorkflow("s2", "wf2")).thenReturn(Mono.just(def));

    final var result =
        webTestClient
            .get()
            .uri("/api/sessions/s2/workflows/wf2")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.message")
            .isEqualTo("Workflow retrieved")
            .jsonPath("$.status")
            .isEqualTo(200)
            .returnResult();
    assertThat(result.getStatus().value()).isEqualTo(200);
  }

  @Test
  void testApplyConfigWithEmptyDescription() {
    final WorkflowDefinitionRequest workflow =
        new WorkflowDefinitionRequest(
            "w1", "Test workflow", List.of(new NodeRequest("n1", "gradle", Map.of())), List.of());
    final ConfigRequest request =
        new ConfigRequest(
            "session-1", "", "initiator-1", Map.of(), "/new/path", Map.of("w1", workflow));

    final var result =
        webTestClient
            .post()
            .uri("/api/sessions")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isBadRequest()
            .expectBody()
            .returnResult();
    assertThat(result.getStatus().value()).isEqualTo(400);
  }

  @Test
  void testApplyConfigWithEmptyInitiator() {
    final WorkflowDefinitionRequest workflow =
        new WorkflowDefinitionRequest(
            "w1", "Test workflow", List.of(new NodeRequest("n1", "gradle", Map.of())), List.of());
    final ConfigRequest request =
        new ConfigRequest("session-1", "desc", "", Map.of(), "/new/path", Map.of("w1", workflow));

    final var result =
        webTestClient
            .post()
            .uri("/api/sessions")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isBadRequest()
            .expectBody()
            .returnResult();
    assertThat(result.getStatus().value()).isEqualTo(400);
  }

  @Test
  void testApplyConfigWithMultipleWorkflows() {
    final WorkflowDefinitionRequest workflow1 =
        new WorkflowDefinitionRequest(
            "w1", "First workflow", List.of(new NodeRequest("n1", "gradle", Map.of())), List.of());
    final WorkflowDefinitionRequest workflow2 =
        new WorkflowDefinitionRequest(
            "w2", "Second workflow", List.of(new NodeRequest("n2", "maven", Map.of())), List.of());
    final ConfigRequest request =
        new ConfigRequest(
            "session-multi",
            "multi-desc",
            "initiator-1",
            Map.of(),
            "/multi/path",
            Map.of("w1", workflow1, "w2", workflow2));

    lenient()
        .when(sessionMapper.configRequestToSessionConfigData(any()))
        .thenReturn(
            new SessionConfigData(
                "session-multi", "multi-desc", "initiator-1", Map.of(), "/multi/path", Map.of()));
    when(sessionService.applyConfig(any())).thenReturn(Mono.empty());

    final var result =
        webTestClient
            .post()
            .uri("/api/sessions")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.message")
            .isEqualTo("Configuration applied successfully")
            .returnResult();
    assertThat(result.getStatus().value()).isEqualTo(200);
  }

  @Test
  void testApplyConfigWithNullTags() {
    final WorkflowDefinitionRequest workflow =
        new WorkflowDefinitionRequest(
            "w1", "Test workflow", List.of(new NodeRequest("n1", "gradle", Map.of())), List.of());
    final ConfigRequest request =
        new ConfigRequest(
            "session-1", "desc", "initiator-1", null, "/new/path", Map.of("w1", workflow));

    lenient()
        .when(sessionMapper.configRequestToSessionConfigData(any()))
        .thenReturn(
            new SessionConfigData(
                "session-1", "desc", "initiator-1", Map.of(), "/new/path", Map.of()));
    when(sessionService.applyConfig(any())).thenReturn(Mono.empty());

    final var result =
        webTestClient
            .post()
            .uri("/api/sessions")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .returnResult();
    assertThat(result.getStatus().value()).isEqualTo(200);
  }

  @Test
  void testGetSessionDetailsWithServiceError() {
    when(sessionService.getSessionConfig("error-session"))
        .thenReturn(Mono.error(new RuntimeException("Service error")));

    final var result =
        webTestClient
            .get()
            .uri("/api/sessions/error-session")
            .exchange()
            .expectStatus()
            .is5xxServerError()
            .returnResult();
    assertThat(result.getStatus().value()).isEqualTo(500);
  }

  @Test
  void testGetWorkflowWithServiceError() {
    when(sessionService.getSessionWorkflow("s1", "error-wf"))
        .thenReturn(Mono.error(new RuntimeException("Service error")));

    final var result =
        webTestClient
            .get()
            .uri("/api/sessions/s1/workflows/error-wf")
            .exchange()
            .expectStatus()
            .is5xxServerError()
            .returnResult();
    assertThat(result.getStatus().value()).isEqualTo(500);
  }

  @Test
  void testApplyConfigWithServiceError() {
    final WorkflowDefinitionRequest workflow =
        new WorkflowDefinitionRequest(
            "w1", "Test workflow", List.of(new NodeRequest("n1", "gradle", Map.of())), List.of());
    final ConfigRequest request =
        new ConfigRequest(
            "error-session", "desc", "initiator-1", Map.of(), "/new/path", Map.of("w1", workflow));

    lenient()
        .when(sessionMapper.configRequestToSessionConfigData(any()))
        .thenReturn(
            new SessionConfigData(
                "error-session", "desc", "initiator-1", Map.of(), "/new/path", Map.of()));
    when(sessionService.applyConfig(any()))
        .thenReturn(Mono.error(new RuntimeException("Service error")));

    final var result =
        webTestClient
            .post()
            .uri("/api/sessions")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .is5xxServerError()
            .returnResult();
    assertThat(result.getStatus().value()).isEqualTo(500);
  }

  @Test
  void testListSessions_WithSessions() {
    // Arrange
    final List<String> expectedSessionIds = List.of("session-1", "session-2", "session-3");
    when(sessionService.getSessionIds()).thenReturn(Flux.fromIterable(expectedSessionIds));

    // Act & Assert
    webTestClient
        .get()
        .uri("/api/sessions")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo(200)
        .jsonPath("$.message")
        .isEqualTo("Sessions retrieved successfully")
        .jsonPath("$.data")
        .exists()
        .consumeWith(
            response -> {
              assertThat(response.getStatus().value()).isEqualTo(200);
            });

    verify(sessionService).getSessionIds();
  }

  @Test
  void testListSessions_NoSessions() {
    // Arrange
    when(sessionService.getSessionIds()).thenReturn(Flux.empty());

    // Act & Assert
    webTestClient
        .get()
        .uri("/api/sessions")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo(200)
        .jsonPath("$.message")
        .isEqualTo("Sessions retrieved successfully")
        .jsonPath("$.data")
        .exists()
        .consumeWith(
            response -> {
              assertThat(response.getStatus().value()).isEqualTo(200);
            });

    verify(sessionService).getSessionIds();
  }

  @Test
  void testListSessions_WithError() {
    // Arrange
    when(sessionService.getSessionIds())
        .thenReturn(Flux.error(new RuntimeException("Database connection failed")));

    // Act & Assert
    webTestClient
        .get()
        .uri("/api/sessions")
        .exchange()
        .expectStatus()
        .is5xxServerError()
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo(500)
        .jsonPath("$.errors")
        .exists()
        .consumeWith(
            response -> {
              assertThat(response.getStatus().value()).isEqualTo(500);
            });

    verify(sessionService).getSessionIds();
  }
}
