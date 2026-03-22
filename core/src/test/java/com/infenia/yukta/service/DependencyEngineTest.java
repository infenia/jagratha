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
package com.infenia.yukta.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/** Unit tests for DependencyEngine. */
class DependencyEngineTest {

  private DependencyEngine engine;
  private static final String EXECUTION_ID = "exec-001";

  @BeforeEach
  void setup() {
    engine = new DependencyEngine();
  }

  @Test
  void testInitExecution() {
    final Map<String, List<String>> parentMap =
        Map.of(
            "node-a", List.of(),
            "node-b", List.of("node-a"),
            "node-c", List.of("node-a", "node-b"));

    engine.initExecution(EXECUTION_ID, parentMap);

    assertEquals(0, engine.getState(EXECUTION_ID, "node-a"));
    assertEquals(1, engine.getState(EXECUTION_ID, "node-b"));
    assertEquals(2, engine.getState(EXECUTION_ID, "node-c"));
  }

  @Test
  void testNodeCompletedEmitsReadyNodes() {
    final Map<String, List<String>> parentMap =
        Map.of(
            "node-a", List.of(),
            "node-b", List.of("node-a"),
            "node-c", List.of("node-a"));

    engine.initExecution(EXECUTION_ID, parentMap);

    StepVerifier.create(engine.nodeCompleted(EXECUTION_ID, "node-a"))
        .expectNext("node-b", "node-c")
        .verifyComplete();
  }

  @Test
  void testNodeCompletedDecrementsSingleParent() {
    final Map<String, List<String>> parentMap =
        Map.of(
            "node-a", List.of(),
            "node-b", List.of("node-a"),
            "node-c", List.of("node-b"));

    engine.initExecution(EXECUTION_ID, parentMap);

    StepVerifier.create(engine.nodeCompleted(EXECUTION_ID, "node-a"))
        .expectNext("node-b")
        .verifyComplete();

    StepVerifier.create(engine.nodeCompleted(EXECUTION_ID, "node-b"))
        .expectNext("node-c")
        .verifyComplete();
  }

  @Test
  void testNodeCompletedNoChildren() {
    final Map<String, List<String>> parentMap =
        Map.of(
            "node-a", List.of(),
            "node-b", List.of("node-a"));

    engine.initExecution(EXECUTION_ID, parentMap);

    StepVerifier.create(engine.nodeCompleted(EXECUTION_ID, "node-b")).expectComplete().verify();
  }

  @Test
  void testCleanupExecution() {
    final Map<String, List<String>> parentMap =
        Map.of(
            "node-a", List.of(),
            "node-b", List.of("node-a"));

    engine.initExecution(EXECUTION_ID, parentMap);
    assertEquals(0, engine.getState(EXECUTION_ID, "node-a"));

    engine.cleanupExecution(EXECUTION_ID);
    assertEquals(-1, engine.getState(EXECUTION_ID, "node-a"));
  }

  @Test
  void testComplexDAG() {
    final Map<String, List<String>> parentMap =
        Map.of(
            "node-a", List.of(),
            "node-b", List.of(),
            "node-c", List.of("node-a", "node-b"),
            "node-d", List.of("node-c"),
            "node-e", List.of("node-c"));

    engine.initExecution(EXECUTION_ID, parentMap);

    // Complete node-a
    StepVerifier.create(engine.nodeCompleted(EXECUTION_ID, "node-a")).expectComplete().verify();

    // Complete node-b
    StepVerifier.create(engine.nodeCompleted(EXECUTION_ID, "node-b"))
        .expectNext("node-c")
        .verifyComplete();

    // Complete node-c - both node-d and node-e should be ready (order may vary)
    StepVerifier.create(engine.nodeCompleted(EXECUTION_ID, "node-c").collectList())
        .assertNext(
            list -> {
              assertEquals(2, list.size());
              org.junit.jupiter.api.Assertions.assertTrue(list.contains("node-d"));
              org.junit.jupiter.api.Assertions.assertTrue(list.contains("node-e"));
            })
        .verifyComplete();
  }
}
