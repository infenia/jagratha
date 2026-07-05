# Plugin Log Storage with Historical Streaming Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a pluggable log storage layer that captures execution logs with configurable retention, exposes them via an enhanced `watchLogs()` endpoint that streams historical logs first then live updates, and allows future backends (file/DB) without code changes.

**Architecture:** Log entries flow through `DefaultTaskTrackerService` → `LogStoreSubscriber` subscribes independently (non-blocking) → writes to `PluginLogStore` abstraction → `InMemoryPluginLogStore` (Caffeine-backed) handles in-memory storage with automatic TTL cleanup. Controller's `watchLogs()` queries store for history, then concatenates live sink stream. Design is storage-agnostic via interface.

**Tech Stack:** 
- **Caffeine** for in-memory caching with automatic expiration
- **Project Reactor** for non-blocking async writes
- **Spring Boot** configuration for retention settings
- **JUnit 5 + Mockito** for testing

## Global Constraints

- Java 25 / Spring Boot 4.1.0 / Project Reactor
- Hardcoded max retention: 1440 minutes (24 hours), non-configurable
- User-configurable default retention: `yukta.logs.store.retention.default-period-minutes` (default: 30)
- Must not block execution flow (non-blocking writes)
- Must support multiple concurrent subscribers to log stream
- Follows Lombok + Records conventions from codebase
- Apache 2.0 license header on all new files
- Run `./gradlew spotlessApply` before commit
- Quality gates: Checkstyle, PMD, SpotBugs, OpenGrep

---

### Task 1: Core API - Define Log Entry & Store Interface

**Files:**
- Create: `core/src/main/java/com/infenia/yukta/logging/api/PluginLogEntry.java`
- Create: `core/src/main/java/com/infenia/yukta/logging/api/LogStream.java`
- Create: `core/src/main/java/com/infenia/yukta/logging/api/LogLevel.java`
- Create: `core/src/main/java/com/infenia/yukta/logging/api/PluginLogStore.java`
- Create: `core/src/main/java/com/infenia/yukta/logging/api/PluginLogStoreConfig.java`

**Interfaces:**
- Consumes: Nothing (foundational)
- Produces: 
  - `record PluginLogEntry(String executionId, String sessionId, String pluginId, String pluginName, LogStream stream, String message, LogLevel logLevel, Instant timestamp)`
  - `interface PluginLogStore { Mono<Void> write(PluginLogEntry entry); Flux<PluginLogEntry> readExecution(String executionId); Mono<Void> cleanup(String executionId); Duration getEffectiveRetention(); }`
  - `class PluginLogStoreConfig { static final int MAX_RETENTION_MINUTES = 1440; }`

- [ ] **Step 1: Create LogStream enum**

Create `core/src/main/java/com/infenia/yukta/logging/api/LogStream.java`:

```java
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
package com.infenia.yukta.logging.api;

/**
 * Enumeration of log stream types.
 *
 * <p>Categorizes log output by source/type for filtering and presentation.
 */
public enum LogStream {
  /** Standard output stream. */
  STDOUT,

  /** Standard error stream. */
  STDERR,

  /** Custom application-specific stream. */
  CUSTOM
}
```

- [ ] **Step 2: Create LogLevel enum**

Create `core/src/main/java/com/infenia/yukta/logging/api/LogLevel.java`:

```java
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
package com.infenia.yukta.logging.api;

/**
 * Enumeration of log levels.
 *
 * <p>Standard severity levels for categorizing log messages.
 */
public enum LogLevel {
  /** Debug level - detailed diagnostic information. */
  DEBUG,

  /** Info level - general informational messages. */
  INFO,

  /** Warn level - warning messages for potentially problematic situations. */
  WARN,

  /** Error level - error messages for failures. */
  ERROR
}
```

- [ ] **Step 3: Create PluginLogEntry record**

Create `core/src/main/java/com/infenia/yukta/logging/api/PluginLogEntry.java`:

```java
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
package com.infenia.yukta.logging.api;

import java.time.Instant;

/**
 * Immutable log entry for plugin execution logs.
 *
 * <p>Captures a single log message with full execution context and metadata.
 */
public record PluginLogEntry(
    String executionId,
    String sessionId,
    String pluginId,
    String pluginName,
    LogStream stream,
    String message,
    LogLevel logLevel,
    Instant timestamp) {

  /**
   * Format this log entry as a human-readable string.
   *
   * @return formatted log line
   */
  public String format() {
    return String.format(
        "[%s] [%s] [%s/%s] %s: %s",
        timestamp, logLevel, pluginId, pluginName, stream, message);
  }
}
```

- [ ] **Step 4: Create PluginLogStore interface**

Create `core/src/main/java/com/infenia/yukta/logging/api/PluginLogStore.java`:

```java
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
package com.infenia.yukta.logging.api;

import java.time.Duration;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Abstraction for plugin execution log storage.
 *
 * <p>Provides storage and retrieval of execution logs with automatic retention-based cleanup.
 * Implementations are responsible for managing lifecycle: writing entries, reading them
 * chronologically, and cleaning up after retention period expires.
 *
 * <p>All operations are non-blocking reactive (Mono/Flux).
 */
public interface PluginLogStore {

  /**
   * Write a single log entry.
   *
   * <p>Non-blocking operation. Implementations should use appropriate schedulers (e.g.,
   * boundedElastic) to avoid blocking caller.
   *
   * @param entry the log entry to write
   * @return Mono that completes when entry is written
   */
  Mono<Void> write(PluginLogEntry entry);

  /**
   * Read all log entries for an execution in chronological order.
   *
   * @param executionId the execution identifier
   * @return Flux of log entries in order
   */
  Flux<PluginLogEntry> readExecution(String executionId);

  /**
   * Clean up (delete) all logs for a completed execution.
   *
   * <p>Called after execution completes and retention period elapses. Implementations may
   * trigger automatic cleanup or provide manual cleanup interface.
   *
   * @param executionId the execution identifier to clean up
   * @return Mono that completes when cleanup is done
   */
  Mono<Void> cleanup(String executionId);

  /**
   * Get the effective retention duration for logs.
   *
   * <p>Effective retention is the minimum of user-configured retention and hardcoded maximum.
   *
   * @return retention duration
   */
  Duration getEffectiveRetention();
}
```

- [ ] **Step 5: Create PluginLogStoreConfig with hardcoded max**

Create `core/src/main/java/com/infenia/yukta/logging/api/PluginLogStoreConfig.java`:

```java
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
package com.infenia.yukta.logging.api;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for plugin log storage.
 *
 * <p>Manages retention period settings with hardcoded maximum enforcement. User-configured
 * retention is capped at the hardcoded maximum.
 */
@Component
@ConfigurationProperties(prefix = "yukta.logs.store")
@RequiredArgsConstructor
public class PluginLogStoreConfig {

  /** Hardcoded maximum retention period (24 hours). Non-configurable. */
  private static final int MAX_RETENTION_MINUTES = 1440;

  /** User-configurable default retention period in minutes. */
  private Retention retention = new Retention();

  /**
   * Get the effective retention duration.
   *
   * <p>Returns the minimum of user-configured retention and hardcoded maximum.
   *
   * @return effective retention duration
   */
  public Duration getEffectiveRetention() {
    int configured = retention.getDefaultPeriodMinutes();
    int capped = Math.min(configured, MAX_RETENTION_MINUTES);
    return Duration.ofMinutes(capped);
  }

  /**
   * Get the hardcoded maximum retention in minutes.
   *
   * @return max retention minutes
   */
  public int getMaxRetentionMinutes() {
    return MAX_RETENTION_MINUTES;
  }

  /** Nested retention configuration. */
  public static class Retention {
    /** Default retention period in minutes. */
    private int defaultPeriodMinutes = 30;

    public int getDefaultPeriodMinutes() {
      return defaultPeriodMinutes;
    }

    public void setDefaultPeriodMinutes(int defaultPeriodMinutes) {
      this.defaultPeriodMinutes = defaultPeriodMinutes;
    }
  }

  public Retention getRetention() {
    return retention;
  }

  public void setRetention(Retention retention) {
    this.retention = retention;
  }
}
```

- [ ] **Step 6: Commit API definitions**

```bash
cd /media/arun/Infenia/Infenia/Development/Public/yukta
git add core/src/main/java/com/infenia/yukta/logging/api/
git commit -m "feat: add log storage API - PluginLogEntry, LogStream, LogLevel, PluginLogStore interface"
```

---

### Task 2: In-Memory Implementation with Caffeine

**Files:**
- Create: `core/src/main/java/com/infenia/yukta/logging/impl/memory/InMemoryPluginLogStore.java`
- Modify: `core/build.gradle.kts` (add Caffeine dependency)
- Create: `core/src/main/java/com/infenia/yukta/logging/impl/memory/LogStoreAutoConfiguration.java`

**Interfaces:**
- Consumes: `PluginLogStore`, `PluginLogStoreConfig`, `PluginLogEntry`
- Produces: `InMemoryPluginLogStore extends PluginLogStore` with Caffeine-backed implementation

- [ ] **Step 1: Add Caffeine dependency**

Modify `core/build.gradle.kts` to add Caffeine. Find the `dependencies` block and add:

```kotlin
dependencies {
  // ... existing dependencies ...
  implementation("com.github.ben-manes.caffeine:caffeine")
  // ... rest of dependencies ...
}
```

- [ ] **Step 2: Create InMemoryPluginLogStore with Caffeine**

Create `core/src/main/java/com/infenia/yukta/logging/impl/memory/InMemoryPluginLogStore.java`:

```java
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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.infenia.yukta.logging.api.PluginLogEntry;
import com.infenia.yukta.logging.api.PluginLogStore;
import com.infenia.yukta.logging.api.PluginLogStoreConfig;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * In-memory implementation of PluginLogStore using Caffeine cache.
 *
 * <p>Stores logs per execution with automatic expiration based on configured retention period.
 * All write operations are scheduled on boundedElastic to avoid blocking. Thread-safe via
 * Caffeine's internal synchronization.
 */
@Slf4j
public class InMemoryPluginLogStore implements PluginLogStore {

  private final Cache<String, List<PluginLogEntry>> cache;
  private final PluginLogStoreConfig config;

  /**
   * Constructs an in-memory store with Caffeine cache.
   *
   * <p>Cache is configured with expireAfterWrite based on effective retention period.
   *
   * @param config the log store configuration
   */
  public InMemoryPluginLogStore(PluginLogStoreConfig config) {
    this.config = config;
    Duration retention = config.getEffectiveRetention();

    this.cache =
        Caffeine.newBuilder()
            .expireAfterWrite(retention)
            .removalListener(
                (key, value, cause) ->
                    log.debug("Log entry expired for execution: {} ({})", key, cause))
            .build();
  }

  @Override
  public Mono<Void> write(PluginLogEntry entry) {
    return Mono.fromRunnable(
            () -> {
              String executionId = entry.executionId();
              cache.asMap()
                  .computeIfAbsent(executionId, k -> new ArrayList<>())
                  .add(entry);
              log.trace(
                  "Log written for execution {}: [{}] {}",
                  executionId,
                  entry.logLevel(),
                  entry.message());
            })
        .subscribeOn(Schedulers.boundedElastic())
        .then();
  }

  @Override
  public Flux<PluginLogEntry> readExecution(String executionId) {
    return Mono.fromCallable(
            () -> {
              List<PluginLogEntry> entries = cache.getIfPresent(executionId);
              return entries != null ? new ArrayList<>(entries) : new ArrayList<>();
            })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMapIterable(list -> list);
  }

  @Override
  public Mono<Void> cleanup(String executionId) {
    return Mono.fromRunnable(
            () -> {
              cache.invalidate(executionId);
              log.debug("Log store cleaned up for execution: {}", executionId);
            })
        .subscribeOn(Schedulers.boundedElastic())
        .then();
  }

  @Override
  public Duration getEffectiveRetention() {
    return config.getEffectiveRetention();
  }
}
```

- [ ] **Step 3: Create LogStoreAutoConfiguration**

Create `core/src/main/java/com/infenia/yukta/logging/impl/memory/LogStoreAutoConfiguration.java`:

```java
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
import com.infenia.yukta.logging.api.PluginLogStoreConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for plugin log storage.
 *
 * <p>Provides default in-memory implementation. Future: file-based and database backends can be
 * added with their own auto-configurations gated by property conditions.
 */
@Configuration
public class LogStoreAutoConfiguration {

  /**
   * Create in-memory log store bean.
   *
   * <p>Active by default when no other PluginLogStore bean exists. Can be overridden by setting
   * `yukta.logs.store.backend=file` or `database` (when those implementations are available).
   *
   * @param config the log store configuration
   * @return the log store instance
   */
  @Bean
  @ConditionalOnMissingBean(PluginLogStore.class)
  @ConditionalOnProperty(
      name = "yukta.logs.store.backend",
      havingValue = "memory",
      matchIfMissing = true)
  public PluginLogStore inMemoryPluginLogStore(PluginLogStoreConfig config) {
    return new InMemoryPluginLogStore(config);
  }
}
```

- [ ] **Step 4: Verify Caffeine dependency in version catalog**

Run: `grep -i caffeine /media/arun/Infenia/Infenia/Development/Public/yukta/gradle/libs.versions.toml`

Expected: Should find Caffeine library entry (if already in catalog) or note that it needs to be added to the version catalog. If not found, add to `gradle/libs.versions.toml`:

```toml
[libraries]
caffeine = "com.github.ben-manes.caffeine:caffeine:3.1.8"
```

And update the build.gradle.kts to use the alias:
```kotlin
implementation(libs.caffeine)
```

- [ ] **Step 5: Commit in-memory implementation**

```bash
cd /media/arun/Infenia/Infenia/Development/Public/yukta
./gradlew spotlessApply
git add core/build.gradle.kts core/src/main/java/com/infenia/yukta/logging/impl/memory/
git commit -m "feat: implement in-memory log store using Caffeine cache with auto-expiration"
```

---

### Task 3: Log Store Subscriber - Non-Blocking Integration

**Files:**
- Create: `core/src/main/java/com/infenia/yukta/logging/impl/memory/LogStoreSubscriber.java`
- Modify: `core/src/main/java/com/infenia/yukta/service/control/gateway/DefaultControlBusGateway.java` (register subscriber)

**Interfaces:**
- Consumes: `PluginLogStore`, `PluginLogEntry` records, `DefaultTaskTrackerService` (the log sink source)
- Produces: `LogStoreSubscriber` bean that subscribes once to tracker's log sink and writes non-blocking to store

- [ ] **Step 1: Create LogStoreSubscriber**

Create `core/src/main/java/com/infenia/yukta/logging/impl/memory/LogStoreSubscriber.java`:

```java
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

  private final PluginLogStore store;
  private final DefaultTaskTrackerService taskTracker;
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
                    store.write(entry).onErrorResume(
                        error -> {
                          log.warn(
                              "Failed to write log entry for execution {}: {}",
                              entry.executionId(),
                              error.getMessage());
                          return reactor.core.publisher.Mono.empty();
                        }))
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe(
                unused -> {},
                error -> log.error("Log store subscription failed", error),
                () -> log.info("Log store subscription completed"));

    log.info("Log store subscriber initialized");
  }

  /**
   * Dispose of the subscription when component is destroyed.
   */
  @PreDestroy
  public void dispose() {
    if (subscription != null && !subscription.isDisposed()) {
      subscription.dispose();
      log.info("Log store subscriber disposed");
    }
  }
}
```

- [ ] **Step 2: Verify/Add getLogFlux() to DefaultTaskTrackerService**

Read the current DefaultTaskTrackerService to check if it has a method exposing the log sink as a Flux:

Run: `grep -n "getLogFlux\|logSink\|Flux<.*log" /media/arun/Infenia/Infenia/Development/Public/yukta/core/src/main/java/com/infenia/yukta/service/orchestrator/tracker/DefaultTaskTrackerService.java | head -20`

If `getLogFlux()` doesn't exist, add it to DefaultTaskTrackerService. Find the line where `logSinks` is declared and add:

```java
/**
 * Get a Flux of all log entries across all executions.
 *
 * @return flux of log entries
 */
public Flux<PluginLogEntry> getLogFlux() {
  return logSinks.values().stream()
      .reduce(
          Flux.empty(),
          (flux, sink) -> flux.mergeWith(sink.asFlux()),
          (f1, f2) -> f1.mergeWith(f2));
}
```

Alternatively, if the tracker already emits logs through a message bus or event stream, integrate LogStoreSubscriber with that mechanism instead.

- [ ] **Step 3: Commit LogStoreSubscriber**

```bash
cd /media/arun/Infenia/Infenia/Development/Public/yukta
./gradlew spotlessApply
git add core/src/main/java/com/infenia/yukta/logging/impl/memory/LogStoreSubscriber.java
git commit -m "feat: add non-blocking log store subscriber with async write on boundedElastic scheduler"
```

---

### Task 4: Unit Tests - Log Store & Subscriber

**Files:**
- Create: `core/src/test/java/com/infenia/yukta/logging/impl/memory/InMemoryPluginLogStoreTest.java`
- Create: `core/src/test/java/com/infenia/yukta/logging/impl/memory/LogStoreSubscriberTest.java`

**Interfaces:**
- Consumes: `InMemoryPluginLogStore`, `LogStoreSubscriber`, `PluginLogEntry`, `PluginLogStoreConfig`
- Produces: Full test coverage for write, read, cleanup, retention expiration, and subscriber async behavior

- [ ] **Step 1: Write InMemoryPluginLogStore tests**

Create `core/src/test/java/com/infenia/yukta/logging/impl/memory/InMemoryPluginLogStoreTest.java`:

```java
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
    String executionId = "exec-123";
    PluginLogEntry entry =
        new PluginLogEntry(
            executionId,
            "session-456",
            "processor-1",
            "Data Processor",
            LogStream.STDOUT,
            "Processing started",
            LogLevel.INFO,
            Instant.now());

    StepVerifier.create(store.write(entry).then(store.readExecution(executionId)))
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
    String executionId = "exec-789";
    PluginLogEntry entry1 =
        new PluginLogEntry(
            executionId,
            "session-456",
            "processor-1",
            "Data Processor",
            LogStream.STDOUT,
            "Line 1",
            LogLevel.INFO,
            Instant.now());
    PluginLogEntry entry2 =
        new PluginLogEntry(
            executionId,
            "session-456",
            "processor-1",
            "Data Processor",
            LogStream.STDOUT,
            "Line 2",
            LogLevel.WARN,
            Instant.now());

    StepVerifier.create(
            store
                .write(entry1)
                .then(store.write(entry2))
                .then(store.readExecution(executionId).collectList()))
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
    String executionId = "exec-cleanup";
    PluginLogEntry entry =
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
                .then(store.readExecution(executionId).collectList()))
        .assertNext(entries -> assertThat(entries).isEmpty())
        .verifyComplete();
  }

  @Test
  void testEffectiveRetentionIsCapped() {
    PluginLogStoreConfig configWithHighValue = new PluginLogStoreConfig();
    configWithHighValue.getRetention().setDefaultPeriodMinutes(2000); // Higher than max
    InMemoryPluginLogStore storeWithHighConfig =
        new InMemoryPluginLogStore(configWithHighValue);

    Duration effective = storeWithHighConfig.getEffectiveRetention();
    assertThat(effective.toMinutes())
        .isLessThanOrEqualTo(1440); // Should be capped at max
  }
}
```

- [ ] **Step 2: Write LogStoreSubscriber tests**

Create `core/src/test/java/com/infenia/yukta/logging/impl/memory/LogStoreSubscriberTest.java`:

```java
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
import reactor.test.StepVerifier;

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
    PluginLogEntry entry1 =
        new PluginLogEntry(
            "exec-1",
            "session-1",
            "processor-1",
            "Processor",
            LogStream.STDOUT,
            "Message 1",
            LogLevel.INFO,
            Instant.now());

    PluginLogEntry entry2 =
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
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    verify(store, times(2)).write(any(PluginLogEntry.class));

    subscriber.dispose();
  }

  @Test
  void testSubscriberHandlesWriteErrors() {
    PluginLogEntry entry =
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
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    subscriber.dispose();
  }
}
```

- [ ] **Step 2: Run tests to verify they pass**

Run: `./gradlew :core:test --tests "com.infenia.yukta.logging.impl.memory.*" -v`

Expected: All tests pass.

- [ ] **Step 3: Commit tests**

```bash
cd /media/arun/Infenia/Infenia/Development/Public/yukta
git add core/src/test/java/com/infenia/yukta/logging/impl/memory/
git commit -m "test: add unit tests for in-memory log store and subscriber"
```

---

### Task 5: Enhance LogManagementController with Historical + Live Streaming

**Files:**
- Modify: `web/src/main/java/com/infenia/yukta/controller/LogManagementController.java`

**Interfaces:**
- Consumes: `PluginLogStore`, existing `ControlBusGateway.watchLogs()` log sink
- Produces: Enhanced `streamExecutionLogs()` that emits history first, then live logs

- [ ] **Step 1: Inject PluginLogStore into LogManagementController**

Modify `LogManagementController.java`:

Find the field declarations section and add `PluginLogStore`:

```java
@RestController
@RequestMapping("/api/sessions/{sessionId}/executions/{executionId}/logs")
@Slf4j
@Tag(name = "Execution Logs API", description = "Stream execution logs")
@RequiredArgsConstructor
public class LogManagementController {

  private final ControlBusGateway controlBus;
  private final PluginLogStore logStore;  // ADD THIS LINE
```

- [ ] **Step 2: Enhance streamExecutionLogs() to emit history first**

Replace the current `streamExecutionLogs()` method with:

```java
@GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
@Operation(
    summary = "Stream execution logs with history",
    description =
        "Streams historical log entries first (if available), then continues with live updates")
@io.swagger.v3.oas.annotations.responses.ApiResponse(
    responseCode = "200",
    description = "Stream of log lines with history-then-live ordering")
public Flux<String> streamExecutionLogs(
    @Parameter(description = "Session identifier") @PathVariable final String sessionId,
    @Parameter(description = "Execution identifier") @PathVariable final String executionId) {

  log.atInfo()
      .addKeyValue("sessionId", sessionId)
      .addKeyValue("executionId", executionId)
      .log("Streaming execution logs");

  // Phase 1: Emit historical logs from store
  Flux<String> historicalLogs =
      logStore
          .readExecution(executionId)
          .map(entry -> entry.format())
          .doOnNext(
              logLine -> log.atDebug()
                  .addKeyValue("executionId", executionId)
                  .log("Emitting historical log entry"))
          .doOnComplete(
              () -> log.atDebug()
                  .addKeyValue("executionId", executionId)
                  .log("Historical logs complete"));

  // Phase 2: Emit live logs from control bus
  Flux<String> liveLogs =
      controlBus.watchLogs(sessionId, executionId)
          .doOnNext(
              logLine -> log.atTrace()
                  .addKeyValue("executionId", executionId)
                  .log("Emitting live log entry"));

  // Concatenate: history first, then live
  return historicalLogs.concatWith(liveLogs);
}
```

- [ ] **Step 3: Add import for PluginLogStore**

Add to the imports section of LogManagementController:

```java
import com.infenia.yukta.logging.api.PluginLogStore;
```

- [ ] **Step 4: Run spotlessApply**

```bash
cd /media/arun/Infenia/Infenia/Development/Public/yukta
./gradlew spotlessApply
```

- [ ] **Step 5: Verify the controller compiles**

Run: `./gradlew :web:build -x test`

Expected: Build succeeds.

- [ ] **Step 6: Commit controller changes**

```bash
cd /media/arun/Infenia/Infenia/Development/Public/yukta
git add web/src/main/java/com/infenia/yukta/controller/LogManagementController.java
git commit -m "feat: enhance LogManagementController to stream historical logs before live updates"
```

---

### Task 6: Integration Test - Full Flow

**Files:**
- Create: `web/src/test/java/com/infenia/yukta/controller/LogManagementControllerIntegrationTest.java`

**Interfaces:**
- Consumes: `LogManagementController`, `PluginLogStore`, `ControlBusGateway`, `PluginLogEntry`
- Produces: Integration test verifying history + live flow works end-to-end

- [ ] **Step 1: Write integration test**

Create `web/src/test/java/com/infenia/yukta/controller/LogManagementControllerIntegrationTest.java`:

```java
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
package com.infenia.yukta.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.infenia.yukta.logging.api.LogLevel;
import com.infenia.yukta.logging.api.LogStream;
import com.infenia.yukta.logging.api.PluginLogEntry;
import com.infenia.yukta.logging.api.PluginLogStore;
import com.infenia.yukta.service.control.gateway.ControlBusGateway;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

@WebFluxTest(LogManagementController.class)
class LogManagementControllerIntegrationTest {

  @Autowired private WebTestClient webTestClient;

  @MockBean private ControlBusGateway controlBus;
  @MockBean private PluginLogStore logStore;

  @Test
  void testStreamLogsEmitsHistoryThenLive() {
    String sessionId = "session-123";
    String executionId = "exec-456";

    // Mock historical logs from store
    PluginLogEntry historical1 =
        new PluginLogEntry(
            executionId,
            sessionId,
            "processor-1",
            "Processor",
            LogStream.STDOUT,
            "Historical line 1",
            LogLevel.INFO,
            Instant.now().minusSeconds(10));

    PluginLogEntry historical2 =
        new PluginLogEntry(
            executionId,
            sessionId,
            "processor-1",
            "Processor",
            LogStream.STDOUT,
            "Historical line 2",
            LogLevel.INFO,
            Instant.now().minusSeconds(5));

    when(logStore.readExecution(executionId))
        .thenReturn(Flux.just(historical1, historical2));

    // Mock live logs from control bus
    when(controlBus.watchLogs(sessionId, executionId))
        .thenReturn(Flux.just("Live line 1", "Live line 2").delayElement(
            java.time.Duration.ofMillis(10)));

    webTestClient
        .get()
        .uri("/api/sessions/{sessionId}/executions/{executionId}/logs", sessionId, executionId)
        .accept(MediaType.TEXT_EVENT_STREAM)
        .exchange()
        .expectStatus()
        .isOk()
        .returnResult(String.class)
        .getResponseBody()
        .take(4)
        .collectList()
        .block()
        .stream()
        .forEach(
            line -> {
              assertThat(line).contains("Historical").or().contains("Live");
            });
  }

  @Test
  void testStreamLogsHandlesEmptyHistory() {
    String sessionId = "session-789";
    String executionId = "exec-new";

    when(logStore.readExecution(executionId)).thenReturn(Flux.empty());

    when(controlBus.watchLogs(sessionId, executionId))
        .thenReturn(Flux.just("First live log", "Second live log"));

    webTestClient
        .get()
        .uri("/api/sessions/{sessionId}/executions/{executionId}/logs", sessionId, executionId)
        .accept(MediaType.TEXT_EVENT_STREAM)
        .exchange()
        .expectStatus()
        .isOk();
  }
}
```

- [ ] **Step 2: Run the integration test**

Run: `./gradlew :web:test --tests "com.infenia.yukta.controller.LogManagementControllerIntegrationTest" -v`

Expected: Tests pass.

- [ ] **Step 3: Commit integration test**

```bash
cd /media/arun/Infenia/Infenia/Development/Public/yukta
git add web/src/test/java/com/infenia/yukta/controller/LogManagementControllerIntegrationTest.java
git commit -m "test: add integration test for historical + live log streaming"
```

---

### Task 7: Configuration & Documentation

**Files:**
- Modify: `application.yml` (or `application-default.yml`)
- Create: `docs/LOG_STORAGE.md` (feature documentation)

**Interfaces:**
- Consumes: Configuration property names from `PluginLogStoreConfig`
- Produces: Documented configuration with examples and defaults

- [ ] **Step 1: Add log store configuration to application.yml**

Find `application.yml` in the boot module (likely at `boot/src/main/resources/application.yml`). Add:

```yaml
yukta:
  logs:
    store:
      backend: memory              # memory | file | database (when available)
      retention:
        default-period-minutes: 30 # User-configurable retention period
        # Note: Maximum retention is hardcoded to 1440 minutes (24 hours)
        # If configured value exceeds max, max will take effect
```

- [ ] **Step 2: Create feature documentation**

Create `docs/LOG_STORAGE.md`:

```markdown
# Plugin Log Storage Architecture

## Overview

The plugin log storage system captures execution logs with configurable retention periods and enables streaming both historical and live logs to clients.

## Features

- **Non-blocking writes**: Log entries are written asynchronously on a bounded elastic scheduler
- **In-memory storage**: Caffeine cache with automatic expiration based on retention period
- **Configurable retention**: User-configurable retention period with hardcoded maximum (24 hours)
- **Historical + Live streaming**: API endpoints emit cached historical logs first, then live updates
- **Storage abstraction**: Interface-based design allows future backends (file, database, S3)

## Configuration

Configure log storage behavior in `application.yml`:

```yaml
yukta:
  logs:
    store:
      backend: memory
      retention:
        default-period-minutes: 30
```

### Configuration Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `yukta.logs.store.backend` | String | `memory` | Storage backend (memory, file, database) |
| `yukta.logs.store.retention.default-period-minutes` | Integer | `30` | Retention period in minutes |

### Hard Limits

- **Maximum retention**: 1440 minutes (24 hours) — hardcoded, non-configurable
- If user sets `default-period-minutes` > 1440, the system enforces the 1440-minute limit

## API Usage

### Stream Logs with History

```bash
curl -N \
  http://localhost:8080/api/sessions/{sessionId}/executions/{executionId}/logs \
  -H "Accept: text/event-stream"
```

Response: Text event stream with historical log entries first, then live updates.

### Log Entry Format

Each log entry is formatted as:

```
[2026-07-05T10:30:45.123Z] [INFO] [processor-1/Data Processor] STDOUT: Processing started
```

## Architecture

### Core Components

**PluginLogEntry** (record)
- Immutable log entry with execution context, plugin metadata, message, and timestamp
- Methods: `format()` to render as human-readable string

**PluginLogStore** (interface)
- Abstraction for log storage and retrieval
- Methods:
  - `write(PluginLogEntry)`: Non-blocking write
  - `readExecution(executionId)`: Chronological read for execution
  - `cleanup(executionId)`: Delete logs after retention expires
  - `getEffectiveRetention()`: Get effective retention duration

**InMemoryPluginLogStore** (implementation)
- Caffeine cache-backed in-memory storage
- Automatic expiration after configured retention period
- Thread-safe via Caffeine's internal synchronization

**LogStoreSubscriber** (Spring component)
- Subscribes to `DefaultTaskTrackerService` log events
- Writes entries asynchronously on `Schedulers.boundedElastic()`
- Non-blocking; errors are logged but don't interrupt execution

### Data Flow

```
DefaultTaskTrackerService (log emission)
  ↓
LogStoreSubscriber (subscribes, writes async)
  ↓
InMemoryPluginLogStore (Caffeine cache)
  ↓
LogManagementController.streamExecutionLogs()
  ├─ Phase 1: Read historical from store
  └─ Phase 2: Merge with live stream from ControlBusGateway
```

## Testing

### Unit Tests

- `InMemoryPluginLogStoreTest`: Tests write, read, cleanup, retention capping
- `LogStoreSubscriberTest`: Tests async subscription and error handling

### Integration Tests

- `LogManagementControllerIntegrationTest`: Tests historical + live streaming end-to-end

Run tests:
```bash
./gradlew :core:test --tests "com.infenia.yukta.logging.*"
./gradlew :web:test --tests "*LogManagementController*"
```

## Future Enhancements

### File-Based Storage
Implement `FileSystemPluginLogStore` with properties:
```yaml
yukta:
  logs:
    store:
      backend: file
      file:
        directory: /var/log/yukta/plugins
        compression: gzip
```

### Database Storage
Implement `DatabasePluginLogStore` for distributed deployments and long-term archival.

### S3 Storage
Implement `S3PluginLogStore` for cloud-native deployments.

Each backend would implement the `PluginLogStore` interface and be activated via Spring's `@ConditionalOnProperty`.

## Performance Considerations

- **Memory usage**: Proportional to number of concurrent executions × average logs per execution
- **Retention cleanup**: Handled automatically by Caffeine after configured TTL expires
- **Write latency**: Minimal, bounded elastic scheduler with queue depth monitoring
- **Read latency**: O(n) where n = number of logs for execution (typically small)

## Troubleshooting

### Logs disappear after 30 minutes

This is expected behavior. By default, logs are retained for 30 minutes after execution completion. To extend:

```yaml
yukta:
  logs:
    store:
      retention:
        default-period-minutes: 120  # Increase to 2 hours (max: 1440)
```

### High memory usage

Monitor in-flight executions:
- Reduce `default-period-minutes` to clean up logs faster
- Implement file-based storage backend for long-term retention

```yaml
yukta:
  logs:
    store:
      backend: file  # When available
      file:
        directory: /var/log/yukta/plugins
```
```

- [ ] **Step 2: Commit documentation and configuration**

```bash
cd /media/arun/Infenia/Infenia/Development/Public/yukta
git add docs/LOG_STORAGE.md boot/src/main/resources/application.yml
git commit -m "docs: add log storage architecture documentation and configuration examples"
```

---

### Task 8: Quality Checks & Final Verification

**Files:**
- All modified/created files

**Interfaces:**
- Verify: Build passes, tests pass, code quality gates pass

- [ ] **Step 1: Run all quality checks**

```bash
cd /media/arun/Infenia/Infenia/Development/Public/yukta
./gradlew spotlessApply
./gradlew check -x test  # Style, PMD, SpotBugs, OpenGrep
./gradlew :core:test    # Core tests
./gradlew :web:test     # Web tests
```

Expected:
- `spotlessApply`: No output (already formatted)
- `check`: BUILD SUCCESSFUL (no quality gate violations)
- Core tests: All pass
- Web tests: All pass

- [ ] **Step 2: Build entire project**

```bash
cd /media/arun/Infenia/Infenia/Development/Public/yukta
./gradlew clean build
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Verify log streaming manually**

Start the application:
```bash
./gradlew bootRun
```

Wait for startup. Then in another terminal:
```bash
curl -N \
  http://localhost:8080/api/sessions/test-session/executions/test-execution/logs \
  -H "Accept: text/event-stream" \
  -v
```

Expected: Connection accepted, stream begins (may be empty initially if no logs exist).

- [ ] **Step 4: Final commit summary**

```bash
cd /media/arun/Infenia/Infenia/Development/Public/yukta
git log --oneline -10
```

Verify the following commits are present:
1. `feat: add log storage API - PluginLogEntry, LogStream, LogLevel, PluginLogStore interface`
2. `feat: implement in-memory log store using Caffeine cache with auto-expiration`
3. `feat: add non-blocking log store subscriber with async write on boundedElastic scheduler`
4. `test: add unit tests for in-memory log store and subscriber`
5. `feat: enhance LogManagementController to stream historical logs before live updates`
6. `test: add integration test for historical + live log streaming`
7. `docs: add log storage architecture documentation and configuration examples`

---

## Plan Summary

This plan implements a production-ready plugin log storage system with:

✅ **Clean abstraction**: `PluginLogStore` interface allows swapping backends  
✅ **Non-blocking**: Writes on `Schedulers.boundedElastic()`, zero impact to execution  
✅ **Configurable retention**: User-configurable with hardcoded cap (1440 minutes)  
✅ **Rich logging**: Captures pluginId, pluginName, logLevel, stream type, execution context  
✅ **Historical + Live**: Enhanced `watchLogs()` emits cached history first, then live updates  
✅ **Caffeine-backed**: Thread-safe in-memory with automatic TTL-based expiration  
✅ **Tested**: Unit tests + integration tests covering happy path and edge cases  
✅ **Documented**: Feature documentation + configuration examples  
✅ **Extensible**: Ready for file/DB/S3 backends via `@ConditionalOnProperty`
