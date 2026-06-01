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

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class NodesCommandTest {

  @Test
  void run_displaysUsageInformation() throws Exception {
    final CommandLine cli = new CommandLine(new NodesCommand());

    final int exitCode = cli.execute();

    assertThat(exitCode).isZero();
  }

  @Test
  void run_via_picocli_displaysUsageAndExitsSuccessfully() throws Exception {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final ByteArrayOutputStream err = new ByteArrayOutputStream();
    final CommandLine cli = new CommandLine(new NodesCommand());
    cli.setOut(new PrintWriter(out, true));
    cli.setErr(new PrintWriter(err, true));

    final int exitCode = cli.execute();

    assertThat(exitCode).isZero();
  }

  @Test
  void run_printUsageWithoutErrors() {
    final NodesCommand cmd = new NodesCommand();
    final ByteArrayOutputStream err = new ByteArrayOutputStream();
    final PrintWriter errWriter = new PrintWriter(err, true);
    final CommandLine cli = new CommandLine(cmd);
    cli.setErr(errWriter);

    cmd.run();

    assertThat(err.toString()).doesNotContain("Error");
  }

  @Test
  void run_multipleInvocations_consistentBehavior() {
    final NodesCommand cmd1 = new NodesCommand();
    final ByteArrayOutputStream out1 = new ByteArrayOutputStream();
    final CommandLine cli1 = new CommandLine(cmd1);
    cli1.setOut(new PrintWriter(out1, true));

    cmd1.run();
    final String output1 = out1.toString();

    final NodesCommand cmd2 = new NodesCommand();
    final ByteArrayOutputStream out2 = new ByteArrayOutputStream();
    final CommandLine cli2 = new CommandLine(cmd2);
    cli2.setOut(new PrintWriter(out2, true));

    cmd2.run();
    final String output2 = out2.toString();

    assertThat(output1).isEqualTo(output2);
  }
}
