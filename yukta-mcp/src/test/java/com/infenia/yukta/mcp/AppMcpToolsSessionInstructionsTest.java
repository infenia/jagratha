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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infenia.yukta.model.api.SessionCreationGuide;
import com.infenia.yukta.plugin.core.PluginCategory;
import com.infenia.yukta.plugin.core.WorkflowPlugin;
import com.infenia.yukta.service.SessionService;
import com.infenia.yukta.service.TaskTrackerService;
import com.infenia.yukta.service.WorkflowRegistry;
import com.infenia.yukta.service.WorkflowService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import tools.jackson.databind.ObjectMapper;

class AppMcpToolsSessionInstructionsTest {

  @Mock private WorkflowService workflowService;
  @Mock private SessionService sessionService;
  @Mock private TaskTrackerService trackerService;
  @Mock private WorkflowRegistry registry;
  @Mock private ObjectMapper objectMapper;

  @InjectMocks private AppMcpTools mcpTools;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  void testGetSessionCreationInstructions_ReturnsNonNull() {
    // Setup mock plugins
    final WorkflowPlugin mockPlugin1 = mock(WorkflowPlugin.class);
    when(mockPlugin1.getType()).thenReturn("gradle-checker");
    when(mockPlugin1.getCategory()).thenReturn(PluginCategory.PROCESSOR);
    when(mockPlugin1.getDescription())
        .thenReturn("Executes Gradle build checks (tests, linting, coverage)");

    final WorkflowPlugin mockPlugin2 = mock(WorkflowPlugin.class);
    when(mockPlugin2.getType()).thenReturn("log-aggregator");
    when(mockPlugin2.getCategory()).thenReturn(PluginCategory.TERMINAL);
    when(mockPlugin2.getDescription()).thenReturn("Aggregates and formats workflow logs");

    when(registry.listPlugins()).thenReturn(List.of(mockPlugin1, mockPlugin2));

    // Execute
    final SessionCreationGuide guide = mcpTools.getSessionCreationInstructions();

    // Assert non-null
    assertNotNull(guide, "SessionCreationGuide should not be null");
  }

  @Test
  void testGetSessionCreationInstructions_AllFieldsNotBlank() {
    // Setup mock plugins
    final WorkflowPlugin mockPlugin = mock(WorkflowPlugin.class);
    when(mockPlugin.getType()).thenReturn("gradle-checker");
    when(mockPlugin.getCategory()).thenReturn(PluginCategory.PROCESSOR);
    when(mockPlugin.getDescription())
        .thenReturn("Executes Gradle build checks (tests, linting, coverage)");

    when(registry.listPlugins()).thenReturn(List.of(mockPlugin));

    // Execute
    final SessionCreationGuide guide = mcpTools.getSessionCreationInstructions();

    // Assert all fields are not blank
    assertNotBlank(guide.namingConventions(), "namingConventions should not be blank");
    assertNotBlank(guide.configurationStructure(), "configurationStructure should not be blank");
    assertNotBlank(guide.exampleSessionConfig(), "exampleSessionConfig should not be blank");
    assertNotBlank(
        guide.workflowDefinitionFormat(), "workflowDefinitionFormat should not be blank");
  }

  @Test
  void testGetSessionCreationInstructions_AvailablePluginsPopulated() {
    // Setup mock plugins
    final WorkflowPlugin mockPlugin1 = mock(WorkflowPlugin.class);
    when(mockPlugin1.getType()).thenReturn("gradle-checker");
    when(mockPlugin1.getCategory()).thenReturn(PluginCategory.PROCESSOR);
    when(mockPlugin1.getDescription())
        .thenReturn("Executes Gradle build checks (tests, linting, coverage)");

    final WorkflowPlugin mockPlugin2 = mock(WorkflowPlugin.class);
    when(mockPlugin2.getType()).thenReturn("log-aggregator");
    when(mockPlugin2.getCategory()).thenReturn(PluginCategory.TERMINAL);
    when(mockPlugin2.getDescription()).thenReturn("Aggregates and formats workflow logs");

    when(registry.listPlugins()).thenReturn(List.of(mockPlugin1, mockPlugin2));

    // Execute
    final SessionCreationGuide guide = mcpTools.getSessionCreationInstructions();

    // Assert availablePlugins is populated
    assertNotNull(guide.availablePlugins(), "availablePlugins should not be null");
    assertEquals(2, guide.availablePlugins().size(), "Should have 2 plugins");
    assertEquals(
        "gradle-checker",
        guide.availablePlugins().get(0).type(),
        "First plugin type should be gradle-checker");
    assertEquals(
        "log-aggregator",
        guide.availablePlugins().get(1).type(),
        "Second plugin type should be log-aggregator");
  }

  @Test
  void testGetSessionCreationInstructions_CommonErrorsPopulated() {
    // Setup mock plugins
    final WorkflowPlugin mockPlugin = mock(WorkflowPlugin.class);
    when(mockPlugin.getType()).thenReturn("gradle-checker");
    when(mockPlugin.getCategory()).thenReturn(PluginCategory.PROCESSOR);
    when(mockPlugin.getDescription())
        .thenReturn("Executes Gradle build checks (tests, linting, coverage)");

    when(registry.listPlugins()).thenReturn(List.of(mockPlugin));

    // Execute
    final SessionCreationGuide guide = mcpTools.getSessionCreationInstructions();

    // Assert commonErrors is populated with at least 3 errors
    assertNotNull(guide.commonErrors(), "commonErrors should not be null");
    assertFalse(guide.commonErrors().isEmpty(), "commonErrors should not be empty");
    assertTrue(
        guide.commonErrors().size() >= 3,
        "Should have at least 3 common error examples. Found: " + guide.commonErrors().size());

    // Verify all error examples have non-blank fields
    guide
        .commonErrors()
        .forEach(
            error -> {
              assertNotBlank(error.error(), "error.error should not be blank");
              assertNotBlank(error.cause(), "error.cause should not be blank");
              assertNotBlank(error.resolution(), "error.resolution should not be blank");
            });
  }

  @Test
  void testGetSessionCreationInstructions_NamingConventionsContainsSessionIdInfo() {
    // Setup mock plugins
    final WorkflowPlugin mockPlugin = mock(WorkflowPlugin.class);
    when(mockPlugin.getType()).thenReturn("gradle-checker");
    when(mockPlugin.getCategory()).thenReturn(PluginCategory.PROCESSOR);
    when(mockPlugin.getDescription())
        .thenReturn("Executes Gradle build checks (tests, linting, coverage)");

    when(registry.listPlugins()).thenReturn(List.of(mockPlugin));

    // Execute
    final SessionCreationGuide guide = mcpTools.getSessionCreationInstructions();

    // Assert naming conventions mention session ID
    assertTrue(
        guide.namingConventions().toLowerCase().contains("session"),
        "namingConventions should mention session IDs");
  }

  @Test
  void testGetSessionCreationInstructions_ExampleConfigIsValidJson() {
    // Setup mock plugins
    final WorkflowPlugin mockPlugin = mock(WorkflowPlugin.class);
    when(mockPlugin.getType()).thenReturn("gradle-checker");
    when(mockPlugin.getCategory()).thenReturn(PluginCategory.PROCESSOR);
    when(mockPlugin.getDescription())
        .thenReturn("Executes Gradle build checks (tests, linting, coverage)");

    when(registry.listPlugins()).thenReturn(List.of(mockPlugin));

    // Execute
    final SessionCreationGuide guide = mcpTools.getSessionCreationInstructions();

    // Assert example config looks like JSON (basic check: starts with { and ends with })
    final String exampleConfig = guide.exampleSessionConfig();
    assertTrue(
        exampleConfig.trim().startsWith("{"),
        "exampleSessionConfig should be valid JSON starting with {");
    assertTrue(
        exampleConfig.trim().endsWith("}"),
        "exampleSessionConfig should be valid JSON ending with }");
  }

  private void assertNotBlank(final String value, final String message) {
    assertNotNull(value, message);
    assertFalse(value.isBlank(), message);
  }
}
