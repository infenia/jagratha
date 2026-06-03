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
import com.infenia.yukta.cli.YuktaDaemonClient;
import com.infenia.yukta.model.api.ConfigRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import tools.jackson.databind.ObjectMapper;

@Component
@Command(
    name = "session-apply",
    description = "Apply session configuration from JSON file or string")
public class SessionApplyCommand implements Runnable {

  private final YuktaDaemonClient daemonClient;
  private final ObjectMapper objectMapper;
  private final CliFormatter formatter;

  public SessionApplyCommand(
      YuktaDaemonClient daemonClient,
      ObjectMapper objectMapper,
      CliFormatter formatter) {
    this.daemonClient = daemonClient;
    this.objectMapper = objectMapper;
    this.formatter = formatter;
  }

  @Parameters(
      index = "0",
      description = "Path to JSON config file OR inline JSON string representing ConfigRequest")
  private String configInput;

  @Option(
      names = {"-o", "--output"},
      description = "Output format: table (default), json",
      defaultValue = "table")
  private String outputFormat;

  @Override
  public void run() {
    try {
      final String jsonContent = readConfigInput(configInput);
      final ConfigRequest request = objectMapper.readValue(jsonContent, ConfigRequest.class);
      daemonClient.applySession(request);

      final String message = "Session applied: " + request.sessionId();
      if ("json".equals(outputFormat)) {
        formatter.printJson(java.util.Map.of("status", "success", "message", message));
      } else {
        formatter.printTable(List.of(message));
      }
    } catch (Exception e) {
      System.err.println("Error applying session: " + e.getMessage());
      throw new RuntimeException(e);
    }
  }

  private String readConfigInput(final String input) throws Exception {
    try {
      final Path path = Path.of(input);
      if (Files.exists(path) && Files.isRegularFile(path)) {
        return new String(Files.readAllBytes(path));
      }
    } catch (final Exception ignored) {
      // Not a valid file path, treat as inline JSON
    }
    return input;
  }
}
