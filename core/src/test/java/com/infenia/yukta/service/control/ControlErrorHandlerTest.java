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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infenia.yukta.model.workflow.PreparedWorkflow;
import com.infenia.yukta.model.workflow.WorkflowDefinition;
import com.infenia.yukta.plugin.gateway.ControlBusGateway;
import com.infenia.yukta.plugin.message.DefaultMessage;
import com.infenia.yukta.plugin.message.Message;
import com.infenia.yukta.plugin.message.control.ControlError;
import com.infenia.yukta.plugin.retry.RetryPolicy;
import com.infenia.yukta.service.ExecutionRegistry;
import com.infenia.yukta.service.TaskTrackerService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

/** Unit tests for ControlErrorHandler. */
@ExtendWith(MockitoExtension.class)
class ControlErrorHandlerTest {

  @Mock private ExecutionRegistry executionRegistry;
  @Mock private TaskTrackerService taskTrackerService;
  @Mock private ControlBusGateway controlBusGateway;

  private ControlErrorHandler handler;
  private static final String EXECUTION_ID = "exec-001";
  private static final String NODE_ID = "node-001";

  @BeforeEach
  void setup() {
    handler = new ControlErrorHandler(executionRegistry, taskTrackerService, controlBusGateway);
  }

  @Test
  void testCanHandle() {
    final ControlError error = new ControlError(NODE_ID, EXECUTION_ID, "error", "detail");
    assertTrue(handler.canHandle(error));
  }

  @Test
  void testCannotHandleOtherEvent() {
    org.junit.jupiter.api.Assertions.assertFalse(handler.canHandle("some-other-payload"));
  }

  @Test
  void testHandleWithRetryRemaining() {
    final ControlError error =
        new ControlError(NODE_ID, EXECUTION_ID, "timeout", "Timeout after 30s");
    final Message<?> message = createMessage(error);
    final PreparedWorkflow workflow = createWorkflowWithRetry();

    when(executionRegistry.getPreparedWorkflow(EXECUTION_ID)).thenReturn(Optional.of(workflow));
    when(executionRegistry.getAndIncrementRetry(EXECUTION_ID, NODE_ID)).thenReturn(1);
    when(controlBusGateway.emit(any())).thenReturn(Mono.empty());

    handler.handle(NODE_ID, message, error);

    verify(executionRegistry).getPreparedWorkflow(EXECUTION_ID);
    verify(executionRegistry).getAndIncrementRetry(EXECUTION_ID, NODE_ID);
    verify(controlBusGateway).emit(any());
  }

  @Test
  void testHandleWithRetryExhausted() {
    final ControlError error =
        new ControlError(NODE_ID, EXECUTION_ID, "timeout", "Timeout after 30s");
    final Message<?> message = createMessage(error);
    final PreparedWorkflow workflow = createWorkflowWithRetry();

    when(executionRegistry.getPreparedWorkflow(EXECUTION_ID)).thenReturn(Optional.of(workflow));
    when(executionRegistry.getAndIncrementRetry(EXECUTION_ID, NODE_ID))
        .thenReturn(4); // Exceeds max
    when(controlBusGateway.emit(any())).thenReturn(Mono.empty());

    handler.handle(NODE_ID, message, error);

    verify(executionRegistry).getPreparedWorkflow(EXECUTION_ID);
    verify(executionRegistry).getAndIncrementRetry(EXECUTION_ID, NODE_ID);
    verify(taskTrackerService)
        .emitTaskStatusEvent(
            EXECUTION_ID, NODE_ID, "default", "FAILURE", Map.of("error", "Timeout after 30s"));
    verify(executionRegistry).resetRetry(EXECUTION_ID, NODE_ID);
  }

  @Test
  void testHandleNoRetryPolicy() {
    final ControlError error = new ControlError(NODE_ID, EXECUTION_ID, "error", "detail");
    final Message<?> message = createMessage(error);
    final PreparedWorkflow workflow = createWorkflowWithoutRetry();

    when(executionRegistry.getPreparedWorkflow(EXECUTION_ID)).thenReturn(Optional.of(workflow));
    when(executionRegistry.getAndIncrementRetry(EXECUTION_ID, NODE_ID)).thenReturn(1);
    when(controlBusGateway.emit(any())).thenReturn(Mono.empty());

    handler.handle(NODE_ID, message, error);

    verify(taskTrackerService)
        .emitTaskStatusEvent(
            EXECUTION_ID, NODE_ID, "default", "FAILURE", Map.of("error", "detail"));
  }

  @Test
  void testHandleWorkflowNotFound() {
    final ControlError error = new ControlError(NODE_ID, EXECUTION_ID, "error", "detail");
    final Message<?> message = createMessage(error);

    when(executionRegistry.getPreparedWorkflow(EXECUTION_ID)).thenReturn(Optional.empty());

    // Should not throw, just log and return
    handler.handle(NODE_ID, message, error);

    verify(executionRegistry).getPreparedWorkflow(EXECUTION_ID);
  }

  private PreparedWorkflow createWorkflowWithRetry() {
    final WorkflowDefinition def =
        new WorkflowDefinition(
            "test",
            List.of(
                new WorkflowDefinition.Node(
                    NODE_ID, "plugin-type", Map.of(), RetryPolicy.fixed(3, 100))),
            List.of());
    return new PreparedWorkflow(
        def, Map.of(), Map.of(), Map.of(), List.of(), (id, payload) -> null);
  }

  private PreparedWorkflow createWorkflowWithoutRetry() {
    final WorkflowDefinition def =
        new WorkflowDefinition(
            "test",
            List.of(new WorkflowDefinition.Node(NODE_ID, "plugin-type", Map.of())),
            List.of());
    return new PreparedWorkflow(
        def, Map.of(), Map.of(), Map.of(), List.of(), (id, payload) -> null);
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
