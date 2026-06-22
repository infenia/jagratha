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

import com.infenia.yukta.cli.command.ControlCommand;
import com.infenia.yukta.cli.command.DaemonCommand;
import com.infenia.yukta.cli.command.control.GetAllNodesCommand;
import com.infenia.yukta.cli.command.control.GetNodesCommand;
import com.infenia.yukta.cli.command.control.HeartbeatCommand;
import com.infenia.yukta.cli.command.control.HistoryCommand;
import com.infenia.yukta.cli.command.control.LogsStreamCommand;
import com.infenia.yukta.cli.command.control.NodesCommand;
import com.infenia.yukta.cli.command.control.ProgressCommand;
import com.infenia.yukta.cli.command.control.ProgressStreamCommand;
import com.infenia.yukta.cli.command.control.SendCommandCommand;
import com.infenia.yukta.cli.command.control.SessionApplyCommand;
import com.infenia.yukta.cli.command.control.WorkflowTriggerCommand;
import com.infenia.yukta.cli.command.daemon.DaemonStartCommand;
import com.infenia.yukta.cli.command.daemon.DaemonStatusCommand;
import com.infenia.yukta.cli.command.daemon.DaemonStopCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

/** Spring ApplicationRunner that orchestrates CLI command execution. */
@Component
@RequiredArgsConstructor
public class CliRunner implements ApplicationRunner {

  private final ControlCommand controlCommand;
  private final NodesCommand nodesCommand;
  private final GetNodesCommand getNodesCommand;
  private final GetAllNodesCommand getAllNodesCommand;
  private final HeartbeatCommand heartbeatCommand;
  private final SendCommandCommand sendCommandCommand;
  private final ProgressCommand progressCommand;
  private final ProgressStreamCommand progressStreamCommand;
  private final LogsStreamCommand logsStreamCommand;
  private final HistoryCommand historyCommand;
  private final SessionApplyCommand sessionApplyCommand;
  private final WorkflowTriggerCommand workflowTriggerCommand;
  private final DaemonCommand daemonCommand;
  private final DaemonStartCommand daemonStartCommand;
  private final DaemonStopCommand daemonStopCommand;
  private final DaemonStatusCommand daemonStatusCommand;
  private final DaemonManager daemonManager;
  private final DaemonProperties daemonProperties;
  private final SystemExitHandler exitHandler;

  @Override
  @SuppressWarnings({
    "PMD.MethodArgumentCouldBeFinal",
    "PMD.LocalVariableCouldBeFinal",
    "PMD.UseVarargs"
  })
  public void run(final ApplicationArguments args) {
    final String[] rawArgs = args.getSourceArgs();
    if (!isCli(rawArgs)) {
      return;
    }

    // Require daemon to already be running for control commands
    if (rawArgs.length > 0 && "control".equals(rawArgs[0])) {
      if (!daemonManager.isRunning()) {
        System.err.println(daemonProperties.getNotRunningMessage());
        exitHandler.exit(1);
        return;
      }
    }

    final CommandLine nodesCmd = new CommandLine(nodesCommand);
    nodesCmd.addSubcommand(getNodesCommand);
    nodesCmd.addSubcommand(getAllNodesCommand);

    final CommandLine controlCmd = new CommandLine(controlCommand);
    controlCmd.addSubcommand(nodesCmd);
    controlCmd.addSubcommand(heartbeatCommand);
    controlCmd.addSubcommand(sendCommandCommand);
    controlCmd.addSubcommand(progressCommand);
    controlCmd.addSubcommand(progressStreamCommand);
    controlCmd.addSubcommand(logsStreamCommand);
    controlCmd.addSubcommand(historyCommand);
    controlCmd.addSubcommand(sessionApplyCommand);
    controlCmd.addSubcommand(workflowTriggerCommand);

    final CommandLine daemonCmd = new CommandLine(daemonCommand);
    daemonCmd.addSubcommand(daemonStartCommand);
    daemonCmd.addSubcommand(daemonStopCommand);
    daemonCmd.addSubcommand(daemonStatusCommand);

    final CommandLine rootCmd = new CommandLine(new YuktaCli());
    rootCmd.addSubcommand(controlCmd);
    rootCmd.addSubcommand(daemonCmd);

    final int exitCode = rootCmd.execute(rawArgs);
    exitHandler.exit(exitCode);
  }

  private boolean isCli(final String[] args) {
    return args.length > 0 && ("control".equals(args[0]) || "daemon".equals(args[0]));
  }
}
