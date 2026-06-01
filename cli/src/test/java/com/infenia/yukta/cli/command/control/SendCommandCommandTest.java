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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infenia.yukta.cli.CliFormatter;
import com.infenia.yukta.plugin.message.Message;
import com.infenia.yukta.service.control.gateway.ControlBusGateway;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import picocli.CommandLine;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class SendCommandCommandTest {

  @Mock private ControlBusGateway controlBus;
  @Mock private CliFormatter formatter;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void sendCommand_defaultTableOutput_sendsCommand() throws Exception {
    final Message<?> response = mockMessage();
    when(controlBus.sendCommand(eq("wf1"), eq("n1"), any(Message.class)))
        .thenReturn(Mono.just(response));

    final SendCommandCommand cmd = new SendCommandCommand(controlBus, objectMapper, formatter);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CommandLine cli = new CommandLine(cmd);
    cli.setOut(new PrintWriter(out, true));

    final int exitCode = cli.execute("wf1", "n1", "{\"action\":\"test\"}");

    assertThat(exitCode).isZero();
  }

  @Test
  void sendCommand_emptyJsonObject_succeeds() throws Exception {
    final Message<?> response = mockMessage();
    when(controlBus.sendCommand(eq("wf4"), eq("n4"), any(Message.class)))
        .thenReturn(Mono.just(response));

    final SendCommandCommand cmd = new SendCommandCommand(controlBus, objectMapper, formatter);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CommandLine cli = new CommandLine(cmd);
    cli.setOut(new PrintWriter(out, true));

    final int exitCode = cli.execute("wf4", "n4", "{}");

    assertThat(exitCode).isZero();
  }

  @Test
  void sendCommand_complexJsonPayload_succeeds() throws Exception {
    final Message<?> response = mockMessage();
    when(controlBus.sendCommand(eq("wf5"), eq("n5"), any(Message.class)))
        .thenReturn(Mono.just(response));

    final SendCommandCommand cmd = new SendCommandCommand(controlBus, objectMapper, formatter);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CommandLine cli = new CommandLine(cmd);
    cli.setOut(new PrintWriter(out, true));

    final int exitCode =
        cli.execute("wf5", "n5", "{\"action\":\"execute\",\"params\":{\"timeout\":30}}");

    assertThat(exitCode).isZero();
  }

  @Test
  void sendCommand_withSpecialCharacters_succeeds() throws Exception {
    final Message<?> response = mockMessage();
    when(controlBus.sendCommand(eq("wf6"), eq("n6"), any(Message.class)))
        .thenReturn(Mono.just(response));

    final SendCommandCommand cmd = new SendCommandCommand(controlBus, objectMapper, formatter);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CommandLine cli = new CommandLine(cmd);
    cli.setOut(new PrintWriter(out, true));

    final int exitCode = cli.execute("wf6", "n6", "{\"msg\":\"hello world\"}");

    assertThat(exitCode).isZero();
  }

  @Test
  void sendCommand_invalidJsonPayload_fails() throws Exception {
    final SendCommandCommand cmd = new SendCommandCommand(controlBus, objectMapper, formatter);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CommandLine cli = new CommandLine(cmd);
    cli.setOut(new PrintWriter(out, true));

    final int exitCode = cli.execute("wf1", "n1", "invalid json");

    assertThat(exitCode).isNotZero();
  }

  @Test
  void sendCommand_multipleFields_succeeds() throws Exception {
    final Message<?> response = mockMessage();
    when(controlBus.sendCommand(eq("wf7"), eq("n7"), any(Message.class)))
        .thenReturn(Mono.just(response));

    final SendCommandCommand cmd = new SendCommandCommand(controlBus, objectMapper, formatter);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CommandLine cli = new CommandLine(cmd);
    cli.setOut(new PrintWriter(out, true));

    final int exitCode =
        cli.execute("wf7", "n7", "{\"field1\":\"value1\",\"field2\":123,\"field3\":true}");

    assertThat(exitCode).isZero();
  }

  @Test
  void sendCommand_monoBlockReturnsNull_throwsException() throws Exception {
    when(controlBus.sendCommand(eq("wf8"), eq("n8"), any(Message.class))).thenReturn(Mono.empty());

    final SendCommandCommand cmd = new SendCommandCommand(controlBus, objectMapper, formatter);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CommandLine cli = new CommandLine(cmd);
    cli.setOut(new PrintWriter(out, true));

    final int exitCode = cli.execute("wf8", "n8", "{\"test\":true}");

    // Mono.empty() returns null on block(), causing exception
    assertThat(exitCode).isNotZero();
  }

  private Message<?> mockMessage() {
    final Message<?> msg = mock(Message.class);
    when(msg.toString()).thenReturn("Message{payload=response data}");
    return msg;
  }
}
