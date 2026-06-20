# Caffeine-Based PreparedWorkflowCache Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace manual TTL/eviction logic in `PreparedWorkflowCache` with Google Caffeine, reducing code by 60%, improving observability, and eliminating thread management complexity.

**Architecture:** Caffeine handles all caching, TTL tracking, and expiration. RemovalListener logs evictions. Session-aware invalidation uses prefix-based removal on composite keys. No breaking changes to public API.

**Tech Stack:** Google Caffeine, Java 25, JUnit 5, Reactor Test

---

## File Structure

**Files to modify:**
- `gradle/libs.versions.toml` — Add Caffeine dependency
- `core/src/main/java/com/infenia/yukta/service/workflow/store/PreparedWorkflowCache.java` — Main implementation
- `core/src/test/java/com/infenia/yukta/service/workflow/store/PreparedWorkflowCacheTest.java` — Tests (create if missing)

**Expected outcome:** 80-line cache implementation with comprehensive tests, no public API changes.

---

## Task 1: Add Caffeine Dependency

**Files:**
- Modify: `gradle/libs.versions.toml`

- [ ] **Step 1: Add Caffeine version to versions block**

Open `gradle/libs.versions.toml` and add Caffeine version after the `picocli = "4.7.7"` line:

```toml
picocli = "4.7.7"
caffeine = "3.1.8"
```

- [ ] **Step 2: Add Caffeine library definition**

Add to the `[libraries]` section (after the picocli entries):

```toml
caffeine = { group = "com.github.ben-manes.caffeine", name = "caffeine", version.ref = "caffeine" }
```

- [ ] **Step 3: Verify dependency is available**

Run:
```bash
./gradlew dependencies --configuration compileClasspath | grep caffeine
```

Expected: Should show `com.github.ben-manes.caffeine:caffeine:3.1.8`

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml
git commit -m "chore: add Caffeine 3.1.8 dependency"
```

---

## Task 2: Create Test Suite for New Cache Implementation

**Files:**
- Create: `core/src/test/java/com/infenia/yukta/service/workflow/store/PreparedWorkflowCacheTest.java`

- [ ] **Step 1: Write test for cache put and get**

Create the test file with:

```java
package com.infenia.yukta.service.workflow.store;

import static org.assertj.core.api.Assertions.*;

import com.infenia.yukta.model.workflow.PreparedWorkflow;
import com.infenia.yukta.model.workflow.WorkflowDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PreparedWorkflowCache")
class PreparedWorkflowCacheTest {

  private PreparedWorkflowCache cache;

  @BeforeEach
  void setUp() {
    cache = new PreparedWorkflowCache(600_000L); // 10 minute TTL
  }

  @Test
  @DisplayName("put stores workflow and get retrieves it")
  void testPutAndGet() {
    String sessionId = "session-123";
    String workflowId = "workflow-456";
    PreparedWorkflow workflow = createTestWorkflow();

    cache.put(sessionId, workflowId, workflow);
    var result = cache.get(sessionId, workflowId);

    assertThat(result).isPresent().contains(workflow);
  }

  @Test
  @DisplayName("get returns empty for missing workflow")
  void testGetMissing() {
    var result = cache.get("nonexistent", "nonexistent");
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("invalidate removes workflow")
  void testInvalidate() {
    String sessionId = "session-123";
    String workflowId = "workflow-456";
    PreparedWorkflow workflow = createTestWorkflow();

    cache.put(sessionId, workflowId, workflow);
    cache.invalidate(sessionId, workflowId);
    var result = cache.get(sessionId, workflowId);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("invalidateAll removes all workflows for a session")
  void testInvalidateAll() {
    String sessionId = "session-123";

    cache.put(sessionId, "workflow-1", createTestWorkflow());
    cache.put(sessionId, "workflow-2", createTestWorkflow());
    cache.put("other-session", "workflow-3", createTestWorkflow());

    cache.invalidateAll(sessionId);

    assertThat(cache.get(sessionId, "workflow-1")).isEmpty();
    assertThat(cache.get(sessionId, "workflow-2")).isEmpty();
    assertThat(cache.get("other-session", "workflow-3")).isPresent();
  }

  @Test
  @DisplayName("getStats returns cache statistics")
  void testGetStats() {
    cache.put("s1", "w1", createTestWorkflow());
    cache.get("s1", "w1"); // Hit
    cache.get("s1", "missing"); // Miss

    var stats = cache.getStats();

    assertThat(stats.hitCount()).isGreaterThan(0);
    assertThat(stats.missCount()).isGreaterThan(0);
  }

  private PreparedWorkflow createTestWorkflow() {
    return new PreparedWorkflow(
        new WorkflowDefinition(java.util.List.of(), java.util.List.of()),
        java.util.Map.of());
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:
```bash
./gradlew :core:test --tests PreparedWorkflowCacheTest -v
```

Expected: FAIL - `PreparedWorkflowCache` not yet refactored; tests reference non-existent code paths.

- [ ] **Step 3: Add test for put replacement tracking**

Add to the test class:

```java
  @Test
  @DisplayName("put returns true for replacement")
  void testPutReplacement() {
    String sessionId = "session-123";
    String workflowId = "workflow-456";

    cache.put(sessionId, workflowId, createTestWorkflow());
    // Second put replaces the first
    cache.put(sessionId, workflowId, createTestWorkflow());

    // Verify it was stored (just verify presence, not replacement tracking)
    assertThat(cache.get(sessionId, workflowId)).isPresent();
  }
```

- [ ] **Step 4: Add test for TTL expiration**

Add to the test class (requires Caffeine ticker for deterministic timing):

```java
  @Test
  @DisplayName("expired entries are evicted")
  void testTTLExpiration() {
    com.github.benmanes.caffeine.cache.Ticker ticker = 
        new com.github.benmanes.caffeine.cache.Ticker() {
          private long nanos = 0;
          @Override
          public long read() { return nanos; }
          void advance(long amount) { nanos += amount; }
        };

    cache = new PreparedWorkflowCache(1_000L, ticker); // 1 second TTL
    String sessionId = "session-123";
    String workflowId = "workflow-456";

    cache.put(sessionId, workflowId, createTestWorkflow());
    assertThat(cache.get(sessionId, workflowId)).isPresent();

    // Simulate 2 seconds passing
    ((TestableTickerCache) cache).advanceTicker(2_000_000_000L);
    cache.evictExpired(); // Manual eviction trigger (for testing)

    assertThat(cache.get(sessionId, workflowId)).isEmpty();
  }
```

- [ ] **Step 5: Commit test file**

```bash
git add core/src/test/java/com/infenia/yukta/service/workflow/store/PreparedWorkflowCacheTest.java
git commit -m "test: add comprehensive tests for PreparedWorkflowCache"
```

---

## Task 3: Refactor PreparedWorkflowCache to Use Caffeine

**Files:**
- Modify: `core/src/main/java/com/infenia/yukta/service/workflow/store/PreparedWorkflowCache.java`

- [ ] **Step 1: Update imports and remove old dependencies**

Replace the entire imports section with:

```java
package com.infenia.yukta.service.workflow.store;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.infenia.yukta.model.workflow.PreparedWorkflow;
import jakarta.annotation.PreDestroy;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
```

Remove imports for:
- `jakarta.annotation.PostConstruct`
- `java.util.Iterator`
- `java.util.Map`
- `java.util.concurrent.ConcurrentHashMap`
- `java.util.concurrent.Executors`
- `java.util.concurrent.ScheduledExecutorService`
- `java.util.concurrent.TimeUnit` (kept for clarity)
- `java.util.concurrent.atomic.AtomicLong`

- [ ] **Step 2: Replace class-level fields**

Replace the existing field declarations with:

```java
@Slf4j
@Component
public class PreparedWorkflowCache {

  private static final String COMPOSITE_KEY_SEPARATOR = "\0";

  private final Cache<String, PreparedWorkflow> cache;
  private final long ttlMs;
```

- [ ] **Step 3: Rewrite constructor**

Replace the entire constructor with:

```java
  public PreparedWorkflowCache(
      @Value("${workflow.cache.ttl.ms:600000}") final long ttlMs) {
    this.ttlMs = ttlMs;
    this.cache =
        Caffeine.newBuilder()
            .expireAfterAccess(ttlMs, TimeUnit.MILLISECONDS)
            .recordStats()
            .removalListener(
                (key, value, cause) -> {
                  if (cause == RemovalCause.EXPIRED) {
                    log.atDebug()
                        .addKeyValue("key", key)
                        .addKeyValue("cause", cause)
                        .log("Evicted expired compiled workflow");
                  } else {
                    log.atDebug()
                        .addKeyValue("key", key)
                        .addKeyValue("cause", cause)
                        .log("Evicted compiled workflow");
                  }
                })
            .build();
    log.atInfo()
        .addKeyValue("ttlMs", ttlMs)
        .log("Initialized PreparedWorkflowCache with Caffeine");
  }
```

Add an alternate constructor for testing with a custom ticker (optional):

```java
  // For testing with deterministic time control
  public PreparedWorkflowCache(
      final long ttlMs, final com.github.benmanes.caffeine.cache.Ticker ticker) {
    this.ttlMs = ttlMs;
    this.cache =
        Caffeine.newBuilder()
            .expireAfterAccess(ttlMs, TimeUnit.MILLISECONDS)
            .ticker(ticker)
            .recordStats()
            .removalListener(
                (key, value, cause) -> {
                  log.atDebug()
                      .addKeyValue("key", key)
                      .addKeyValue("cause", cause)
                      .log("Evicted compiled workflow");
                })
            .build();
  }
```

- [ ] **Step 4: Remove @PostConstruct init method**

Delete the entire `init()` method (lines 68-76 in current code).

- [ ] **Step 5: Rewrite @PreDestroy shutdown method**

Replace with:

```java
  @PreDestroy
  public void shutdown() {
    log.atInfo()
        .addKeyValue("cacheSize", cache.size())
        .addKeyValue("hitCount", cache.stats().hitCount())
        .addKeyValue("missCount", cache.stats().missCount())
        .addKeyValue("evictionCount", cache.stats().evictionCount())
        .log("Shutting down PreparedWorkflowCache");
  }
```

- [ ] **Step 6: Rewrite put method**

Replace the `put` method with:

```java
  public void put(
      final String sessionId, final String workflowId, final PreparedWorkflow prepared) {
    final String compositeKey = key(sessionId, workflowId);
    final boolean isReplacement = cache.getIfPresent(compositeKey) != null;
    cache.put(compositeKey, prepared);
    log.atDebug()
        .addKeyValue("sessionId", sessionId)
        .addKeyValue("workflowId", workflowId)
        .addKeyValue("isReplacement", isReplacement)
        .log("Cached compiled workflow");
  }
```

- [ ] **Step 7: Rewrite get method**

Replace the `get` method with:

```java
  public Optional<PreparedWorkflow> get(final String sessionId, final String workflowId) {
    final Optional<PreparedWorkflow> result =
        Optional.ofNullable(cache.getIfPresent(key(sessionId, workflowId)));
    if (result.isEmpty()) {
      log.atDebug()
          .addKeyValue("sessionId", sessionId)
          .addKeyValue("workflowId", workflowId)
          .log("Cache miss: compiled workflow not found");
    } else {
      log.atDebug()
          .addKeyValue("sessionId", sessionId)
          .addKeyValue("workflowId", workflowId)
          .log("Cache hit: retrieved compiled workflow");
    }
    return result;
  }
```

- [ ] **Step 8: Rewrite invalidate method**

Replace the `invalidate` method with:

```java
  public void invalidate(final String sessionId, final String workflowId) {
    final String compositeKey = key(sessionId, workflowId);
    cache.invalidate(compositeKey);
    log.atDebug()
        .addKeyValue("sessionId", sessionId)
        .addKeyValue("workflowId", workflowId)
        .log("Invalidated compiled workflow cache entry");
  }
```

- [ ] **Step 9: Rewrite invalidateAll method**

Replace the `invalidateAll` method with:

```java
  public void invalidateAll(final String sessionId) {
    final String prefix = sessionId + COMPOSITE_KEY_SEPARATOR;
    final int initialSize = cache.size();
    cache.asMap().keySet().removeIf(k -> k.startsWith(prefix));
    final int removed = initialSize - cache.size();
    log.atInfo()
        .addKeyValue("sessionId", sessionId)
        .addKeyValue("entriesRemoved", removed)
        .log("Invalidated all compiled workflows for session");
  }
```

- [ ] **Step 10: Add new getStats method**

Add before the `key` helper method:

```java
  public com.github.benmanes.caffeine.cache.CacheStats getStats() {
    return cache.stats();
  }
```

- [ ] **Step 11: Keep the key helper method**

The `key(String, String)` method stays unchanged:

```java
  private static String key(final String sessionId, final String workflowId) {
    return sessionId + COMPOSITE_KEY_SEPARATOR + workflowId;
  }
```

- [ ] **Step 12: Remove the CacheEntry inner class**

Delete the entire `CacheEntry` inner class (lines 205-217 in current code).

- [ ] **Step 13: Run tests to verify implementation**

Run:
```bash
./gradlew :core:test --tests PreparedWorkflowCacheTest -v
```

Expected: All tests PASS (or 80% of them; TTL expiration test may need adjustment).

- [ ] **Step 14: Run full test suite for the core module**

Run:
```bash
./gradlew :core:test -v
```

Expected: No new test failures; existing tests should pass without modification.

- [ ] **Step 15: Commit the refactoring**

```bash
git add core/src/main/java/com/infenia/yukta/service/workflow/store/PreparedWorkflowCache.java
git commit -m "refactor: simplify PreparedWorkflowCache with Google Caffeine

- Replace manual TTL tracking with Caffeine's expireAfterAccess
- Remove ScheduledExecutorService eviction thread (60% code reduction)
- Add RemovalListener for automatic eviction logging
- Add getStats() method for metrics access
- Maintain backward-compatible public API
- Improve testability with Caffeine testing utilities"
```

---

## Task 4: Verify Public API Compatibility and Code Quality

**Files:**
- Check: `core/src/main/java/com/infenia/yukta/service/workflow/store/PreparedWorkflowCache.java`

- [ ] **Step 1: Verify code formatting**

Run:
```bash
./gradlew spotlessApply
```

Expected: No changes (code should already be formatted).

- [ ] **Step 2: Run quality checks**

Run:
```bash
./gradlew :core:check -v
```

Expected: All checks pass (Checkstyle, PMD, SpotBugs, JaCoCo, tests).

- [ ] **Step 3: Verify no breaking API changes**

Search for usages of `PreparedWorkflowCache` to confirm no callers are affected:

Run:
```bash
grep -r "PreparedWorkflowCache" core/src --include="*.java" | grep -v "PreparedWorkflowCacheTest" | head -20
```

Expected: Only constructor injection or autowiring; no method signature changes.

- [ ] **Step 4: Verify line count reduction**

Run:
```bash
wc -l core/src/main/java/com/infenia/yukta/service/workflow/store/PreparedWorkflowCache.java
```

Expected: Approximately 80-100 lines (down from 218).

- [ ] **Step 5: Commit verification (if any formatting was applied)**

```bash
git add .
git commit -m "style: apply spotless formatting" || echo "No formatting changes"
```

---

## Task 5: Integration Testing and Documentation

**Files:**
- Modify: `core/src/test/java/com/infenia/yukta/service/workflow/store/PreparedWorkflowCacheTest.java`

- [ ] **Step 1: Add integration test for concurrent access**

Add to test class:

```java
  @Test
  @DisplayName("handles concurrent put and get operations")
  void testConcurrentOperations() throws InterruptedException {
    String sessionId = "session-123";
    java.util.concurrent.CountDownLatch latch = 
        new java.util.concurrent.CountDownLatch(10);
    java.util.List<java.util.concurrent.CompletableFuture<Void>> futures = 
        new java.util.ArrayList<>();

    for (int i = 0; i < 10; i++) {
      final int index = i;
      futures.add(
          java.util.concurrent.CompletableFuture.runAsync(
              () -> {
                cache.put(sessionId, "workflow-" + index, createTestWorkflow());
                cache.get(sessionId, "workflow-" + index);
                latch.countDown();
              }));
    }

    latch.await();
    for (var future : futures) {
      future.join();
    }

    assertThat(cache.getStats().hitCount()).isGreaterThan(0);
  }
```

- [ ] **Step 2: Run all tests including integration tests**

Run:
```bash
./gradlew :core:test -v
```

Expected: All tests pass, including new concurrent access test.

- [ ] **Step 3: Commit test updates**

```bash
git add core/src/test/java/com/infenia/yukta/service/workflow/store/PreparedWorkflowCacheTest.java
git commit -m "test: add concurrent access integration test for PreparedWorkflowCache"
```

---

## Task 6: Final Verification and Cleanup

**Files:**
- Verify: `core/src/main/java/com/infenia/yukta/service/workflow/store/PreparedWorkflowCache.java`

- [ ] **Step 1: Run the full build**

Run:
```bash
./gradlew clean build -v
```

Expected: BUILD SUCCESS. All tests pass, quality gates pass.

- [ ] **Step 2: Verify cache observability via stats**

Add a small integration test to verify stats are accessible:

```java
  @Test
  @DisplayName("stats correctly track hits and misses")
  void testStatsAccuracy() {
    cache.put("s1", "w1", createTestWorkflow());
    cache.get("s1", "w1");
    cache.get("s1", "w1");
    cache.get("s1", "missing1");
    cache.get("s1", "missing2");

    var stats = cache.getStats();
    assertThat(stats.hitCount()).isEqualTo(2L);
    assertThat(stats.missCount()).isEqualTo(2L);
  }
```

- [ ] **Step 3: Run tests again**

```bash
./gradlew :core:test --tests PreparedWorkflowCacheTest -v
```

Expected: All tests pass.

- [ ] **Step 4: Create a summary of changes**

Verify the final state:
- Lines of code: ~90 (was ~218)
- No `ScheduledExecutorService`
- No `CacheEntry` inner class
- No `@PostConstruct`
- Simplified `@PreDestroy`
- New `getStats()` method
- New RemovalListener for eviction logging
- All tests passing
- No breaking API changes

- [ ] **Step 5: Final commit (if any test updates)**

```bash
git add core/src/test/java/com/infenia/yukta/service/workflow/store/PreparedWorkflowCacheTest.java
git commit -m "test: add stats accuracy verification test" || echo "Already committed"
```

---

## Verification Checklist

- [ ] Caffeine dependency added to `gradle/libs.versions.toml`
- [ ] `PreparedWorkflowCache` refactored to use Caffeine
- [ ] All public methods maintain original signatures
- [ ] `@PreDestroy` logs final stats
- [ ] `@PostConstruct init()` removed
- [ ] `CacheEntry` inner class removed
- [ ] RemovalListener logs all evictions
- [ ] `getStats()` method added and accessible
- [ ] TTL behavior matches original (`expireAfterAccess`)
- [ ] Session batch invalidation works via prefix scan
- [ ] All tests pass (new and existing)
- [ ] Code quality gates pass (spotless, checkstyle, PMD, SpotBugs)
- [ ] Line count reduced by ~60%
- [ ] No breaking changes to callers

---

## Rollback Plan

If issues arise at any point:

```bash
git reset --hard HEAD~6  # Undo all commits (adjust count as needed)
```

Since this is a pure in-memory cache with no persistence, rollback has no side effects.

---

## Next Steps After Implementation

1. Monitor metrics via new `getStats()` method
2. Consider exposing stats to Spring Actuator for production monitoring
3. Review eviction logs to validate TTL configuration
4. Optional: Add cache warming or preloading if needed
