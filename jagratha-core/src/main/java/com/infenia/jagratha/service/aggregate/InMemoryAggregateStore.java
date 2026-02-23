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
package com.infenia.jagratha.service.aggregate;

import com.infenia.jagratha.plugin.Message;
import com.infenia.jagratha.util.SpelUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/** In-memory implementation of AggregateStore using LinkedHashMap for LRU. */
@Slf4j
@Component
@SuppressWarnings({"PMD.OnlyOneReturn", "PMD.DoNotUseThreads", "PMD.TooManyMethods"})
public class InMemoryAggregateStore implements AggregateStore {

  private static final int CLEANUP_DELAY_MS = 100;

  private final Map<String, AggregateState> store =
      Collections.synchronizedMap(
          new LinkedHashMap<>(16, 0.75f, true));

  private final Sinks.Many<AggregateResult> asyncResults =
      Sinks.many().multicast().onBackpressureBuffer();

  private final ScheduledExecutorService scheduler =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            final Thread thread = new Thread(r, "aggregate-store-cleanup");
            thread.setDaemon(true);
            return thread;
          });

  /** Default constructor. */
  public InMemoryAggregateStore() {
    super();
  }

  /** Initialize the cleanup scheduler. */
  @PostConstruct
  public void init() {
    scheduler.scheduleAtFixedRate(
        this::cleanup, CLEANUP_DELAY_MS, CLEANUP_DELAY_MS, TimeUnit.MILLISECONDS);
  }

  /** Shutdown the scheduler. */
  @PreDestroy
  public void shutdown() {
    scheduler.shutdown();
    try {
      if (!scheduler.awaitTermination(1, TimeUnit.SECONDS)) {
        scheduler.shutdownNow();
      }
    } catch (final InterruptedException e) {
      scheduler.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public Mono<AggregateResult> addValue(
      final String key, final Object value, final Message message, final AggregateConfig config) {
    return Mono.fromCallable(() -> processAdd(key, value, message, config));
  }

  private AggregateResult processAdd(
      final String key, final Object value, final Message message, final AggregateConfig config) {

    AggregateResult evictionResult = null;
    synchronized (store) {
      if (!store.containsKey(key) && store.size() >= config.maxPendingWindows()) {
        final var it = store.entrySet().iterator();
        if (it.hasNext()) {
          final var entry = it.next();
          it.remove();
          final AggregateState oldest = entry.getValue();
          evictionResult =
              new AggregateResult(
                  AggregateResult.Status.EVICTED,
                  oldest.getFinalResult(),
                  entry.getKey(),
                  oldest.getLastMessage());
        }
      }
    }

    if (evictionResult != null) {
      asyncResults.tryEmitNext(evictionResult);
    }

    final AggregateState state =
        store.compute(
            key,
            (k, existing) -> {
              AggregateState s = existing;
              if (s == null) {
                s = new AggregateState(message, config);
              }
              s.update(value, message);
              return s;
            });

    if (state.isTriggered()) {
      store.remove(key);
      return new AggregateResult(
          AggregateResult.Status.COMPLETED, state.getFinalResult(), key, state.getLastMessage());
    }

    return new AggregateResult(AggregateResult.Status.WAITING, null, key, message);
  }

  @Override
  public Mono<AggregateResult> flush(final String key) {
    return Mono.fromCallable(
        () -> {
          final AggregateState state = store.remove(key);
          if (state == null) {
            return null;
          }
          return new AggregateResult(
              AggregateResult.Status.COMPLETED, state.getFinalResult(), key, state.getLastMessage());
        });
  }

  @Override
  public Flux<AggregateResult> flushAll() {
    return flushAll("");
  }

  @Override
  public Flux<AggregateResult> flushAll(final String keyPrefix) {
    final List<AggregateResult> results = new ArrayList<>();
    synchronized (store) {
      final var it = store.entrySet().iterator();
      while (it.hasNext()) {
        final var entry = it.next();
        if (entry.getKey().startsWith(keyPrefix)) {
          final AggregateState state = entry.getValue();
          results.add(
              new AggregateResult(
                  AggregateResult.Status.COMPLETED,
                  state.getFinalResult(),
                  entry.getKey(),
                  state.getLastMessage()));
          it.remove();
        }
      }
    }
    return Flux.fromIterable(results);
  }

  @Override
  public Flux<AggregateResult> getAsyncResults() {
    return asyncResults.asFlux();
  }

  private void cleanup() {
    final long now = System.currentTimeMillis();
    final List<String> toRemove = new ArrayList<>();
    final List<String> toDiscard = new ArrayList<>();

    synchronized (store) {
      for (final Map.Entry<String, AggregateState> entry : store.entrySet()) {
        final AggregateState state = entry.getValue();
        final AggregateConfig config = state.getConfig();

        if ("TIME".equals(config.windowType())) {
          if (now - state.getStartTime() >= config.durationMs()) {
            if (config.emitOnTimeout()) {
              toRemove.add(entry.getKey());
            } else {
              toDiscard.add(entry.getKey());
            }
          }
        } else if ("SESSION".equals(config.windowType())) {
          if (now - state.getLastAccessTime() >= config.durationMs()) {
            if (config.emitOnTimeout()) {
              toRemove.add(entry.getKey());
            } else {
              toDiscard.add(entry.getKey());
            }
          }
        }
      }
      toDiscard.forEach(store::remove);
      toRemove.forEach(
          key -> {
            final AggregateState state = store.remove(key);
            if (state != null) {
              asyncResults.tryEmitNext(
                  new AggregateResult(
                      AggregateResult.Status.COMPLETED,
                      state.getFinalResult(),
                      key,
                      state.getLastMessage()));
            }
          });
    }
  }

  private static class AggregateState {
    private Object accumulator;
    private int count;
    private Message lastMessage;
    private final long startTime;
    private long lastAccessTime;
    private final AggregateConfig config;

    AggregateState(final Message message, final AggregateConfig config) {
      this.startTime = System.currentTimeMillis();
      this.lastAccessTime = startTime;
      this.lastMessage = message;
      this.config = config;
      this.accumulator = initAccumulator(config);
    }

    private Object initAccumulator(final AggregateConfig cfg) {
      return switch (cfg.aggregationType()) {
        case "SUM", "AVERAGE", "MIN", "MAX" -> 0.0;
        case "COLLECT_LIST" -> new ArrayList<>();
        case "CUSTOM" -> cfg.customInitValue();
        default -> null;
      };
    }

    void update(final Object value, final Message message) {
      this.lastAccessTime = System.currentTimeMillis();
      this.lastMessage = message;

      Object val = value;
      if (val == null) {
        switch (config.nullPolicy()) {
          case "ZERO" -> val = 0.0;
          case "FAIL" -> throw new IllegalArgumentException("Null value not allowed");
          case "IGNORE" -> {
            return;
          }
          default -> {
            return;
          }
        }
      }

      count++;
      accumulator = performAggregation(accumulator, val);
    }

    private Object performAggregation(final Object acc, final Object val) {
      Number nVal = null;
      if (val instanceof Number num) {
        nVal = num;
      } else if (!"COLLECT_LIST".equals(config.aggregationType())
          && !"CUSTOM".equals(config.aggregationType())) {
        try {
          nVal = Double.parseDouble(val.toString());
        } catch (final NumberFormatException e) {
          nVal = 0.0;
        }
      }

      return switch (config.aggregationType()) {
        case "SUM", "AVERAGE" ->
            ((Number) acc).doubleValue() + (nVal != null ? nVal.doubleValue() : 0.0);
        case "COLLECT_LIST" -> {
          @SuppressWarnings("unchecked")
          final List<Object> list = (List<Object>) acc;
          list.add(val);
          yield list;
        }
        case "MIN" ->
            count == 1
                ? (nVal != null ? nVal.doubleValue() : 0.0)
                : Math.min(((Number) acc).doubleValue(), (nVal != null ? nVal.doubleValue() : 0.0));
        case "MAX" ->
            count == 1
                ? (nVal != null ? nVal.doubleValue() : 0.0)
                : Math.max(((Number) acc).doubleValue(), (nVal != null ? nVal.doubleValue() : 0.0));
        case "CUSTOM" ->
            SpelUtils.evaluateSync(
                config.customAccumulateExp(), lastMessage, Map.of("acc", acc, "val", val));
        default -> acc;
      };
    }

    Object getFinalResult() {
      Object result = accumulator;
      if ("AVERAGE".equals(config.aggregationType())) {
        result = count > 0 ? ((Number) accumulator).doubleValue() / count : 0.0;
      } else if ("CUSTOM".equals(config.aggregationType()) && config.customResultExp() != null) {
        result =
            SpelUtils.evaluateSync(config.customResultExp(), lastMessage, Map.of("acc", accumulator));
      }
      return result;
    }

    boolean isTriggered() {
      return "COUNT".equals(config.windowType()) && count >= config.windowSize();
    }

    long getStartTime() {
      return startTime;
    }

    long getLastAccessTime() {
      return lastAccessTime;
    }

    Message getLastMessage() {
      return lastMessage;
    }

    AggregateConfig getConfig() {
      return config;
    }
  }
}
