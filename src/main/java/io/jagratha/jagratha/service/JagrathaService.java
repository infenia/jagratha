package io.jagratha.jagratha.service;

import io.jagratha.jagratha.config.JagrathaConfig;
import io.jagratha.jagratha.model.TaskResponse;
import jakarta.validation.constraints.NotBlank;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Service
@Validated
@RequiredArgsConstructor
public class JagrathaService {

  private final JagrathaConfig config;

  private static final String FAILURE_STATUS = "FAILURE";

  public Mono<Void> saveFile(@NotBlank String relativePath, @NotBlank final String content) {
    return Mono.defer(
            () -> {
              final var projectRoot = getProjectRoot();
              if (projectRoot == null || projectRoot.isEmpty()) {
                return Mono.error(
                    new IllegalStateException("External project path is not configured"));
              }
              final var path = Paths.get(projectRoot);
              final var fullPath = path.resolve(relativePath).normalize();

              // Security check: ensure the path is within the project root
              if (!fullPath.startsWith(path.normalize())) {
                return Mono.error(
                    new IllegalArgumentException("Invalid file path: " + relativePath));
              }

              return Mono.fromRunnable(
                      () -> {
                        try {
                          Files.createDirectories(fullPath.getParent());
                          Files.writeString(fullPath, content);
                          log.info("Saved file to {}", fullPath);
                        } catch (IOException e) {
                          log.error("Failed to save file", e);
                          throw new UncheckedFileIOException(
                              "Failed to save file: " + e.getMessage(), e);
                        }
                      })
                  .subscribeOn(Schedulers.boundedElastic());
            })
        .then();
  }

  public Mono<TaskResponse> runQualityChecks() {
    return Mono.fromCallable(this::executeQualityChecks).subscribeOn(Schedulers.boundedElastic());
  }

  private TaskResponse executeQualityChecks() {
    String projectRoot = getProjectRoot();
    if (projectRoot == null || projectRoot.isEmpty()) {
      return new TaskResponse(FAILURE_STATUS, "External project path is not configured");
    }

    final var projectDir = new File(projectRoot);
    if (!projectDir.exists() || !projectDir.isDirectory()) {
      return new TaskResponse(
          FAILURE_STATUS, "External project directory does not exist: " + projectRoot);
    }

    List<String> command = buildGradleCommand();
    log.info("Running quality checks in {}: {}", projectRoot, String.join(" ", command));

    try {
      ProcessBuilder pb = new ProcessBuilder(command);
      pb.directory(projectDir);
      pb.redirectErrorStream(true);
      Process process = pb.start();

      String output = readProcessOutput(process);
      boolean finished = process.waitFor(10, TimeUnit.MINUTES);

      if (!finished) {
        process.destroyForcibly();
        return new TaskResponse(FAILURE_STATUS, "Timeout while running quality checks.\n" + output);
      }

      int exitCode = process.exitValue();
      log.info("Quality checks finished with exit code {}", exitCode);
      return new TaskResponse(exitCode == 0 ? "SUCCESS" : FAILURE_STATUS, output);

    } catch (IOException e) {
      log.error("Error executing Gradle", e);
      return new TaskResponse(FAILURE_STATUS, "Error executing Gradle: " + e.getMessage());
    } catch (InterruptedException e) {
      log.error("Quality checks interrupted", e);
      Thread.currentThread().interrupt();
      return new TaskResponse(FAILURE_STATUS, "Execution interrupted: " + e.getMessage());
    }
  }

  private String getProjectRoot() {
    return config.externalProject() != null ? config.externalProject().path() : null;
  }

  private List<String> buildGradleCommand() {
    String gradlePath =
        config.externalProject() != null ? config.externalProject().gradlePath() : null;
    List<String> command = new ArrayList<>();
    command.add(gradlePath != null ? gradlePath : "./gradlew");
    command.add("spotlessApply");
    command.add("spotlessCheck");
    command.add("checkstyleMain");
    command.add("test");
    return command;
  }

  private String readProcessOutput(Process process) throws IOException {
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
      return reader.lines().collect(Collectors.joining("\n"));
    }
  }

  private static final class UncheckedFileIOException extends RuntimeException {
    @Serial private static final long serialVersionUID = 1L;

    private UncheckedFileIOException(final String message, final Throwable cause) {
      super(message, cause);
    }
  }
}
