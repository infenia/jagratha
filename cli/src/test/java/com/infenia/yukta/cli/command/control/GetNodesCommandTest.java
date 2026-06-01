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
import com.infenia.yukta.service.control.gateway.ControlBusGateway;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import picocli.CommandLine;

@ExtendWith(MockitoExtension.class)
class GetNodesCommandTest {

  @Mock private ControlBusGateway controlBus;
  @Mock private CliFormatter formatter;

  @Test
  void getNodes_defaultTableOutput_printsNodeList() {
    final List<String> nodes = List.of("n1", "n2");
    when(controlBus.getActiveNodes("wf1")).thenReturn(nodes);

    final GetNodesCommand cmd = new GetNodesCommand(controlBus, formatter);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CommandLine cli = new CommandLine(cmd);
    cli.setOut(new PrintWriter(out, true));

    final int exitCode = cli.execute("wf1");

    assertThat(exitCode).isZero();
    verify(formatter).printTable(nodes);
  }

  @Test
  void getNodes_jsonOutput_printsNodesAsJson() throws Exception {
    final List<String> nodes = List.of("n1", "n2");
    when(controlBus.getActiveNodes("wf1")).thenReturn(nodes);

    final GetNodesCommand cmd = new GetNodesCommand(controlBus, formatter);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CommandLine cli = new CommandLine(cmd);
    cli.setOut(new PrintWriter(out, true));

    final int exitCode = cli.execute("wf1", "-o", "json");

    assertThat(exitCode).isZero();
    verify(formatter).printJson(nodes);
  }

  @Test
  void getNodes_jsonOutputLongForm_printsNodesAsJson() throws Exception {
    final List<String> nodes = List.of("nodeA", "nodeB", "nodeC");
    when(controlBus.getActiveNodes("wf2")).thenReturn(nodes);

    final GetNodesCommand cmd = new GetNodesCommand(controlBus, formatter);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CommandLine cli = new CommandLine(cmd);
    cli.setOut(new PrintWriter(out, true));

    final int exitCode = cli.execute("wf2", "--output", "json");

    assertThat(exitCode).isZero();
    verify(formatter).printJson(nodes);
  }

  @Test
  void getNodes_tableFormattingException_throwsRuntimeException() throws Exception {
    final List<String> nodes = List.of("n1");
    when(controlBus.getActiveNodes("wf1")).thenReturn(nodes);
    doThrow(new RuntimeException("Format error")).when(formatter).printTable(nodes);

    final GetNodesCommand cmd = new GetNodesCommand(controlBus, formatter);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CommandLine cli = new CommandLine(cmd);
    cli.setOut(new PrintWriter(out, true));

    final int exitCode = cli.execute("wf1");

    assertThat(exitCode).isNotZero();
  }

  @Test
  void getNodes_emptyNodeList_printsEmptyList() throws Exception {
    final List<String> nodes = List.of();
    when(controlBus.getActiveNodes("wf3")).thenReturn(nodes);

    final GetNodesCommand cmd = new GetNodesCommand(controlBus, formatter);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CommandLine cli = new CommandLine(cmd);
    cli.setOut(new PrintWriter(out, true));

    final int exitCode = cli.execute("wf3");

    assertThat(exitCode).isZero();
    verify(formatter).printTable(nodes);
  }

  @Test
  void getNodes_singleNode_printsSingleNode() throws Exception {
    final List<String> nodes = List.of("singleNode");
    when(controlBus.getActiveNodes("wf4")).thenReturn(nodes);

    final GetNodesCommand cmd = new GetNodesCommand(controlBus, formatter);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CommandLine cli = new CommandLine(cmd);
    cli.setOut(new PrintWriter(out, true));

    final int exitCode = cli.execute("wf4");

    assertThat(exitCode).isZero();
    verify(formatter).printTable(nodes);
  }
}
