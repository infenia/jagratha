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
package com.infenia.yukta.service.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.infenia.yukta.plugin.core.NodeState;
import com.infenia.yukta.plugin.message.DefaultMessage;
import com.infenia.yukta.plugin.message.Message;
import com.infenia.yukta.plugin.message.control.NodeStateEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for NodeStateEventHandler. */
class NodeStateEventHandlerTest {

  private NodeStateEventHandler handler;
  private static final String EXECUTION_ID = "exec-001";
  private static final String NODE_ID = "node-001";

  @BeforeEach
  void setup() {
    handler = new NodeStateEventHandler();
  }

  @Test
  void testCanHandle() {
    final NodeStateEvent event =
        new NodeStateEvent(
            NODE_ID, EXECUTION_ID, NodeState.PENDING, NodeState.READY, Instant.now());
    assertTrue(handler.canHandle(event));
  }

  @Test
  void testCannotHandleOtherEvent() {
    assertFalse(handler.canHandle("some-other-payload"));
  }

  @Test
  void testHandle() {
    final NodeStateEvent event =
        new NodeStateEvent(
            NODE_ID, EXECUTION_ID, NodeState.PENDING, NodeState.READY, Instant.now());
    final Message<?> message = createMessage(event);

    handler.handle(NODE_ID, message, event);

    assertEquals(NodeState.READY, handler.getNodeState(EXECUTION_ID, NODE_ID));
  }

  @Test
  void testHandleMultipleTransitions() {
    final NodeStateEvent event1 =
        new NodeStateEvent(
            NODE_ID, EXECUTION_ID, NodeState.PENDING, NodeState.READY, Instant.now());
    final NodeStateEvent event2 =
        new NodeStateEvent(
            NODE_ID, EXECUTION_ID, NodeState.READY, NodeState.RUNNING, Instant.now());
    final NodeStateEvent event3 =
        new NodeStateEvent(
            NODE_ID, EXECUTION_ID, NodeState.RUNNING, NodeState.COMPLETED, Instant.now());

    final Message<?> msg1 = createMessage(event1);
    final Message<?> msg2 = createMessage(event2);
    final Message<?> msg3 = createMessage(event3);

    handler.handle(NODE_ID, msg1, event1);
    assertEquals(NodeState.READY, handler.getNodeState(EXECUTION_ID, NODE_ID));

    handler.handle(NODE_ID, msg2, event2);
    assertEquals(NodeState.RUNNING, handler.getNodeState(EXECUTION_ID, NODE_ID));

    handler.handle(NODE_ID, msg3, event3);
    assertEquals(NodeState.COMPLETED, handler.getNodeState(EXECUTION_ID, NODE_ID));
  }

  @Test
  void testGetNodeStateNotFound() {
    assertNull(handler.getNodeState(EXECUTION_ID, "unknown-node"));
  }

  @Test
  void testRemoveNode() {
    final NodeStateEvent event =
        new NodeStateEvent(
            NODE_ID, EXECUTION_ID, NodeState.PENDING, NodeState.READY, Instant.now());
    final Message<?> message = createMessage(event);

    handler.handle(NODE_ID, message, event);
    assertEquals(NodeState.READY, handler.getNodeState(EXECUTION_ID, NODE_ID));

    handler.removeNode(NODE_ID);
    assertNull(handler.getNodeState(EXECUTION_ID, NODE_ID));
  }

  @Test
  void testMultipleExecutions() {
    final String execId1 = "exec-001";
    final String execId2 = "exec-002";
    final String nodeId1 = "node-001";
    final String nodeId2 = "node-002";

    final NodeStateEvent event1 =
        new NodeStateEvent(nodeId1, execId1, NodeState.PENDING, NodeState.RUNNING, Instant.now());
    final NodeStateEvent event2 =
        new NodeStateEvent(nodeId2, execId2, NodeState.PENDING, NodeState.COMPLETED, Instant.now());

    handler.handle(nodeId1, createMessage(event1), event1);
    handler.handle(nodeId2, createMessage(event2), event2);

    assertEquals(NodeState.RUNNING, handler.getNodeState(execId1, nodeId1));
    assertEquals(NodeState.COMPLETED, handler.getNodeState(execId2, nodeId2));
  }

  private Message<?> createMessage(final Object payload) {
    return new DefaultMessage<>(
        UUID.randomUUID().toString(),
        null,
        null,
        null,
        0,
        null,
        java.util.Map.of(),
        java.util.List.of(),
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

  // Helper method since assertFalse is not imported
  private void assertFalse(final boolean value) {
    org.junit.jupiter.api.Assertions.assertFalse(value);
  }

  private void assertTrue(final boolean value) {
    org.junit.jupiter.api.Assertions.assertTrue(value);
  }
}
