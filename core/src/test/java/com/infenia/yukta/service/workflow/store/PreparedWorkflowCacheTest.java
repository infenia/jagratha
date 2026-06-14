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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PreparedWorkflowCacheTest {

  private PreparedWorkflowCache cache;

  private static PreparedWorkflow mockPrepared() {
    return new PreparedWorkflow(List.of(), Map.of(), Map.of(), Map.of(), List.of(), null);
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
    Thread.sleep(150L); // 250ms since put but only 150ms since last access
    cache.evictExpired();
    assertThat(cache.get("s1", "wf1")).contains(prepared);
  }
}
