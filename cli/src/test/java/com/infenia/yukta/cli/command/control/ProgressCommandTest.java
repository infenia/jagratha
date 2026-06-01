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
import com.infenia.yukta.model.monitoring.WorkflowProgress;
import com.infenia.yukta.service.control.gateway.ControlBusGateway;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import picocli.CommandLine;

@ExtendWith(MockitoExtension.class)
class ProgressCommandTest {

  @Mock private ControlBusGateway controlBus;
  @Mock private CliFormatter formatter;

  @Test
  void progress_defaultTableOutput_printsProgress() throws Exception {
    final WorkflowProgress progress =
        new WorkflowProgress(
            "exec1", "sess1", "wf1", "IN_PROGRESS", new ArrayList<>(), LocalDateTime.now(), null);
    when(controlBus.getCurrentProgress("exec1")).thenReturn(progress);

    final ProgressCommand cmd = new ProgressCommand(controlBus, formatter);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CommandLine cli = new CommandLine(cmd);
    cli.setOut(new PrintWriter(out, true));

    final int exitCode = cli.execute("exec1");

    assertThat(exitCode).isZero();
    verify(formatter).printTable(List.of(progress.toString()));
  }

  @Test
  void progress_jsonOutput_printsProgressAsJson() throws Exception {
    final WorkflowProgress progress =
        new WorkflowProgress(
            "exec2", "sess2", "wf2", "IN_PROGRESS", new ArrayList<>(), LocalDateTime.now(), null);
    when(controlBus.getCurrentProgress("exec2")).thenReturn(progress);

    final ProgressCommand cmd = new ProgressCommand(controlBus, formatter);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CommandLine cli = new CommandLine(cmd);
    cli.setOut(new PrintWriter(out, true));

    final int exitCode = cli.execute("exec2", "-o", "json");

    assertThat(exitCode).isZero();
    verify(formatter).printJson(progress);
  }

  @Test
  void progress_jsonOutputLongForm_printsProgressAsJson() throws Exception {
    final WorkflowProgress progress =
        new WorkflowProgress(
            "exec3",
            "sess3",
            "wf3",
            "COMPLETED",
            new ArrayList<>(),
            LocalDateTime.now(),
            LocalDateTime.now());
    when(controlBus.getCurrentProgress("exec3")).thenReturn(progress);

    final ProgressCommand cmd = new ProgressCommand(controlBus, formatter);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CommandLine cli = new CommandLine(cmd);
    cli.setOut(new PrintWriter(out, true));

    final int exitCode = cli.execute("exec3", "--output", "json");

    assertThat(exitCode).isZero();
    verify(formatter).printJson(progress);
  }

  @Test
  void progress_tableFormattingException_throwsRuntimeException() throws Exception {
    final WorkflowProgress progress =
        new WorkflowProgress(
            "exec1", "sess1", "wf1", "IN_PROGRESS", new ArrayList<>(), LocalDateTime.now(), null);
    when(controlBus.getCurrentProgress("exec1")).thenReturn(progress);
    doThrow(new RuntimeException("Format error"))
        .when(formatter)
        .printTable(List.of(progress.toString()));

    final ProgressCommand cmd = new ProgressCommand(controlBus, formatter);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CommandLine cli = new CommandLine(cmd);
    cli.setOut(new PrintWriter(out, true));

    final int exitCode = cli.execute("exec1");

    assertThat(exitCode).isNotZero();
  }

  @Test
  void progress_jsonFormattingException_throwsRuntimeException() throws Exception {
    final WorkflowProgress progress =
        new WorkflowProgress(
            "exec1", "sess1", "wf1", "PENDING", new ArrayList<>(), LocalDateTime.now(), null);
    when(controlBus.getCurrentProgress("exec1")).thenReturn(progress);
    doThrow(new RuntimeException("Json error")).when(formatter).printJson(progress);

    final ProgressCommand cmd = new ProgressCommand(controlBus, formatter);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CommandLine cli = new CommandLine(cmd);
    cli.setOut(new PrintWriter(out, true));

    final int exitCode = cli.execute("exec1", "-o", "json");

    assertThat(exitCode).isNotZero();
  }

  @Test
  void progress_completedExecution_showsFinalState() throws Exception {
    final LocalDateTime now = LocalDateTime.now();
    final WorkflowProgress progress =
        new WorkflowProgress(
            "exec4", "sess4", "wf4", "COMPLETED", new ArrayList<>(), now.minusHours(1), now);
    when(controlBus.getCurrentProgress("exec4")).thenReturn(progress);

    final ProgressCommand cmd = new ProgressCommand(controlBus, formatter);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CommandLine cli = new CommandLine(cmd);
    cli.setOut(new PrintWriter(out, true));

    final int exitCode = cli.execute("exec4");

    assertThat(exitCode).isZero();
    verify(formatter).printTable(List.of(progress.toString()));
  }
}
