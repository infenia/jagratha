package io.jagratha.jagratha.model;

import java.util.List;

/**
 * Request object for configuration updates.
 */
public record ConfigRequest(
    String externalProjectPath,
    String gradlePath,
    List<String> tasks,
    Long executionTimeout,
    String modifiedFilesLogDir,
    String gradleResultsLogDir) {}
