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

import com.infenia.yukta.model.workflow.PreparedWorkflow;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PreparedWorkflowCacheTest {

  private PreparedWorkflowCache cache;

  private static PreparedWorkflow mockPrepared() {
    return new PreparedWorkflow(List.of(), Map.of(), Map.of(), Map.of(), List.of(), null);
  }

  @BeforeEach
  void setUp() {
    // TTL of 200ms for fast eviction testing; init() is intentionally not called — eviction is
    // triggered manually via evictExpired()
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
    Thread.sleep(150L); // 250ms since put but only 150ms since last access
    cache.evictExpired();
    assertThat(cache.get("s1", "wf1")).contains(prepared);
  }

  @Test
  void initStartsEvictionScheduler() throws InterruptedException {
    final PreparedWorkflowCache liveCache = new PreparedWorkflowCache(100L);
    liveCache.init();
    // Scheduler is running; just verify the cache is still usable after init
    liveCache.put("s1", "wf1", mockPrepared());
    assertThat(liveCache.get("s1", "wf1")).isPresent();
    liveCache.shutdown();
  }

  @Test
  void shutdownTerminatesScheduler() throws InterruptedException {
    final PreparedWorkflowCache liveCache = new PreparedWorkflowCache(600_000L);
    liveCache.init();
    liveCache.shutdown(); // must complete without hanging
  }

  @Test
  void shutdownForcesShutdownNowWhenTerminationTimesOut() throws Exception {
    final PreparedWorkflowCache liveCache = new PreparedWorkflowCache(600_000L);

    // Inject a scheduler that never terminates within 1s, forcing the shutdownNow() branch
    final java.lang.reflect.Field schedulerField =
        PreparedWorkflowCache.class.getDeclaredField("scheduler");
    schedulerField.setAccessible(true);
    final java.util.concurrent.ScheduledExecutorService nonTerminatingScheduler =
        new java.util.concurrent.ScheduledThreadPoolExecutor(1) {
          @Override
          public void shutdown() {
            // no-op
          }

          @Override
          public boolean awaitTermination(final long timeout, final TimeUnit unit) {
            return false; // always times out, triggering shutdownNow()
          }

          @Override
          public java.util.List<Runnable> shutdownNow() {
            return java.util.Collections.emptyList();
          }
        };
    schedulerField.set(liveCache, nonTerminatingScheduler);

    liveCache.shutdown(); // must call shutdownNow() and return normally
  }

  @Test
  void invalidate_nonExistentKey_isNoOp() {
    assertThat(cache.get("s1", "wf1")).isEmpty();
    cache.invalidate("s1", "wf1"); // must not throw
    assertThat(cache.get("s1", "wf1")).isEmpty();
  }

  @Test
  void invalidateAll_unknownSession_isNoOp() {
    cache.put("s2", "wf1", mockPrepared());
    cache.invalidateAll("s1"); // must not throw; s2 must be untouched
    assertThat(cache.get("s2", "wf1")).isPresent();
  }

  @Test
  void put_existingEntry_overwritesWithNewValue() {
    final PreparedWorkflow first = mockPrepared();
    final PreparedWorkflow second = mockPrepared();
    cache.put("s1", "wf1", first);
    cache.put("s1", "wf1", second);
    assertThat(cache.get("s1", "wf1")).contains(second);
  }

  @Test
  void evictExpired_emptyCache_completesWithoutException() {
    cache.evictExpired(); // must not throw on empty cache
    assertThat(cache.get("s1", "wf1")).isEmpty();
  }

  @Test
  void evictExpired_freshEntry_isNotEvicted() {
    final PreparedWorkflow prepared = mockPrepared();
    cache.put("s1", "wf1", prepared);
    cache.evictExpired(); // TTL has not elapsed yet
    assertThat(cache.get("s1", "wf1")).contains(prepared);
  }

  @Test
  void get_differentSessions_returnsCorrectEntry() {
    final PreparedWorkflow forS1 = mockPrepared();
    final PreparedWorkflow forS2 = mockPrepared();
    cache.put("s1", "wf1", forS1);
    cache.put("s2", "wf1", forS2);
    assertThat(cache.get("s1", "wf1")).contains(forS1);
    assertThat(cache.get("s2", "wf1")).contains(forS2);
  }

  @Test
  void constructorThreadFactoryCreatesDaemonThread() throws Exception {
    // Reflectively extract the scheduler to inspect the thread factory it uses
    final PreparedWorkflowCache liveCache = new PreparedWorkflowCache(600_000L);
    final java.lang.reflect.Field schedulerField =
        PreparedWorkflowCache.class.getDeclaredField("scheduler");
    schedulerField.setAccessible(true);
    final ScheduledExecutorService scheduler =
        (ScheduledExecutorService) schedulerField.get(liveCache);

    final Thread[] created = new Thread[1];
    scheduler.submit(() -> created[0] = Thread.currentThread()).get(1, TimeUnit.SECONDS);

    assertThat(created[0]).isNotNull();
    assertThat(created[0].isDaemon()).isTrue();
    assertThat(created[0].getName()).isEqualTo("prepared-workflow-cache-eviction");
    liveCache.shutdown();
  }

  @Test
  void shutdownHandlesInterruptedExceptionGracefully() throws Exception {
    final PreparedWorkflowCache liveCache = new PreparedWorkflowCache(600_000L);

    // Inject a scheduler whose awaitTermination blocks until interrupted
    final java.lang.reflect.Field schedulerField =
        PreparedWorkflowCache.class.getDeclaredField("scheduler");
    schedulerField.setAccessible(true);
    final java.util.concurrent.CountDownLatch awaitingTermination =
        new java.util.concurrent.CountDownLatch(1);
    final java.util.concurrent.ScheduledExecutorService blockingScheduler =
        new java.util.concurrent.ScheduledThreadPoolExecutor(1) {
          @Override
          public void shutdown() {
            // no-op: scheduler is not actually running
          }

          @Override
          public boolean awaitTermination(final long timeout, final TimeUnit unit)
              throws InterruptedException {
            awaitingTermination.countDown();
            Thread.sleep(Long.MAX_VALUE); // blocks until the calling thread is interrupted
            return false;
          }

          @Override
          public java.util.List<Runnable> shutdownNow() {
            return java.util.Collections.emptyList();
          }
        };
    schedulerField.set(liveCache, blockingScheduler);

    final Thread shutdownThread = new Thread(liveCache::shutdown);
    shutdownThread.start();
    // Wait until shutdown() is blocked inside awaitTermination, then interrupt it
    awaitingTermination.await(1, TimeUnit.SECONDS);
    shutdownThread.interrupt();
    shutdownThread.join(2_000L);

    assertThat(shutdownThread.isAlive()).isFalse();
  }
}
