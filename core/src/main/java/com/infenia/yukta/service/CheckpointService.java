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

import com.infenia.yukta.plugin.message.DefaultMessage;
import com.infenia.yukta.plugin.message.Message;
import com.infenia.yukta.plugin.message.control.CheckpointEvent;
import com.infenia.yukta.plugin.store.CheckpointStore;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Service for managing workflow checkpoint operations.
 *
 * <p>Wraps CheckpointStore and emits CheckpointEvent to the control bus when checkpoints are
 * created.
 */
@Slf4j
@Service
@SuppressWarnings({"PMD.LinguisticNaming", "PMD.UseDiamondOperator"})
public class CheckpointService {

  private final CheckpointStore store;
  private final ControlBusService controlBus;

  /**
   * Creates a new CheckpointService.
   *
   * @param store the checkpoint store implementation
   * @param controlBus the control bus for event emission
   */
  public CheckpointService(
      @NotNull final CheckpointStore store,
      @Autowired(required = false) final ControlBusService controlBus) {
    this.store = store;
    this.controlBus = controlBus;
  }

  /**
   * Check if a checkpoint exists for a node.
   *
   * @param executionId the execution identifier
   * @param nodeId the node identifier
   * @return a Mono emitting true if checkpoint exists, false otherwise
   */
  public Mono<Boolean> hasCheckpoint(
      @NotBlank final String executionId, @NotBlank final String nodeId) {
    return store.hasCheckpoint(executionId, nodeId);
  }

  /**
   * Load a checkpoint message for a node.
   *
   * @param executionId the execution identifier
   * @param nodeId the node identifier
   * @return a Mono emitting the checkpointed message, or empty if not found
   */
  public Mono<Message<?>> getOrEmpty(
      @NotBlank final String executionId, @NotBlank final String nodeId) {
    return store.loadCheckpoint(executionId, nodeId);
  }

  /**
   * Save a checkpoint message for a node and emit CheckpointEvent to control bus.
   *
   * @param executionId the execution identifier
   * @param nodeId the node identifier
   * @param message the message to checkpoint
   * @return a Mono that completes when the checkpoint is saved
   */
  public Mono<Void> checkpoint(
      @NotBlank final String executionId,
      @NotBlank final String nodeId,
      @NotNull final Message<?> message) {
    final String checkpointKey = executionId + ":" + nodeId;
    return store
        .saveCheckpoint(executionId, nodeId, message)
        .then(
            Mono.defer(
                () -> {
                  if (controlBus != null) {
                    final CheckpointEvent event =
                        new CheckpointEvent(nodeId, executionId, checkpointKey, Instant.now());
                    @SuppressWarnings("rawtypes")
                    final DefaultMessage msg =
                        new DefaultMessage(
                            UUID.randomUUID().toString(),
                            null,
                            null,
                            null,
                            0,
                            null,
                            Map.of(),
                            List.of(),
                            null,
                            0,
                            0,
                            0,
                            true,
                            null,
                            null,
                            null,
                            0,
                            event,
                            Instant.now(),
                            null,
                            nodeId);
                    return controlBus.emit(msg);
                  }
                  return Mono.empty();
                }))
        .doOnError(
            e ->
                log.atWarn()
                    .setCause(e)
                    .log("Failed to checkpoint node {} in execution {}", nodeId, executionId));
  }

  /**
   * Clear the checkpoint for a node.
   *
   * @param executionId the execution identifier
   * @param nodeId the node identifier
   * @return a Mono that completes when the checkpoint is cleared
   */
  public Mono<Void> clearCheckpoint(
      @NotBlank final String executionId, @NotBlank final String nodeId) {
    return store.clearCheckpoint(executionId, nodeId);
  }

  /**
   * Clear all checkpoints for an execution.
   *
   * @param executionId the execution identifier
   * @return a Mono that completes when all checkpoints are cleared
   */
  public Mono<Void> clearAllCheckpoints(@NotBlank final String executionId) {
    return store.clearAllCheckpoints(executionId);
  }
}
