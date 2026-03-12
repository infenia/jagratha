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

import com.infenia.yukta.plugin.core.WorkflowPlugin;
import com.infenia.yukta.plugin.message.Message;
import com.infenia.yukta.service.control.ControlSignalHandler;
import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import reactor.util.concurrent.Queues;

/**
 * Service for managing the system's Control Bus.
 *
 * <p>Handles administrative commands, heartbeats, and performance metrics from plugins.
 * Dispatches signals to registered handlers for extensible processing.
 */
@Slf4j
@Service
public class ControlBusService {

  private final int batchSize;
  private final Duration batchTimeout;
  private final int bufferSize;
  private final List<ControlSignalHandler> handlers;
  private final Map<String, WorkflowPlugin> activePlugins = new ConcurrentHashMap<>();
  private Sinks.Many<Message<?>> controlSink;
  private static final Sinks.EmitFailureHandler RETRY_HANDLER =
      Sinks.EmitFailureHandler.busyLooping(Duration.ofMillis(100));

  public ControlBusService(
      @Value("${control.bus.batch.size:100}") final int batchSize,
      @Value("${control.bus.batch.timeout.ms:50}") final int batchTimeoutMs,
      @Value("${control.bus.buffer.size:256}") final int bufferSize,
      final List<ControlSignalHandler> handlers) {
    this.batchSize = batchSize;
    this.batchTimeout = Duration.ofMillis(batchTimeoutMs);
    this.bufferSize = bufferSize;
    this.handlers = handlers;
  }

  /** Initialize the control sink and background event consumer. */
  @PostConstruct
  public void init() {
    controlSink =
        Sinks.many()
            .multicast()
            .onBackpressureBuffer(
                bufferSize < 0 ? Queues.SMALL_BUFFER_SIZE : bufferSize, false);

    controlSink
        .asFlux()
        .publishOn(Schedulers.parallel())
        .bufferTimeout(batchSize, batchTimeout)
        .concatMap(
            batch ->
                Mono.fromRunnable(() -> handleControlBatch(batch))
                    .onErrorResume(
                        e -> {
                          log.atError().setCause(e).log("Error processing control signal batch");
                          return Mono.empty();
                        }))
        .subscribe();
  }

  /**
   * Emit a control signal to the bus.
   *
   * @param signal the control signal message
   * @return a Mono that completes when the signal is emitted
   */
  public Mono<Void> emit(final Message<?> signal) {
    return Mono.fromRunnable(() -> controlSink.emitNext(signal, RETRY_HANDLER));
  }

  /**
   * Get a stream of all control signals.
   *
   * @return a Flux of control messages
   */
  public Flux<Message<?>> getControlStream() {
    return controlSink.asFlux();
  }

  /**
   * Get the last heartbeat for a node.
   *
   * @param nodeId the node identifier
   * @return the last heartbeat message, or null
   */
  public Message<?> getLastHeartbeat(final String nodeId) {
    for (final ControlSignalHandler handler : handlers) {
      if (handler instanceof com.infenia.yukta.service.control.ControlHeartbeatHandler hb) {
        return hb.getLastHeartbeat(nodeId);
      }
    }
    return null;
  }

  /**
   * Get the last statistics for a node.
   *
   * @param nodeId the node identifier
   * @return the last statistics message, or null
   */
  public Message<?> getLastStatistics(final String nodeId) {
    for (final ControlSignalHandler handler : handlers) {
      if (handler instanceof com.infenia.yukta.service.control.ControlStatisticsHandler cs) {
        return cs.getLastStatistics(nodeId);
      }
    }
    return null;
  }

  /**
   * List all node IDs that have emitted heartbeats.
   *
   * @return list of node IDs
   */
  public List<String> getActiveNodes() {
    for (final ControlSignalHandler handler : handlers) {
      if (handler instanceof com.infenia.yukta.service.control.ControlHeartbeatHandler hb) {
        return hb.getActiveNodes();
      }
    }
    return List.of();
  }

  /**
   * Register a plugin to receive control signals.
   *
   * @param nodeId the node identifier
   * @param plugin the plugin instance
   */
  public void registerPlugin(@NotBlank final String nodeId, final WorkflowPlugin plugin) {
    activePlugins.put(nodeId, plugin);
  }

  /**
   * Unregister a plugin from the control bus and clean up all state.
   *
   * @param nodeId the node identifier
   */
  public void unregisterPlugin(@NotBlank final String nodeId) {
    activePlugins.remove(nodeId);
    // Clean up handler state
    for (final ControlSignalHandler handler : handlers) {
      if (handler instanceof com.infenia.yukta.service.control.ControlHeartbeatHandler hb) {
        hb.removeNode(nodeId);
      } else if (handler instanceof com.infenia.yukta.service.control.ControlStatisticsHandler cs) {
        cs.removeNode(nodeId);
      }
    }
  }

  /**
   * Send a command to a specific node and wait for response.
   *
   * @param nodeId the target node identifier
   * @param command the command message
   * @return a Mono of the response message
   */
  public Mono<Message<?>> sendCommand(
      @NotBlank final String nodeId, final Message<?> command) {
    final WorkflowPlugin plugin = activePlugins.get(nodeId);
    if (plugin == null) {
      return Mono.error(new IllegalArgumentException("Node not found: " + nodeId));
    }
    return plugin.onControlSignal(command);
  }

  /**
   * Shutdown the control bus gracefully.
   *
   * <p>Signals completion on the control sink, terminating the control stream and allowing
   * subscribers to close cleanly.
   */
  public void shutdown() {
    controlSink.emitComplete(RETRY_HANDLER);
  }

  private void handleControlBatch(final List<Message<?>> batch) {
    final List<Message<?>> prioritized =
        batch.stream()
            .sorted(Comparator.comparingInt((Message<?> m) -> m.getPriority()).reversed())
            .toList();

    for (final Message<?> msg : prioritized) {
      final Object payload = msg.getPayload();
      final String nodeId = msg.getSourceNodeId();

      if (nodeId != null) {
        for (final ControlSignalHandler handler : handlers) {
          if (handler.canHandle(payload)) {
            handler.handle(nodeId, msg, payload);
            break;
          }
        }
      }
    }
  }
}
