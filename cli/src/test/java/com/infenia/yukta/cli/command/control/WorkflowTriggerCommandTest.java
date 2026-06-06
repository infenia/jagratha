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
package com.infenia.yukta.cli.command.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infenia.yukta.cli.CliFormatter;
import com.infenia.yukta.cli.YuktaDaemonClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class WorkflowTriggerCommandTest {
  @Mock private YuktaDaemonClient mockClient;
  @Mock private ObjectMapper mockMapper;
  @Mock private CliFormatter mockFormatter;
  private WorkflowTriggerCommand command;

  @BeforeEach
  void setUp() {
    command = new WorkflowTriggerCommand(mockClient, mockMapper, mockFormatter);
  }

  @Test
  void constructor_createsInstance() {
    assertThat(command).isNotNull();
  }

  @Test
  void isRunnable() {
    assertThat(command).isInstanceOf(Runnable.class);
  }

  @Test
  void run_printsTableByDefault() throws Exception {
    setPrivateField("sessionId", "session123");
    setPrivateField("workflowId", "workflow456");
    setPrivateField("jsonPayload", "{}");

    when(mockMapper.readValue(eq("{}"), ArgumentMatchers.isA(TypeReference.class)))
        .thenReturn(Map.of());
    when(mockClient.triggerWorkflow("session123", "workflow456", Map.of())).thenReturn("exec789");

    command.run();

    verify(mockFormatter).printTable(List.of("Execution ID: exec789"));
  }

  @Test
  void run_printsJsonWhenFormatIsJson() throws Exception {
    setPrivateField("sessionId", "session123");
    setPrivateField("workflowId", "workflow456");
    setPrivateField("jsonPayload", "{}");
    setPrivateField("outputFormat", "json");

    when(mockMapper.readValue(eq("{}"), ArgumentMatchers.isA(TypeReference.class)))
        .thenReturn(Map.of());
    when(mockClient.triggerWorkflow("session123", "workflow456", Map.of())).thenReturn("exec789");

    command.run();

    verify(mockFormatter).printJson(Map.of("executionId", "exec789"));
  }

  @Test
  void run_throwsRuntimeExceptionOnJsonParseError() throws Exception {
    setPrivateField("sessionId", "session123");
    setPrivateField("workflowId", "workflow456");
    setPrivateField("jsonPayload", "invalid json");

    when(mockMapper.readValue(eq("invalid json"), ArgumentMatchers.isA(TypeReference.class)))
        .thenThrow(new RuntimeException("Invalid JSON"));

    assertThatThrownBy(command::run).isInstanceOf(RuntimeException.class);
  }

  @Test
  void run_throwsRuntimeExceptionOnClientError() throws Exception {
    setPrivateField("sessionId", "session123");
    setPrivateField("workflowId", "workflow456");
    setPrivateField("jsonPayload", "{}");

    when(mockMapper.readValue(eq("{}"), ArgumentMatchers.isA(TypeReference.class)))
        .thenReturn(Map.of());
    when(mockClient.triggerWorkflow("session123", "workflow456", Map.of()))
        .thenThrow(new RuntimeException("Client error"));

    assertThatThrownBy(command::run).isInstanceOf(RuntimeException.class);
  }

  private void setPrivateField(String fieldName, String value) {
    try {
      final var field = WorkflowTriggerCommand.class.getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(command, value);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }
}
