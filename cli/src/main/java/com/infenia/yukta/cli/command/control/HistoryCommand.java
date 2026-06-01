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

import com.infenia.yukta.cli.CliFormatter;
import com.infenia.yukta.model.monitoring.WorkflowExecutionSummary;
import com.infenia.yukta.service.control.gateway.ControlBusGateway;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Component
@RequiredArgsConstructor
@Command(name = "history", description = "Get session execution history")
public class HistoryCommand implements Runnable {

  private final ControlBusGateway controlBus;
  private final CliFormatter formatter;

  @Parameters(index = "0", description = "Session ID")
  private String sessionId;

  @Option(
      names = {"-o", "--output"},
      description = "Output format: table (default), json",
      defaultValue = "table")
  private String outputFormat;

  @Override
  public void run() {
    final List<WorkflowExecutionSummary> history = controlBus.getHistory(sessionId);
    try {
      if ("json".equals(outputFormat)) {
        formatter.printJson(history);
      } else {
        final List<String> summaries =
            history.stream().map(WorkflowExecutionSummary::toString).toList();
        formatter.printTable(summaries);
      }
    } catch (Exception e) {
      System.err.println("Error formatting output: " + e.getMessage());
      throw new RuntimeException(e);
    }
  }
}
