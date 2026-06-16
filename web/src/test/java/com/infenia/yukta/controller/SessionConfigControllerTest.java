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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infenia.yukta.mapper.AppConfigMapper;
import com.infenia.yukta.model.api.ConfigRequest;
import com.infenia.yukta.model.api.WorkflowDefinitionRequest;
import com.infenia.yukta.model.api.WorkflowDefinitionRequest.NodeRequest;
import com.infenia.yukta.model.session.SessionConfigData;
import com.infenia.yukta.model.workflow.WorkflowDefinition;
import com.infenia.yukta.service.session.SessionService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@WebFluxTest(SessionConfigController.class)
class SessionConfigControllerTest {

  @Autowired private WebTestClient webTestClient;

  @MockitoBean private SessionService sessionService;
  @MockitoBean private AppConfigMapper configMapper;

  @Test
  void testGetSessionDetails() {
    Map<String, Object> config = Map.of("workflows", Map.of("wf1", Map.of()));
    when(sessionService.getSessionConfig("s1")).thenReturn(Mono.just(config));

    webTestClient
        .get()
        .uri("/api/sessions/s1")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.data.workflowIds[0]")
        .isEqualTo("wf1");

    when(sessionService.getSessionConfig("unknown")).thenReturn(Mono.empty());
    webTestClient.get().uri("/api/sessions/unknown").exchange().expectStatus().isNotFound();
  }

  @Test
  void testGetWorkflow() {
    WorkflowDefinition def = new WorkflowDefinition("wf1", "desc", List.of(), List.of());
    when(sessionService.getSessionWorkflow("s1", "wf1")).thenReturn(Mono.just(def));

    webTestClient
        .get()
        .uri("/api/sessions/s1/workflows/wf1")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.data.description")
        .isEqualTo("desc");

    when(sessionService.getSessionWorkflow("s1", "unknown")).thenReturn(Mono.empty());
    webTestClient
        .get()
        .uri("/api/sessions/s1/workflows/unknown")
        .exchange()
        .expectStatus()
        .isNotFound();
  }

  @Test
  void testApplyConfig() {
    WorkflowDefinitionRequest workflow =
        new WorkflowDefinitionRequest(
            "w1", "Test workflow", List.of(new NodeRequest("n1", "gradle", Map.of())), List.of());
    ConfigRequest request =
        new ConfigRequest(
            "session-1", "desc", "initiator-1", Map.of(), "/new/path", Map.of("w1", workflow));

    lenient()
        .when(configMapper.toData(any()))
        .thenReturn(
            new SessionConfigData(
                "session-1", "desc", "initiator-1", Map.of(), "/new/path", Map.of()));
    when(sessionService.applyConfig(any())).thenReturn(Mono.empty());

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
        .isEqualTo("Configuration applied successfully");

    verify(configMapper).toData(any());
    verify(sessionService).applyConfig(any());
  }

  @Test
  void testGetSessionDetailsWithNullSessionId() {
    webTestClient.get().uri("/api/sessions/").exchange().expectStatus().isNotFound();
  }

  @Test
  void testGetWorkflowWithNonExistentSession() {
    when(sessionService.getSessionWorkflow("nonexistent", "wf1")).thenReturn(Mono.empty());

    webTestClient
        .get()
        .uri("/api/sessions/nonexistent/workflows/wf1")
        .exchange()
        .expectStatus()
        .isNotFound();
  }

  @Test
  void testApplyConfigWithEmptyWorkflows() {
    ConfigRequest request =
        new ConfigRequest("session-1", "desc", "initiator-1", Map.of(), "/new/path", Map.of());

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
        .isEqualTo("Validation failed");
  }

  @Test
  void testApplyConfigWithMissingSessionId() {
    WorkflowDefinitionRequest workflow =
        new WorkflowDefinitionRequest(
            "w1", "Test workflow", List.of(new NodeRequest("n1", "gradle", Map.of())), List.of());
    ConfigRequest request =
        new ConfigRequest(
            null, "desc", "initiator-1", Map.of(), "/new/path", Map.of("w1", workflow));

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
        .isEqualTo(400);
  }

  @Test
  void testApplyConfigWithMissingProjectPath() {
    WorkflowDefinitionRequest workflow =
        new WorkflowDefinitionRequest(
            "w1", "Test workflow", List.of(new NodeRequest("n1", "gradle", Map.of())), List.of());
    ConfigRequest request =
        new ConfigRequest(
            "session-1", "desc", "initiator-1", Map.of(), null, Map.of("w1", workflow));

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
        .isEqualTo(400);
  }

  @Test
  void testGetSessionDetailsWithEmptyWorkflows() {
    Map<String, Object> config = Map.of("workflows", Map.of());
    when(sessionService.getSessionConfig("s1")).thenReturn(Mono.just(config));

    webTestClient
        .get()
        .uri("/api/sessions/s1")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.data.workflowIds")
        .isArray();
  }

  @Test
  void testGetSessionDetailsSuccess() {
    Map<String, Object> config = Map.of("workflows", Map.of("wf1", Map.of(), "wf2", Map.of()));
    when(sessionService.getSessionConfig("s2")).thenReturn(Mono.just(config));

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
        .isEqualTo(200);
  }

  @Test
  void testGetWorkflowSuccess() {
    WorkflowDefinition def = new WorkflowDefinition("wf2", "another desc", List.of(), List.of());
    when(sessionService.getSessionWorkflow("s2", "wf2")).thenReturn(Mono.just(def));

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
        .isEqualTo(200);
  }

  @Test
  void testApplyConfigWithEmptyDescription() {
    WorkflowDefinitionRequest workflow =
        new WorkflowDefinitionRequest(
            "w1", "Test workflow", List.of(new NodeRequest("n1", "gradle", Map.of())), List.of());
    ConfigRequest request =
        new ConfigRequest(
            "session-1", "", "initiator-1", Map.of(), "/new/path", Map.of("w1", workflow));

    webTestClient
        .post()
        .uri("/api/sessions")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest();
  }

  @Test
  void testApplyConfigWithEmptyInitiator() {
    WorkflowDefinitionRequest workflow =
        new WorkflowDefinitionRequest(
            "w1", "Test workflow", List.of(new NodeRequest("n1", "gradle", Map.of())), List.of());
    ConfigRequest request =
        new ConfigRequest("session-1", "desc", "", Map.of(), "/new/path", Map.of("w1", workflow));

    webTestClient
        .post()
        .uri("/api/sessions")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest();
  }

  @Test
  void testApplyConfigWithMultipleWorkflows() {
    WorkflowDefinitionRequest workflow1 =
        new WorkflowDefinitionRequest(
            "w1", "First workflow", List.of(new NodeRequest("n1", "gradle", Map.of())), List.of());
    WorkflowDefinitionRequest workflow2 =
        new WorkflowDefinitionRequest(
            "w2", "Second workflow", List.of(new NodeRequest("n2", "maven", Map.of())), List.of());
    ConfigRequest request =
        new ConfigRequest(
            "session-multi",
            "multi-desc",
            "initiator-1",
            Map.of(),
            "/multi/path",
            Map.of("w1", workflow1, "w2", workflow2));

    lenient()
        .when(configMapper.toData(any()))
        .thenReturn(
            new SessionConfigData(
                "session-multi", "multi-desc", "initiator-1", Map.of(), "/multi/path", Map.of()));
    when(sessionService.applyConfig(any())).thenReturn(Mono.empty());

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
        .isEqualTo("Configuration applied successfully");
  }

  @Test
  void testApplyConfigWithNullTags() {
    WorkflowDefinitionRequest workflow =
        new WorkflowDefinitionRequest(
            "w1", "Test workflow", List.of(new NodeRequest("n1", "gradle", Map.of())), List.of());
    ConfigRequest request =
        new ConfigRequest(
            "session-1", "desc", "initiator-1", null, "/new/path", Map.of("w1", workflow));

    lenient()
        .when(configMapper.toData(any()))
        .thenReturn(
            new SessionConfigData(
                "session-1", "desc", "initiator-1", Map.of(), "/new/path", Map.of()));
    when(sessionService.applyConfig(any())).thenReturn(Mono.empty());

    webTestClient
        .post()
        .uri("/api/sessions")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk();
  }

  @Test
  void testGetSessionDetailsWithServiceError() {
    when(sessionService.getSessionConfig("error-session"))
        .thenReturn(Mono.error(new RuntimeException("Service error")));

    webTestClient
        .get()
        .uri("/api/sessions/error-session")
        .exchange()
        .expectStatus()
        .is5xxServerError();
  }

  @Test
  void testGetWorkflowWithServiceError() {
    when(sessionService.getSessionWorkflow("s1", "error-wf"))
        .thenReturn(Mono.error(new RuntimeException("Service error")));

    webTestClient
        .get()
        .uri("/api/sessions/s1/workflows/error-wf")
        .exchange()
        .expectStatus()
        .is5xxServerError();
  }

  @Test
  void testApplyConfigWithServiceError() {
    WorkflowDefinitionRequest workflow =
        new WorkflowDefinitionRequest(
            "w1", "Test workflow", List.of(new NodeRequest("n1", "gradle", Map.of())), List.of());
    ConfigRequest request =
        new ConfigRequest(
            "error-session", "desc", "initiator-1", Map.of(), "/new/path", Map.of("w1", workflow));

    lenient()
        .when(configMapper.toData(any()))
        .thenReturn(
            new SessionConfigData(
                "error-session", "desc", "initiator-1", Map.of(), "/new/path", Map.of()));
    when(sessionService.applyConfig(any()))
        .thenReturn(Mono.error(new RuntimeException("Service error")));

    webTestClient
        .post()
        .uri("/api/sessions")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .is5xxServerError();
  }
}
