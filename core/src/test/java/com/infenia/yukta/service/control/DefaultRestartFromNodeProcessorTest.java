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

import com.infenia.yukta.plugin.control.WorkflowDirective;
import com.infenia.yukta.plugin.message.control.ControlCommand;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class DefaultRestartFromNodeProcessorTest {

  private DefaultRestartFromNodeProcessor processor;

  @BeforeEach
  void setUp() {
    processor = new DefaultRestartFromNodeProcessor();
  }

  @Test
  void testCanProcessRestartFromNode() {
    final ControlCommand command =
        new ControlCommand(
            ControlCommand.CommandType.RESTART_FROM_NODE,
            "workflow-1",
            "session-1",
            "node-1",
            Map.of());
    assertThat(processor.canProcess(command)).isTrue();
  }

  @Test
  void testCannotProcessStop() {
    final ControlCommand command =
        new ControlCommand(
            ControlCommand.CommandType.STOP, "workflow-1", "session-1", null, Map.of());
    assertThat(processor.canProcess(command)).isFalse();
  }

  @Test
  void testCannotProcessRestart() {
    final ControlCommand command =
        new ControlCommand(
            ControlCommand.CommandType.RESTART, "workflow-1", "session-1", null, Map.of());
    assertThat(processor.canProcess(command)).isFalse();
  }

  @Test
  void testProcessRestartFromNode() {
    final ControlCommand command =
        new ControlCommand(
            ControlCommand.CommandType.RESTART_FROM_NODE,
            "workflow-1",
            "session-1",
            "node-1",
            Map.of());

    StepVerifier.create(processor.process(command))
        .assertNext(
            directive ->
                assertThat(directive)
                    .isInstanceOf(WorkflowDirective.RestartFromNode.class)
                    .extracting(d -> ((WorkflowDirective.RestartFromNode) d).nodeId())
                    .isEqualTo("node-1"))
        .verifyComplete();
  }

  @Test
  void testProcessRestartFromNodeWithNullNodeId() {
    final ControlCommand command =
        new ControlCommand(
            ControlCommand.CommandType.RESTART_FROM_NODE,
            "workflow-1",
            "session-1",
            null,
            Map.of());

    StepVerifier.create(processor.process(command))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void testProcessRestartFromNodeWithBlankNodeId() {
    final ControlCommand command =
        new ControlCommand(
            ControlCommand.CommandType.RESTART_FROM_NODE,
            "workflow-1",
            "session-1",
            "  ",
            Map.of());

    StepVerifier.create(processor.process(command))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void testGetDefaultPriority() {
    assertThat(processor.getPriority()).isEqualTo(0);
  }
}
