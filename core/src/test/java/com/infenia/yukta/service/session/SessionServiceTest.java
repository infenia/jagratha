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
package com.infenia.yukta.service.session;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.infenia.yukta.model.session.SessionConfigData;
import com.infenia.yukta.model.workflow.WorkflowDefinition;
import com.infenia.yukta.service.control.gateway.ControlBusGateway;
import com.infenia.yukta.service.workflow.store.WorkflowDefinitionStore;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

  @Mock private SessionConfigStore configService;
  @Mock private ControlBusGateway controlBus;
  @Mock private WorkflowDefinitionStore workflowDefinitionStore;

  private SessionService sessionService;

  @BeforeEach
  void setUp() {
    sessionService = new SessionService(configService, controlBus, workflowDefinitionStore);
  }

  @Test
  void testApplyConfig() {
    String sessionId = "sess-1";
    WorkflowDefinition workflow =
        new WorkflowDefinition("test-workflow", "desc", List.of(), List.of());
    SessionConfigData data =
        new SessionConfigData(
            sessionId, "desc", "initiator-1", Map.of(), "/path", Map.of("w1", workflow));

    when(configService.applySessionConfig(any())).thenReturn(Mono.empty());
    when(controlBus.compileAndCacheWorkflow(eq(sessionId), any())).thenReturn(Mono.empty());

    StepVerifier.create(sessionService.applyConfig(data)).verifyComplete();
  }

  @Test
  void testApplyConfigPartial() {
    String sessionId = "sess-partial";
    SessionConfigData data =
        new SessionConfigData(sessionId, "desc", "initiator-p", Map.of(), null, Map.of());

    when(configService.applySessionConfig(any())).thenReturn(Mono.empty());

    StepVerifier.create(sessionService.applyConfig(data)).verifyComplete();
  }

  @Test
  void testGetSessionConfig() {
    String sessionId = "sess-1";
    when(configService.getAllConfigs(sessionId)).thenReturn(Mono.just(Map.of("k", "v")));

    StepVerifier.create(sessionService.getSessionConfig(sessionId))
        .expectNextMatches(m -> "v".equals(m.get("k")))
        .verifyComplete();
  }

  @Test
  void testGetSessionWorkflowDelegatesToStore() {
    String sessionId = "sess-wf";
    WorkflowDefinition workflow =
        new WorkflowDefinition("test-workflow", "desc", List.of(), List.of());

    when(workflowDefinitionStore.find(sessionId, "w1")).thenReturn(Mono.just(workflow));

    StepVerifier.create(sessionService.getSessionWorkflow(sessionId, "w1"))
        .expectNext(workflow)
        .verifyComplete();
  }

  @Test
  void testGetSessionWorkflowNotFoundReturnsEmpty() {
    String sessionId = "sess-notfound";

    when(workflowDefinitionStore.find(sessionId, "missing-wf")).thenReturn(Mono.empty());

    StepVerifier.create(sessionService.getSessionWorkflow(sessionId, "missing-wf"))
        .verifyComplete();
  }

  @Test
  void testGetSessionConfigDelegatesStore() {
    String sessionId = "sess-disk-config";
    Map<String, Object> configMap = Map.of("k", "v");

    when(configService.getAllConfigs(sessionId)).thenReturn(Mono.just(configMap));

    StepVerifier.create(sessionService.getSessionConfig(sessionId))
        .expectNextMatches(m -> "v".equals(m.get("k")))
        .verifyComplete();
  }

  @Test
  void testGetSessionIds() {
    when(configService.getSessionIds()).thenReturn(Flux.just("s1", "s2"));
    StepVerifier.create(sessionService.getSessionIds()).expectNext("s1", "s2").verifyComplete();
  }
}
