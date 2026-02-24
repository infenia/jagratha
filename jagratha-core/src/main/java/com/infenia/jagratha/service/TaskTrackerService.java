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

import com.infenia.jagratha.model.TaskProgress;
import com.infenia.jagratha.model.WorkflowProgress;
import com.infenia.jagratha.validation.NodeId;
import com.infenia.jagratha.validation.SessionId;
import com.infenia.jagratha.validation.WorkflowId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/** Service for tracking the progress of workflows and tasks. */
@Service
@Validated
public class TaskTrackerService {

  private final Map<String, WorkflowState> executionStates = new ConcurrentHashMap<>();
  private final Map<String, Sinks.Many<String>> executionLogSinks = new ConcurrentHashMap<>();
  private final Map<String, Sinks.Many<WorkflowProgress>> executionStatusSinks =
      new ConcurrentHashMap<>();
  private final Map<String, String> latestExecutionBySession = new ConcurrentHashMap<>();
  private final Map<String, String> latestExecutionByWorkflow = new ConcurrentHashMap<>();
  private final Map<String, List<String>> executionsBySession = new ConcurrentHashMap<>();

  /** Default constructor. */
  public TaskTrackerService() {
    // Standard service initialization
  }

  /**
   * Start tracking a new workflow for a session.
   *
   * @param sessionId the session identifier
   * @param workflowId the workflow identifier
   * @param nodeIds the list of node IDs in the workflow DAG
   * @return a Mono that completes when the workflow tracking is started
   */
  public Mono<Void> startWorkflow(
      @SessionId final String sessionId,
      @WorkflowId final String workflowId,
      @NotEmpty final List<String> nodeIds) {
    return startWorkflow(sessionId, workflowId, java.util.UUID.randomUUID().toString(), nodeIds);
  }

  /**
   * Start tracking a new workflow for a session with a unique execution ID.
   *
   * @param sessionId the session identifier
   * @param workflowId the workflow identifier
   * @param executionId the unique execution identifier
   * @param nodeIds the list of node IDs in the workflow DAG
   * @return a Mono that completes when the workflow tracking is started
   */
  public Mono<Void> startWorkflow(
      @SessionId final String sessionId,
      @WorkflowId final String workflowId,
      @NotBlank final String executionId,
      @NotEmpty final List<String> nodeIds) {
    return Mono.fromRunnable(
        () -> {
          final List<TaskProgress> initialTasks =
              nodeIds.stream()
                  .map(id -> new TaskProgress(id, "", "PENDING", null, null, Map.of()))
                  .toList();
          executionStates.put(
              executionId,
              new WorkflowState(
                  sessionId,
                  workflowId,
                  executionId,
                  "RUNNING",
                  new ArrayList<>(initialTasks),
                  LocalDateTime.now()));

          final Sinks.Many<String> sink = Sinks.many().multicast().directBestEffort();
          executionLogSinks.put(executionId, sink);

          final Sinks.Many<WorkflowProgress> statusSink =
              Sinks.many().multicast().directBestEffort();
          executionStatusSinks.put(executionId, statusSink);

          latestExecutionBySession.put(sessionId, executionId);
          latestExecutionByWorkflow.put(getWorkflowKey(sessionId, workflowId), executionId);
          executionsBySession
              .computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>())
              .add(executionId);
        });
  }

  /**
   * Update the status of a specific node.
   *
   * @param executionId the unique execution identifier
   * @param nodeId the ID of the node
   * @param module the module name
   * @param status the new status
   * @return a Mono that completes when the task status is updated
   */
  @SuppressWarnings("PMD.UseObjectForClearerAPI")
  public Mono<Void> updateTaskStatus(
      @NotBlank final String executionId,
      @NodeId final String nodeId,
      @NotBlank @Size(max = 256) final String module,
      @NotBlank @Size(max = 256) final String status) {
    return updateTaskStatus(executionId, nodeId, module, status, Map.of());
  }

  /**
   * Update the status and metadata of a specific node.
   *
   * @param executionId the unique execution identifier
   * @param nodeId the ID of the node
   * @param module the module name
   * @param status the new status
   * @param metadata the task metadata
   * @return a Mono that completes when the task status is updated
   */
  @SuppressWarnings("PMD.UseObjectForClearerAPI")
  public Mono<Void> updateTaskStatus(
      @NotBlank final String executionId,
      @NodeId final String nodeId,
      @NotBlank @Size(max = 256) final String module,
      @NotBlank @Size(max = 256) final String status,
      @NotNull final Map<String, Object> metadata) {
    return Mono.fromRunnable(
        () -> {
          final WorkflowState state = executionStates.get(executionId);
          if (state != null) {
            state.updateTask(nodeId, module, status, metadata);
            notifyStatusChange(executionId);
          }
        });
  }

  /**
   * Finish the workflow.
   *
   * @param executionId the unique execution identifier
   * @param status the final status
   * @return a Mono that completes when the workflow is finished
   */
  public Mono<Void> finishWorkflow(
      @NotBlank final String executionId, @NotBlank @Size(max = 256) final String status) {
    return Mono.fromRunnable(
        () -> {
          final WorkflowState state = executionStates.get(executionId);
          if (state != null) {
            state.setStatus(status);
            state.setEndTime(LocalDateTime.now());
            notifyStatusChange(executionId);
          }
        });
  }

  /**
   * Append a log line to the live output.
   *
   * @param executionId the unique execution identifier
   * @param line the log line
   * @return a Mono that completes when the log line is appended
   */
  public Mono<Void> appendLog(
      @NotBlank final String executionId, @NotBlank @Size(max = 16_384) final String line) {
    return Mono.fromRunnable(
        () -> {
          final Sinks.Many<String> sink = executionLogSinks.get(executionId);
          if (sink != null) {
            sink.tryEmitNext(line);
          }
        });
  }

  /**
   * Get the current progress of a workflow for a session.
   *
   * @param sessionId the session identifier
   * @param workflowId the workflow identifier
   * @return the workflow progress
   */
  public WorkflowProgress getProgress(
      @SessionId final String sessionId, @WorkflowId final String workflowId) {
    final String executionId = latestExecutionByWorkflow.get(getWorkflowKey(sessionId, workflowId));
    return executionId != null ? getExecutionProgress(executionId) : null;
  }

  /**
   * Get the current progress of the latest workflow for a session.
   *
   * @param sessionId the session identifier
   * @return the workflow progress
   */
  public WorkflowProgress getProgress(@SessionId final String sessionId) {
    final String executionId = latestExecutionBySession.get(sessionId);
    return executionId != null ? getExecutionProgress(executionId) : null;
  }

  /**
   * Get the progress of a specific workflow execution.
   *
   * @param executionId the execution identifier
   * @return the workflow progress
   */
  public WorkflowProgress getExecutionProgress(@NotBlank final String executionId) {
    final WorkflowState state = executionStates.get(executionId);
    WorkflowProgress progress = null;
    if (state != null) {
      progress =
          new WorkflowProgress(
              state.sessionId,
              state.workflowId,
              state.executionId,
              state.status,
              List.copyOf(state.tasks),
              state.startTime,
              state.endTime);
    }
    return progress;
  }

  /**
   * Get the log stream for a specific execution.
   *
   * @param executionId the execution identifier
   * @return the log flux
   */
  public Flux<String> getLogStream(@NotBlank final String executionId) {
    final Sinks.Many<String> sink = executionLogSinks.get(executionId);
    return sink != null ? sink.asFlux() : Flux.empty();
  }

  /**
   * Get the log stream for a specific session and workflow.
   *
   * @param sessionId the session identifier
   * @param workflowId the workflow identifier
   * @return the log flux
   */
  public Flux<String> getLogStream(
      @SessionId final String sessionId, @WorkflowId final String workflowId) {
    final String executionId = latestExecutionByWorkflow.get(getWorkflowKey(sessionId, workflowId));
    return executionId != null ? getLogStream(executionId) : Flux.empty();
  }

  /**
   * List all active sessions being tracked.
   *
   * @return list of session IDs
   */
  public List<String> getActiveSessions() {
    return List.copyOf(latestExecutionBySession.keySet());
  }

  /**
   * Remove tracking data for a session and workflow.
   *
   * @param sessionId the session identifier
   * @param workflowId the workflow identifier
   */
  public void removeSession(
      @SessionId final String sessionId, @WorkflowId final String workflowId) {
    final String executionId = latestExecutionByWorkflow.remove(getWorkflowKey(sessionId, workflowId));
    if (executionId != null) {
      executionStates.remove(executionId);
      executionLogSinks.remove(executionId);
      executionStatusSinks.remove(executionId);
      final List<String> executions = executionsBySession.get(sessionId);
      if (executions != null) {
        executions.remove(executionId);
        if (executions.isEmpty()) {
          executionsBySession.remove(sessionId);
          latestExecutionBySession.remove(sessionId);
        }
      }
    }
  }

  private void notifyStatusChange(final String executionId) {
    final Sinks.Many<WorkflowProgress> sink = executionStatusSinks.get(executionId);
    if (sink != null) {
      sink.tryEmitNext(getExecutionProgress(executionId));
    }
  }

  /**
   * Get the execution history for a session.
   *
   * @param sessionId the session identifier
   * @return list of execution identifiers
   */
  public List<String> getExecutionHistory(@SessionId final String sessionId) {
    return List.copyOf(executionsBySession.getOrDefault(sessionId, List.of()));
  }

  /**
   * Get the status stream for a specific execution.
   *
   * @param executionId the execution identifier
   * @return the status flux
   */
  public Flux<WorkflowProgress> getStatusStream(@NotBlank final String executionId) {
    final Sinks.Many<WorkflowProgress> sink = executionStatusSinks.get(executionId);
    return sink != null ? sink.asFlux() : Flux.empty();
  }

  /**
   * Get the status stream for a specific session and workflow.
   *
   * @param sessionId the session identifier
   * @param workflowId the workflow identifier
   * @return the status flux
   */
  public Flux<WorkflowProgress> getStatusStream(
      @SessionId final String sessionId, @WorkflowId final String workflowId) {
    final String executionId = latestExecutionByWorkflow.get(getWorkflowKey(sessionId, workflowId));
    return executionId != null ? getStatusStream(executionId) : Flux.empty();
  }

  private String getWorkflowKey(final String sessionId, final String workflowId) {
    return sessionId + ":" + workflowId;
  }

  private static final class WorkflowState {
    private final String sessionId;
    private final String workflowId;
    private final String executionId;
    private String status;
    private final List<TaskProgress> tasks;
    private final LocalDateTime startTime;
    private LocalDateTime endTime;

    /* default */ WorkflowState(
        final String sessionId,
        final String workflowId,
        final String executionId,
        final String status,
        final List<TaskProgress> tasks,
        final LocalDateTime startTime) {
      this.sessionId = sessionId;
      this.workflowId = workflowId;
      this.executionId = executionId;
      this.status = status;
      this.tasks = new CopyOnWriteArrayList<>(tasks);
      this.startTime = startTime;
    }

    /* default */ void updateTask(
        final String nodeId,
        final String module,
        final String status,
        final Map<String, Object> metadata) {
      int index = -1;
      for (int i = 0; i < tasks.size(); i++) {
        if (tasks.get(i).nodeId().equals(nodeId)) {
          index = i;
          break;
        }
      }

      if (index != -1) {
        final TaskProgress current = tasks.get(index);
        tasks.set(index, createUpdatedTask(current, module, status, metadata));
      }
    }

    private TaskProgress createUpdatedTask(
        final TaskProgress current,
        final String module,
        final String status,
        final Map<String, Object> metadata) {
      final LocalDateTime taskStartTime = determineStartTime(current.startTime(), status);
      final LocalDateTime taskEndTime = determineEndTime(current.endTime(), status);
      final Map<String, Object> newMetadata = mergeMetadata(current.metadata(), metadata);

      return new TaskProgress(
          current.nodeId(), module, status, taskStartTime, taskEndTime, Map.copyOf(newMetadata));
    }

    private LocalDateTime determineStartTime(final LocalDateTime current, final String status) {
      return ("RUNNING".equals(status) && current == null) ? LocalDateTime.now() : current;
    }

    private LocalDateTime determineEndTime(final LocalDateTime current, final String status) {
      return (("SUCCESS".equals(status) || "FAILURE".equals(status)) && current == null)
          ? LocalDateTime.now()
          : current;
    }

    private Map<String, Object> mergeMetadata(
        final Map<String, Object> current, final Map<String, Object> additional) {
      final Map<String, Object> merged = new ConcurrentHashMap<>(current);
      if (additional != null) {
        merged.putAll(additional);
      }
      return merged;
    }

    /* default */ void setStatus(final String status) {
      this.status = status;
    }

    /* default */ void setEndTime(final LocalDateTime endTime) {
      this.endTime = endTime;
    }
  }
}
