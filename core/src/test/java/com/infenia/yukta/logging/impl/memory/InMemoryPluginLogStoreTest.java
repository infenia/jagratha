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

import com.infenia.yukta.logging.api.LogLevel;
import com.infenia.yukta.logging.api.LogStream;
import com.infenia.yukta.logging.api.PluginLogEntry;
import com.infenia.yukta.logging.api.PluginLogStoreConfig;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class InMemoryPluginLogStoreTest {

  private InMemoryPluginLogStore store;
  private PluginLogStoreConfig config;

  @BeforeEach
  void setUp() {
    config = new PluginLogStoreConfig();
    config.getRetention().setDefaultPeriodMinutes(1); // Short retention for tests
    store = new InMemoryPluginLogStore(config);
  }

  @Test
  void testWriteAndReadLogEntry() {
    final String executionId = "exec-123";
    final PluginLogEntry entry =
        new PluginLogEntry(
            executionId,
            "session-456",
            "processor-1",
            "Data Processor",
            LogStream.STDOUT,
            "Processing started",
            LogLevel.INFO,
            Instant.now());

    store.write(entry).block();

    StepVerifier.create(store.readExecution(executionId))
        .assertNext(
            read -> {
              assertThat(read.executionId()).isEqualTo(executionId);
              assertThat(read.message()).isEqualTo("Processing started");
              assertThat(read.logLevel()).isEqualTo(LogLevel.INFO);
            })
        .verifyComplete();
  }

  @Test
  void testReadMultipleEntries() {
    final String executionId = "exec-789";
    final PluginLogEntry entry1 =
        new PluginLogEntry(
            executionId,
            "session-456",
            "processor-1",
            "Data Processor",
            LogStream.STDOUT,
            "Line 1",
            LogLevel.INFO,
            Instant.now());
    final PluginLogEntry entry2 =
        new PluginLogEntry(
            executionId,
            "session-456",
            "processor-1",
            "Data Processor",
            LogStream.STDOUT,
            "Line 2",
            LogLevel.WARN,
            Instant.now());

    store.write(entry1).then(store.write(entry2)).block();

    StepVerifier.create(store.readExecution(executionId).collectList())
        .assertNext(
            entries -> {
              assertThat(entries).hasSize(2);
              assertThat(entries.get(0).message()).isEqualTo("Line 1");
              assertThat(entries.get(1).message()).isEqualTo("Line 2");
            })
        .verifyComplete();
  }

  @Test
  void testReadNonExistentExecution() {
    StepVerifier.create(store.readExecution("nonexistent").collectList())
        .assertNext(entries -> assertThat(entries).isEmpty())
        .verifyComplete();
  }

  @Test
  void testCleanupRemovesLogs() {
    final String executionId = "exec-cleanup";
    final PluginLogEntry entry =
        new PluginLogEntry(
            executionId,
            "session-456",
            "processor-1",
            "Data Processor",
            LogStream.STDOUT,
            "To be deleted",
            LogLevel.INFO,
            Instant.now());

    StepVerifier.create(
            store
                .write(entry)
                .then(store.cleanup(executionId))
                .thenMany(store.readExecution(executionId).collectList()))
        .assertNext(entries -> assertThat(entries).isEmpty())
        .verifyComplete();
  }

  @Test
  void testEffectiveRetentionIsCapped() {
    final PluginLogStoreConfig configWithHighValue = new PluginLogStoreConfig();
    configWithHighValue.getRetention().setDefaultPeriodMinutes(2000); // Higher than max
    final InMemoryPluginLogStore storeWithHighConfig =
        new InMemoryPluginLogStore(configWithHighValue);

    final Duration effective = storeWithHighConfig.getEffectiveRetention();
    assertThat(effective.toMinutes()).isLessThanOrEqualTo(1440); // Should be capped at max
  }
}
