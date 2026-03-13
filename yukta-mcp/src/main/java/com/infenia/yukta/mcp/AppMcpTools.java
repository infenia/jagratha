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
import com.infenia.yukta.model.api.SessionCreationResponse;
import com.infenia.yukta.model.api.SessionDetails;
import com.infenia.yukta.model.api.SessionInfo;
import com.infenia.yukta.model.monitoring.WorkflowExecutionSummary;
import com.infenia.yukta.model.session.SessionConfigData;
import com.infenia.yukta.model.workflow.WorkflowDefinition;
import com.infenia.yukta.plugin.core.WorkflowPlugin;
import com.infenia.yukta.service.SessionService;
import com.infenia.yukta.service.TaskTrackerService;
import com.infenia.yukta.service.WorkflowRegistry;
import com.infenia.yukta.service.WorkflowService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * MCP (Model Context Protocol) tools for Yukta. Provides tools for interacting with workflows,
 * sessions, and plugins.
 */
@Slf4j
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
   * List all available sessions.
   *
   * @return Flux of session information
   */
  @Tool(description = "List all available Yukta sessions with metadata")
  @SuppressWarnings("unchecked")
  public Flux<SessionInfo> listSessions() {
    return sessionService
        .getSessionIds()
        .flatMap(
            sessionId ->
                sessionService
                    .getSessionConfig(sessionId)
                    .map(
                        config -> {
                          final Map<String, Object> workflows =
                              (Map<String, Object>) config.getOrDefault("workflows", Map.of());
                          final int workflowCount = workflows.size();
                          final LocalDateTime now = LocalDateTime.now();
                          return new SessionInfo(sessionId, workflowCount, now, now, "active");
                        })
                    .onErrorResume(
                        e -> {
                          log.atWarn()
                              .setCause(e)
                              .log(
                                  "Failed to load session config for {}: {}",
                                  sessionId,
                                  e.getMessage());
                          return Mono.empty();
                        }));
  }

  /**
   * Stream session logs with optional filtering.
   *
   * @param sessionId the session identifier
   * @param workflowId optional workflow filter
   * @param executionId optional execution filter
   * @param filterPattern optional regex pattern filter
   * @return Flux of log lines
   */
  @Tool(
      description =
          "Stream session logs with optional filtering by workflow, execution, or pattern")
  public Flux<String> streamSessionLogs(
      final String sessionId,
      final String workflowId,
      final String executionId,
      final String filterPattern) {
    return Mono.fromCallable(
            () -> {
              if (filterPattern != null && !filterPattern.isBlank()) {
                try {
                  Pattern.compile(filterPattern);
                } catch (final PatternSyntaxException e) {
                  throw new IllegalArgumentException("Invalid regex pattern: " + e.getMessage(), e);
                }
              }
              return trackerService.getHistory(sessionId);
            })
        .flatMapIterable(
            history ->
                history.stream()
                    .filter(
                        exec ->
                            (workflowId == null || exec.workflowId().equals(workflowId))
                                && (executionId == null || exec.executionId().equals(executionId)))
                    .map(
                        exec ->
                            String.format(
                                "Execution: %s | Workflow: %s | Status: %s | Start: %s | End: %s",
                                exec.executionId(),
                                exec.workflowId(),
                                exec.status(),
                                exec.startTime(),
                                exec.endTime()))
                    .filter(line -> filterPattern == null || matchesPattern(line, filterPattern))
                    .toList());
  }

  /**
   * Check if text matches the given regex pattern.
   *
   * @param text the text to match
   * @param pattern the regex pattern
   * @return true if matches, false otherwise
   */
  private boolean matchesPattern(final String text, final String pattern) {
    try {
      return Pattern.compile(pattern).matcher(text).find();
    } catch (final PatternSyntaxException e) {
      throw new IllegalArgumentException("Invalid regex pattern: " + e.getMessage(), e);
    }
  }

  /**
   * Get workflow execution logs.
   *
   * @param sessionId the session identifier
   * @param executionId the execution identifier
   * @param filterPattern optional regex pattern filter
   * @return Mono containing formatted logs
   */
  @Tool(description = "Get all logs for a specific workflow execution with optional filtering")
  public Mono<String> getWorkflowExecutionLogs(
      final String sessionId, final String executionId, final String filterPattern) {
    return Mono.fromCallable(
            () -> {
              if (filterPattern != null && !filterPattern.isBlank()) {
                try {
                  Pattern.compile(filterPattern);
                } catch (final PatternSyntaxException e) {
                  throw new IllegalArgumentException("Invalid regex pattern: " + e.getMessage(), e);
                }
              }
              return trackerService.getHistory(sessionId);
            })
        .map(
            history ->
                history.stream()
                    .filter(exec -> exec.executionId().equals(executionId))
                    .findFirst()
                    .orElseThrow(
                        () -> new IllegalArgumentException("Execution not found: " + executionId)))
        .map(
            exec ->
                String.format(
                    "Execution: %s | Workflow: %s | Status: %s | Start: %s | End: %s",
                    exec.executionId(),
                    exec.workflowId(),
                    exec.status(),
                    exec.startTime(),
                    exec.endTime()))
        .map(
            logs ->
                filterPattern != null && !filterPattern.isBlank()
                    ? filterLogsByPattern(logs, filterPattern)
                    : logs);
  }

  /**
   * Filter logs by regex pattern.
   *
   * @param logs the logs to filter
   * @param pattern the regex pattern
   * @return filtered logs
   */
  private String filterLogsByPattern(final String logs, final String pattern) {
    try {
      if (Pattern.compile(pattern).matcher(logs).find()) {
        return logs;
      } else {
        return "";
      }
    } catch (final PatternSyntaxException e) {
      throw new IllegalArgumentException("Invalid regex pattern: " + e.getMessage(), e);
    }
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

  /**
   * Create a new Yukta session with the provided configuration JSON.
   *
   * @param sessionConfigJson JSON string containing session configuration with sessionId,
   *     workflows, and other configuration details
   * @return Mono containing SessionCreationResponse with sessionId, created workflows, warnings,
   *     and success status
   */
  @Tool(description = "Create a new Yukta session with the provided configuration JSON")
  public Mono<SessionCreationResponse> createSession(final String sessionConfigJson) {
    return parseSessionConfig(sessionConfigJson)
        .flatMap(
            config ->
                sessionService
                    .applyConfig(config)
                    .then(
                        Mono.fromCallable(
                            () -> {
                              final List<String> createdWorkflows =
                                  new ArrayList<>(config.workflows().keySet());
                              return new SessionCreationResponse(
                                  config.sessionId(), createdWorkflows, List.of(), true);
                            })))
        .onErrorResume(
            e -> {
              log.atWarn().setCause(e).log("Failed to create session: {}", e.getMessage());
              return Mono.just(
                  new SessionCreationResponse(
                      "", List.of(), List.of("Error creating session: " + e.getMessage()), false));
            });
  }

  /**
   * Parse session configuration from JSON string.
   *
   * @param sessionConfigJson JSON string to parse
   * @return Mono containing parsed SessionConfigData
   */
  private Mono<SessionConfigData> parseSessionConfig(final String sessionConfigJson) {
    return Mono.fromCallable(
            () -> objectMapper.readValue(sessionConfigJson, SessionConfigData.class))
        .onErrorResume(
            e -> {
              log.atWarn()
                  .setCause(e)
                  .log("Failed to parse session configuration JSON: {}", e.getMessage());
              return Mono.error(
                  new IllegalArgumentException(
                      "Invalid JSON format or missing required fields: " + e.getMessage(), e));
            });
  }
}
