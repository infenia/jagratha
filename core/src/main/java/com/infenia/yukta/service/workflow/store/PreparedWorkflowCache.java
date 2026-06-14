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
import java.util.concurrent.atomic.AtomicLong;
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
  public PreparedWorkflowCache(@Value("${workflow.cache.ttl.ms:600000}") final long ttlMs) {
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
        this::evictExpired, EVICTION_INTERVAL_MS, EVICTION_INTERVAL_MS, TimeUnit.MILLISECONDS);
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
  public void put(
      final String sessionId, final String workflowId, final PreparedWorkflow prepared) {
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
    final Optional<PreparedWorkflow> result;
    if (entry == null) {
      result = Optional.empty();
    } else {
      entry.touch();
      result = Optional.of(entry.prepared);
    }
    return result;
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
    final Iterator<Map.Entry<String, CacheEntry>> iterator = cache.entrySet().iterator();
    while (iterator.hasNext()) {
      final Map.Entry<String, CacheEntry> entry = iterator.next();
      if (now - entry.getValue().lastAccessTime.get() > ttlMs) {
        iterator.remove();
        log.atDebug().addKeyValue("key", entry.getKey()).log("Evicted expired PreparedWorkflow");
      }
    }
  }

  private static String key(final String sessionId, final String workflowId) {
    return sessionId + COMPOSITE_KEY_SEPARATOR + workflowId;
  }

  private static final class CacheEntry {
    private final PreparedWorkflow prepared;
    private final AtomicLong lastAccessTime;

    private CacheEntry(final PreparedWorkflow prepared) {
      this.prepared = prepared;
      this.lastAccessTime = new AtomicLong(System.currentTimeMillis());
    }

    private void touch() {
      this.lastAccessTime.set(System.currentTimeMillis());
    }
  }
}
