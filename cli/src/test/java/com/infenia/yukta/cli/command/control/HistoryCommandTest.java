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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infenia.yukta.cli.CliFormatter;
import com.infenia.yukta.model.monitoring.WorkflowExecutionSummary;
import com.infenia.yukta.service.control.gateway.ControlBusGateway;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import picocli.CommandLine;

@ExtendWith(MockitoExtension.class)
class HistoryCommandTest {

  @Mock private ControlBusGateway controlBus;
  @Mock private CliFormatter formatter;

  @Test
  void history_defaultTableOutput_printsHistory() throws Exception {
    final LocalDateTime now = LocalDateTime.now();
    final WorkflowExecutionSummary summary =
        new WorkflowExecutionSummary("exec1", "wf1", "COMPLETED", now, now);
    final List<WorkflowExecutionSummary> history = List.of(summary);
    when(controlBus.getHistory("sess1")).thenReturn(history);

    final HistoryCommand cmd = new HistoryCommand(controlBus, formatter);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CommandLine cli = new CommandLine(cmd);
    cli.setOut(new PrintWriter(out, true));

    final int exitCode = cli.execute("sess1");

    assertThat(exitCode).isZero();
    verify(formatter).printTable(List.of(summary.toString()));
  }

  @Test
  void history_jsonOutput_printsHistoryAsJson() throws Exception {
    final LocalDateTime now = LocalDateTime.now();
    final WorkflowExecutionSummary summary =
        new WorkflowExecutionSummary("exec2", "wf2", "COMPLETED", now, now);
    final List<WorkflowExecutionSummary> history = List.of(summary);
    when(controlBus.getHistory("sess2")).thenReturn(history);

    final HistoryCommand cmd = new HistoryCommand(controlBus, formatter);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CommandLine cli = new CommandLine(cmd);
    cli.setOut(new PrintWriter(out, true));

    final int exitCode = cli.execute("sess2", "-o", "json");

    assertThat(exitCode).isZero();
    verify(formatter).printJson(history);
  }

  @Test
  void history_jsonOutputLongForm_printsHistoryAsJson() throws Exception {
    final LocalDateTime now = LocalDateTime.now();
    final List<WorkflowExecutionSummary> history = List.of();
    when(controlBus.getHistory("sess3")).thenReturn(history);

    final HistoryCommand cmd = new HistoryCommand(controlBus, formatter);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CommandLine cli = new CommandLine(cmd);
    cli.setOut(new PrintWriter(out, true));

    final int exitCode = cli.execute("sess3", "--output", "json");

    assertThat(exitCode).isZero();
    verify(formatter).printJson(history);
  }

  @Test
  void history_tableFormattingException_throwsRuntimeException() throws Exception {
    final LocalDateTime now = LocalDateTime.now();
    final WorkflowExecutionSummary summary =
        new WorkflowExecutionSummary("exec1", "wf1", "COMPLETED", now, now);
    final List<WorkflowExecutionSummary> history = List.of(summary);
    when(controlBus.getHistory("sess1")).thenReturn(history);
    doThrow(new RuntimeException("Format error"))
        .when(formatter)
        .printTable(List.of(summary.toString()));

    final HistoryCommand cmd = new HistoryCommand(controlBus, formatter);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CommandLine cli = new CommandLine(cmd);
    cli.setOut(new PrintWriter(out, true));

    final int exitCode = cli.execute("sess1");

    assertThat(exitCode).isNotZero();
  }

  @Test
  void history_jsonFormattingException_throwsRuntimeException() throws Exception {
    final LocalDateTime now = LocalDateTime.now();
    final WorkflowExecutionSummary summary =
        new WorkflowExecutionSummary("exec1", "wf1", "FAILED", now, now);
    final List<WorkflowExecutionSummary> history = List.of(summary);
    when(controlBus.getHistory("sess1")).thenReturn(history);
    doThrow(new RuntimeException("Json error")).when(formatter).printJson(history);

    final HistoryCommand cmd = new HistoryCommand(controlBus, formatter);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CommandLine cli = new CommandLine(cmd);
    cli.setOut(new PrintWriter(out, true));

    final int exitCode = cli.execute("sess1", "-o", "json");

    assertThat(exitCode).isNotZero();
  }

  @Test
  void history_emptyHistory_printsEmptyList() throws Exception {
    final List<WorkflowExecutionSummary> history = List.of();
    when(controlBus.getHistory("sess4")).thenReturn(history);

    final HistoryCommand cmd = new HistoryCommand(controlBus, formatter);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CommandLine cli = new CommandLine(cmd);
    cli.setOut(new PrintWriter(out, true));

    final int exitCode = cli.execute("sess4");

    assertThat(exitCode).isZero();
    verify(formatter).printTable(List.of());
  }

  @Test
  void history_multipleExecutions_printsAllSummaries() throws Exception {
    final LocalDateTime now = LocalDateTime.now();
    final WorkflowExecutionSummary summary1 =
        new WorkflowExecutionSummary("exec1", "wf1", "COMPLETED", now, now);
    final WorkflowExecutionSummary summary2 =
        new WorkflowExecutionSummary("exec2", "wf2", "FAILED", now, now.plusHours(1));
    final WorkflowExecutionSummary summary3 =
        new WorkflowExecutionSummary("exec3", "wf3", "IN_PROGRESS", now, null);
    final List<WorkflowExecutionSummary> history = List.of(summary1, summary2, summary3);
    when(controlBus.getHistory("sess5")).thenReturn(history);

    final HistoryCommand cmd = new HistoryCommand(controlBus, formatter);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CommandLine cli = new CommandLine(cmd);
    cli.setOut(new PrintWriter(out, true));

    final int exitCode = cli.execute("sess5");

    assertThat(exitCode).isZero();
    verify(formatter)
        .printTable(List.of(summary1.toString(), summary2.toString(), summary3.toString()));
  }
}
