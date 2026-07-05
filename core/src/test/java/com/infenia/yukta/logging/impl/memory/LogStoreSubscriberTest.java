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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.infenia.yukta.logging.api.LogLevel;
import com.infenia.yukta.logging.api.LogStream;
import com.infenia.yukta.logging.api.PluginLogEntry;
import com.infenia.yukta.logging.api.PluginLogStore;
import com.infenia.yukta.service.orchestrator.tracker.DefaultTaskTrackerService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Tests for LogStoreSubscriber reactive subscription to log flux. */
@MockitoSettings
@NoArgsConstructor
@DisplayName("LogStoreSubscriber")
class LogStoreSubscriberTest {

  /** Mock task tracker service. */
  @Mock private DefaultTaskTrackerService taskTracker;

  /** Mock log store for persistence. */
  @Mock private PluginLogStore store;

  @Captor private ArgumentCaptor<PluginLogEntry> entryCaptor;

  /** Subject under test. */
  private LogStoreSubscriber subscriber;

  @BeforeEach
  void setUp() {
    when(taskTracker.getLogFlux()).thenReturn(Flux.empty());
    subscriber = new LogStoreSubscriber(store, taskTracker);
  }

  @Nested
  @DisplayName("Initialization and Subscription")
  class InitializationTests {

    @Test
    @DisplayName("init subscribes to log flux and writes entries")
    void init_subscribesToLogFlux_writesToStore() {
      final PluginLogEntry entry =
          new PluginLogEntry(
              "exec-1",
              "session-1",
              "processor-1",
              "Processor",
              LogStream.STDOUT,
              "Message 1",
              LogLevel.INFO,
              Instant.now());

      when(taskTracker.getLogFlux()).thenReturn(Flux.just(entry));
      when(store.write(any(PluginLogEntry.class))).thenReturn(Mono.empty());

      subscriber.init();

      try {
        Thread.sleep(200);
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
      }

      verify(store).write(any(PluginLogEntry.class));
      subscriber.dispose();
    }

    @Test
    @DisplayName("init with multiple log events writes all entries")
    void init_multipleLogEvents_allWritten() {
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

      final PluginLogEntry entry3 =
          new PluginLogEntry(
              "exec-1",
              "session-1",
              "processor-1",
              "Processor",
              LogStream.STDERR,
              "Message 3",
              LogLevel.ERROR,
              Instant.now());

      when(taskTracker.getLogFlux()).thenReturn(Flux.just(entry1, entry2, entry3));
      when(store.write(any(PluginLogEntry.class))).thenReturn(Mono.empty());

      subscriber.init();

      try {
        Thread.sleep(300);
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
      }

      verify(store, times(3)).write(any(PluginLogEntry.class));
      subscriber.dispose();
    }
  }

  @Nested
  @DisplayName("Error Handling")
  class ErrorHandlingTests {

    @Test
    @DisplayName("handles write errors gracefully without throwing")
    void init_writeErrorOccurs_handlesErrorGracefully() {
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

      try {
        Thread.sleep(200);
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
      }

      verify(store).write(any(PluginLogEntry.class));
      subscriber.dispose();
    }

    @Test
    @DisplayName("handles multiple errors without terminating subscription")
    void init_multipleErrors_continuesProcessing() {
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
      when(store.write(any(PluginLogEntry.class)))
          .thenReturn(
              Mono.error(new RuntimeException("Store write failed")),
              Mono.error(new RuntimeException("Store write failed")));

      subscriber.init();

      try {
        Thread.sleep(200);
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
      }

      verify(store, times(2)).write(any(PluginLogEntry.class));
      subscriber.dispose();
    }
  }

  @Nested
  @DisplayName("Cleanup and Disposal")
  class CleanupTests {

    @Test
    @DisplayName("dispose does not throw exception")
    void dispose_withActiveSubscription_disposesSubscription() {
      final PluginLogEntry entry =
          new PluginLogEntry(
              "exec-1",
              "session-1",
              "processor-1",
              "Processor",
              LogStream.STDOUT,
              "Message",
              LogLevel.INFO,
              Instant.now());

      when(taskTracker.getLogFlux()).thenReturn(Flux.just(entry));
      when(store.write(any(PluginLogEntry.class))).thenReturn(Mono.empty());

      subscriber.init();

      try {
        Thread.sleep(100);
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
      }

      // Should not throw
      subscriber.dispose();
      assertThat(subscriber).isNotNull();
    }
  }

  @Nested
  @DisplayName("Entry Capture and Verification")
  class EntryCapturingTests {

    @Test
    @DisplayName("captures and verifies entry details")
    void init_capturesEntryDetails_verifiesContent() {
      final Instant now = Instant.now();
      final PluginLogEntry entry =
          new PluginLogEntry(
              "exec-123",
              "session-456",
              "plugin-789",
              "MyPlugin",
              LogStream.STDOUT,
              "Test log message",
              LogLevel.INFO,
              now);

      when(taskTracker.getLogFlux()).thenReturn(Flux.just(entry));
      when(store.write(any(PluginLogEntry.class))).thenReturn(Mono.empty());

      subscriber.init();

      try {
        Thread.sleep(200);
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
      }

      verify(store).write(entryCaptor.capture());
      final PluginLogEntry captured = entryCaptor.getValue();
      assertThat(captured.executionId()).isEqualTo("exec-123");
      assertThat(captured.sessionId()).isEqualTo("session-456");
      assertThat(captured.pluginId()).isEqualTo("plugin-789");
      assertThat(captured.pluginName()).isEqualTo("MyPlugin");
      assertThat(captured.stream()).isEqualTo(LogStream.STDOUT);
      assertThat(captured.message()).isEqualTo("Test log message");
      assertThat(captured.logLevel()).isEqualTo(LogLevel.INFO);
      assertThat(captured.timestamp()).isEqualTo(now);

      subscriber.dispose();
    }
  }
}
