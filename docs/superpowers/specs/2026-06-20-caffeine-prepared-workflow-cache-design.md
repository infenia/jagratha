# Design: Simplify PreparedWorkflowCache with Caffeine

**Date:** 2026-06-20  
**Status:** Ready for Implementation  
**Scope:** Refactor `PreparedWorkflowCache` to use Google Caffeine instead of manual TTL/eviction logic

---

## Overview

Replace the manual TTL-tracking and background eviction thread in `PreparedWorkflowCache` with Google Caffeine cache. This reduces code from ~150 lines to ~80, improves observability with built-in metrics, and delegates complexity to a battle-tested library.

**Key Change:** Move from custom `ScheduledExecutorService` eviction to Caffeine's internal expiration and `RemovalListener` callbacks.

---

## Current State

The existing `PreparedWorkflowCache` (`core/src/main/java/com/infenia/yukta/service/workflow/store/PreparedWorkflowCache.java`):
- Manual TTL tracking via `CacheEntry` with `AtomicLong lastAccessTime`
- Background `ScheduledExecutorService` eviction thread (fires every 60 seconds)
- Custom eviction scan and logging
- Composite keys: `sessionId\0workflowId`
- Session-aware batch invalidation: `invalidateAll(sessionId)` removes all entries for a session

### Pain Points
- 218 lines for a cache (verbose)
- Manual thread lifecycle management (init, shutdown, graceful termination)
- Difficult to test (mocking scheduler, verifying eviction timing)
- Limited observability (must log manually for metrics)

---

## Architecture

### Technology Choice: Google Caffeine

**Caffeine Configuration:**
```
LoadingCache<String, PreparedWorkflow> cache = Caffeine.newBuilder()
    .expireAfterAccess(ttlMs, TimeUnit.MILLISECONDS)  // Reset TTL on every access
    .recordStats()                                      // Collect hit/miss/eviction metrics
    .removalListener((key, value, cause) -> {         // Log evictions automatically
        // Log with key, cause (EXPIRED, REPLACED, EXPLICIT, SIZE)
    })
    .build(key -> { /* loader */ });  // Loader for cache misses (if using LoadingCache)
```

**Key Decisions:**
- **No loader function:** Keep explicit `put()` calls (current behavior). Don't use `load(key)` pattern.
- **Composite keys unchanged:** `sessionId\0workflowId` — no breaking changes to cache key strategy.
- **RemovalListener:** Automatic logging of evictions; captures cause (EXPIRED, REPLACED, EXPLICIT, SIZE).
- **Stats tracking:** Enable `recordStats()` for `getStats()` method.

### Components

1. **Caffeine Cache** — Core storage, TTL, automatic expiration
2. **RemovalListener** — Logs evictions at DEBUG level
3. **Session Prefix Removal** — `cache.asMap().keySet().removeIf(k -> k.startsWith(prefix))` for batch session invalidation
4. **Metrics Exposure** — `getStats()` method returns `CacheStats`

### What Gets Removed

- `ScheduledExecutorService scheduler` field and construction
- `CacheEntry` inner class (Caffeine tracks timestamps)
- `@PostConstruct init()` method
- Simplified `@PreDestroy shutdown()` — optional logging only

---

## Public API (No Breaking Changes)

All method signatures remain unchanged; callers are unaffected.

### Methods

| Method | Behavior |
|--------|----------|
| `put(sessionId, workflowId, prepared)` | Store workflow with auto TTL. Log at DEBUG. |
| `get(sessionId, workflowId)` | Retrieve & refresh TTL. Log hit/miss at DEBUG. Return `Optional`. |
| `invalidate(sessionId, workflowId)` | Remove single entry. Log at DEBUG. |
| `invalidateAll(sessionId)` | Remove all workflows for session (prefix scan). Log at INFO. Eventually consistent. |
| **`getStats()`** (NEW) | Return `CacheStats` (hits, misses, evictions, load times). For monitoring. |

### Logging

- **DEBUG:** Per-operation logging (get hit/miss, put, explicit invalidate)
- **INFO:** Batch operations (invalidateAll count), shutdown stats
- **Evictions:** Automatic via RemovalListener (DEBUG level, includes cause: EXPIRED, REPLACED, EXPLICIT, SIZE)

---

## Data Flow & Lifecycle

### Initialization
- Constructor takes `ttlMs` from `@Value("${workflow.cache.ttl.ms:600000}")`
- Caffeine cache instantiated in constructor or via `@Bean` method
- **No background thread to start** (removed @PostConstruct)

### Runtime Operations

1. **`put(sessionId, workflowId, prepared)`**
   - Delegate to `cache.put(compositeKey, prepared)`
   - Log operation with isReplacement flag

2. **`get(sessionId, workflowId)`**
   - Delegate to `cache.getIfPresent(compositeKey)`
   - Caffeine automatically resets TTL
   - Log hit/miss; return `Optional`

3. **`invalidate(sessionId, workflowId)`**
   - Delegate to `cache.invalidate(compositeKey)`
   - Log operation with wasPresent flag

4. **`invalidateAll(sessionId)`**
   - Scan `cache.asMap().keySet()` for prefix match
   - Remove matching entries via `removeIf()`
   - Log count of removed entries at INFO
   - **Eventually consistent:** Races on concurrent access are acceptable (eventual expiration handles stragglers)

5. **TTL & Expiration**
   - Caffeine tracks last-access time internally
   - `expireAfterAccess(ttlMs)` automatically resets on every get/put
   - Evictions happen lazily (on access) or on background refresh cycles
   - RemovalListener logs each eviction with cause

### Shutdown
- Optional `@PreDestroy`: Log final cache size and stats; Caffeine requires no explicit cleanup
- No thread termination logic needed

---

## Error Handling & Edge Cases

### No New Error Cases
- Caffeine is fault-tolerant; null-safety already enforced by `Optional` return type
- Session invalidation races: Eventually consistent (a workflow expires naturally if the prefix removal misses it)

### Observability

- **Built-in Metrics:** `CacheStats` accessible via `getStats()`:
  - hitCount, missCount, loadCount, loadSuccessCount, loadExceptionCount
  - totalLoadTime, evictionCount, evictionWeight
- **Eviction Logging:** RemovalListener captures cause (EXPIRED, REPLACED, EXPLICIT, SIZE) — useful for debugging TTL config
- **Integration:** Expose stats to monitoring system (e.g., Spring Actuator metrics) in future

---

## Testing Strategy

### Unit Tests
- Test get/put/invalidate operations without threads
- Mock scenarios for hit/miss, explicit eviction, batch invalidation
- Verify `invalidateAll(sessionId)` removes correct prefix entries
- Use Caffeine's `ticker` utility for time-based testing (no real delays)

### Integration Tests
- Verify TTL expiration with Caffeine's test utilities
- Confirm RemovalListener fires on eviction
- Test metrics accuracy (hitCount, missCount, evictionCount)
- Verify session batch removal behavior

### Benefits Over Current Tests
- No need to mock `ScheduledExecutorService`
- No flaky timing-dependent assertions
- Simpler thread-free test setup

---

## Migration Plan

1. **Replace implementation:** Update `PreparedWorkflowCache` class
2. **Update `@Bean` (if exists):** Ensure Caffeine dependency is available
3. **No caller changes:** Public API unchanged
4. **Run existing tests:** Should pass without modification
5. **Add new integration tests:** Verify TTL, metrics, eviction behavior
6. **Commit:** Single commit with design spec + implementation

---

## Code Impact

| Metric | Before | After | Change |
|--------|--------|-------|--------|
| Lines of code | ~218 | ~80 | -63% |
| Thread management | Custom | None | Eliminated |
| Eviction logic | Manual scan + TTL | Caffeine internal | Simplified |
| Metrics | Logging only | `CacheStats` + logging | Enhanced |
| Observability | Log noise (50+ log statements) | Structured + optional stats | Better |
| Test complexity | High (scheduler mocking) | Low (Caffeine testing APIs) | Reduced |

---

## Dependencies

- **Add:** Google Caffeine (likely already available in project dependencies)
- **Remove:** None (Caffeine is lightweight, no new transitive deps beyond Caffeine itself)

Check `gradle/libs.versions.toml` for version pinning.

---

## Rollback

If issues arise:
- Revert to prior commit
- No database changes or long-lived state affected
- Cache is in-memory only; loss of entries on revert is acceptable

---

## Sign-Off

- **Approval:** User approved architecture, API, and lifecycle design
- **Ready for:** Implementation plan generation via writing-plans skill
