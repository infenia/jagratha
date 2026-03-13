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
package com.infenia.yukta.mcp;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infenia.yukta.model.api.SessionInfo;
import com.infenia.yukta.service.SessionService;
import com.infenia.yukta.service.TaskTrackerService;
import com.infenia.yukta.service.WorkflowRegistry;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.ObjectMapper;

class AppMcpToolsListSessionsTest {

  private AppMcpTools appMcpTools;
  private SessionService sessionService;
  private TaskTrackerService trackerService;
  private WorkflowRegistry registry;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    sessionService = mock(SessionService.class);
    trackerService = mock(TaskTrackerService.class);
    registry = mock(WorkflowRegistry.class);
    objectMapper = new ObjectMapper();

    appMcpTools =
        new AppMcpTools(
            mock(), // workflowService
            sessionService,
            trackerService,
            registry,
            objectMapper);
  }

  @Test
  void testListSessionsEmpty() {
    when(sessionService.getSessionIds()).thenReturn(Flux.empty());

    Flux<SessionInfo> result = appMcpTools.listSessions();

    StepVerifier.create(result).verifyComplete();
  }

  @Test
  void testListSessionsSingleSession() {
    when(sessionService.getSessionIds()).thenReturn(Flux.just("session-1"));

    final Map<String, Object> config = new HashMap<>();
    config.put("workflows", Map.of("workflow-1", new Object()));
    when(sessionService.getSessionConfig("session-1")).thenReturn(Mono.just(config));

    Flux<SessionInfo> result = appMcpTools.listSessions();

    StepVerifier.create(result)
        .expectNextMatches(
            info ->
                info.sessionId().equals("session-1")
                    && info.workflowCount() == 1
                    && info.status().equals("active"))
        .verifyComplete();
  }

  @Test
  void testListSessionsMultipleSessions() {
    when(sessionService.getSessionIds()).thenReturn(Flux.just("session-1", "session-2"));

    final Map<String, Object> config1 = new HashMap<>();
    config1.put(
        "workflows",
        Map.of(
            "workflow-1", new Object(),
            "workflow-2", new Object()));

    final Map<String, Object> config2 = new HashMap<>();
    config2.put("workflows", Map.of("workflow-3", new Object()));

    when(sessionService.getSessionConfig("session-1")).thenReturn(Mono.just(config1));
    when(sessionService.getSessionConfig("session-2")).thenReturn(Mono.just(config2));

    Flux<SessionInfo> result = appMcpTools.listSessions();

    StepVerifier.create(result)
        .expectNextMatches(
            info ->
                info.sessionId().equals("session-1")
                    && info.workflowCount() == 2
                    && info.status().equals("active"))
        .expectNextMatches(
            info ->
                info.sessionId().equals("session-2")
                    && info.workflowCount() == 1
                    && info.status().equals("active"))
        .verifyComplete();
  }

  @Test
  void testListSessionsHandlesErrorGracefully() {
    when(sessionService.getSessionIds()).thenReturn(Flux.just("session-1", "session-2"));

    when(sessionService.getSessionConfig("session-1"))
        .thenReturn(Mono.error(new RuntimeException("Config load failed")));

    final Map<String, Object> config2 = new HashMap<>();
    config2.put("workflows", Map.of("workflow-1", new Object()));
    when(sessionService.getSessionConfig("session-2")).thenReturn(Mono.just(config2));

    Flux<SessionInfo> result = appMcpTools.listSessions();

    StepVerifier.create(result)
        .expectNextMatches(
            info -> info.sessionId().equals("session-2") && info.workflowCount() == 1)
        .verifyComplete();
  }
}
