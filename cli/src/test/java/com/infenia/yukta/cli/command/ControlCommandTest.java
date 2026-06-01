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
package com.infenia.yukta.cli.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class ControlCommandTest {

  @Test
  void run_helpFlagShort_printsUsage() {
    final ControlCommand cmd = new ControlCommand();
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CommandLine cli = new CommandLine(cmd);
    cli.setOut(new PrintWriter(out, true));

    final int exitCode = cli.execute("-h");

    assertThat(exitCode).isZero();
    final String output = out.toString();
    assertThat(output).contains("Usage:", "control", "Control bus operations");
  }

  @Test
  void run_helpFlagLong_printsUsage() {
    final ControlCommand cmd = new ControlCommand();
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CommandLine cli = new CommandLine(cmd);
    cli.setOut(new PrintWriter(out, true));

    final int exitCode = cli.execute("--help");

    assertThat(exitCode).isZero();
    assertThat(out.toString()).contains("Usage:", "control");
  }

  @Test
  void run_commandNameInAnnotation_appearsInHelp() {
    final ControlCommand cmd = new ControlCommand();
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CommandLine cli = new CommandLine(cmd);
    cli.setOut(new PrintWriter(out, true));

    final int exitCode = cli.execute("-h");

    assertThat(exitCode).isZero();
    assertThat(out.toString()).contains("control");
  }

  @Test
  void run_descriptionInAnnotation_appearsInHelp() {
    final ControlCommand cmd = new ControlCommand();
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CommandLine cli = new CommandLine(cmd);
    cli.setOut(new PrintWriter(out, true));

    final int exitCode = cli.execute("-h");

    assertThat(exitCode).isZero();
    assertThat(out.toString()).contains("Control bus operations");
  }

  @Test
  void run_nothingProvided_executesCommand() {
    final ControlCommand cmd = new ControlCommand();
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CommandLine cli = new CommandLine(cmd);
    cli.setOut(new PrintWriter(out, true));

    final int exitCode = cli.execute();

    assertThat(exitCode).isZero();
  }
}
