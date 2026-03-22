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
package com.infenia.yukta.service.store;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.infenia.yukta.plugin.message.DefaultMessage;
import com.infenia.yukta.plugin.message.Message;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/** Unit tests for InMemoryCheckpointStore. */
class InMemoryCheckpointStoreTest {

  private InMemoryCheckpointStore store;
  private static final String EXECUTION_ID = "exec-001";
  private static final String NODE_ID = "node-001";

  @BeforeEach
  void setup() {
    store = new InMemoryCheckpointStore(java.time.Duration.ofHours(1));
  }

  @Test
  void testSaveCheckpoint() {
    final Message<?> msg = createMessage("test-payload");

    StepVerifier.create(store.saveCheckpoint(EXECUTION_ID, NODE_ID, msg)).expectComplete().verify();
  }

  @Test
  void testLoadCheckpoint() {
    final Message<?> msg = createMessage("test-payload");

    StepVerifier.create(store.saveCheckpoint(EXECUTION_ID, NODE_ID, msg)).expectComplete().verify();

    StepVerifier.create(store.loadCheckpoint(EXECUTION_ID, NODE_ID))
        .assertNext(
            loaded -> {
              assertEquals("test-payload", loaded.getPayload());
              assertEquals(NODE_ID, loaded.getSourceNodeId());
            })
        .verifyComplete();
  }

  @Test
  void testLoadCheckpointNotFound() {
    StepVerifier.create(store.loadCheckpoint(EXECUTION_ID, "unknown-node"))
        .expectComplete()
        .verify();
  }

  @Test
  void testHasCheckpoint() {
    final Message<?> msg = createMessage("test-payload");

    StepVerifier.create(store.saveCheckpoint(EXECUTION_ID, NODE_ID, msg)).expectComplete().verify();

    StepVerifier.create(store.hasCheckpoint(EXECUTION_ID, NODE_ID))
        .expectNext(true)
        .verifyComplete();
  }

  @Test
  void testHasCheckpointNotFound() {
    StepVerifier.create(store.hasCheckpoint(EXECUTION_ID, "unknown-node"))
        .expectNext(false)
        .verifyComplete();
  }

  @Test
  void testClearCheckpoint() {
    final Message<?> msg = createMessage("test-payload");

    StepVerifier.create(store.saveCheckpoint(EXECUTION_ID, NODE_ID, msg)).expectComplete().verify();

    StepVerifier.create(store.hasCheckpoint(EXECUTION_ID, NODE_ID))
        .expectNext(true)
        .verifyComplete();

    StepVerifier.create(store.clearCheckpoint(EXECUTION_ID, NODE_ID)).expectComplete().verify();

    StepVerifier.create(store.hasCheckpoint(EXECUTION_ID, NODE_ID))
        .expectNext(false)
        .verifyComplete();
  }

  @Test
  void testClearAllCheckpoints() {
    final Message<?> msg1 = createMessage("payload1");
    final Message<?> msg2 = createMessage("payload2");
    final Message<?> msg3 = createMessage("payload3");

    StepVerifier.create(store.saveCheckpoint(EXECUTION_ID, "node-1", msg1))
        .expectComplete()
        .verify();
    StepVerifier.create(store.saveCheckpoint(EXECUTION_ID, "node-2", msg2))
        .expectComplete()
        .verify();
    StepVerifier.create(store.saveCheckpoint(EXECUTION_ID, "node-3", msg3))
        .expectComplete()
        .verify();

    StepVerifier.create(store.hasCheckpoint(EXECUTION_ID, "node-1"))
        .expectNext(true)
        .verifyComplete();

    StepVerifier.create(store.clearAllCheckpoints(EXECUTION_ID)).expectComplete().verify();

    StepVerifier.create(store.hasCheckpoint(EXECUTION_ID, "node-1"))
        .expectNext(false)
        .verifyComplete();
    StepVerifier.create(store.hasCheckpoint(EXECUTION_ID, "node-2"))
        .expectNext(false)
        .verifyComplete();
    StepVerifier.create(store.hasCheckpoint(EXECUTION_ID, "node-3"))
        .expectNext(false)
        .verifyComplete();
  }

  @Test
  void testMultipleExecutionIsolation() {
    final String execId1 = "exec-001";
    final String execId2 = "exec-002";
    final Message<?> msg1 = createMessage("payload1");
    final Message<?> msg2 = createMessage("payload2");

    StepVerifier.create(store.saveCheckpoint(execId1, NODE_ID, msg1)).expectComplete().verify();
    StepVerifier.create(store.saveCheckpoint(execId2, NODE_ID, msg2)).expectComplete().verify();

    StepVerifier.create(store.loadCheckpoint(execId1, NODE_ID))
        .assertNext(msg -> assertEquals("payload1", msg.getPayload()))
        .verifyComplete();

    StepVerifier.create(store.loadCheckpoint(execId2, NODE_ID))
        .assertNext(msg -> assertEquals("payload2", msg.getPayload()))
        .verifyComplete();
  }

  @Test
  void testOverwriteCheckpoint() {
    final Message<?> msg1 = createMessage("first-payload");
    final Message<?> msg2 = createMessage("second-payload");

    StepVerifier.create(store.saveCheckpoint(EXECUTION_ID, NODE_ID, msg1))
        .expectComplete()
        .verify();

    StepVerifier.create(store.loadCheckpoint(EXECUTION_ID, NODE_ID))
        .assertNext(msg -> assertEquals("first-payload", msg.getPayload()))
        .verifyComplete();

    StepVerifier.create(store.saveCheckpoint(EXECUTION_ID, NODE_ID, msg2))
        .expectComplete()
        .verify();

    StepVerifier.create(store.loadCheckpoint(EXECUTION_ID, NODE_ID))
        .assertNext(msg -> assertEquals("second-payload", msg.getPayload()))
        .verifyComplete();
  }

  @Test
  void testMultipleNodesPerExecution() {
    final Message<?> msg1 = createMessage("node1-payload");
    final Message<?> msg2 = createMessage("node2-payload");
    final Message<?> msg3 = createMessage("node3-payload");

    StepVerifier.create(store.saveCheckpoint(EXECUTION_ID, "node-1", msg1))
        .expectComplete()
        .verify();
    StepVerifier.create(store.saveCheckpoint(EXECUTION_ID, "node-2", msg2))
        .expectComplete()
        .verify();
    StepVerifier.create(store.saveCheckpoint(EXECUTION_ID, "node-3", msg3))
        .expectComplete()
        .verify();

    StepVerifier.create(store.loadCheckpoint(EXECUTION_ID, "node-1"))
        .assertNext(msg -> assertEquals("node1-payload", msg.getPayload()))
        .verifyComplete();
    StepVerifier.create(store.loadCheckpoint(EXECUTION_ID, "node-2"))
        .assertNext(msg -> assertEquals("node2-payload", msg.getPayload()))
        .verifyComplete();
    StepVerifier.create(store.loadCheckpoint(EXECUTION_ID, "node-3"))
        .assertNext(msg -> assertEquals("node3-payload", msg.getPayload()))
        .verifyComplete();
  }

  @Test
  void testClearSingleCheckpointPreservesOthers() {
    final Message<?> msg1 = createMessage("payload1");
    final Message<?> msg2 = createMessage("payload2");

    StepVerifier.create(store.saveCheckpoint(EXECUTION_ID, "node-1", msg1))
        .expectComplete()
        .verify();
    StepVerifier.create(store.saveCheckpoint(EXECUTION_ID, "node-2", msg2))
        .expectComplete()
        .verify();

    StepVerifier.create(store.clearCheckpoint(EXECUTION_ID, "node-1")).expectComplete().verify();

    StepVerifier.create(store.hasCheckpoint(EXECUTION_ID, "node-1"))
        .expectNext(false)
        .verifyComplete();

    StepVerifier.create(store.hasCheckpoint(EXECUTION_ID, "node-2"))
        .expectNext(true)
        .verifyComplete();
  }

  private Message<?> createMessage(final Object payload) {
    return new DefaultMessage<>(
        UUID.randomUUID().toString(),
        null,
        null,
        null,
        0,
        null,
        Map.of(),
        List.of(),
        null,
        0,
        0,
        0,
        true,
        null,
        null,
        null,
        0,
        payload,
        Instant.now(),
        null,
        NODE_ID);
  }
}
