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
package com.infenia.yukta.service.orchestrator.stream;

import com.infenia.yukta.model.workflow.ParentEdgeInfo;
import com.infenia.yukta.plugin.message.Message;
import com.infenia.yukta.plugin.store.MessageStore;
import com.infenia.yukta.plugin.store.NodeCheckpointStore;
import com.infenia.yukta.service.orchestrator.tracker.TaskTrackerService;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * Decorates reactive message streams with topology-aware routing, logging, and checkpointing.
 *
 * <p>Encapsulates complex Flux manipulation for:
 *
 * <ul>
 *   <li>Merging parent streams with edge-based routing and filtering
 *   <li>Applying logging, wire-tap, and checkpoint operators
 *   <li>Broadcasting messages to downstream subscribers via multicast sinks
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StreamTopologyDecorator {

  private static final int SINGLE_PARENT = 1;

  @Nullable private final MessageStore messageStore;
  private final TaskTrackerService tracker;
  private final NodeCheckpointStore checkpointStore;

  private static final Sinks.EmitFailureHandler RETRY_HANDLER =
      (unusedSignalType, emitResult) -> handleEmitFailure(emitResult);

  // ~ Package-private visibility
  /* default */ static boolean handleEmitFailure(final Sinks.EmitResult emitResult) {
    final boolean shouldRetry = emitResult == Sinks.EmitResult.FAIL_NON_SERIALIZED;
    if (shouldRetry) {
      java.util.concurrent.locks.LockSupport.parkNanos(10_000);
    }
    return shouldRetry;
  }

  /**
   * Merges parent node streams, applying edge-based routing and port filtering.
   *
   * <p>Routes incoming messages from parent nodes based on edge metadata (source node ID and
   * optional port). Returns empty flux if no parents.
   *
   * @param streams array of all node Flux streams indexed by their topological position
   * @param parentEdges edge metadata describing parent connections
   * @return merged Flux with routed and filtered messages
   */
  public Flux<Message<?>> mergeParentStreams(
      final Flux<Message<?>>[] streams, final ParentEdgeInfo... parentEdges) {
    final List<Flux<Message<?>>> parentFluxes = new ArrayList<>(parentEdges.length);
    for (final ParentEdgeInfo edge : parentEdges) {
      parentFluxes.add(applyEdgeRouting(streams, edge));
    }

    return parentFluxes.isEmpty()
        ? Flux.empty()
        : parentFluxes.size() == SINGLE_PARENT ? parentFluxes.get(0) : Flux.merge(parentFluxes);
  }

  /**
   * Routes a single parent stream, filtering by port if specified.
   *
   * <p>Applies source node ID mapping and optional port-based filtering. Returns the routed stream.
   *
   * @param streams array of all node Flux streams
   * @param edge edge metadata (parent index, source node ID, source port)
   * @return routed Flux with messages mapped to source node
   */
  public Flux<Message<?>> applyEdgeRouting(
      final Flux<Message<?>>[] streams, final ParentEdgeInfo edge) {
    Flux<Message<?>> stream =
        streams[edge.parentIndex()].map(msg -> msg.withSourceNodeId(edge.sourceNodeId()));

    if (edge.sourcePort() != null) {
      stream = stream.filter(msg -> edge.sourcePort().equals(msg.getSourcePort()));
    }
    return stream;
  }

  /**
   * Applies logging, wire-tap, and checkpointing, then broadcasts to downstream subscribers.
   *
   * <p>Decorates stream with:
   *
   * <ul>
   *   <li>Reactor logging (if DEBUG enabled)
   *   <li>Wire-tap to MessageStore (if available)
   *   <li>Checkpoint save to NodeCheckpointStore
   *   <li>Task tracker log events (if TRACE enabled)
   * </ul>
   *
   * <p>Broadcasts via multicast sink to support multiple downstream subscribers without replaying
   * upstream operators.
   *
   * @param executionId the execution identifier
   * @param nodeId the node identifier
   * @param stream the incoming message stream
   * @param bufferSize multicast sink buffer size
   * @param disposables lifecycle management for subscriptions
   * @param connectors deferred subscription tasks (executed during assembly finalization)
   * @return multicast Flux for downstream consumption
   */
  public Flux<Message<?>> applyLoggingAndBroadcasting(
      final String executionId,
      final String nodeId,
      final Flux<Message<?>> stream,
      final int bufferSize,
      final List<Disposable> disposables,
      final List<Runnable> connectors) {
    final Flux<Message<?>> historiedStream = stream.map(msg -> msg.withAddedHistory(nodeId));
    final Flux<Message<?>> processedStream = getMessageFlux(executionId, nodeId, historiedStream);

    final Sinks.Many<Message<?>> sink = Sinks.many().multicast().onBackpressureBuffer(bufferSize);

    connectors.add(
        () ->
            disposables.add(
                processedStream.subscribe(
                    msg -> sink.emitNext(msg, RETRY_HANDLER),
                    err -> sink.emitError(err, RETRY_HANDLER),
                    () -> sink.emitComplete(RETRY_HANDLER))));

    return sink.asFlux();
  }

  /**
   * Applies operators for conditional logging, wire-tapping, and checkpointing.
   *
   * <p>Pipeline:
   *
   * <ol>
   *   <li>Reactor logging (if DEBUG enabled on this class)
   *   <li>Wire-tap to MessageStore (if configured)
   *   <li>Checkpoint save to NodeCheckpointStore
   *   <li>Task tracker emission (if TRACE enabled)
   * </ol>
   *
   * @param executionId the execution identifier
   * @param nodeId the node identifier
   * @param stream the incoming message stream
   * @return decorated Flux with logging, wire-tap, and checkpoint operators
   */
  private Flux<Message<?>> getMessageFlux(
      final String executionId, final String nodeId, final Flux<Message<?>> stream) {
    Flux<Message<?>> logStream =
        stream.doOnNext(msg -> log.atDebug().log("Node-{}: {}", nodeId, msg));

    if (messageStore != null) {
      logStream = logStream.flatMap(msg -> messageStore.store(msg).thenReturn(msg));
    }

    logStream =
        logStream.flatMap(msg -> checkpointStore.save(executionId, nodeId, msg).thenReturn(msg));

    return logStream.doOnNext(
        msg -> {
          log.atTrace().log("Processing message: {}", msg.getPayload());
          tracker.emitLogEvent(executionId, String.valueOf(msg.getPayload()));
        });
  }
}
