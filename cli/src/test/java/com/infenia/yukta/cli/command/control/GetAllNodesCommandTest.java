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
class GetAllNodesCommandTest {

  @Mock private ControlBusGateway controlBus;
  @Mock private CliFormatter formatter;

  @Test
  void getAllNodes_defaultTableOutput_printsNodes() {
    final List<String> nodes = List.of("node1", "node2", "node3");
    when(controlBus.getActiveNodes()).thenReturn(nodes);

    final GetAllNodesCommand cmd = new GetAllNodesCommand(controlBus, formatter);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CommandLine cli = new CommandLine(cmd);
    cli.setOut(new PrintWriter(out, true));

    final int exitCode = cli.execute();

    assertThat(exitCode).isZero();
    verify(formatter).printTable(nodes);
  }

  @Test
  void getAllNodes_jsonOutput_printsNodesAsJson() throws Exception {
    final List<String> nodes = List.of("node1", "node2");
    when(controlBus.getActiveNodes()).thenReturn(nodes);

    final GetAllNodesCommand cmd = new GetAllNodesCommand(controlBus, formatter);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CommandLine cli = new CommandLine(cmd);
    cli.setOut(new PrintWriter(out, true));

    final int exitCode = cli.execute("-o", "json");

    assertThat(exitCode).isZero();
    verify(formatter).printJson(nodes);
  }

  @Test
  void getAllNodes_jsonOutputLongForm_printsNodesAsJson() throws Exception {
    final List<String> nodes = List.of("nodeA");
    when(controlBus.getActiveNodes()).thenReturn(nodes);

    final GetAllNodesCommand cmd = new GetAllNodesCommand(controlBus, formatter);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CommandLine cli = new CommandLine(cmd);
    cli.setOut(new PrintWriter(out, true));

    final int exitCode = cli.execute("--output", "json");

    assertThat(exitCode).isZero();
    verify(formatter).printJson(nodes);
  }

  @Test
  void getAllNodes_formattingException_throwsRuntimeException() {
    final List<String> nodes = List.of("node1");
    when(controlBus.getActiveNodes()).thenReturn(nodes);
    doThrow(new RuntimeException("Format error")).when(formatter).printTable(nodes);

    final GetAllNodesCommand cmd = new GetAllNodesCommand(controlBus, formatter);

    assertThatThrownBy(cmd::run)
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Format error");
  }

  @Test
  void getAllNodes_emptyNodeList_printsEmptyList() {
    final List<String> nodes = List.of();
    when(controlBus.getActiveNodes()).thenReturn(nodes);

    final GetAllNodesCommand cmd = new GetAllNodesCommand(controlBus, formatter);

    cmd.run();

    verify(formatter).printTable(nodes);
  }
}
