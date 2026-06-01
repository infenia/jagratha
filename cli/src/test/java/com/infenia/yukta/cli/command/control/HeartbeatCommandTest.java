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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infenia.yukta.cli.CliFormatter;
import com.infenia.yukta.plugin.message.Message;
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
class HeartbeatCommandTest {

  @Mock private ControlBusGateway controlBus;
  @Mock private CliFormatter formatter;

  @Test
  void heartbeat_defaultTableOutput_printsHeartbeat() throws Exception {
    final Message<?> heartbeat = mock(Message.class);
    when(controlBus.getLastHeartbeat("wf1", "n1")).thenReturn((Message) heartbeat);

    final HeartbeatCommand cmd = new HeartbeatCommand(controlBus, formatter);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CommandLine cli = new CommandLine(cmd);
    cli.setOut(new PrintWriter(out, true));

    final int exitCode = cli.execute("wf1", "n1");

    assertThat(exitCode).isZero();
    verify(formatter).printTable(List.of(heartbeat.toString()));
  }

  @Test
  void heartbeat_jsonOutput_printsHeartbeatAsJson() throws Exception {
    final Message<?> heartbeat = mock(Message.class);
    when(controlBus.getLastHeartbeat("wf2", "n2")).thenReturn((Message) heartbeat);

    final HeartbeatCommand cmd = new HeartbeatCommand(controlBus, formatter);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CommandLine cli = new CommandLine(cmd);
    cli.setOut(new PrintWriter(out, true));

    final int exitCode = cli.execute("wf2", "n2", "-o", "json");

    assertThat(exitCode).isZero();
    verify(formatter).printJson(heartbeat);
  }

  @Test
  void heartbeat_jsonOutputLongForm_printsHeartbeatAsJson() throws Exception {
    final Message<?> heartbeat = mock(Message.class);
    when(controlBus.getLastHeartbeat("wf3", "n3")).thenReturn((Message) heartbeat);

    final HeartbeatCommand cmd = new HeartbeatCommand(controlBus, formatter);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CommandLine cli = new CommandLine(cmd);
    cli.setOut(new PrintWriter(out, true));

    final int exitCode = cli.execute("wf3", "n3", "--output", "json");

    assertThat(exitCode).isZero();
    verify(formatter).printJson(heartbeat);
  }

  @Test
  void heartbeat_tableFormattingException_throwsRuntimeException() throws Exception {
    final Message<?> heartbeat = mock(Message.class);
    when(controlBus.getLastHeartbeat("wf1", "n1")).thenReturn((Message) heartbeat);
    when(heartbeat.toString()).thenReturn("test heartbeat");
    doThrow(new RuntimeException("Format error"))
        .when(formatter)
        .printTable(List.of("test heartbeat"));

    final HeartbeatCommand cmd = new HeartbeatCommand(controlBus, formatter);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CommandLine cli = new CommandLine(cmd);
    cli.setOut(new PrintWriter(out, true));

    final int exitCode = cli.execute("wf1", "n1");

    assertThat(exitCode).isNotZero();
  }

  @Test
  void heartbeat_jsonFormattingException_throwsRuntimeException() throws Exception {
    final Message<?> heartbeat = mock(Message.class);
    when(controlBus.getLastHeartbeat("wf2", "n2")).thenReturn((Message) heartbeat);
    doThrow(new RuntimeException("Json error")).when(formatter).printJson((Message) heartbeat);

    final HeartbeatCommand cmd = new HeartbeatCommand(controlBus, formatter);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CommandLine cli = new CommandLine(cmd);
    cli.setOut(new PrintWriter(out, true));

    final int exitCode = cli.execute("wf2", "n2", "-o", "json");

    assertThat(exitCode).isNotZero();
  }
}
