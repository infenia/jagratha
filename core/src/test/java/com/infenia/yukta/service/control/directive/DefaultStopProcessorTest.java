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
package com.infenia.yukta.service.control.directive;

import static org.assertj.core.api.Assertions.assertThat;

import com.infenia.yukta.plugin.control.WorkflowDirective;
import com.infenia.yukta.plugin.message.control.ControlCommand;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class DefaultStopProcessorTest {

  private DefaultStopProcessor processor;

  @BeforeEach
  void setUp() {
    processor = new DefaultStopProcessor();
  }

  @Test
  void testCanProcessStop() {
    final ControlCommand command =
        new ControlCommand(
            ControlCommand.CommandType.STOP, "workflow-1", "session-1", null, Map.of());
    assertThat(processor.canProcess(command)).isTrue();
  }

  @Test
  void testCannotProcessRestart() {
    final ControlCommand command =
        new ControlCommand(
            ControlCommand.CommandType.RESTART, "workflow-1", "session-1", null, Map.of());
    assertThat(processor.canProcess(command)).isFalse();
  }

  @Test
  void testCannotProcessRestartFromNode() {
    final ControlCommand command =
        new ControlCommand(
            ControlCommand.CommandType.RESTART_FROM_NODE,
            "workflow-1",
            "session-1",
            "node-1",
            Map.of());
    assertThat(processor.canProcess(command)).isFalse();
  }

  @Test
  void testProcessStop() {
    final Map<String, Object> params = Map.of("reason", "User requested");
    final ControlCommand command =
        new ControlCommand(
            ControlCommand.CommandType.STOP, "workflow-1", "session-1", null, params);

    StepVerifier.create(processor.process(command))
        .assertNext(
            directive ->
                assertThat(directive)
                    .isInstanceOf(WorkflowDirective.Stop.class)
                    .extracting(d -> ((WorkflowDirective.Stop) d).reason())
                    .isEqualTo("User requested"))
        .verifyComplete();
  }

  @Test
  void testProcessStopWithoutReason() {
    final ControlCommand command =
        new ControlCommand(
            ControlCommand.CommandType.STOP, "workflow-1", "session-1", null, Map.of());

    StepVerifier.create(processor.process(command))
        .assertNext(
            directive ->
                assertThat(directive)
                    .isInstanceOf(WorkflowDirective.Stop.class)
                    .extracting(d -> ((WorkflowDirective.Stop) d).reason())
                    .isEqualTo("User requested stop"))
        .verifyComplete();
  }

  @Test
  void testGetDefaultPriority() {
    assertThat(processor.getPriority()).isEqualTo(0);
  }
}
