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
package com.infenia.yukta.cli;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.infenia.yukta.cli.command.ControlCommand;
import com.infenia.yukta.cli.command.control.GetAllNodesCommand;
import com.infenia.yukta.cli.command.control.GetNodesCommand;
import com.infenia.yukta.cli.command.control.HeartbeatCommand;
import com.infenia.yukta.cli.command.control.HistoryCommand;
import com.infenia.yukta.cli.command.control.LogsStreamCommand;
import com.infenia.yukta.cli.command.control.ProgressCommand;
import com.infenia.yukta.cli.command.control.ProgressStreamCommand;
import com.infenia.yukta.cli.command.control.SendCommandCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

@ExtendWith(MockitoExtension.class)
class CliRunnerTest {

  @Mock private ControlCommand controlCommand;
  @Mock private GetNodesCommand getNodesCommand;
  @Mock private GetAllNodesCommand getAllNodesCommand;
  @Mock private HeartbeatCommand heartbeatCommand;
  @Mock private SendCommandCommand sendCommandCommand;
  @Mock private ProgressCommand progressCommand;
  @Mock private ProgressStreamCommand progressStreamCommand;
  @Mock private LogsStreamCommand logsStreamCommand;
  @Mock private HistoryCommand historyCommand;
  @Mock private SystemExitHandler exitHandler;

  @Mock private com.infenia.yukta.cli.command.control.NodesCommand nodesCommand;

  @Test
  void cliRunner_doesNothing_whenNoArgs() {
    final CliRunner cliRunner =
        new CliRunner(
            controlCommand,
            nodesCommand,
            getNodesCommand,
            getAllNodesCommand,
            heartbeatCommand,
            sendCommandCommand,
            progressCommand,
            progressStreamCommand,
            logsStreamCommand,
            historyCommand,
            exitHandler);
    final DefaultApplicationArguments appArgs = new DefaultApplicationArguments();
    cliRunner.run(appArgs);
    verify(exitHandler, never()).exit(0);
  }

  @Test
  void cliRunner_doesNothing_whenNonCliArgs() {
    final CliRunner cliRunner =
        new CliRunner(
            controlCommand,
            nodesCommand,
            getNodesCommand,
            getAllNodesCommand,
            heartbeatCommand,
            sendCommandCommand,
            progressCommand,
            progressStreamCommand,
            logsStreamCommand,
            historyCommand,
            exitHandler);
    final DefaultApplicationArguments appArgs = new DefaultApplicationArguments("server");
    cliRunner.run(appArgs);
    verify(exitHandler, never()).exit(0);
  }
}
