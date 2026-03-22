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

import com.infenia.yukta.model.workflow.PreparedWorkflow;
import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Central registry for per-execution mutable state.
 *
 * <p>Manages cancel sinks, prepared workflow references, and retry counters for each execution.
 * Periodically cleans up stale executions via background TTL scan.
 */
@Slf4j
@Service
@SuppressWarnings("PMD.CommentDefaultAccessModifier")
public class ExecutionRegistry {

  private final Duration ttl;
  private final Map<String, ExecutionState> executions = new ConcurrentHashMap<>();
  private final Map<String, Long> executionTimestamps = new ConcurrentHashMap<>();

  /**
   * Creates a new ExecutionRegistry.
   *
   * @param ttl the time-to-live for execution data after registration
   */
  public ExecutionRegistry(@Value("${yukta.execution.registry.ttl:10m}") final Duration ttl) {
    this.ttl = ttl;
  }

  /** Initialize background TTL cleanup. */
  @PostConstruct
  public void init() {
    Mono.delay(ttl)
        .repeat()
        .publishOn(Schedulers.boundedElastic())
        .doOnNext(unused -> cleanupStaleExecutions())
        .onErrorContinue(
            (err, unused) ->
                log.atWarn().setCause(err).log("Error during execution registry cleanup"))
        .subscribe();
  }

  /**
   * Register a new execution with its prepared workflow.
   *
   * @param executionId the execution identifier
   * @param preparedWorkflow the prepared workflow to execute
   */
  public void register(
      @NotBlank final String executionId, @NotNull final PreparedWorkflow preparedWorkflow) {
    executions.put(executionId, new ExecutionState(preparedWorkflow));
    executionTimestamps.put(executionId, System.currentTimeMillis());
  }

  /**
   * Get the cancel signal Mono for an execution.
   *
   * @param executionId the execution identifier
   * @return a Mono that completes when the execution is cancelled
   */
  public Mono<Void> getCancelSignal(@NotBlank final String executionId) {
    final ExecutionState state = executions.get(executionId);
    if (state == null) {
      return Mono.never(); // No cancel signal if execution not found
    }
    return state.cancelSink.asMono();
  }

  /**
   * Cancel an execution.
   *
   * @param executionId the execution identifier
   * @return a Mono that completes with true if the execution was found and cancelled, false
   *     otherwise
   */
  public Mono<Boolean> cancel(@NotBlank final String executionId) {
    return Mono.defer(
        () -> {
          final ExecutionState state = executions.get(executionId);
          if (state == null) {
            return Mono.just(false);
          }
          try {
            final reactor.core.publisher.Sinks.EmitResult result = state.cancelSink.tryEmitEmpty();
            if (result.isFailure()) {
              log.atWarn()
                  .log("Failed to emit cancel signal for execution {}: {}", executionId, result);
            }
            return Mono.just(true);
          } catch (final Exception e) {
            log.atWarn()
                .setCause(e)
                .log("Failed to emit cancel signal for execution: {}", executionId);
            return Mono.just(true); // Consider it successfully cancelled
          }
        });
  }

  /**
   * Get the prepared workflow for an execution.
   *
   * @param executionId the execution identifier
   * @return an Optional containing the prepared workflow, or empty if not found
   */
  public Optional<PreparedWorkflow> getPreparedWorkflow(@NotBlank final String executionId) {
    final ExecutionState state = executions.get(executionId);
    return state != null ? Optional.of(state.preparedWorkflow) : Optional.empty();
  }

  /**
   * Get and increment the retry counter for a node in an execution.
   *
   * @param executionId the execution identifier
   * @param nodeId the node identifier
   * @return the new retry count after incrementing
   */
  public int getAndIncrementRetry(
      @NotBlank final String executionId, @NotBlank final String nodeId) {
    final ExecutionState state = executions.get(executionId);
    if (state == null) {
      return 1;
    }
    final AtomicInteger counter =
        state.retryCounters.computeIfAbsent(nodeId, unused -> new AtomicInteger(0));
    return counter.incrementAndGet();
  }

  /**
   * Reset the retry counter for a node in an execution.
   *
   * @param executionId the execution identifier
   * @param nodeId the node identifier
   */
  public void resetRetry(@NotBlank final String executionId, @NotBlank final String nodeId) {
    final ExecutionState state = executions.get(executionId);
    if (state != null) {
      state.retryCounters.remove(nodeId);
    }
  }

  /**
   * Unregister an execution and clean up all its state.
   *
   * @param executionId the execution identifier
   */
  public void unregister(@NotBlank final String executionId) {
    executions.remove(executionId);
    executionTimestamps.remove(executionId);
  }

  private void cleanupStaleExecutions() {
    final long now = System.currentTimeMillis();
    final long ttlMillis = ttl.toMillis();

    executionTimestamps.forEach(
        (executionId, timestamp) -> {
          if (now - timestamp > ttlMillis) {
            unregister(executionId);
            log.atDebug().log("Cleaned up stale execution: {}", executionId);
          }
        });
  }

  /** Internal state holder for an execution. */
  private static final class ExecutionState {
    final PreparedWorkflow preparedWorkflow;
    final reactor.core.publisher.Sinks.One<Void> cancelSink;
    final Map<String, AtomicInteger> retryCounters;

    ExecutionState(final PreparedWorkflow preparedWorkflow) {
      this.preparedWorkflow = preparedWorkflow;
      this.cancelSink = reactor.core.publisher.Sinks.one();
      this.retryCounters = new ConcurrentHashMap<>();
    }
  }
}
