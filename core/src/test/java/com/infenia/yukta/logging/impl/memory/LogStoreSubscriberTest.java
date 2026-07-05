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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infenia.yukta.logging.api.LogLevel;
import com.infenia.yukta.logging.api.LogStream;
import com.infenia.yukta.logging.api.PluginLogEntry;
import com.infenia.yukta.logging.api.PluginLogStore;
import com.infenia.yukta.service.orchestrator.tracker.DefaultTaskTrackerService;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class LogStoreSubscriberTest {

  @Mock private DefaultTaskTrackerService taskTracker;
  @Mock private PluginLogStore store;

  private LogStoreSubscriber subscriber;

  @BeforeEach
  void setUp() {
    subscriber = new LogStoreSubscriber(store, taskTracker);
  }

  @Test
  void testSubscriberWritesEntriesNonBlocking() {
    final PluginLogEntry entry1 =
        new PluginLogEntry(
            "exec-1",
            "session-1",
            "processor-1",
            "Processor",
            LogStream.STDOUT,
            "Message 1",
            LogLevel.INFO,
            Instant.now());

    final PluginLogEntry entry2 =
        new PluginLogEntry(
            "exec-1",
            "session-1",
            "processor-1",
            "Processor",
            LogStream.STDOUT,
            "Message 2",
            LogLevel.INFO,
            Instant.now());

    when(taskTracker.getLogFlux()).thenReturn(Flux.just(entry1, entry2));
    when(store.write(any(PluginLogEntry.class))).thenReturn(Mono.empty());

    subscriber.init();

    // Allow time for async subscription to complete
    try {
      Thread.sleep(200);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    verify(store, times(2)).write(any(PluginLogEntry.class));

    subscriber.dispose();
  }

  @Test
  void testSubscriberHandlesWriteErrors() {
    final PluginLogEntry entry =
        new PluginLogEntry(
            "exec-1",
            "session-1",
            "processor-1",
            "Processor",
            LogStream.STDERR,
            "Error message",
            LogLevel.ERROR,
            Instant.now());

    when(taskTracker.getLogFlux()).thenReturn(Flux.just(entry));
    when(store.write(any(PluginLogEntry.class)))
        .thenReturn(Mono.error(new RuntimeException("Store write failed")));

    subscriber.init();

    // Should not throw, error should be handled gracefully
    try {
      Thread.sleep(200);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    subscriber.dispose();
  }
}
