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
package com.infenia.yukta.logging.impl.memory;

import com.infenia.yukta.logging.api.PluginLogStore;
import com.infenia.yukta.service.orchestrator.tracker.DefaultTaskTrackerService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.scheduler.Schedulers;

/**
 * Non-blocking subscriber to log events from DefaultTaskTrackerService.
 *
 * <p>Subscribes once to the tracker's log sink at startup and writes each log entry to the
 * PluginLogStore asynchronously on boundedElastic scheduler. Designed to be transparent to
 * execution flow — writes happen in parallel, never blocking the main execution threads.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LogStoreSubscriber {

  /** The log storage backend. */
  private final PluginLogStore store;

  /** The source of log events. */
  private final DefaultTaskTrackerService taskTracker;

  /** The subscription to the log event stream. */
  private Disposable subscription;

  /**
   * Initialize and subscribe to the log sink.
   *
   * <p>Called automatically by Spring after construction. Subscribes to task tracker's log events
   * and writes them to the store.
   */
  @PostConstruct
  public void init() {
    subscription =
        taskTracker
            .getLogFlux()
            .flatMap(
                entry ->
                    store
                        .write(entry)
                        .onErrorResume(
                            error -> {
                              log.atWarn()
                                  .addKeyValue("executionId", entry.executionId())
                                  .addKeyValue("error", error.getMessage())
                                  .log("Failed to write log entry");
                              return reactor.core.publisher.Mono.empty();
                            }))
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe(
                _ -> {},
                error -> log.error("Log store subscription failed", error),
                () -> log.info("Log store subscription completed"));

    log.info("Log store subscriber initialized");
  }

  /** Dispose of the subscription when component is destroyed. */
  @PreDestroy
  public void dispose() {
    if (subscription != null && !subscription.isDisposed()) {
      subscription.dispose();
      log.info("Log store subscriber disposed");
    }
  }
}
