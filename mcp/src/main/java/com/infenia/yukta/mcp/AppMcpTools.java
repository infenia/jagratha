// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Infenia Private Limited
package com.infenia.yukta.mcp;

import com.infenia.yukta.dto.response.ControlBusStatus;
import com.infenia.yukta.dto.response.PluginCreationGuide;
import com.infenia.yukta.dto.response.PluginDetails;
import com.infenia.yukta.dto.response.PluginSummary;
import com.infenia.yukta.dto.response.SessionCreationGuide;
import com.infenia.yukta.dto.response.SessionCreationResponse;
import com.infenia.yukta.dto.response.SessionDetails;
import com.infenia.yukta.dto.response.SessionInfo;
import com.infenia.yukta.mcp.dto.WorkflowStartResult;
import com.infenia.yukta.mcp.provider.DefaultLogProvider;
import com.infenia.yukta.mcp.provider.DefaultPluginInfoProvider;
import com.infenia.yukta.mcp.provider.DefaultSessionInfoProvider;
import com.infenia.yukta.mcp.provider.DefaultSystemHealthProvider;
import com.infenia.yukta.mcp.provider.DefaultWorkflowExecutionProvider;
import com.infenia.yukta.model.execution.WorkflowExecutionSummary;
import com.infenia.yukta.model.execution.WorkflowProgress;
import com.infenia.yukta.model.workflow.WorkflowDefinition;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * MCP (Model Context Protocol) tools for Yukta. Provides facade for interacting with workflows,
 * sessions, and plugins by delegating to specialized provider classes.
 */
@Component
@RequiredArgsConstructor
@SuppressWarnings("PMD.UseObjectForClearerAPI")
public class AppMcpTools {

  /** Description for session ID parameter. */
  private static final String SESSION_ID_DESC = "The unique identifier of the session";

  /** Provides session information for MCP tools. */
  private final DefaultSessionInfoProvider sessionInfoProvider;

  /** Provides log data for MCP tools. */
  private final DefaultLogProvider logProvider;

  /** Provides workflow execution information for MCP tools. */
  private final DefaultWorkflowExecutionProvider workflowExecutionProvider;

  /** Provides plugin information for MCP tools. */
  private final DefaultPluginInfoProvider pluginInfoProvider;

  /** Provides system health information for MCP tools. */
  private final DefaultSystemHealthProvider systemHealthProvider;

  /**
   * Get details of a specific session.
   *
   * @param sessionId the session identifier
   * @return Mono containing session details
   */
  @McpTool(
      name = "get_session_details",
      title = "Get Session Details",
      description = "Get details of a specific Yukta session including workflow IDs",
      generateOutputSchema = true,
      annotations = @McpTool.McpAnnotations(readOnlyHint = true, openWorldHint = false))
  public Mono<SessionDetails> getSessionDetails(
      @McpToolParam(required = true, description = SESSION_ID_DESC) final String sessionId) {
    return sessionInfoProvider.getSessionDetails(sessionId);
  }

  /**
   * List all available sessions.
   *
   * @return Flux of session information
   */
  @McpTool(
      name = "list_sessions",
      title = "List Sessions",
      description = "List all available Yukta sessions with metadata",
      annotations = @McpTool.McpAnnotations(readOnlyHint = true, openWorldHint = false))
  public Flux<SessionInfo> listSessions() {
    return sessionInfoProvider.listSessions();
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
  @McpTool(
      name = "stream_session_logs",
      title = "Stream Session Logs",
      description =
          "Stream session logs with optional filtering by workflow, execution, or pattern",
      annotations = @McpTool.McpAnnotations(readOnlyHint = true, openWorldHint = false))
  public Flux<String> streamSessionLogs(
      @McpToolParam(required = true, description = SESSION_ID_DESC) final String sessionId,
      @McpToolParam(required = false, description = "Optional workflow identifier to filter logs")
          final String workflowId,
      @McpToolParam(required = false, description = "Optional execution identifier to filter logs")
          final String executionId,
      @McpToolParam(required = false, description = "Optional regex pattern to filter log content")
          final String filterPattern) {
    return logProvider.streamSessionLogs(sessionId, workflowId, executionId, filterPattern);
  }

  /**
   * Get workflow execution logs.
   *
   * @param sessionId the session identifier
   * @param executionId the execution identifier
   * @param filterPattern optional regex pattern filter
   * @return Mono containing formatted logs
   */
  @McpTool(
      name = "get_workflow_execution_logs",
      title = "Get Workflow Execution Logs",
      description = "Get all logs for a specific workflow execution with optional filtering",
      annotations = @McpTool.McpAnnotations(readOnlyHint = true, openWorldHint = false))
  public Mono<String> getWorkflowExecutionLogs(
      @McpToolParam(required = true, description = SESSION_ID_DESC) final String sessionId,
      @McpToolParam(
              required = true,
              description = "The unique identifier of the workflow execution")
          final String executionId,
      @McpToolParam(required = false, description = "Optional regex pattern to filter log content")
          final String filterPattern) {
    return logProvider.getWorkflowExecutionLogs(sessionId, executionId, filterPattern);
  }

  /**
   * Get workflow definition.
   *
   * @param sessionId the session identifier
   * @param workflowId the workflow identifier
   * @return Mono containing workflow definition
   */
  @McpTool(
      name = "get_workflow_details",
      title = "Get Workflow Details",
      description = "Get the full DAG definition (nodes and edges) of a Yukta workflow",
      generateOutputSchema = true,
      annotations = @McpTool.McpAnnotations(readOnlyHint = true, openWorldHint = false))
  public Mono<WorkflowDefinition> getWorkflowDetails(
      @McpToolParam(required = true, description = SESSION_ID_DESC) final String sessionId,
      @McpToolParam(required = true, description = "The unique identifier of the workflow")
          final String workflowId) {
    return workflowExecutionProvider.getWorkflowDetails(sessionId, workflowId);
  }

  /**
   * Start a workflow execution.
   *
   * @param sessionId the session identifier
   * @param workflowId the workflow identifier
   * @return Mono containing the start result with the new execution ID
   */
  @McpTool(
      name = "start_workflow",
      title = "Start Workflow",
      description =
          "Start a Yukta workflow execution. Returns the execution ID to use with "
              + "get_workflow_status and get_execution_logs.",
      generateOutputSchema = true,
      annotations =
          @McpTool.McpAnnotations(
              readOnlyHint = false,
              destructiveHint = false,
              openWorldHint = false))
  public Mono<WorkflowStartResult> startWorkflow(
      @McpToolParam(required = true, description = SESSION_ID_DESC) final String sessionId,
      @McpToolParam(required = true, description = "The unique identifier of the workflow to start")
          final String workflowId) {
    return workflowExecutionProvider.startWorkflow(sessionId, workflowId);
  }

  /**
   * Get the current progress snapshot of a workflow execution.
   *
   * @param executionId the execution identifier
   * @return Mono containing the current workflow progress including per-node task status
   */
  @McpTool(
      name = "get_workflow_status",
      title = "Get Workflow Status",
      description =
          "Get the current progress snapshot of a workflow execution, including overall "
              + "status, per-node task progress, and start/end times. Poll this tool to track "
              + "a running execution.",
      generateOutputSchema = true,
      annotations = @McpTool.McpAnnotations(readOnlyHint = true, openWorldHint = false))
  public Mono<WorkflowProgress> getWorkflowStatus(
      @McpToolParam(
              required = true,
              description = "The unique identifier of the workflow execution")
          final String executionId) {
    return workflowExecutionProvider.getWorkflowStatus(executionId);
  }

  /**
   * Get the execution history of a session.
   *
   * @param sessionId the session identifier
   * @return Mono containing the list of execution summaries for the session
   */
  @McpTool(
      name = "get_workflow_history",
      title = "Get Workflow History",
      description =
          "List all workflow executions of a session with their status and start/end times",
      generateOutputSchema = true,
      annotations = @McpTool.McpAnnotations(readOnlyHint = true, openWorldHint = false))
  public Mono<List<WorkflowExecutionSummary>> getWorkflowHistory(
      @McpToolParam(required = true, description = SESSION_ID_DESC) final String sessionId) {
    return workflowExecutionProvider.getWorkflowHistory(sessionId);
  }

  /**
   * List available plugins.
   *
   * @return list of plugin summaries
   */
  @McpTool(
      name = "list_plugins",
      title = "List Plugins",
      description = "List all available Yukta workflow plugins",
      annotations = @McpTool.McpAnnotations(readOnlyHint = true, openWorldHint = false))
  public Flux<PluginSummary> listPlugins() {
    return Flux.fromIterable(pluginInfoProvider.listPlugins());
  }

  /**
   * Get plugin details.
   *
   * @param type the plugin type
   * @return plugin details
   */
  @McpTool(
      name = "get_plugin_details",
      title = "Get Plugin Details",
      description = "Get full details of a specific Yukta plugin including usage pattern",
      generateOutputSchema = true,
      annotations = @McpTool.McpAnnotations(readOnlyHint = true, openWorldHint = false))
  public Mono<PluginDetails> getPluginDetails(
      @McpToolParam(required = true, description = "The unique plugin type/identifier")
          final String type) {
    return Mono.fromCallable(() -> pluginInfoProvider.getPluginDetails(type));
  }

  /**
   * Get control bus status with comprehensive monitoring information.
   *
   * @param filterType optional filter ("sessions", "plugins", "health", "executions")
   * @return ControlBusStatus with monitoring data
   */
  @McpTool(
      name = "get_control_bus_status",
      title = "Get Control Bus Status",
      description = "Get comprehensive control bus status with monitoring information",
      generateOutputSchema = true,
      annotations = @McpTool.McpAnnotations(readOnlyHint = true, openWorldHint = false))
  public Mono<ControlBusStatus> getControlBusStatus(
      @McpToolParam(
              required = false,
              description = "Optional filter: sessions, plugins, health, or executions")
          final String filterType) {
    return Mono.fromCallable(() -> systemHealthProvider.getControlBusStatus(filterType));
  }

  /**
   * Get comprehensive instructions on how to create a new Yukta session with workflows and plugins.
   *
   * @return SessionCreationGuide with detailed guidance
   */
  @McpTool(
      name = "get_session_creation_instructions",
      title = "Get Session Creation Instructions",
      description =
          "Get comprehensive instructions on how to create a new Yukta session with "
              + "workflows and plugins",
      generateOutputSchema = true,
      annotations = @McpTool.McpAnnotations(readOnlyHint = true, openWorldHint = false))
  public Mono<SessionCreationGuide> getSessionCreationInstructions() {
    return Mono.fromCallable(sessionInfoProvider::getSessionCreationInstructions);
  }

  /**
   * Create a new Yukta session with the provided configuration JSON.
   *
   * @param sessionConfigJson JSON string containing session configuration with sessionId,
   *     workflows, and other configuration details
   * @return Mono containing SessionCreationResponse with sessionId, created workflows, warnings,
   *     and success status
   */
  @McpTool(
      name = "create_session",
      title = "Create Session",
      description = "Create a new Yukta session with the provided configuration JSON",
      generateOutputSchema = true,
      annotations =
          @McpTool.McpAnnotations(
              readOnlyHint = false,
              destructiveHint = false,
              openWorldHint = false))
  public Mono<SessionCreationResponse> createSession(
      @McpToolParam(
              required = true,
              description =
                  "JSON string containing session configuration (sessionId, workflows, etc.)")
          final String sessionConfigJson) {
    return sessionInfoProvider.createSession(sessionConfigJson);
  }

  /**
   * Get comprehensive plugin creation guide.
   *
   * @param templateType optional filter ("trigger", "processor", "terminal", "all")
   * @return PluginCreationGuide with comprehensive guidance
   */
  @McpTool(
      name = "get_plugin_creation_guide",
      title = "Get Plugin Creation Guide",
      description = "Get comprehensive guide for creating Yukta plugins with templates",
      generateOutputSchema = true,
      annotations = @McpTool.McpAnnotations(readOnlyHint = true, openWorldHint = false))
  public Mono<PluginCreationGuide> getPluginCreationGuide(
      @McpToolParam(
              required = false,
              description = "Template type filter: trigger, processor, terminal, or all")
          final String templateType) {
    return Mono.fromCallable(() -> pluginInfoProvider.getPluginCreationGuide(templateType));
  }
}
