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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Sinks;

class ExecutionControlRegistryTest {

  private ExecutionControlRegistry registry;

  @BeforeEach
  void setUp() {
    registry = new ExecutionControlRegistry();
  }

  @Test
  void testRegisterAndFindByExecutionId() {
    final String executionId = "exec-1";
    final String sessionId = "session-1";
    final String workflowId = "workflow-1";
    final Sinks.One<Void> stopSink = Sinks.one();
    final ExecutionControl control =
        new ExecutionControl(sessionId, workflowId, executionId, null, Map.of(), stopSink);

    registry.register(control);

    final var found = registry.findByExecutionId(executionId);
    assertThat(found).isPresent().contains(control);
  }

  @Test
  void testFindByExecutionIdNotFound() {
    final var found = registry.findByExecutionId("non-existent");
    assertThat(found).isEmpty();
  }

  @Test
  void testFindActiveByWorkflow() {
    final String executionId = "exec-1";
    final String sessionId = "session-1";
    final String workflowId = "workflow-1";
    final Sinks.One<Void> stopSink = Sinks.one();
    final ExecutionControl control =
        new ExecutionControl(sessionId, workflowId, executionId, null, Map.of(), stopSink);

    registry.register(control);

    final var found = registry.findActiveByWorkflow(sessionId, workflowId);
    assertThat(found).isPresent().contains(control);
  }

  @Test
  void testFindActiveByWorkflowNotFound() {
    final var found = registry.findActiveByWorkflow("session-1", "workflow-1");
    assertThat(found).isEmpty();
  }

  @Test
  void testUnregister() {
    final String executionId = "exec-1";
    final String sessionId = "session-1";
    final String workflowId = "workflow-1";
    final Sinks.One<Void> stopSink = Sinks.one();
    final ExecutionControl control =
        new ExecutionControl(sessionId, workflowId, executionId, null, Map.of(), stopSink);

    registry.register(control);
    registry.unregister(executionId);

    final var found = registry.findByExecutionId(executionId);
    assertThat(found).isEmpty();
  }

  @Test
  void testMultipleExecutions() {
    final Sinks.One<Void> sink1 = Sinks.one();
    final Sinks.One<Void> sink2 = Sinks.one();
    final ExecutionControl control1 =
        new ExecutionControl("session-1", "workflow-1", "exec-1", null, Map.of(), sink1);
    final ExecutionControl control2 =
        new ExecutionControl("session-1", "workflow-1", "exec-2", null, Map.of(), sink2);

    registry.register(control1);
    registry.register(control2);

    assertThat(registry.findByExecutionId("exec-1")).isPresent();
    assertThat(registry.findByExecutionId("exec-2")).isPresent();
  }

  @Test
  void testFindActiveByWorkflowMultipleSessions() {
    final Sinks.One<Void> sink1 = Sinks.one();
    final Sinks.One<Void> sink2 = Sinks.one();
    final ExecutionControl control1 =
        new ExecutionControl("session-1", "workflow-1", "exec-1", null, Map.of(), sink1);
    final ExecutionControl control2 =
        new ExecutionControl("session-2", "workflow-1", "exec-2", null, Map.of(), sink2);

    registry.register(control1);
    registry.register(control2);

    final var found1 = registry.findActiveByWorkflow("session-1", "workflow-1");
    final var found2 = registry.findActiveByWorkflow("session-2", "workflow-1");

    assertThat(found1).isPresent().contains(control1);
    assertThat(found2).isPresent().contains(control2);
  }
}
