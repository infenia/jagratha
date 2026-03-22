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
package com.infenia.yukta.service.orchestrator;

import com.infenia.yukta.model.workflow.WorkflowDefinition.Node;
import com.infenia.yukta.plugin.gateway.ControlBusGateway;
import com.infenia.yukta.plugin.message.DefaultMessage;
import com.infenia.yukta.plugin.message.Message;
import com.infenia.yukta.plugin.message.control.ControlError;
import com.infenia.yukta.plugin.retry.RetryPolicy;
import com.infenia.yukta.service.CheckpointService;
import com.infenia.yukta.service.DependencyEngine;
import com.infenia.yukta.service.ExecutionRegistry;
import com.infenia.yukta.service.TaskTrackerService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeoutException;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;

/**
 * Fluent builder for constructing reactive streams with unified stream patterns across all plugin
 * types (Trigger, Processor, Terminal).
 *
 * <p>StreamBuilder encapsulates common stream transformation patterns: timeout handling with
 * TimeoutException mapping, task status tracking (RUNNING → SUCCESS/FAILURE), and error handling
 * with ControlBusGateway emission.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * Flux<Message<?>> stream = new StreamBuilder(
 *     node,
 *     Duration.ofSeconds(10),
 *     taskTracker,
 *     controlBus)
 *   .withSource(sourceFlux)
 *   .withTimeout()
 *   .withTaskTracking("exec-001", "session-001")
 *   .withErrorHandling("exec-001")
 *   .build();
 * }</pre>
 */
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class StreamBuilder {

  /** Default task identifier constant. */
  private static final String DEFAULT_TASK_ID = "default";

  /** Status constant: RUNNING. */
  private static final String STATUS_RUNNING = "RUNNING";

  /** Status constant: SUCCESS. */
  private static final String STATUS_SUCCESS = "SUCCESS";

  /** Status constant: FAILURE. */
  private static final String STATUS_FAILURE = "FAILURE";

  private final Node node;
  private final Duration timeout;
  private final TaskTrackerService taskTracker;
  private final ControlBusGateway controlBusGateway;

  @Nullable private Flux<Message<?>> sourceStream;

  private boolean applyTimeout;
  private boolean applyTaskTracking;
  private boolean applyErrors;
  private boolean applyRetry;
  private boolean applyCheckpointing;
  private boolean applyDependencyTracking;

  @Nullable private String executionId;
  @Nullable private RetryPolicy retryPolicy;
  @Nullable private ExecutionRegistry executionRegistry;
  @Nullable private CheckpointService checkpointService;
  @Nullable private DependencyEngine dependencyEngine;

  /**
   * Creates a new StreamBuilder instance.
   *
   * @param node the workflow node
   * @param timeout the operation timeout duration
   * @param taskTracker the task tracker service for status events
   * @param controlBusGateway the control bus gateway for error emission
   */
  public StreamBuilder(
      final Node node,
      final Duration timeout,
      final TaskTrackerService taskTracker,
      final ControlBusGateway controlBusGateway) {
    this.node = node;
    this.timeout = timeout;
    this.taskTracker = taskTracker;
    this.controlBusGateway = controlBusGateway;
  }

  /**
   * Sets the source stream for this builder.
   *
   * @param stream the Flux source stream
   * @return this builder for fluent chaining
   */
  public StreamBuilder withSource(final Flux<Message<?>> stream) {
    this.sourceStream = stream;
    return this;
  }

  /**
   * Enables timeout handling with TimeoutException mapping.
   *
   * @return this builder for fluent chaining
   */
  public StreamBuilder withTimeout() {
    this.applyTimeout = true;
    return this;
  }

  /**
   * Enables task status tracking with RUNNING, SUCCESS, and FAILURE events.
   *
   * @param execId the execution identifier
   * @return this builder for fluent chaining
   */
  public StreamBuilder withTaskTracking(final String execId) {
    this.applyTaskTracking = true;
    this.executionId = execId;
    return this;
  }

  /**
   * Enables error handling with ControlBusGateway emission of errors.
   *
   * @param execId the execution identifier for error tracking
   * @return this builder for fluent chaining
   */
  public StreamBuilder withErrorHandling(final String execId) {
    this.applyErrors = true;
    this.executionId = execId;
    return this;
  }

  /**
   * Enables retry handling with the given retry policy.
   *
   * @param policy the retry policy configuration
   * @param registry the execution registry for tracking retry attempts
   * @return this builder for fluent chaining
   */
  public StreamBuilder withRetry(final RetryPolicy policy, final ExecutionRegistry registry) {
    this.applyRetry = true;
    this.retryPolicy = policy;
    this.executionRegistry = registry;
    return this;
  }

  /**
   * Enables checkpointing of successful node outputs.
   *
   * @param service the checkpoint service
   * @return this builder for fluent chaining
   */
  public StreamBuilder withCheckpointing(final CheckpointService service) {
    this.applyCheckpointing = true;
    this.checkpointService = service;
    return this;
  }

  /**
   * Enables dependency tracking for DAG nodes.
   *
   * @param engine the dependency engine
   * @return this builder for fluent chaining
   */
  public StreamBuilder withDependencyTracking(final DependencyEngine engine) {
    this.applyDependencyTracking = true;
    this.dependencyEngine = engine;
    return this;
  }

  /**
   * Builds and returns the configured Flux stream with all registered transformations applied in
   * order.
   *
   * @return the constructed Flux stream
   */
  public Flux<Message<?>> build() {
    Flux<Message<?>> stream = sourceStream;

    if (stream == null) {
      stream = Flux.empty();
    }

    // Apply timeout wrapping
    if (applyTimeout) {
      stream = applyTimeoutTransform(stream);
    }

    // Apply retry transformation
    if (applyRetry && retryPolicy != null && retryPolicy.maxAttempts() > 0) {
      stream = applyRetryTransform(stream);
    }

    // Apply checkpointing
    if (applyCheckpointing && checkpointService != null) {
      stream = applyCheckpointTransform(stream);
    }

    // Apply task tracking
    if (applyTaskTracking) {
      stream = applyTaskTrackingTransform(stream);
    }

    // Apply dependency tracking
    if (applyDependencyTracking && dependencyEngine != null && executionId != null) {
      stream = applyDependencyTrackingTransform(stream);
    }

    // Apply error handling
    if (applyErrors) {
      stream = applyErrorsTransform(stream);
    }

    return stream;
  }

  /**
   * Applies timeout transformation to the stream.
   *
   * @param flux the source flux
   * @return the flux with timeout applied
   */
  private Flux<Message<?>> applyTimeoutTransform(final Flux<Message<?>> flux) {
    return flux.timeout(timeout).onErrorMap(TimeoutException.class, e -> e);
  }

  /**
   * Applies task tracking transformation to the stream.
   *
   * @param flux the source flux
   * @return the flux with task tracking applied
   */
  private Flux<Message<?>> applyTaskTrackingTransform(final Flux<Message<?>> flux) {
    return flux.doOnSubscribe(
            sub ->
                taskTracker.emitTaskStatusEvent(
                    executionId,
                    node.nodeId(),
                    DEFAULT_TASK_ID,
                    STATUS_RUNNING,
                    Collections.emptyMap()))
        .doOnComplete(
            () ->
                taskTracker.emitTaskStatusEvent(
                    executionId,
                    node.nodeId(),
                    DEFAULT_TASK_ID,
                    STATUS_SUCCESS,
                    Collections.emptyMap()))
        .doOnError(
            error ->
                taskTracker.emitTaskStatusEvent(
                    executionId,
                    node.nodeId(),
                    DEFAULT_TASK_ID,
                    STATUS_FAILURE,
                    Collections.emptyMap()));
  }

  /**
   * Applies error handling transformation to the stream.
   *
   * @param flux the source flux
   * @return the flux with error handling applied
   */
  private Flux<Message<?>> applyErrorsTransform(final Flux<Message<?>> flux) {
    return flux.doOnError(
        error -> {
          // Emit FAILURE status to task tracker
          taskTracker.emitTaskStatusEvent(
              executionId, node.nodeId(), DEFAULT_TASK_ID, STATUS_FAILURE, Collections.emptyMap());

          // Emit control error to control bus
          final ControlError controlError =
              new ControlError(
                  node.nodeId(), executionId, error.getClass().getSimpleName(), error.getMessage());
          controlBusGateway
              .emit(
                  DefaultMessage.create(null, controlError)
                      .withSourceNodeId(node.nodeId())
                      .withControl(true)
                      .withPriority(10))
              .subscribe();
        });
  }

  /**
   * Applies retry transformation to the stream using the configured retry policy.
   *
   * <p>Note: Simplified retry implementation using Flux.retry(). In a production system, use
   * retryWhen with Retry.backoff() for more sophisticated backoff handling.
   *
   * @param flux the source flux
   * @return the flux with retry logic applied
   */
  private Flux<Message<?>> applyRetryTransform(final Flux<Message<?>> flux) {
    if (retryPolicy == null || executionRegistry == null) {
      return flux;
    }

    final int maxAttempts = retryPolicy.maxAttempts();
    if (maxAttempts <= 0) {
      return flux;
    }

    // For now, use simple retry without backoff
    // A full implementation would use retryWhen with Retry.backoff for reactive backoff
    return flux.doOnError(
            error -> executionRegistry.getAndIncrementRetry(executionId, node.nodeId()))
        .retry(maxAttempts);
  }

  /**
   * Applies checkpoint transformation to the stream. Checkpoints successful node output.
   *
   * @param flux the source flux
   * @return the flux with checkpointing applied
   */
  private Flux<Message<?>> applyCheckpointTransform(final Flux<Message<?>> flux) {
    if (checkpointService == null || executionId == null) {
      return flux;
    }

    return flux.doOnNext(
        message ->
            checkpointService
                .checkpoint(executionId, node.nodeId(), message)
                .subscribe()); // Fire-and-forget
  }

  /**
   * Applies dependency tracking transformation to the stream. When a node completes, emits ready
   * nodes to the control bus.
   *
   * @param flux the source flux
   * @return the flux with dependency tracking applied
   */
  private Flux<Message<?>> applyDependencyTrackingTransform(final Flux<Message<?>> flux) {
    if (dependencyEngine == null || executionId == null) {
      return flux;
    }

    return flux.doOnComplete(
        () ->
            dependencyEngine
                .nodeCompleted(executionId, node.nodeId())
                .subscribe(
                    readyNodeId -> {
                      // Optional: emit node ready signal for downstream handling
                    }));
  }

  /**
   * Calculate backoff delay based on retry policy and attempt number.
   *
   * @param policy the retry policy
   * @param attemptNumber the attempt number (1-based)
   * @return the delay in milliseconds
   */
  private long calculateBackoff(final RetryPolicy policy, final int attemptNumber) {
    return switch (policy.backoffType()) {
      case FIXED -> policy.initialDelay();
      case EXPONENTIAL ->
          Math.min(
              policy.initialDelay() * (long) Math.pow(2, attemptNumber - 1), policy.maxDelay());
    };
  }
}
