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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.infenia.yukta.model.workflow.PreparedWorkflow;
import com.infenia.yukta.model.workflow.WorkflowDefinition;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/** Unit tests for ExecutionRegistry. */
class ExecutionRegistryTest {

  private ExecutionRegistry registry;
  private PreparedWorkflow mockWorkflow;
  private static final String EXECUTION_ID = "exec-001";
  private static final String NODE_ID = "node-001";

  @BeforeEach
  void setup() {
    registry = new ExecutionRegistry(Duration.ofSeconds(10));
    mockWorkflow =
        new PreparedWorkflow(
            new WorkflowDefinition("test", List.of(), List.of()),
            Map.of(),
            Map.of(),
            Map.of(),
            List.of(),
            (id, payload) -> null);
  }

  @Test
  void testRegisterAndGetPreparedWorkflow() {
    registry.register(EXECUTION_ID, mockWorkflow);

    assertTrue(registry.getPreparedWorkflow(EXECUTION_ID).isPresent());
    assertEquals(mockWorkflow, registry.getPreparedWorkflow(EXECUTION_ID).get());
  }

  @Test
  void testGetPreparedWorkflowNotFound() {
    assertFalse(registry.getPreparedWorkflow("unknown-id").isPresent());
  }

  @Test
  void testGetCancelSignal() {
    registry.register(EXECUTION_ID, mockWorkflow);

    StepVerifier.create(registry.getCancelSignal(EXECUTION_ID).timeout(Duration.ofMillis(100)))
        .expectError()
        .verify();
  }

  @Test
  void testCancelExecution() {
    registry.register(EXECUTION_ID, mockWorkflow);

    StepVerifier.create(registry.cancel(EXECUTION_ID)).expectNext(true).verifyComplete();
  }

  @Test
  void testCancelUnknownExecution() {
    StepVerifier.create(registry.cancel("unknown-id")).expectNext(false).verifyComplete();
  }

  @Test
  void testGetAndIncrementRetry() {
    registry.register(EXECUTION_ID, mockWorkflow);

    assertEquals(1, registry.getAndIncrementRetry(EXECUTION_ID, NODE_ID));
    assertEquals(2, registry.getAndIncrementRetry(EXECUTION_ID, NODE_ID));
    assertEquals(3, registry.getAndIncrementRetry(EXECUTION_ID, NODE_ID));
  }

  @Test
  void testResetRetry() {
    registry.register(EXECUTION_ID, mockWorkflow);

    registry.getAndIncrementRetry(EXECUTION_ID, NODE_ID);
    registry.getAndIncrementRetry(EXECUTION_ID, NODE_ID);

    registry.resetRetry(EXECUTION_ID, NODE_ID);

    assertEquals(1, registry.getAndIncrementRetry(EXECUTION_ID, NODE_ID));
  }

  @Test
  void testUnregister() {
    registry.register(EXECUTION_ID, mockWorkflow);
    assertTrue(registry.getPreparedWorkflow(EXECUTION_ID).isPresent());

    registry.unregister(EXECUTION_ID);
    assertFalse(registry.getPreparedWorkflow(EXECUTION_ID).isPresent());
  }

  @Test
  void testMultipleExecutions() {
    final String execId1 = "exec-001";
    final String execId2 = "exec-002";

    registry.register(execId1, mockWorkflow);
    registry.register(execId2, mockWorkflow);

    assertTrue(registry.getPreparedWorkflow(execId1).isPresent());
    assertTrue(registry.getPreparedWorkflow(execId2).isPresent());

    registry.unregister(execId1);
    assertFalse(registry.getPreparedWorkflow(execId1).isPresent());
    assertTrue(registry.getPreparedWorkflow(execId2).isPresent());
  }
}
