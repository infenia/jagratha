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
package com.infenia.yukta.service.execution.status;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.util.concurrent.Queues;

/**
 * Default implementation of ExecutionStatusPublisher using Reactor Sinks.
 *
 * <p>Maintains an internal multicast sink that receives status events from the orchestrator and
 * distributes them to all subscribers (control bus, task tracker, monitoring systems, etc.).
 */
@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class DefaultExecutionStatusPublisher implements ExecutionStatusPublisher {

  private static final Sinks.EmitFailureHandler RETRY_HANDLER =
      Sinks.EmitFailureHandler.busyLooping(Duration.ofMillis(100));

  private final Sinks.Many<ExecutionStatusEvent> statusSink =
      Sinks.many().multicast().onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);

  @Override
  public Mono<Void> publishStatus(@NotNull final ExecutionStatusEvent event) {
    return Mono.create(
        sink -> {
          try {
            statusSink.emitNext(event, RETRY_HANDLER);
            sink.success();
          } catch (final RuntimeException e) {
            log.atError()
                .setCause(e)
                .addKeyValue("executionId", event.executionId())
                .log("Failed to publish status event");
            sink.error(new IllegalStateException("Status event publish failed", e));
          }
        });
  }

  @Override
  public Flux<ExecutionStatusEvent> statusStream() {
    return statusSink.asFlux();
  }
}
