package io.jagratha.jagratha.config;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Service for managing Jagratha configuration with runtime overrides.
 * Provides a way to update configuration via API while maintaining
 * precedence: API > Command Line > Environment > application.yaml.
 */
@Service
@RequiredArgsConstructor
public class JagrathaConfigService {

  private final JagrathaConfig staticConfig;

  private final AtomicReference<String> externalProjectPath = new AtomicReference<>();
  private final AtomicReference<String> gradlePath = new AtomicReference<>();
  private final AtomicReference<List<String>> tasks = new AtomicReference<>();
  private final AtomicReference<Long> executionTimeout = new AtomicReference<>();
  private final AtomicReference<String> modifiedFilesLogDir = new AtomicReference<>();
  private final AtomicReference<String> gradleResultsLogDir = new AtomicReference<>();

  public String getExternalProjectPath() {
    String override = externalProjectPath.get();
    if (override != null) {
      return override;
    }
    return staticConfig.externalProject() != null ? staticConfig.externalProject().path() : null;
  }

  public void setExternalProjectPath(String path) {
    externalProjectPath.set(path);
  }

  public String getGradlePath() {
    String override = gradlePath.get();
    if (override != null) {
      return override;
    }
    return staticConfig.externalProject() != null ? staticConfig.externalProject().gradlePath() : null;
  }

  public void setGradlePath(String path) {
    gradlePath.set(path);
  }

  public List<String> getTasks() {
    List<String> override = tasks.get();
    if (override != null) {
      return override;
    }
    return staticConfig.tasks();
  }

  public void setTasks(List<String> tasksList) {
    tasks.set(tasksList);
  }

  public Long getExecutionTimeout() {
    Long override = executionTimeout.get();
    if (override != null) {
      return override;
    }
    return staticConfig.executionTimeout();
  }

  public void setExecutionTimeout(Long timeout) {
    executionTimeout.set(timeout);
  }

  public String getModifiedFilesLogDir() {
    String override = modifiedFilesLogDir.get();
    if (override != null) {
      return override;
    }
    return staticConfig.logs() != null ? staticConfig.logs().modifiedFilesDir() : null;
  }

  public void setModifiedFilesLogDir(String dir) {
    modifiedFilesLogDir.set(dir);
  }

  public String getGradleResultsLogDir() {
    String override = gradleResultsLogDir.get();
    if (override != null) {
      return override;
    }
    return staticConfig.logs() != null ? staticConfig.logs().gradleResultsDir() : null;
  }

  public void setGradleResultsLogDir(String dir) {
    gradleResultsLogDir.set(dir);
  }
}
