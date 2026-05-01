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
package com.infenia.yukta.service.control;

import com.infenia.yukta.service.control.store.ExecutionControlRegistry;
import com.infenia.yukta.service.control.valve.ReactiveControlValve;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/** Default implementation of WorkflowControlApi. */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultWorkflowControlApi implements WorkflowControlApi {

  private final ExecutionControlRegistry registry;

  private Mono<ExecutionControl> getControl(final String executionId) {
    return Mono.justOrEmpty(registry.findByExecutionId(executionId))
        .switchIfEmpty(
            Mono.error(new IllegalArgumentException("Execution not found: " + executionId)));
  }

  @Override
  public Mono<Void> stopImmediately(final String executionId) {
    return getControl(executionId)
        .flatMap(
            control -> {
              final Sinks.One<Void> sink = control.immediateStopSink();
              return Mono.from(
                  m -> {
                    sink.emitEmpty(Sinks.EmitFailureHandler.FAIL_FAST);
                    m.onComplete();
                  });
            });
  }

  @Override
  public Mono<Void> stopSafely(final String executionId) {
    return getControl(executionId)
        .flatMap(
            control -> {
              final Sinks.One<Void> sink = control.safeStopSink();
              return Mono.from(
                  m -> {
                    sink.emitEmpty(Sinks.EmitFailureHandler.FAIL_FAST);
                    m.onComplete();
                  });
            });
  }

  @Override
  public Mono<Void> pauseWorkflow(final String executionId) {
    return getControl(executionId)
        .flatMap(
            control -> {
              final ReactiveControlValve valve = control.globalPauseValve();
              if (valve == null) {
                return Mono.error(new IllegalStateException("Global pause valve not initialized"));
              }
              valve.pause();
              return Mono.empty();
            });
  }

  @Override
  public Mono<Void> unpauseWorkflow(final String executionId) {
    return getControl(executionId)
        .flatMap(
            control -> {
              final ReactiveControlValve valve = control.globalPauseValve();
              if (valve == null) {
                return Mono.error(new IllegalStateException("Global pause valve not initialized"));
              }
              valve.resume();
              return Mono.empty();
            });
  }

  @Override
  public Mono<Void> pauseNode(final String executionId, final String nodeId) {
    return getControl(executionId)
        .flatMap(
            control -> {
              final ReactiveControlValve valve = control.nodePauseValves().get(nodeId);
              if (valve == null) {
                return Mono.error(
                    new IllegalArgumentException("Node not found or not pausable: " + nodeId));
              }
              valve.pause();
              return Mono.empty();
            });
  }

  @Override
  public Mono<Void> unpauseNode(final String executionId, final String nodeId) {
    return getControl(executionId)
        .flatMap(
            control -> {
              final ReactiveControlValve valve = control.nodePauseValves().get(nodeId);
              if (valve == null) {
                return Mono.error(
                    new IllegalArgumentException("Node not found or not pausable: " + nodeId));
              }
              valve.resume();
              return Mono.empty();
            });
  }

  @Override
  public Mono<Void> stopNodeImmediately(final String executionId, final String nodeId) {
    return getControl(executionId)
        .flatMap(
            control -> {
              final Sinks.One<Void> sink = control.nodeImmediateStopSinks().get(nodeId);
              if (sink == null) {
                return Mono.error(
                    new IllegalArgumentException("Node not found or not stoppable: " + nodeId));
              }
              return Mono.from(
                  m -> {
                    sink.emitEmpty(Sinks.EmitFailureHandler.FAIL_FAST);
                    m.onComplete();
                  });
            });
  }

  @Override
  public Mono<Void> stopNodeSafely(final String executionId, final String nodeId) {
    return getControl(executionId)
        .flatMap(
            control -> {
              final Sinks.One<Void> sink = control.nodeSafeStopSinks().get(nodeId);
              if (sink == null) {
                return Mono.error(
                    new IllegalArgumentException("Node not found or not stoppable: " + nodeId));
              }
              return Mono.from(
                  m -> {
                    sink.emitEmpty(Sinks.EmitFailureHandler.FAIL_FAST);
                    m.onComplete();
                  });
            });
  }

  @Override
  public Mono<Void> skipNode(final String executionId, final String nodeId, final boolean skip) {
    return getControl(executionId)
        .flatMap(
            control -> {
              final AtomicBoolean flag = control.nodeSkipFlags().get(nodeId);
              if (flag == null) {
                return Mono.error(
                    new IllegalArgumentException("Node not found or not skippable: " + nodeId));
              }
              flag.set(skip);
              return Mono.empty();
            });
  }

  @Override
  public Mono<WorkflowExecutionSnapshot> getStatus(final String executionId) {
    return getControl(executionId).map(this::buildSnapshot);
  }

  @Override
  public Mono<Void> enableNodeStepMode(final String executionId, final String nodeId) {
    return getControl(executionId)
        .flatMap(
            control -> {
              final ReactiveControlValve valve = control.nodePauseValves().get(nodeId);
              if (valve == null) {
                return Mono.error(
                    new IllegalArgumentException(
                        "Node not found or not controllable: " + nodeId));
              }
              valve.enableStepMode();
              return Mono.empty();
            });
  }

  @Override
  public Mono<Void> disableNodeStepMode(final String executionId, final String nodeId) {
    return getControl(executionId)
        .flatMap(
            control -> {
              final ReactiveControlValve valve = control.nodePauseValves().get(nodeId);
              if (valve == null) {
                return Mono.error(
                    new IllegalArgumentException(
                        "Node not found or not controllable: " + nodeId));
              }
              valve.disableStepMode();
              return Mono.empty();
            });
  }

  @Override
  public Mono<Void> stepNode(final String executionId, final String nodeId) {
    return getControl(executionId)
        .flatMap(
            control -> {
              final ReactiveControlValve valve = control.nodePauseValves().get(nodeId);
              if (valve == null) {
                return Mono.error(
                    new IllegalArgumentException(
                        "Node not found or not controllable: " + nodeId));
              }
              if (!valve.step()) {
                return Mono.error(
                    new IllegalStateException("Node is not in step mode: " + nodeId));
              }
              return Mono.empty();
            });
  }

  private WorkflowExecutionSnapshot buildSnapshot(final ExecutionControl control) {
    final Set<String> pausedNodes = new HashSet<>();
    final Set<String> skippedNodes = new HashSet<>();
    final Set<String> stoppedNodes = new HashSet<>();

    control
        .nodePauseValves()
        .forEach(
            (nodeId, valve) -> {
              if (valve.isPaused()) {
                pausedNodes.add(nodeId);
              }
            });

    control
        .nodeSkipFlags()
        .forEach(
            (nodeId, flag) -> {
              if (flag.get()) {
                skippedNodes.add(nodeId);
              }
            });

    return new WorkflowExecutionSnapshot(
        control.executionId(),
        control.sessionId(),
        control.workflowId(),
        control.globalPauseValve() != null && control.globalPauseValve().isPaused(),
        pausedNodes,
        skippedNodes,
        stoppedNodes,
        Instant.now(),
        Instant.now());
  }
}
