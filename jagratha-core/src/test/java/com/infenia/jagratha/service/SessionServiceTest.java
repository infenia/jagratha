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
package com.infenia.jagratha.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infenia.jagratha.config.AppConfigService;
import com.infenia.jagratha.model.AppConfigData;
import com.infenia.jagratha.model.WorkflowDefinition;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

  @Mock private AppConfigService configService;
  private final ObjectMapper objectMapper = new ObjectMapper();
  @Mock private WorkflowOrchestrator orchestrator;

  private SessionService sessionService;

  @BeforeEach
  void setUp() {
    sessionService = new SessionService(configService, objectMapper, orchestrator);
  }

  @Test
  void testApplyConfigOverrides() {
    String sessionId = "sess-1";
    WorkflowDefinition workflow = new WorkflowDefinition(List.of(), List.of());
    AppConfigData data = new AppConfigData(sessionId, "/path", workflow);

    when(configService.setProjectPath(anyString(), anyString())).thenReturn(Mono.empty());
    when(configService.setWorkflow(anyString(), any())).thenReturn(Mono.empty());
    when(orchestrator.prepareWorkflow(any())).thenReturn(Mono.empty());
    when(configService.getResultLogDir(anyString())).thenReturn(Mono.just("build/results"));
    when(configService.getAllConfigs(anyString())).thenReturn(Mono.just(java.util.Map.of()));

    StepVerifier.create(sessionService.applyConfigOverrides(data))
        .verifyComplete();
  }
}
