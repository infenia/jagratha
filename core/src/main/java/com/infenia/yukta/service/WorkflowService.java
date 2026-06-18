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
package com.infenia.yukta.service;

import com.infenia.yukta.model.session.TaskResponse;
import com.infenia.yukta.model.workflow.PreparedWorkflow;
import com.infenia.yukta.model.workflow.WorkflowExecution;
import com.infenia.yukta.service.orchestrator.WorkflowOrchestrator;
import com.infenia.yukta.service.orchestrator.tracker.TaskTrackerService;
import com.infenia.yukta.service.workflow.store.PreparedWorkflowCache;
import com.infenia.yukta.service.workflow.store.WorkflowDefinitionStore;
import com.infenia.yukta.validation.SessionId;
import com.infenia.yukta.validation.WorkflowId;
import jakarta.validation.constraints.NotEmpty;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

/** Service for orchestrating workflow execution. */
@Slf4j
@Service
@Validated
@RequiredArgsConstructor
@SuppressWarnings("PMD.LongVariable")
public class WorkflowService {

  // Logging key constants
  private static final String LOG_KEY_SESSION_ID = "sessionId";
  private static final String LOG_KEY_WORKFLOW_ID = "workflowId";
  private static final String LOG_KEY_EXECUTION_ID = "executionId";
  private static final String LOG_KEY_QUEUE_KEY = "queueKey";
  private static final String LOG_KEY_ERROR_MSG = "errorMessage";

  private final WorkflowOrchestrator orchestrator;
  private final TaskTrackerService tracker;
  private final WorkflowDefinitionStore workflowDefinitionStore;
  private final PreparedWorkflowCache preparedWorkflowCache;

  private final Map<String, Mono<Void>> workflowQueues = new ConcurrentHashMap<>();

  /**
   * Validate and run a specific workflow for a session.
   *
   * @param sessionId the session identifier
   * @param workflowId the workflow identifier
   * @param payload the initial trigger payload
   * @return a Mono containing the WorkflowExecution
   * @throws IllegalArgumentException if session or workflow not found
   */
  public Mono<WorkflowExecution> validateAndTriggerWorkflow(
      @SessionId final String sessionId,
      @WorkflowId final String workflowId,
      @NotEmpty final Map<String, Object> payload) {
    log.atInfo()
        .addKeyValue(LOG_KEY_SESSION_ID, sessionId)
        .addKeyValue(LOG_KEY_WORKFLOW_ID, workflowId)
        .addKeyValue("payloadSize", payload.size())
        .log("Validating and triggering workflow");

    return workflowDefinitionStore
        .find(sessionId, workflowId)
        .doOnNext(
            def -> {
              log.atDebug()
                  .addKeyValue(LOG_KEY_SESSION_ID, sessionId)
                  .addKeyValue(LOG_KEY_WORKFLOW_ID, workflowId)
                  .addKeyValue("nodeCount", def.nodes().size())
                  .addKeyValue("edgeCount", def.edges().size())
                  .log("Workflow definition found and loaded");
            })
        .switchIfEmpty(
            Mono.defer(
                () -> {
                  log.atWarn()
                      .addKeyValue(LOG_KEY_SESSION_ID, sessionId)
                      .addKeyValue(LOG_KEY_WORKFLOW_ID, workflowId)
                      .log("Workflow definition not found");
                  return Mono.error(workflowNotFound(sessionId, workflowId));
                }))
        .map(
            _ -> {
              log.atDebug()
                  .addKeyValue(LOG_KEY_SESSION_ID, sessionId)
                  .addKeyValue(LOG_KEY_WORKFLOW_ID, workflowId)
                  .log("Extracted node IDs from workflow definition");
              return runWorkflow(sessionId, workflowId, payload);
            });
  }

  private WorkflowExecution runWorkflow(
      final String sessionId, final String workflowId, final Map<String, Object> payload) {
    final String executionId = UUID.randomUUID().toString();
    final String queueKey = sessionId + ":" + workflowId;
    final Sinks.One<TaskResponse> sink = Sinks.one();

    log.atInfo()
        .addKeyValue(LOG_KEY_SESSION_ID, sessionId)
        .addKeyValue(LOG_KEY_WORKFLOW_ID, workflowId)
        .addKeyValue(LOG_KEY_EXECUTION_ID, executionId)
        .log("Submitting workflow execution request");

    workflowQueues
        .compute(
            queueKey,
            (key, previousTail) -> {
              log.atDebug()
                  .addKeyValue(LOG_KEY_QUEUE_KEY, queueKey)
                  .addKeyValue("queuedExecutions", previousTail != null ? 1 : 0)
                  .log("Processing workflow queue");

              final Mono<Void> current =
                  Mono.defer(
                          () ->
                              resolveAndExecute(sessionId, workflowId, executionId, payload, sink))
                      .subscribeOn(Schedulers.boundedElastic());

              final Mono<Void> nextTail;
              if (previousTail == null) {
                log.atTrace()
                    .addKeyValue(LOG_KEY_QUEUE_KEY, queueKey)
                    .log("First execution in queue, starting immediately");
                nextTail = current;
              } else {
                log.atDebug()
                    .addKeyValue(LOG_KEY_QUEUE_KEY, queueKey)
                    .addKeyValue(LOG_KEY_EXECUTION_ID, executionId)
                    .log("Queueing execution behind previous workflow");
                nextTail =
                    previousTail
                        .onErrorResume(
                            e -> {
                              log.atWarn()
                                  .setCause(e)
                                  .addKeyValue(LOG_KEY_QUEUE_KEY, queueKey)
                                  .log("Previous workflow failed, proceeding with current");
                              return Mono.empty();
                            })
                        .then(current);
              }
              @SuppressWarnings("unchecked")
              final Mono<Void>[] tailRef = new Mono[1];
              tailRef[0] =
                  nextTail
                      .doOnSuccess(
                          v ->
                              log.atDebug()
                                  .addKeyValue(LOG_KEY_EXECUTION_ID, executionId)
                                  .log("Workflow completed successfully"))
                      .doOnError(
                          e ->
                              log.atError()
                                  .setCause(e)
                                  .addKeyValue(LOG_KEY_EXECUTION_ID, executionId)
                                  .log("Workflow execution error"))
                      .doOnTerminate(
                          () -> {
                            log.atTrace()
                                .addKeyValue(LOG_KEY_QUEUE_KEY, queueKey)
                                .log("Cleaning up queue entry");
                            workflowQueues.computeIfPresent(
                                queueKey,
                                (k, tail) -> {
                                  if (tail.equals(tailRef[0])) {
                                    log.atDebug()
                                        .addKeyValue(LOG_KEY_QUEUE_KEY, queueKey)
                                        .log("Removing queue entry");
                                    return null;
                                  }
                                  return tail;
                                });
                          })
                      .cache();
              return tailRef[0];
            })
        .subscribe();

    return new WorkflowExecution(executionId, sink.asMono());
  }

  private static IllegalArgumentException workflowNotFound(
      final String sessionId, final String workflowId) {
    return new IllegalArgumentException(
        "Workflow not found for session: " + sessionId + ", workflow: " + workflowId);
  }

  private Mono<Void> resolveAndExecute(
      final String sessionId,
      final String workflowId,
      final String executionId,
      final Map<String, Object> payload,
      final Sinks.One<TaskResponse> sink) {
    final Optional<PreparedWorkflow> cached = preparedWorkflowCache.get(sessionId, workflowId);
    final Mono<PreparedWorkflow> preparedMono;
    if (cached.isPresent()) {
      log.atDebug()
          .addKeyValue(LOG_KEY_SESSION_ID, sessionId)
          .addKeyValue(LOG_KEY_WORKFLOW_ID, workflowId)
          .addKeyValue(LOG_KEY_EXECUTION_ID, executionId)
          .log("Using cached PreparedWorkflow");
      preparedMono = Mono.just(cached.get());
    } else {
      preparedMono =
          workflowDefinitionStore
              .find(sessionId, workflowId)
              .switchIfEmpty(Mono.error(workflowNotFound(sessionId, workflowId)))
              .flatMap(
                  def -> {
                    log.atTrace()
                        .addKeyValue(LOG_KEY_WORKFLOW_ID, workflowId)
                        .log("Workflow definition loaded from store");
                    return orchestrator
                        .prepareWorkflow(def)
                        .doOnNext(
                            p -> {
                              log.atDebug()
                                  .addKeyValue(LOG_KEY_EXECUTION_ID, executionId)
                                  .log("Workflow prepared and cached");
                              preparedWorkflowCache.put(sessionId, workflowId, p);
                            });
                  });
    }
    return preparedMono
        .flatMap(
            prepared -> orchestrator.execute(sessionId, workflowId, executionId, prepared, payload))
        .then(Mono.just(new TaskResponse("SUCCESS", "Workflow executed successfully")))
        .onErrorResume(
            e -> {
              log.atError()
                  .setCause(e)
                  .addKeyValue(LOG_KEY_SESSION_ID, sessionId)
                  .addKeyValue(LOG_KEY_WORKFLOW_ID, workflowId)
                  .addKeyValue(LOG_KEY_EXECUTION_ID, executionId)
                  .addKeyValue(LOG_KEY_ERROR_MSG, e.getMessage())
                  .log("Workflow execution failed");
              return tracker
                  .finishWorkflow(executionId, "FAILURE")
                  .onErrorComplete()
                  .thenReturn(new TaskResponse("FAILURE", "Workflow failed: " + e.getMessage()));
            })
        .flatMap(
            response -> {
              sink.tryEmitValue(response);
              return Mono.empty();
            })
        .then();
  }
}
