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
package com.infenia.yukta.plugin.store;

import com.infenia.yukta.plugin.message.Message;
import reactor.core.publisher.Mono;

/**
 * Interface for persisting and retrieving checkpoint messages during workflow execution.
 *
 * <p>Checkpoints allow workflows to resume from a specific node using previously saved message
 * state, enabling resumption without re-executing upstream nodes.
 */
@SuppressWarnings("PMD.LinguisticNaming")
public interface CheckpointStore {

  /**
   * Save a checkpoint message for a node.
   *
   * @param executionId the execution identifier
   * @param nodeId the node identifier
   * @param message the message to checkpoint
   * @return a Mono that completes when the checkpoint is saved
   */
  Mono<Void> saveCheckpoint(String executionId, String nodeId, Message<?> message);

  /**
   * Load a checkpoint message for a node.
   *
   * @param executionId the execution identifier
   * @param nodeId the node identifier
   * @return a Mono emitting the checkpointed message, or empty if not found
   */
  Mono<Message<?>> loadCheckpoint(String executionId, String nodeId);

  /**
   * Check if a checkpoint exists for a node.
   *
   * @param executionId the execution identifier
   * @param nodeId the node identifier
   * @return a Mono emitting true if checkpoint exists, false otherwise
   */
  Mono<Boolean> hasCheckpoint(String executionId, String nodeId);

  /**
   * Clear the checkpoint for a node.
   *
   * @param executionId the execution identifier
   * @param nodeId the node identifier
   * @return a Mono that completes when the checkpoint is cleared
   */
  Mono<Void> clearCheckpoint(String executionId, String nodeId);

  /**
   * Clear all checkpoints for an execution.
   *
   * @param executionId the execution identifier
   * @return a Mono that completes when all checkpoints are cleared
   */
  Mono<Void> clearAllCheckpoints(String executionId);
}
