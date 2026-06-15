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

import jakarta.annotation.PostConstruct;
import java.nio.file.Path;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "yukta.daemon")
@Data
@NoArgsConstructor
public class DaemonProperties {

  private int port = 8080;
  private String pidFile;
  private String logFile;
  private int startupTimeoutSeconds = 30;
  private int healthCheckIntervalMs = 500;
  private String jarPath;
  private String notRunningMessage = "Daemon is not running. Start it with: yukta daemon start";

  @PostConstruct
  public void setDefaults() {
    if (pidFile == null) {
      pidFile = "/tmp/.yukta/daemon.pid";
    }
    if (logFile == null) {
      logFile = "/tmp/.yukta/daemon.log";
    }
  }

  public Path getPidFilePath() {
    return Path.of(pidFile);
  }

  public Path getLogFilePath() {
    return Path.of(logFile);
  }
}
