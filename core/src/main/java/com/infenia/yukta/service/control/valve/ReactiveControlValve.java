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
package com.infenia.yukta.service.control.valve;

import java.util.concurrent.atomic.AtomicBoolean;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;

/**
 * Reactive flow control valve for pause/resume via backpressure.
 *
 * <p>Uses {@code delayUntil} to halt downstream items when paused. Fast path when flowing; blocks
 * when paused until explicit resume.
 */
public final class ReactiveControlValve {

  private final AtomicBoolean paused = new AtomicBoolean(false);
  private final Many<Boolean> resumeSink = Sinks.many().replay().latestOrDefault(true);

  /**
   * Checks if passage is allowed. Returns immediately if flowing; blocks if paused.
   *
   * @return Mono that emits true when passage is allowed
   */
  public Mono<Boolean> allowPassage() {
    if (!paused.get()) {
      return Mono.just(true);
    }
    return resumeSink.asFlux().filter(Boolean::booleanValue).next().thenReturn(true);
  }

  /** Pauses the valve, causing downstream to block until resumed. */
  public void pause() {
    paused.set(true);
    resumeSink.tryEmitNext(false);
  }

  /** Resumes the valve, unblocking downstream. */
  public void resume() {
    paused.set(false);
    resumeSink.tryEmitNext(true);
  }

  /**
   * Checks current pause state without blocking.
   *
   * @return true if paused, false if flowing
   */
  public boolean isPaused() {
    return paused.get();
  }

  /**
   * Applies this valve as a transform to a flux source.
   *
   * <p>Usage: {@code flux.transform(valve.asTransform())}
   *
   * @return a function suitable for use with {@code Flux.transform()}
   */
  public <T> java.util.function.Function<Flux<T>, Flux<T>> asTransform() {
    return source -> source.delayUntil(t -> allowPassage().then());
  }
}
