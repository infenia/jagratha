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

import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DaemonManager {

  private final DaemonProperties props;
  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();

  public boolean isRunning() {
    try {
      return httpHealthCheck();
    } catch (Exception e) {
      log.debug("Health check failed", e);
      return false;
    }
  }

  public Process startDaemon() throws IOException, DaemonStartupException {
    Path pidFilePath = props.getPidFilePath();
    Path logFilePath = props.getLogFilePath();

    Files.createDirectories(pidFilePath.getParent());
    Files.createDirectories(logFilePath.getParent());

    try (var randomAccessFile = new java.io.RandomAccessFile(pidFilePath.toFile(), "rw");
        FileLock lock = randomAccessFile.getChannel().tryLock()) {

      if (lock == null) {
        throw new DaemonStartupException(
            "Another process is starting the daemon. Please wait and retry.");
      }

      if (isDaemonProcess()) {
        log.info("Daemon already running with PID {}", readPidFile());
        return null;
      }

      // Launch daemon detached from parent I/O (logs to file, not stdout)
      ProcessBuilder pb = new ProcessBuilder(buildDaemonCommand());
      pb.directory(new File("."));
      pb.redirectOutput(ProcessBuilder.Redirect.to(logFilePath.toFile()));
      pb.redirectError(ProcessBuilder.Redirect.to(logFilePath.toFile()));

      Process process = pb.start();
      long pid = process.pid();

      Files.writeString(
          pidFilePath,
          String.valueOf(pid),
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING);

      log.info("Started daemon process with PID {}", pid);
      return process;

    } catch (OverlappingFileLockException e) {
      throw new DaemonStartupException(
          "Another CLI process is starting the daemon. Please wait and retry.", e);
    }
  }

  public void waitForReady() throws DaemonStartupException, InterruptedException {
    Instant deadline = Instant.now().plusSeconds(props.getStartupTimeoutSeconds());

    while (Instant.now().isBefore(deadline)) {
      try {
        if (httpHealthCheck()) {
          log.info("Daemon is ready");
          return;
        }
      } catch (ConnectException e) {
        log.debug("Daemon not yet responding: {}", e.getMessage());
      } catch (Exception e) {
        log.debug("Health check failed", e);
      }

      Thread.sleep(props.getHealthCheckIntervalMs());
    }

    throw new DaemonStartupException(
        "Daemon failed to start within "
            + props.getStartupTimeoutSeconds()
            + " seconds. Check "
            + props.getLogFile()
            + " for details.");
  }

  public String ensureRunning() throws DaemonStartupException, InterruptedException, IOException {
    if (isRunning()) {
      log.info("Using existing daemon at http://127.0.0.1:{}", props.getPort());
      return "http://127.0.0.1:" + props.getPort();
    }

    log.info("Starting daemon...");
    startDaemon();
    waitForReady();
    return "http://127.0.0.1:" + props.getPort();
  }

  public boolean stopDaemon() {
    Path pidFilePath = props.getPidFilePath();

    if (!Files.exists(pidFilePath)) {
      log.info("No daemon PID file found");
      return false;
    }

    try {
      long pid = readPidFile();
      java.lang.ProcessHandle.of(pid).ifPresent(handle -> {
        handle.destroy();
        log.info("Terminated daemon process with PID {}", pid);
      });
      Files.delete(pidFilePath);
      return true;
    } catch (Exception e) {
      log.warn("Error stopping daemon: {}", e.getMessage());
      try {
        Files.deleteIfExists(pidFilePath);
      } catch (IOException ex) {
        log.warn("Failed to delete PID file: {}", ex.getMessage());
      }
      return false;
    }
  }

  public DaemonStatus status() {
    boolean running = isRunning();
    long pid = -1;
    try {
      pid = readPidFile();
    } catch (Exception ignored) {
    }
    String url = running ? "http://127.0.0.1:" + props.getPort() : null;
    return new DaemonStatus(running, pid, url);
  }

  private boolean isDaemonProcess() {
    try {
      long pid = readPidFile();
      if (java.lang.ProcessHandle.of(pid).isEmpty()) {
        log.debug("PID {} is stale, cleaning up", pid);
        Files.deleteIfExists(props.getPidFilePath());
        return false;
      }
      return httpHealthCheck();
    } catch (Exception e) {
      log.debug("Stale PID file detected", e);
      try {
        Files.deleteIfExists(props.getPidFilePath());
      } catch (IOException ex) {
        log.debug("Failed to delete stale PID file", ex);
      }
      return false;
    }
  }

  private long readPidFile() throws IOException {
    String content = Files.readString(props.getPidFilePath());
    return Long.parseLong(content.trim());
  }

  private List<String> buildDaemonCommand() throws DaemonStartupException {
    String javaHome =
        System.getProperty("java.home", System.getProperty("JAVA_HOME", "/usr/lib/jvm/java"));
    String javaExe = javaHome + File.separator + "bin" + File.separator + "java";

    boolean isNative =
        "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));

    if (isNative) {
      String processCmd = java.lang.ProcessHandle.current().info().command().orElse(null);
      if (processCmd == null) {
        throw new DaemonStartupException("Cannot determine native image command path");
      }
      return List.of(processCmd, "--spring.profiles.active=" + getActiveProfile());
    }

    String jarPath = resolveJarPath();
    return List.of(
        javaExe,
        "-jar",
        jarPath,
        "--spring.profiles.active=" + getActiveProfile(),
        "--server.port=" + props.getPort());
  }

  private String resolveJarPath() throws DaemonStartupException {
    if (props.getJarPath() != null && !props.getJarPath().isEmpty()) {
      return props.getJarPath();
    }

    String classPath = System.getProperty("java.class.path", "");
    String[] entries = classPath.split(File.pathSeparator);
    for (String entry : entries) {
      if (entry.endsWith(".jar")) {
        return entry;
      }
    }

    throw new DaemonStartupException(
        "Cannot determine JAR path. Set yukta.daemon.jar-path in configuration or ensure "
            + "the application runs from the fat JAR.");
  }

  private String getActiveProfile() {
    String profile = System.getProperty("spring.profiles.active");
    if (profile != null && !profile.isEmpty()) {
      return profile;
    }
    String envProfile = System.getenv("SPRING_PROFILES_ACTIVE");
    if (envProfile != null && !envProfile.isEmpty()) {
      return envProfile;
    }
    return "dev";
  }

  private boolean httpHealthCheck() throws Exception {
    String url = "http://127.0.0.1:" + props.getPort() + "/actuator/health";
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(1)).GET().build();

    try {
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      return response.statusCode() == 200;
    } catch (ConnectException | java.net.SocketTimeoutException e) {
      throw new ConnectException("Daemon not responding: " + e.getMessage());
    }
  }
}
