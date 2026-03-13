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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.infenia.yukta.model.api.PluginCreationGuide;
import com.infenia.yukta.service.SessionService;
import com.infenia.yukta.service.TaskTrackerService;
import com.infenia.yukta.service.WorkflowRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AppMcpToolsPluginGuideTest {

  private AppMcpTools appMcpTools;

  @BeforeEach
  void setUp() {
    final SessionService sessionService = mock(SessionService.class);
    final TaskTrackerService trackerService = mock(TaskTrackerService.class);
    final WorkflowRegistry registry = mock(WorkflowRegistry.class);
    final ObjectMapper objectMapper = new ObjectMapper();

    appMcpTools =
        new AppMcpTools(
            mock(), // workflowService
            sessionService,
            trackerService,
            registry,
            objectMapper);
  }

  @Test
  void testGetPluginCreationGuideAll() {
    PluginCreationGuide guide = appMcpTools.getPluginCreationGuide("all");

    assertNotNull(guide);
    assertNotNull(guide.architectureOverview());
    assertFalse(guide.architectureOverview().isEmpty());
    assertNotNull(guide.templateCode());
    assertFalse(guide.templateCode().isEmpty());
    assertNotNull(guide.integrationExamples());
    assertNotNull(guide.configurationReference());
    assertNotNull(guide.validationChecklist());
    assertNotNull(guide.testingStrategy());
    assertNotNull(guide.deploymentGuide());
  }

  @Test
  void testGetPluginCreationGuideTrigger() {
    PluginCreationGuide guide = appMcpTools.getPluginCreationGuide("trigger");

    assertNotNull(guide);
    assertNotNull(guide.templateCode());
    assertTrue(guide.templateCode().containsKey("trigger"));
  }

  @Test
  void testGetPluginCreationGuideProcessor() {
    PluginCreationGuide guide = appMcpTools.getPluginCreationGuide("processor");

    assertNotNull(guide);
    assertNotNull(guide.templateCode());
    assertTrue(guide.templateCode().containsKey("processor"));
  }

  @Test
  void testGetPluginCreationGuideTerminal() {
    PluginCreationGuide guide = appMcpTools.getPluginCreationGuide("terminal");

    assertNotNull(guide);
    assertNotNull(guide.templateCode());
    assertTrue(guide.templateCode().containsKey("terminal"));
  }

  @Test
  void testGetPluginCreationGuideDefault() {
    PluginCreationGuide guide = appMcpTools.getPluginCreationGuide(null);

    assertNotNull(guide);
    assertNotNull(guide.templateCode());
    assertFalse(guide.templateCode().isEmpty());
  }

  @Test
  void testGetPluginCreationGuideContainsTemplates() {
    PluginCreationGuide guide = appMcpTools.getPluginCreationGuide("all");

    assertNotNull(guide.templateCode());
    assertTrue(guide.templateCode().containsKey("trigger"));
    assertTrue(guide.templateCode().containsKey("processor"));
    assertTrue(guide.templateCode().containsKey("terminal"));
  }

  @Test
  void testGetPluginCreationGuideAllSectionsPopulated() {
    PluginCreationGuide guide = appMcpTools.getPluginCreationGuide("all");

    assertFalse(guide.architectureOverview().isEmpty());
    assertFalse(guide.integrationExamples().isEmpty());
    assertFalse(guide.configurationReference().isEmpty());
    assertFalse(guide.validationChecklist().isEmpty());
    assertFalse(guide.testingStrategy().isEmpty());
    assertFalse(guide.deploymentGuide().isEmpty());
  }
}
