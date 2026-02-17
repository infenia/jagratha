package io.jagratha.jagratha.model;

import java.util.List;

/** Request object for configuration updates. */
public record ConfigRequest(
    String projectPath,
    String gradlePath,
    List<String> tasks,
    Long executionTimeout,
    String fileLogDir,
    String resultLogDir) {}
