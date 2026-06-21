# Workflow Definition Store Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Introduce `WorkflowDefinitionStore` as the single source of truth for workflow definitions (keyed by `sessionId + workflowId`), add a TTL-evicting `PreparedWorkflowCache` to avoid recompilation on every run, and remove the redundant workflow storage from `SessionConfigStore`.

**Architecture:** A new `service/workflow/store/` package holds a reactive `WorkflowDefinitionStore` interface, its `InMemoryWorkflowDefinitionStore` component, and a synchronous `PreparedWorkflowCache` with TTL eviction. `ControlBusService.prepareWorkflow()` becomes the orchestration point: save → invalidate cache → compile → warm cache. `WorkflowService.runWorkflow()` does a cache-first lookup before falling back to the store and recompiling. `SessionConfigStore` loses its three workflow methods; `applySessionConfig()` in both impls delegates to `WorkflowDefinitionStore`.

**Tech Stack:** Java 25, Spring Boot 4 WebFlux, Project Reactor (`Mono`/`Flux`), JUnit 5, Mockito, StepVerifier, AssertJ, Lombok (`@Slf4j`, `@RequiredArgsConstructor`, `@Component`), `ScheduledExecutorService` (daemon, same pattern as `InMemoryAggregateStore`).

---

## File Map

**Created:**
- `core/src/main/java/com/infenia/yukta/service/workflow/store/WorkflowDefinitionStore.java` — reactive interface
- `core/src/main/java/com/infenia/yukta/service/workflow/store/InMemoryWorkflowDefinitionStore.java` — `@Component` impl
- `core/src/main/java/com/infenia/yukta/service/workflow/store/PreparedWorkflowCache.java` — `@Component`, TTL eviction
- `core/src/test/java/com/infenia/yukta/service/workflow/store/InMemoryWorkflowDefinitionStoreTest.java`
- `core/src/test/java/com/infenia/yukta/service/workflow/store/PreparedWorkflowCacheTest.java`

**Modified:**
- `core/src/main/java/com/infenia/yukta/service/control/command/PrepareWorkflowCommand.java` — add `sessionId`
- `core/src/main/java/com/infenia/yukta/service/control/gateway/ControlBusGateway.java` — add `sessionId` param
- `core/src/main/java/com/infenia/yukta/service/control/gateway/DefaultControlBusGateway.java` — add `sessionId` param
- `core/src/main/java/com/infenia/yukta/service/control/ControlBusService.java` — orchestrate save → invalidate → compile → cache
- `core/src/main/java/com/infenia/yukta/service/WorkflowService.java` — cache-first lookup, remove `configService` workflow calls
- `core/src/main/java/com/infenia/yukta/service/session/SessionConfigStore.java` — remove 3 workflow methods
- `core/src/main/java/com/infenia/yukta/service/session/InMemorySessionConfigStore.java` — remove `workflowsMap`, delegate to store
- `core/src/main/java/com/infenia/yukta/service/session/FileSessionConfigStore.java` — remove workflow methods, delegate in `applySessionConfig`
- `core/src/main/java/com/infenia/yukta/service/DefaultWorkflowGateway.java` — use `WorkflowDefinitionStore` for session workflow copy
- `core/src/main/java/com/infenia/yukta/service/session/SessionService.java` — switch `getSessionWorkflow` to `WorkflowDefinitionStore`
- `core/src/test/java/com/infenia/yukta/service/session/InMemorySessionConfigStoreTest.java` — remove workflow assertions, add delegation test
- `core/src/test/java/com/infenia/yukta/service/session/FileSessionConfigStoreTest.java` — remove workflow assertions, add delegation test
- `core/src/test/java/com/infenia/yukta/service/WorkflowServiceTest.java` — add cache-hit/miss tests

---

## Task 1: `WorkflowDefinitionStore` interface + `InMemoryWorkflowDefinitionStore`

**Files:**
- Create: `core/src/main/java/com/infenia/yukta/service/workflow/store/WorkflowDefinitionStore.java`
- Create: `core/src/main/java/com/infenia/yukta/service/workflow/store/InMemoryWorkflowDefinitionStore.java`
- Create: `core/src/test/java/com/infenia/yukta/service/workflow/store/InMemoryWorkflowDefinitionStoreTest.java`

- [ ] **Step 1: Write the failing tests**

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
package com.infenia.yukta.service.workflow.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.infenia.yukta.model.workflow.WorkflowDefinition;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class InMemoryWorkflowDefinitionStoreTest {

  private InMemoryWorkflowDefinitionStore store;

  private static WorkflowDefinition definition(final String workflowId) {
    return new WorkflowDefinition(
        workflowId,
        "desc",
        List.of(new WorkflowDefinition.Node("n1", "trigger", Map.of())),
        List.of());
  }

  @BeforeEach
  void setUp() {
    store = new InMemoryWorkflowDefinitionStore();
  }

  @Test
  void saveAndFindReturnsDefinition() {
    final WorkflowDefinition def = definition("wf1");
    StepVerifier.create(store.save("s1", def).then(store.find("s1", "wf1")))
        .expectNext(def)
        .verifyComplete();
  }

  @Test
  void findOnUnknownKeyReturnsEmpty() {
    StepVerifier.create(store.find("unknown", "wf1")).verifyComplete();
  }

  @Test
  void findAllReturnsAllDefinitionsForSession() {
    final WorkflowDefinition wf1 = definition("wf1");
    final WorkflowDefinition wf2 = definition("wf2");
    StepVerifier.create(
            store
                .save("s1", wf1)
                .then(store.save("s1", wf2))
                .then(store.findAll("s1")))
        .assertNext(map -> assertThat(map).containsKeys("wf1", "wf2"))
        .verifyComplete();
  }

  @Test
  void removeDeletesOnlyTargetWorkflow() {
    final WorkflowDefinition wf1 = definition("wf1");
    final WorkflowDefinition wf2 = definition("wf2");
    StepVerifier.create(
            store
                .save("s1", wf1)
                .then(store.save("s1", wf2))
                .then(store.remove("s1", "wf1"))
                .then(store.find("s1", "wf1")))
        .verifyComplete();
    StepVerifier.create(store.find("s1", "wf2"))
        .expectNext(wf2)
        .verifyComplete();
  }

  @Test
  void removeAllClearsSessionLeavesOthersIntact() {
    final WorkflowDefinition wf1 = definition("wf1");
    final WorkflowDefinition wf2 = definition("wf2");
    StepVerifier.create(
            store
                .save("s1", wf1)
                .then(store.save("s2", wf2))
                .then(store.removeAll("s1"))
                .then(store.find("s1", "wf1")))
        .verifyComplete();
    StepVerifier.create(store.find("s2", "wf2"))
        .expectNext(wf2)
        .verifyComplete();
  }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
./gradlew :core:test --tests com.infenia.yukta.service.workflow.store.InMemoryWorkflowDefinitionStoreTest
```

Expected: compilation failure — `InMemoryWorkflowDefinitionStore` does not exist yet.

- [ ] **Step 3: Create the interface**

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
package com.infenia.yukta.service.workflow.store;

import com.infenia.yukta.model.workflow.WorkflowDefinition;
import java.util.Map;
import reactor.core.publisher.Mono;

/** Persistent store for workflow definitions, keyed by sessionId and workflowId. */
public interface WorkflowDefinitionStore {

  /**
   * Save or replace a workflow definition for a session.
   *
   * @param sessionId the session identifier
   * @param definition the workflow definition (workflowId is taken from definition.workflowId())
   * @return Mono that completes when saved
   */
  Mono<Void> save(String sessionId, WorkflowDefinition definition);

  /**
   * Find a workflow definition.
   *
   * @param sessionId the session identifier
   * @param workflowId the workflow identifier
   * @return Mono containing the definition, or empty if not found
   */
  Mono<WorkflowDefinition> find(String sessionId, String workflowId);

  /**
   * Find all workflow definitions for a session.
   *
   * @param sessionId the session identifier
   * @return Mono containing map of workflowId to definition (empty map if none)
   */
  Mono<Map<String, WorkflowDefinition>> findAll(String sessionId);

  /**
   * Remove a single workflow definition.
   *
   * @param sessionId the session identifier
   * @param workflowId the workflow identifier
   * @return Mono that completes when removed
   */
  Mono<Void> remove(String sessionId, String workflowId);

  /**
   * Remove all workflow definitions for a session.
   *
   * @param sessionId the session identifier
   * @return Mono that completes when all removed
   */
  Mono<Void> removeAll(String sessionId);
}
```

- [ ] **Step 4: Create the in-memory implementation**

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
package com.infenia.yukta.service.workflow.store;

import com.infenia.yukta.model.workflow.WorkflowDefinition;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** In-memory implementation of {@link WorkflowDefinitionStore}. Thread-safe via ConcurrentHashMap. */
@Component
@SuppressWarnings("PMD.AtLeastOneConstructor")
public class InMemoryWorkflowDefinitionStore implements WorkflowDefinitionStore {

  private final Map<String, Map<String, WorkflowDefinition>> store = new ConcurrentHashMap<>();

  @Override
  public Mono<Void> save(final String sessionId, final WorkflowDefinition definition) {
    return Mono.fromRunnable(
        () ->
            store
                .computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>())
                .put(definition.workflowId(), definition));
  }

  @Override
  public Mono<WorkflowDefinition> find(final String sessionId, final String workflowId) {
    final Map<String, WorkflowDefinition> session = store.get(sessionId);
    if (session == null) {
      return Mono.empty();
    }
    final WorkflowDefinition def = session.get(workflowId);
    return def != null ? Mono.just(def) : Mono.empty();
  }

  @Override
  public Mono<Map<String, WorkflowDefinition>> findAll(final String sessionId) {
    final Map<String, WorkflowDefinition> session = store.get(sessionId);
    return Mono.just(session != null ? Map.copyOf(session) : Map.of());
  }

  @Override
  public Mono<Void> remove(final String sessionId, final String workflowId) {
    return Mono.fromRunnable(
        () -> {
          final Map<String, WorkflowDefinition> session = store.get(sessionId);
          if (session != null) {
            session.remove(workflowId);
          }
        });
  }

  @Override
  public Mono<Void> removeAll(final String sessionId) {
    return Mono.fromRunnable(() -> store.remove(sessionId));
  }
}
```

- [ ] **Step 5: Run tests to confirm they pass**

```bash
./gradlew :core:test --tests com.infenia.yukta.service.workflow.store.InMemoryWorkflowDefinitionStoreTest
```

Expected: 5 tests PASS.

- [ ] **Step 6: Run quality checks**

```bash
./gradlew :core:check
```

Expected: BUILD SUCCESSFUL. Fix any Checkstyle/PMD/SpotBugs issues before continuing.

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/com/infenia/yukta/service/workflow/store/WorkflowDefinitionStore.java \
        core/src/main/java/com/infenia/yukta/service/workflow/store/InMemoryWorkflowDefinitionStore.java \
        core/src/test/java/com/infenia/yukta/service/workflow/store/InMemoryWorkflowDefinitionStoreTest.java
git commit -m "feat: add WorkflowDefinitionStore interface and in-memory implementation"
```

---

## Task 2: `PreparedWorkflowCache` with TTL eviction

**Files:**
- Create: `core/src/main/java/com/infenia/yukta/service/workflow/store/PreparedWorkflowCache.java`
- Create: `core/src/test/java/com/infenia/yukta/service/workflow/store/PreparedWorkflowCacheTest.java`

- [ ] **Step 1: Write the failing tests**

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
package com.infenia.yukta.service.workflow.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.infenia.yukta.model.workflow.PreparedWorkflow;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PreparedWorkflowCacheTest {

  private PreparedWorkflowCache cache;

  private static PreparedWorkflow mockPrepared() {
    return mock(PreparedWorkflow.class);
  }

  @BeforeEach
  void setUp() {
    // TTL of 200ms so we can test eviction without long sleeps
    cache = new PreparedWorkflowCache(200L);
  }

  @Test
  void putAndGetReturnsPreparedWorkflow() {
    final PreparedWorkflow prepared = mockPrepared();
    cache.put("s1", "wf1", prepared);
    assertThat(cache.get("s1", "wf1")).contains(prepared);
  }

  @Test
  void getOnUnknownKeyReturnsEmpty() {
    assertThat(cache.get("unknown", "wf1")).isEmpty();
  }

  @Test
  void invalidateRemovesEntry() {
    final PreparedWorkflow prepared = mockPrepared();
    cache.put("s1", "wf1", prepared);
    cache.invalidate("s1", "wf1");
    assertThat(cache.get("s1", "wf1")).isEmpty();
  }

  @Test
  void invalidateAllRemovesAllEntriesForSession() {
    cache.put("s1", "wf1", mockPrepared());
    cache.put("s1", "wf2", mockPrepared());
    cache.put("s2", "wf1", mockPrepared());
    cache.invalidateAll("s1");
    assertThat(cache.get("s1", "wf1")).isEmpty();
    assertThat(cache.get("s1", "wf2")).isEmpty();
    assertThat(cache.get("s2", "wf1")).isPresent();
  }

  @Test
  void entryExpiredAfterTtl() throws InterruptedException {
    final PreparedWorkflow prepared = mockPrepared();
    cache.put("s1", "wf1", prepared);
    Thread.sleep(300L); // past the 200ms TTL
    cache.evictExpired(); // trigger eviction manually
    assertThat(cache.get("s1", "wf1")).isEmpty();
  }

  @Test
  void accessResetsLastAccessTimeAndSurvivesTtl() throws InterruptedException {
    final PreparedWorkflow prepared = mockPrepared();
    cache.put("s1", "wf1", prepared);
    Thread.sleep(100L);
    cache.get("s1", "wf1"); // resets lastAccessTime
    Thread.sleep(150L);     // 250ms since put but only 150ms since last access
    cache.evictExpired();
    assertThat(cache.get("s1", "wf1")).contains(prepared);
  }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
./gradlew :core:test --tests com.infenia.yukta.service.workflow.store.PreparedWorkflowCacheTest
```

Expected: compilation failure — `PreparedWorkflowCache` does not exist yet.

- [ ] **Step 3: Create `PreparedWorkflowCache`**

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
package com.infenia.yukta.service.workflow.store;

import com.infenia.yukta.model.workflow.PreparedWorkflow;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * In-memory cache of compiled {@link PreparedWorkflow} instances, keyed by sessionId + workflowId.
 *
 * <p>Entries expire after {@code workflow.cache.ttl.ms} milliseconds of inactivity. A background
 * thread evicts expired entries every 60 seconds. Access resets the TTL.
 */
@Slf4j
@Component
@SuppressWarnings("PMD.DoNotUseThreads")
public class PreparedWorkflowCache {

  private static final String COMPOSITE_KEY_SEPARATOR = "\0";
  private static final long EVICTION_INTERVAL_MS = 60_000L;

  private final long ttlMs;
  private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
  private final ScheduledExecutorService scheduler;

  /**
   * Spring-managed constructor.
   *
   * @param ttlMs TTL in milliseconds from application properties
   */
  public PreparedWorkflowCache(
      @Value("${workflow.cache.ttl.ms:600000}") final long ttlMs) {
    this.ttlMs = ttlMs;
    this.scheduler =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              final Thread thread = new Thread(r, "prepared-workflow-cache-eviction");
              thread.setDaemon(true);
              return thread;
            });
  }

  /** Start the background eviction task. */
  @PostConstruct
  public void init() {
    scheduler.scheduleAtFixedRate(
        this::evictExpired,
        EVICTION_INTERVAL_MS,
        EVICTION_INTERVAL_MS,
        TimeUnit.MILLISECONDS);
  }

  /** Shut down the eviction scheduler. */
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

  /**
   * Store a compiled workflow in the cache.
   *
   * @param sessionId the session identifier
   * @param workflowId the workflow identifier
   * @param prepared the compiled workflow
   */
  public void put(final String sessionId, final String workflowId, final PreparedWorkflow prepared) {
    cache.put(key(sessionId, workflowId), new CacheEntry(prepared));
  }

  /**
   * Retrieve a compiled workflow, resetting its TTL.
   *
   * @param sessionId the session identifier
   * @param workflowId the workflow identifier
   * @return the cached workflow, or empty if absent or evicted
   */
  public Optional<PreparedWorkflow> get(final String sessionId, final String workflowId) {
    final CacheEntry entry = cache.get(key(sessionId, workflowId));
    if (entry == null) {
      return Optional.empty();
    }
    entry.touch();
    return Optional.of(entry.prepared);
  }

  /**
   * Remove a single entry from the cache.
   *
   * @param sessionId the session identifier
   * @param workflowId the workflow identifier
   */
  public void invalidate(final String sessionId, final String workflowId) {
    cache.remove(key(sessionId, workflowId));
  }

  /**
   * Remove all entries for a session from the cache.
   *
   * @param sessionId the session identifier
   */
  public void invalidateAll(final String sessionId) {
    final String prefix = sessionId + COMPOSITE_KEY_SEPARATOR;
    cache.keySet().removeIf(k -> k.startsWith(prefix));
  }

  /** Evict all entries whose lastAccessTime is older than the configured TTL. */
  public void evictExpired() {
    final long now = System.currentTimeMillis();
    final Iterator<Map.Entry<String, CacheEntry>> it = cache.entrySet().iterator();
    while (it.hasNext()) {
      final Map.Entry<String, CacheEntry> entry = it.next();
      if (now - entry.getValue().lastAccessTime > ttlMs) {
        it.remove();
        log.atDebug().addKeyValue("key", entry.getKey()).log("Evicted expired PreparedWorkflow");
      }
    }
  }

  private static String key(final String sessionId, final String workflowId) {
    return sessionId + COMPOSITE_KEY_SEPARATOR + workflowId;
  }

  private static final class CacheEntry {
    private final PreparedWorkflow prepared;
    private volatile long lastAccessTime;

    private CacheEntry(final PreparedWorkflow prepared) {
      this.prepared = prepared;
      this.lastAccessTime = System.currentTimeMillis();
    }

    private void touch() {
      this.lastAccessTime = System.currentTimeMillis();
    }
  }
}
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
./gradlew :core:test --tests com.infenia.yukta.service.workflow.store.PreparedWorkflowCacheTest
```

Expected: 5 tests PASS.

- [ ] **Step 5: Run quality checks**

```bash
./gradlew :core:check
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/infenia/yukta/service/workflow/store/PreparedWorkflowCache.java \
        core/src/test/java/com/infenia/yukta/service/workflow/store/PreparedWorkflowCacheTest.java
git commit -m "feat: add PreparedWorkflowCache with TTL eviction"
```

---

## Task 3: Update `PrepareWorkflowCommand` and `ControlBusGateway` signatures

**Files:**
- Modify: `core/src/main/java/com/infenia/yukta/service/control/command/PrepareWorkflowCommand.java`
- Modify: `core/src/main/java/com/infenia/yukta/service/control/gateway/ControlBusGateway.java`
- Modify: `core/src/main/java/com/infenia/yukta/service/control/gateway/DefaultControlBusGateway.java`

- [ ] **Step 1: Update `PrepareWorkflowCommand`**

Replace the entire file content:

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
package com.infenia.yukta.service.control.command;

import com.infenia.yukta.model.workflow.WorkflowDefinition;

/**
 * Command to prepare a workflow for execution.
 *
 * @param sessionId the session that owns this workflow
 * @param workflowDefinition the workflow definition to prepare
 */
public record PrepareWorkflowCommand(String sessionId, WorkflowDefinition workflowDefinition) {}
```

- [ ] **Step 2: Update `ControlBusGateway` interface**

In `ControlBusGateway.java`, replace the `prepareWorkflow` method signature (around line 89):

```java
  /**
   * Prepare a workflow for execution.
   *
   * @param sessionId the session that owns this workflow
   * @param workflowDefinition the workflow definition to prepare
   * @return a Mono that completes when the workflow is prepared
   */
  Mono<Void> prepareWorkflow(String sessionId, WorkflowDefinition workflowDefinition);
```

- [ ] **Step 3: Update `DefaultControlBusGateway.prepareWorkflow()`**

Replace lines 138–141 in `DefaultControlBusGateway.java`:

```java
  @Override
  public Mono<Void> prepareWorkflow(
      final String sessionId, final WorkflowDefinition workflowDefinition) {
    return controlBusService.prepareWorkflow(
        new PrepareWorkflowCommand(sessionId, workflowDefinition));
  }
```

- [ ] **Step 4: Verify compilation**

```bash
./gradlew :core:compileJava
```

Expected: compilation errors in callers of `prepareWorkflow` (they will be fixed in Task 4 and 5). The three files edited here should compile cleanly in isolation; errors from other files referencing old signature are expected and will be resolved in subsequent tasks.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/infenia/yukta/service/control/command/PrepareWorkflowCommand.java \
        core/src/main/java/com/infenia/yukta/service/control/gateway/ControlBusGateway.java \
        core/src/main/java/com/infenia/yukta/service/control/gateway/DefaultControlBusGateway.java
git commit -m "refactor: add sessionId to PrepareWorkflowCommand and ControlBusGateway"
```

---

## Task 4: Wire `ControlBusService.prepareWorkflow()` — save → invalidate → compile → cache

**Files:**
- Modify: `core/src/main/java/com/infenia/yukta/service/control/ControlBusService.java`

- [ ] **Step 1: Add dependencies to `ControlBusService`**

Add two new `final` fields to `ControlBusService` (after existing fields):

```java
  private final WorkflowDefinitionStore workflowDefinitionStore;
  private final PreparedWorkflowCache preparedWorkflowCache;
  private final WorkflowOrchestrator orchestrator;
```

Add imports:
```java
import com.infenia.yukta.service.orchestrator.WorkflowOrchestrator;
import com.infenia.yukta.service.workflow.store.PreparedWorkflowCache;
import com.infenia.yukta.service.workflow.store.WorkflowDefinitionStore;
```

Update the constructor to inject the three new dependencies (they come after existing params):

```java
  public ControlBusService(
      @Value("${control.bus.batch.size:100}") final int batchSize,
      @Value("${control.bus.batch.timeout.ms:50}") final int batchTimeoutMs,
      @Value("${control.bus.buffer.size:256}") final int bufferSize,
      final List<ControlSignalHandler> handlers,
      final WorkflowDefinitionStore workflowDefinitionStore,
      final PreparedWorkflowCache preparedWorkflowCache,
      final WorkflowOrchestrator orchestrator) {
    this.batchSize = batchSize;
    this.batchTimeout = Duration.ofMillis(batchTimeoutMs);
    this.bufferSize = Math.max(bufferSize, Queues.SMALL_BUFFER_SIZE);
    this.handlers = List.copyOf(handlers);
    this.workflowDefinitionStore = workflowDefinitionStore;
    this.preparedWorkflowCache = preparedWorkflowCache;
    this.orchestrator = orchestrator;
  }
```

Note: `WorkflowService` was previously injected — remove it entirely (the `workflowService` field and its constructor param). `ControlBusService` will own preparation directly.

- [ ] **Step 2: Replace `prepareWorkflow()` method body**

Replace the existing `prepareWorkflow` method:

```java
  /**
   * Prepare a workflow for execution: persist definition, invalidate stale cache, compile, warm
   * cache.
   *
   * @param command the prepare workflow command
   * @return a Mono that completes when the workflow is prepared and cached
   */
  public Mono<Void> prepareWorkflow(final PrepareWorkflowCommand command) {
    final String sessionId = command.sessionId();
    final String workflowId = command.workflowDefinition().workflowId();
    return workflowDefinitionStore
        .save(sessionId, command.workflowDefinition())
        .doOnSuccess(v -> preparedWorkflowCache.invalidate(sessionId, workflowId))
        .then(orchestrator.prepareWorkflow(command.workflowDefinition()))
        .doOnNext(prepared -> preparedWorkflowCache.put(sessionId, workflowId, prepared))
        .then();
  }
```

- [ ] **Step 3: Verify compilation**

```bash
./gradlew :core:compileJava
```

Expected: compiles. Fix any residual issues (e.g., unused `WorkflowService` import).

- [ ] **Step 4: Run quality checks**

```bash
./gradlew :core:check
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/infenia/yukta/service/control/ControlBusService.java
git commit -m "feat: wire ControlBusService.prepareWorkflow to save, invalidate, compile, and cache"
```

---

## Task 5: Update `WorkflowService` — cache-first lookup in `runWorkflow()`

**Files:**
- Modify: `core/src/main/java/com/infenia/yukta/service/WorkflowService.java`
- Modify: `core/src/test/java/com/infenia/yukta/service/WorkflowServiceTest.java`

- [ ] **Step 1: Add `WorkflowDefinitionStore` and `PreparedWorkflowCache` to `WorkflowService`**

`WorkflowService` currently has `SessionConfigStore configService`, `WorkflowOrchestrator orchestrator`, `TaskTrackerService tracker`. Add two fields:

```java
  private final WorkflowDefinitionStore workflowDefinitionStore;
  private final PreparedWorkflowCache preparedWorkflowCache;
```

Add imports:
```java
import com.infenia.yukta.service.workflow.store.PreparedWorkflowCache;
import com.infenia.yukta.service.workflow.store.WorkflowDefinitionStore;
```

Remove `SessionConfigStore configService` field and its usages from `runWorkflow` (the `configService.getWorkflow(...)` call inside the workflow queue logic). `WorkflowService` no longer reads workflow definitions from `SessionConfigStore`.

Update the constructor (since `@RequiredArgsConstructor` is used, just add the two new `final` fields — Lombok generates the constructor):

```java
  private final WorkflowOrchestrator orchestrator;
  private final TaskTrackerService tracker;
  private final WorkflowDefinitionStore workflowDefinitionStore;
  private final PreparedWorkflowCache preparedWorkflowCache;
```

- [ ] **Step 2: Replace definition loading + compilation inside `runWorkflow()`**

Inside the `Mono.defer(...)` block in `runWorkflow()` private method, replace the chain that starts with `configService.getWorkflow(sessionId, workflowId)...` with a cache-first lookup:

```java
final Mono<Void> current =
    Mono.defer(
            () ->
                resolveAndExecute(sessionId, workflowId, executionId, payload, sink))
        .subscribeOn(Schedulers.boundedElastic());
```

Add a new private helper method:

```java
  private Mono<Void> resolveAndExecute(
      final String sessionId,
      final String workflowId,
      final String executionId,
      final Map<String, Object> payload,
      final Sinks.One<TaskResponse> sink) {
    final var cached = preparedWorkflowCache.get(sessionId, workflowId);
    final Mono<PreparedWorkflow> preparedMono;
    if (cached.isPresent()) {
      preparedMono = Mono.just(cached.get());
    } else {
      preparedMono =
          workflowDefinitionStore
              .find(sessionId, workflowId)
              .switchIfEmpty(
                  Mono.error(
                      new IllegalArgumentException(
                          "Workflow not found: " + sessionId + "/" + workflowId)))
              .flatMap(
                  def ->
                      orchestrator
                          .prepareWorkflow(def)
                          .doOnNext(p -> preparedWorkflowCache.put(sessionId, workflowId, p)));
    }
    return preparedMono
        .flatMap(prepared -> orchestrator.execute(sessionId, workflowId, executionId, prepared, payload))
        .then(Mono.just(new TaskResponse("SUCCESS", "Workflow executed successfully")))
        .onErrorResume(
            e -> {
              log.atError()
                  .setCause(e)
                  .addKeyValue(LOG_KEY_SESSION_ID, sessionId)
                  .addKeyValue(LOG_KEY_WORKFLOW_ID, workflowId)
                  .addKeyValue(LOG_KEY_EXECUTION_ID, executionId)
                  .addKeyValue(LOG_KEY_ERROR_MSG, e.getMessage())
                  .log("Workflow execution failed");
              return tracker
                  .finishWorkflow(executionId, "FAILURE")
                  .onErrorComplete()
                  .thenReturn(new TaskResponse("FAILURE", "Workflow failed: " + e.getMessage()));
            })
        .flatMap(
            response -> {
              sink.tryEmitValue(response);
              return Mono.empty();
            })
        .then();
  }
```

Also remove the `validateAndTriggerWorkflow` method's `configService.getWorkflow(...)` call — replace it with `workflowDefinitionStore.find(sessionId, workflowId)`.

- [ ] **Step 3: Add cache-hit and cache-miss tests to `WorkflowServiceTest`**

Open `core/src/test/java/com/infenia/yukta/service/WorkflowServiceTest.java` and add the following test methods (alongside existing tests):

```java
  @Test
  void runWorkflowUsesCacheHitWithoutCallingStore() {
    final PreparedWorkflow prepared = mock(PreparedWorkflow.class);
    preparedWorkflowCache.put("s1", "wf1", prepared);
    when(orchestrator.execute(eq("s1"), eq("wf1"), any(), eq(prepared), any()))
        .thenReturn(Mono.empty());

    final WorkflowExecution execution = workflowService.runWorkflow("s1", "wf1", Map.of("k", "v"));
    StepVerifier.create(execution.result())
        .expectNextMatches(r -> "SUCCESS".equals(r.status()))
        .verifyComplete();

    verify(workflowDefinitionStore, never()).find(any(), any());
    verify(orchestrator, never()).prepareWorkflow(any());
  }

  @Test
  void runWorkflowOnCacheMissLoadsFromStoreAndCompiles() {
    final WorkflowDefinition def = testDefinition("wf1");
    final PreparedWorkflow prepared = mock(PreparedWorkflow.class);
    when(workflowDefinitionStore.find("s1", "wf1")).thenReturn(Mono.just(def));
    when(orchestrator.prepareWorkflow(def)).thenReturn(Mono.just(prepared));
    when(orchestrator.execute(eq("s1"), eq("wf1"), any(), eq(prepared), any()))
        .thenReturn(Mono.empty());

    final WorkflowExecution execution = workflowService.runWorkflow("s1", "wf1", Map.of("k", "v"));
    StepVerifier.create(execution.result())
        .expectNextMatches(r -> "SUCCESS".equals(r.status()))
        .verifyComplete();

    verify(workflowDefinitionStore).find("s1", "wf1");
    verify(orchestrator).prepareWorkflow(def);
  }

  @Test
  void runWorkflowOnStoreMissPropagatesError() {
    when(workflowDefinitionStore.find("s1", "wf1")).thenReturn(Mono.empty());
    when(tracker.finishWorkflow(any(), any())).thenReturn(Mono.empty());

    final WorkflowExecution execution = workflowService.runWorkflow("s1", "wf1", Map.of("k", "v"));
    StepVerifier.create(execution.result())
        .expectNextMatches(r -> "FAILURE".equals(r.status()))
        .verifyComplete();
  }

  private static WorkflowDefinition testDefinition(final String workflowId) {
    return new WorkflowDefinition(
        workflowId,
        "desc",
        List.of(new WorkflowDefinition.Node("n1", "trigger", Map.of())),
        List.of());
  }
```

You will need `@Mock WorkflowDefinitionStore workflowDefinitionStore` and `PreparedWorkflowCache preparedWorkflowCache` (real instance with long TTL) in the test class setup, and inject them into `WorkflowService`.

- [ ] **Step 4: Run the updated tests**

```bash
./gradlew :core:test --tests com.infenia.yukta.service.workflow.WorkflowServiceTest
```

Expected: all tests PASS including the three new ones.

- [ ] **Step 5: Run quality checks**

```bash
./gradlew :core:check
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/infenia/yukta/service/WorkflowService.java \
        core/src/test/java/com/infenia/yukta/service/WorkflowServiceTest.java
git commit -m "feat: cache-first PreparedWorkflow lookup in WorkflowService.runWorkflow"
```

---

## Task 6: Remove workflow storage from `SessionConfigStore` and its impls

**Files:**
- Modify: `core/src/main/java/com/infenia/yukta/service/session/SessionConfigStore.java`
- Modify: `core/src/main/java/com/infenia/yukta/service/session/InMemorySessionConfigStore.java`
- Modify: `core/src/main/java/com/infenia/yukta/service/session/FileSessionConfigStore.java`
- Modify: `core/src/test/java/com/infenia/yukta/service/session/InMemorySessionConfigStoreTest.java`
- Modify: `core/src/test/java/com/infenia/yukta/service/session/FileSessionConfigStoreTest.java`

- [ ] **Step 1: Remove 3 methods from `SessionConfigStore` interface**

In `SessionConfigStore.java`, delete the following three method declarations and their Javadoc:
- `Mono<Map<String, WorkflowDefinition>> getWorkflows(String sessionId)`
- `Mono<WorkflowDefinition> getWorkflow(String sessionId, String workflowId)`
- `Mono<Void> setWorkflows(String sessionId, Map<String, WorkflowDefinition> workflows)`

Also remove the import of `WorkflowDefinition` if it becomes unused (check `getAllConfigs` return type — it returns `Map<String, Object>` so the import can go).

- [ ] **Step 2: Update `InMemorySessionConfigStore`**

Remove:
- The `workflowsMap` field: `private final Map<String, Map<String, WorkflowDefinition>> workflowsMap`
- The `getWorkflows()`, `getWorkflow()`, `setWorkflows()` method implementations
- The `workflowsMap.put(sessionId, data.workflows())` line inside `applySessionConfig()`
- The `workflowsMap.containsKey(sessionId)` check inside `sessionExists()`
- The `allSessions.addAll(workflowsMap.keySet())` line inside `getSessionIds()`

Add `WorkflowDefinitionStore` as a dependency:

```java
  private final WorkflowDefinitionStore workflowDefinitionStore;
```

In `applySessionConfig()`, after storing projectPath, descriptions, initiators etc., add workflow delegation:

```java
    return Mono.fromRunnable(
            () -> {
              final String sessionId = data.sessionId();
              projectPaths.put(sessionId, data.projectPath());
              descriptions.put(sessionId, data.description());
              initiators.put(sessionId, data.initiator());
              initiatedTimes.put(sessionId, Instant.now().toString());
              tagsMap.put(sessionId, data.tags());
            })
        .then(
            data.workflows() != null && !data.workflows().isEmpty()
                ? Flux.fromIterable(data.workflows().values())
                    .flatMap(def -> workflowDefinitionStore.save(data.sessionId(), def))
                    .then()
                : Mono.empty());
```

Add import:
```java
import com.infenia.yukta.service.workflow.store.WorkflowDefinitionStore;
import reactor.core.publisher.Flux;
```

- [ ] **Step 3: Update `FileSessionConfigStore`**

Remove methods: `getWorkflows()`, `getWorkflow()`, `setWorkflows()`.

Keep `SessionConfig.workflows` field (for JSON deserialization of existing files — reads still work).

Add `WorkflowDefinitionStore` as a constructor dependency:

```java
  private final WorkflowDefinitionStore workflowDefinitionStore;
```

In `applySessionConfig()`, after building the `SessionConfig` object and calling `saveSessionConfig()`, chain workflow delegation:

```java
    return Mono.fromCallable(
            () ->
                new SessionConfig(
                    data.sessionId(),
                    data.projectPath(),
                    null, // workflows no longer written to file — stored in WorkflowDefinitionStore
                    data.description(),
                    data.initiator(),
                    Instant.now().toString(),
                    data.tags()))
        .flatMap(config -> saveSessionConfig(data.sessionId(), config))
        .then(
            data.workflows() != null && !data.workflows().isEmpty()
                ? Flux.fromIterable(data.workflows().values())
                    .flatMap(def -> workflowDefinitionStore.save(data.sessionId(), def))
                    .then()
                : Mono.empty())
        .subscribeOn(Schedulers.boundedElastic());
```

Add import:
```java
import com.infenia.yukta.service.workflow.store.WorkflowDefinitionStore;
import reactor.core.publisher.Flux;
```

- [ ] **Step 4: Update tests — remove workflow assertions, add delegation assertions**

In `InMemorySessionConfigStoreTest.java`:
- Remove any test that calls `getWorkflow`, `getWorkflows`, or `setWorkflows` on the store
- Add a mock `WorkflowDefinitionStore` to the test setup
- Add a test verifying `applySessionConfig()` calls `workflowDefinitionStore.save()` for each workflow:

```java
  @Test
  void applySessionConfigDelegatesWorkflowsToStore() {
    final WorkflowDefinition wf =
        new WorkflowDefinition(
            "wf1", "desc", List.of(new WorkflowDefinition.Node("n1", "t", Map.of())), List.of());
    final SessionConfigData data =
        new SessionConfigData("s1", "/path", Map.of("wf1", wf), "desc", "user", Map.of());

    when(workflowDefinitionStore.save("s1", wf)).thenReturn(Mono.empty());

    StepVerifier.create(store.applySessionConfig(data)).verifyComplete();

    verify(workflowDefinitionStore).save("s1", wf);
  }
```

Apply the same pattern to `FileSessionConfigStoreTest.java`.

- [ ] **Step 5: Verify compilation and tests**

```bash
./gradlew :core:test --tests "com.infenia.yukta.service.session.store.InMemorySessionConfigStoreTest,com.infenia.yukta.service.session.store.FileSessionConfigStoreTest"
```

Expected: all tests PASS.

- [ ] **Step 6: Run quality checks**

```bash
./gradlew :core:check
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/com/infenia/yukta/service/session/SessionConfigStore.java \
        core/src/main/java/com/infenia/yukta/service/session/InMemorySessionConfigStore.java \
        core/src/main/java/com/infenia/yukta/service/session/FileSessionConfigStore.java \
        core/src/test/java/com/infenia/yukta/service/session/InMemorySessionConfigStoreTest.java \
        core/src/test/java/com/infenia/yukta/service/session/FileSessionConfigStoreTest.java
git commit -m "refactor: remove WorkflowDefinition storage from SessionConfigStore, delegate to WorkflowDefinitionStore"
```

---

## Task 7: Update `DefaultWorkflowGateway` and `SessionService`

**Files:**
- Modify: `core/src/main/java/com/infenia/yukta/service/DefaultWorkflowGateway.java`
- Modify: `core/src/main/java/com/infenia/yukta/service/session/SessionService.java`

- [ ] **Step 1: Update `DefaultWorkflowGateway`**

In `DefaultWorkflowGateway`, add `ObjectProvider<WorkflowDefinitionStore>` as a constructor parameter (use `ObjectProvider` to match the existing pattern for avoiding circular dependencies):

```java
  private final ObjectProvider<WorkflowDefinitionStore> wfStoreProv;

  public DefaultWorkflowGateway(
      final ObjectProvider<WorkflowOrchestrator> orchProv,
      final ObjectProvider<SessionConfigStore> cfgServProv,
      final ObjectProvider<WorkflowDefinitionStore> wfStoreProv) {
    this.orchProv = orchProv;
    this.cfgServProv = cfgServProv;
    this.wfStoreProv = wfStoreProv;
  }
```

Add import:
```java
import com.infenia.yukta.service.workflow.store.WorkflowDefinitionStore;
```

Replace the workflow loading + sub-workflow copying block inside `executeSubWorkflow()`:

```java
    final WorkflowOrchestrator orchestrator = orchProv.getIfAvailable();
    final SessionConfigStore configService = cfgServProv.getIfAvailable();
    final WorkflowDefinitionStore wfStore = wfStoreProv.getIfAvailable();

    if (orchestrator == null || configService == null || wfStore == null) {
      return Mono.error(
          new WorkflowExecutionException(
              "Required services (Orchestrator/Config/WorkflowStore) not available"));
    }

    final ResultCollector collector = new ResultCollector();

    return wfStore
        .find(parentSessionId, workflowId)
        .switchIfEmpty(
            Mono.error(new WorkflowExecutionException("Workflow not found: " + workflowId)))
        .flatMap(
            def ->
                configService
                    .getProjectPath(parentSessionId)
                    .flatMap(path -> configService.setProjectPath(childSessionId, path))
                    .then(
                        wfStore
                            .findAll(parentSessionId)
                            .flatMapMany(wfs -> Flux.fromIterable(wfs.values()))
                            .flatMap(d -> wfStore.save(childSessionId, d))
                            .then())
                    .then(
                        configService
                            .getInitiator(parentSessionId)
                            .flatMap(init -> configService.setInitiator(childSessionId, init)))
                    .then(
                        configService
                            .getDescription(parentSessionId)
                            .flatMap(
                                desc ->
                                    configService.setDescription(
                                        childSessionId, desc + " (Sub-workflow)")))
                    .then(
                        orchestrator.prepareWorkflow(
                            new WorkflowDefinition(
                                workflowId, def.description(), def.nodes(), def.edges())))
                    .flatMap(
                        prepared -> {
                          final String executionId = UUID.randomUUID().toString();
                          return orchestrator
                              .execute(childSessionId, workflowId, executionId, prepared, payload)
                              .contextWrite(ctx -> ctx.put("resultCollector", collector))
                              .then(Mono.fromCallable(collector::getResults));
                        }))
        .onErrorMap(
            e -> {
              if (e instanceof WorkflowExecutionException) {
                return e;
              }
              return new WorkflowExecutionException(
                  "Sub-workflow execution failed for " + workflowId + ": " + e.getMessage(), e);
            });
```

Add import: `import reactor.core.publisher.Flux;`

- [ ] **Step 2: Update `SessionService.getSessionWorkflow()`**

Add `WorkflowDefinitionStore` as a field in `SessionService`:

```java
  private final WorkflowDefinitionStore workflowDefinitionStore;
```

Add import:
```java
import com.infenia.yukta.service.workflow.store.WorkflowDefinitionStore;
```

Replace the `getSessionWorkflow()` method:

```java
  /**
   * Get workflow for a session.
   *
   * @param sessionId the session identifier
   * @param workflowId the workflow identifier
   * @return Mono containing the workflow definition
   */
  public Mono<WorkflowDefinition> getSessionWorkflow(
      @SessionId final String sessionId, @WorkflowId final String workflowId) {
    return workflowDefinitionStore.find(sessionId, workflowId);
  }
```

Remove the now-unused `parseWorkflow()` private method and the `objectMapper` field if it is only used there. Check if `objectMapper` is used elsewhere in `SessionService` before removing.

- [ ] **Step 3: Run full test suite**

```bash
./gradlew :core:test
```

Expected: all tests PASS.

- [ ] **Step 4: Run quality checks**

```bash
./gradlew :core:check
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/infenia/yukta/service/DefaultWorkflowGateway.java \
        core/src/main/java/com/infenia/yukta/service/session/SessionService.java
git commit -m "refactor: use WorkflowDefinitionStore in DefaultWorkflowGateway and SessionService"
```

---

## Task 8: Final verification

- [ ] **Step 1: Run the full build**

```bash
./gradlew clean build
```

Expected: BUILD SUCCESSFUL, all tests pass, no quality violations.

- [ ] **Step 2: Run `spotlessApply` and re-check**

```bash
./gradlew spotlessApply && ./gradlew check
```

Expected: BUILD SUCCESSFUL with no formatting changes needed (or apply and re-verify).

- [ ] **Step 3: Detect changes with GitNexus**

```bash
npx gitnexus detect-changes
```

Review the output to confirm only the expected symbols are affected.

- [ ] **Step 4: Commit any spotless fixes**

If `spotlessApply` made changes:

```bash
git add -u
git commit -m "style: apply spotless formatting"
```
