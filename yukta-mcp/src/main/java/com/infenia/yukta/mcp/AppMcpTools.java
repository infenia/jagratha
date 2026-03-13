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

import com.infenia.yukta.model.api.ErrorExample;
import com.infenia.yukta.model.api.PluginDetails;
import com.infenia.yukta.model.api.PluginReference;
import com.infenia.yukta.model.api.PluginSummary;
import com.infenia.yukta.model.api.SessionCreationGuide;
import com.infenia.yukta.model.api.SessionDetails;
import com.infenia.yukta.model.monitoring.WorkflowExecutionSummary;
import com.infenia.yukta.model.workflow.WorkflowDefinition;
import com.infenia.yukta.plugin.core.WorkflowPlugin;
import com.infenia.yukta.service.SessionService;
import com.infenia.yukta.service.TaskTrackerService;
import com.infenia.yukta.service.WorkflowRegistry;
import com.infenia.yukta.service.WorkflowService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * MCP (Model Context Protocol) tools for Yukta. Provides tools for interacting with workflows,
 * sessions, and plugins.
 */
@Component
@RequiredArgsConstructor
public class AppMcpTools {

  private final WorkflowService workflowService;
  private final SessionService sessionService;
  private final TaskTrackerService trackerService;
  private final WorkflowRegistry registry;
  private final ObjectMapper objectMapper;

  /**
   * Get details of a specific session.
   *
   * @param sessionId the session identifier
   * @return Mono containing session details
   */
  @Tool(description = "Get details of a specific Yukta session including workflow IDs")
  @SuppressWarnings("unchecked")
  public Mono<SessionDetails> getSessionDetails(final String sessionId) {
    return sessionService
        .getSessionConfig(sessionId)
        .map(
            config -> {
              final Map<String, Object> workflows =
                  (Map<String, Object>) config.getOrDefault("workflows", Map.of());
              return new SessionDetails(sessionId, List.copyOf(workflows.keySet()));
            });
  }

  /**
   * Get workflow definition.
   *
   * @param sessionId the session identifier
   * @param workflowId the workflow identifier
   * @return Mono containing workflow definition
   */
  @Tool(description = "Get the full DAG definition (nodes and edges) of a Yukta workflow")
  public Mono<WorkflowDefinition> getWorkflowDetails(
      final String sessionId, final String workflowId) {
    return sessionService.getSessionWorkflow(sessionId, workflowId);
  }

  /**
   * Trigger a workflow execution.
   *
   * @param sessionId the session identifier
   * @param workflowId the workflow identifier
   * @param payloadJson optional JSON string for trigger payload
   * @return Mono containing the execution ID
   */
  @Tool(description = "Trigger a Yukta workflow with an optional JSON payload")
  public Mono<String> triggerWorkflow(
      final String sessionId, final String workflowId, final String payloadJson) {
    final Mono<String> result;
    if (payloadJson != null && !payloadJson.isBlank()) {
      result = parseAndTrigger(sessionId, workflowId, payloadJson);
    } else {
      result =
          Mono.just(workflowService.runWorkflow(sessionId, workflowId, Map.of()).executionId());
    }
    return result;
  }

  private Mono<String> parseAndTrigger(
      final String sessionId, final String workflowId, final String payloadJson) {
    Mono<String> result;
    try {
      final Map<String, Object> payload =
          objectMapper.readValue(payloadJson, new TypeReference<>() {});
      result = Mono.just(workflowService.runWorkflow(sessionId, workflowId, payload).executionId());
    } catch (final tools.jackson.core.JacksonException e) {
      result = Mono.error(new IllegalArgumentException("Invalid JSON payload: " + e.getMessage()));
    }
    return result;
  }

  /**
   * Get status of a workflow execution.
   *
   * @param sessionId the session identifier
   * @param executionId the execution identifier
   * @return Mono containing concise workflow execution summary
   */
  @Tool(description = "Get the current high-level status of a workflow execution")
  public Mono<WorkflowExecutionSummary> getWorkflowStatus(
      final String sessionId, final String executionId) {
    return Mono.fromCallable(() -> trackerService.getHistory(sessionId))
        .flatMapIterable(list -> list)
        .filter(s -> s.executionId().equals(executionId))
        .next();
  }

  /**
   * List available plugins.
   *
   * @return list of plugin summaries
   */
  @Tool(description = "List all available Yukta workflow plugins")
  public List<PluginSummary> listPlugins() {
    return registry.listPlugins().stream()
        .map(p -> new PluginSummary(p.getType(), p.getCategory()))
        .toList();
  }

  /**
   * Get plugin details.
   *
   * @param type the plugin type
   * @return plugin details
   */
  @Tool(description = "Get full details of a specific Yukta plugin including usage pattern")
  public PluginDetails getPluginDetails(final String type) {
    final WorkflowPlugin plugin = registry.get(type);
    if (plugin == null) {
      throw new IllegalArgumentException("Plugin not found: " + type);
    }
    return new PluginDetails(
        plugin.getType(),
        plugin.getCategory(),
        plugin.getDescription(),
        plugin.getUsagePattern(),
        plugin.getUiDesign().orElse(null),
        plugin.getOutputPorts());
  }

  /**
   * Get comprehensive instructions on how to create a new Yukta session with workflows and plugins.
   *
   * @return SessionCreationGuide with detailed guidance
   */
  @Tool(
      description =
          "Get comprehensive instructions on how to create a new Yukta session with "
              + "workflows and plugins")
  public SessionCreationGuide getSessionCreationInstructions() {
    final String namingConventions =
        "Session IDs must be alphanumeric lowercase with hyphens (e.g., 'my-session-001'). "
            + "Workflow names should follow camelCase or kebab-case (e.g., 'quality-checks', "
            + "'codeReview'). Use descriptive names that indicate the workflow's purpose.";

    final String configurationStructure =
        "A session configuration is a JSON object with a 'workflows' key containing "
            + "workflow definitions. Each workflow definition includes: 'nodes' (array of node "
            + "definitions) and 'edges' (array of directed connections between nodes). "
            + "Each node has 'id', 'type' (plugin type), and 'config' (plugin-specific settings).";

    final String exampleSessionConfig =
        "{\n"
            + "  \"workflows\": {\n"
            + "    \"quality-checks\": {\n"
            + "      \"nodes\": [\n"
            + "        {\n"
            + "          \"id\": \"trigger-check\",\n"
            + "          \"type\": \"webhook-trigger\",\n"
            + "          \"config\": { \"port\": 8080 }\n"
            + "        },\n"
            + "        {\n"
            + "          \"id\": \"run-gradle\",\n"
            + "          \"type\": \"gradle-checker\",\n"
            + "          \"config\": { \"tasks\": [\"test\", \"check\"] }\n"
            + "        },\n"
            + "        {\n"
            + "          \"id\": \"log-results\",\n"
            + "          \"type\": \"log-aggregator\",\n"
            + "          \"config\": { \"format\": \"json\" }\n"
            + "        }\n"
            + "      ],\n"
            + "      \"edges\": [\n"
            + "        { \"from\": \"trigger-check\", \"to\": \"run-gradle\", \"port\": "
            + "\"default\" },\n"
            + "        { \"from\": \"run-gradle\", \"to\": \"log-results\", \"port\": "
            + "\"default\" }\n"
            + "      ]\n"
            + "    }\n"
            + "  }\n"
            + "}";

    final String workflowDefinitionFormat =
        "A workflow definition is a Directed Acyclic Graph (DAG) where: "
            + "Nodes represent processing steps (trigger, processor, or terminal plugins). "
            + "Each node has a unique 'id', a 'type' (matching a registered plugin), and "
            + "'config' for plugin-specific parameters. Edges connect nodes using 'from' "
            + "(source node id), 'to' (target node id), and 'port' (output port name). "
            + "Cycles are not allowed. Execution flows from trigger nodes through processors "
            + "to terminal nodes.";

    // Dynamically fetch available plugins from registry
    final List<PluginReference> availablePlugins =
        registry.listPlugins().stream()
            .map(
                p ->
                    new PluginReference(
                        p.getType(), p.getCategory().toString(), p.getDescription()))
            .toList();

    // Define common errors with resolutions
    final List<ErrorExample> commonErrors =
        List.of(
            new ErrorExample(
                "Plugin Not Found",
                "Used a plugin type that is not registered in the WorkflowRegistry. "
                    + "For example, referencing 'invalid-plugin' when only 'gradle-checker' is "
                    + "available.",
                "Check the available plugins using the listPlugins() tool. Ensure the 'type' "
                    + "field in your node configuration matches exactly (case-sensitive) with a "
                    + "registered plugin."),
            new ErrorExample(
                "Cyclic DAG Detected",
                "The workflow definition contains a cycle (e.g., Node A → B → A), which "
                    + "violates the DAG (Directed Acyclic Graph) requirement.",
                "Review your edges configuration and ensure no node can be reached from itself "
                    + "by following the edges forward. Use acyclic topological ordering."),
            new ErrorExample(
                "Missing Required Configuration Fields",
                "A plugin node is missing required configuration fields. For example, a "
                    + "gradle-checker node missing the 'tasks' array in its config.",
                "Review the plugin's usage pattern using getPluginDetails(pluginType) and ensure "
                    + "all required configuration fields are present in the node's config."));

    return new SessionCreationGuide(
        namingConventions,
        configurationStructure,
        exampleSessionConfig,
        workflowDefinitionFormat,
        availablePlugins,
        commonErrors);
  }
}
