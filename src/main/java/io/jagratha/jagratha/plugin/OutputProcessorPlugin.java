package io.jagratha.jagratha.plugin;

import java.util.Map;

/** Interface for processing task execution output. */
public interface OutputProcessorPlugin {
  /**
   * Get the name of the plugin.
   *
   * @return the plugin name
   */
  String getName();

  /**
   * Process the output of a task.
   *
   * @param input the input data for processing
   * @return the result of the processing
   */
  ProcessorResult process(ProcessorInput input);

  /** Input data for output processing. */
  record ProcessorInput(
      String sessionId,
      String projectRoot,
      String module,
      String taskName,
      String taskOutput,
      String resultsDir,
      Map<String, Object> config) {}

  /** Result of output processing. */
  record ProcessorResult(String status, String output, String artifactPath) {}
}
