// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Infenia Private Limited
package com.infenia.yukta.service.control.processor;

import com.infenia.yukta.plugin.control.ControlSignalProcessor;
import com.infenia.yukta.plugin.control.ExecutionControlCommand;
import com.infenia.yukta.plugin.control.ExecutionControlCommand.RestartCommand;
import com.infenia.yukta.plugin.control.WorkflowDirective;
import com.infenia.yukta.service.control.gateway.RestartCompletionSink;
import com.infenia.yukta.service.control.store.ExecutionControlRegistry;
import com.infenia.yukta.service.orchestrator.WorkflowOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

/**
 * Processor for restart commands.
 *
 * <p>Stops the current execution and restarts the entire workflow from the beginning with the
 * original payload. The new execution is detached ({@code
 * subscribeOn(boundedElastic()).subscribe()}) so this processor reports completion as soon as the
 * new execution is subscribed, not once it finishes — matching how {@code
 * DefaultControlBusGateway.prepareAndExecute} starts a normal (non-restart) execution.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RestartCommandProcessor implements ControlSignalProcessor {

  /** The execution control registry for accessing execution control state. */
  private final ExecutionControlRegistry registry;

  /** The workflow orchestrator for restarting workflows. */
  private final WorkflowOrchestrator orchestrator;

  /** Reports the outcome of a restart back to the awaiting gateway caller. */
  private final RestartCompletionSink completionSink;

  @Override
  public boolean canProcess(final ExecutionControlCommand command) {
    return command instanceof RestartCommand;
  }

  @Override
  public Mono<WorkflowDirective> process(final ExecutionControlCommand command) {
    final RestartCommand restart = (RestartCommand) command;

    return Mono.fromSupplier(
            () ->
                registry
                    .findByExecutionId(restart.executionId())
                    .orElseThrow(
                        () ->
                            new IllegalArgumentException(
                                "Execution not found: " + restart.executionId())))
        .doOnNext(
            control -> {
              registry.unregister(control.executionId());
              final Sinks.EmitResult stopResult = control.safeStopSink().tryEmitEmpty();
              if (stopResult.isFailure()) {
                throw new IllegalStateException("Failed to emit stop signal: " + stopResult);
              }

              orchestrator
                  .execute(
                      control.sessionId(),
                      control.workflowId(),
                      restart.newExecutionId(),
                      control.prepared(),
                      control.payload())
                  .subscribeOn(Schedulers.boundedElastic())
                  .doOnError(
                      err ->
                          log.atError()
                              .setCause(err)
                              .addKeyValue("oldExecutionId", restart.executionId())
                              .addKeyValue("newExecutionId", restart.newExecutionId())
                              .log("Restarted execution failed"))
                  .subscribe();

              log.atInfo()
                  .addKeyValue("oldExecutionId", restart.executionId())
                  .addKeyValue("newExecutionId", restart.newExecutionId())
                  .addKeyValue("workflowId", control.workflowId())
                  .log("Restarted execution");
              completionSink.completeRestartSuccess(restart.newExecutionId());
            })
        .then(Mono.<WorkflowDirective>empty())
        .onErrorResume(
            e -> {
              log.atError()
                  .addKeyValue("executionId", restart.executionId())
                  .setCause(e)
                  .log("Restart failed");
              completionSink.completeRestartFailure(restart.newExecutionId(), e);
              return Mono.empty();
            });
  }

  @Override
  public int getPriority() {
    return 20;
  }
}
