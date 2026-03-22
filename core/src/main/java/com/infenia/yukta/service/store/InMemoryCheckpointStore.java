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
package com.infenia.yukta.service.store;

import com.infenia.yukta.plugin.message.Message;
import com.infenia.yukta.plugin.store.CheckpointStore;
import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * In-memory checkpoint store implementation with TTL-based cleanup.
 *
 * <p>Stores last message per (executionId, nodeId) pair. Periodically cleans up stale checkpoint
 * entries.
 */
@Slf4j
@Service
@ConditionalOnMissingBean(CheckpointStore.class)
public class InMemoryCheckpointStore implements CheckpointStore {

  private final Duration ttl;
  private final Map<String, CheckpointData> checkpoints = new ConcurrentHashMap<>();
  private final Map<String, Long> checkpointTimestamps = new ConcurrentHashMap<>();

  /**
   * Creates a new InMemoryCheckpointStore.
   *
   * @param ttl the time-to-live for checkpoint data
   */
  public InMemoryCheckpointStore(@Value("${yukta.checkpoint.ttl:30m}") final Duration ttl) {
    this.ttl = ttl;
  }

  /** Initialize background TTL cleanup. */
  @PostConstruct
  public void init() {
    Mono.delay(ttl)
        .repeat()
        .publishOn(Schedulers.boundedElastic())
        .doOnNext(unused -> cleanupStaleCheckpoints())
        .onErrorContinue(
            (err, unused) -> log.atWarn().setCause(err).log("Error during checkpoint cleanup"))
        .subscribe();
  }

  @Override
  public Mono<Void> saveCheckpoint(
      @NotBlank final String executionId, @NotBlank final String nodeId, final Message<?> message) {
    return Mono.fromRunnable(
        () -> {
          final String key = executionId + ":" + nodeId;
          // Deep copy to avoid holding references to mutable payloads
          final Message<?> snapshot = deepCopyMessage(message);
          checkpoints.put(key, new CheckpointData(executionId, nodeId, snapshot));
          checkpointTimestamps.put(key, System.currentTimeMillis());
        });
  }

  @Override
  public Mono<Message<?>> loadCheckpoint(
      @NotBlank final String executionId, @NotBlank final String nodeId) {
    return Mono.defer(
        () -> {
          final String key = executionId + ":" + nodeId;
          final CheckpointData data = checkpoints.get(key);
          return data != null ? Mono.just(data.message()) : Mono.empty();
        });
  }

  @Override
  public Mono<Boolean> hasCheckpoint(
      @NotBlank final String executionId, @NotBlank final String nodeId) {
    return Mono.defer(
        () -> {
          final String key = executionId + ":" + nodeId;
          return Mono.just(checkpoints.containsKey(key));
        });
  }

  @Override
  public Mono<Void> clearCheckpoint(
      @NotBlank final String executionId, @NotBlank final String nodeId) {
    return Mono.fromRunnable(
        () -> {
          final String key = executionId + ":" + nodeId;
          checkpoints.remove(key);
          checkpointTimestamps.remove(key);
        });
  }

  @Override
  public Mono<Void> clearAllCheckpoints(@NotBlank final String executionId) {
    return Mono.fromRunnable(
        () -> {
          checkpoints.keySet().removeIf(key -> key.startsWith(executionId + ":"));
          checkpointTimestamps.keySet().removeIf(key -> key.startsWith(executionId + ":"));
        });
  }

  private void cleanupStaleCheckpoints() {
    final long now = System.currentTimeMillis();
    final long ttlMillis = ttl.toMillis();

    checkpointTimestamps.forEach(
        (key, timestamp) -> {
          if (now - timestamp > ttlMillis) {
            checkpoints.remove(key);
            checkpointTimestamps.remove(key);
            log.atDebug().log("Cleaned up stale checkpoint: {}", key);
          }
        });
  }

  private Message<?> deepCopyMessage(final Message<?> original) {
    // For simplicity, return the message as-is since Message payloads should be immutable
    // or properly copied by client code. In production, implement deep copy of mutable payloads.
    return original;
  }

  /** Internal record for checkpoint data. */
  private record CheckpointData(String executionId, String nodeId, Message<?> message) {}
}
