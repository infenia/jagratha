// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Infenia Private Limited
package com.infenia.yukta.plugin.process;

import java.util.List;
import java.util.Map;

/**
 * Fully resolved and validated configuration of a single process executor invocation.
 *
 * <p>Built by the plugin after variable resolution; execution fields map onto {@link
 * ProcessExecutionSpec} while the remaining fields control how the result is turned into an output
 * message.
 *
 * <p>Fields:
 * <ul>
 *   <li>command: resolved command and arguments</li>
 *   <li>workingDir: resolved working directory, or null for the current directory</li>
 *   <li>timeout: resolved timeout in seconds (positive)</li>
 *   <li>env: resolved environment variables</li>
 *   <li>useShell: whether to execute the command via an OS shell</li>
 *   <li>outputFormat: shape of the output message payload</li>
 *   <li>failureMode: how to react to a non-zero exit code or timeout</li>
 *   <li>inputMode: how the input message is handed to the process</li>
 *   <li>routeByExitCode: whether output messages are stamped with the "success"/"failure" source
 *       port based on the process outcome (requires failureMode CONTINUE)</li>
 *   <li>includeOutput: whether structured payloads embed stdout/stderr text</li>
 *   <li>includeInput: whether structured payloads embed the original input payload</li>
 *   <li>captureStderr: whether stderr is captured separately instead of merged into stdout</li>
 *   <li>maxOutputLines: maximum output lines retained per stream (0 = unlimited)</li>
 *   <li>maxOutputBytes: maximum output bytes retained per stream (0 = unlimited)</li>
 * </ul>
 */
/* package */ record ProcessExecutorConfig(
    List<String> command,
    String workingDir,
    long timeout,
    Map<String, String> env,
    boolean useShell,
    OutputFormat outputFormat,
    FailureMode failureMode,
    InputMode inputMode,
    boolean routeByExitCode,
    boolean includeOutput,
    boolean includeInput,
    boolean captureStderr,
    int maxOutputLines,
    long maxOutputBytes) {

  private static List<String> copyCommand(List<String> command) {
    return command == null ? List.of() : List.copyOf(command);
  }

  private static Map<String, String> copyEnv(Map<String, String> env) {
    return env == null ? Map.of() : Map.copyOf(env);
  }

  /** Defensively copies mutable collections to prevent external mutations. */
  @SuppressWarnings("PMD.PublicMemberInNonPublicType")
  public ProcessExecutorConfig {
    command = copyCommand(command);
    env = copyEnv(env);
  }

  static Builder builder() {
    return new Builder();
  }

  static final class Builder {
    private List<String> command = List.of();
    private String workingDir;
    private long timeout;
    private Map<String, String> env = Map.of();
    private boolean useShell;
    private OutputFormat outputFormat;
    private FailureMode failureMode;
    private InputMode inputMode;
    private boolean routeByExitCode;
    private boolean includeOutput;
    private boolean includeInput;
    private boolean captureStderr;
    private int maxOutputLines;
    private long maxOutputBytes;

    Builder command(List<String> command) {
      this.command = copyCommand(command);
      return this;
    }

    Builder workingDir(String workingDir) {
      this.workingDir = workingDir;
      return this;
    }

    Builder timeout(long timeout) {
      this.timeout = timeout;
      return this;
    }

    Builder env(Map<String, String> env) {
      this.env = copyEnv(env);
      return this;
    }

    Builder useShell(boolean useShell) {
      this.useShell = useShell;
      return this;
    }

    Builder outputFormat(OutputFormat outputFormat) {
      this.outputFormat = outputFormat;
      return this;
    }

    Builder failureMode(FailureMode failureMode) {
      this.failureMode = failureMode;
      return this;
    }

    Builder inputMode(InputMode inputMode) {
      this.inputMode = inputMode;
      return this;
    }

    Builder routeByExitCode(boolean routeByExitCode) {
      this.routeByExitCode = routeByExitCode;
      return this;
    }

    Builder includeOutput(boolean includeOutput) {
      this.includeOutput = includeOutput;
      return this;
    }

    Builder includeInput(boolean includeInput) {
      this.includeInput = includeInput;
      return this;
    }

    Builder captureStderr(boolean captureStderr) {
      this.captureStderr = captureStderr;
      return this;
    }

    Builder maxOutputLines(int maxOutputLines) {
      this.maxOutputLines = maxOutputLines;
      return this;
    }

    Builder maxOutputBytes(long maxOutputBytes) {
      this.maxOutputBytes = maxOutputBytes;
      return this;
    }

    ProcessExecutorConfig build() {
      return new ProcessExecutorConfig(
          command,
          workingDir,
          timeout,
          env,
          useShell,
          outputFormat,
          failureMode,
          inputMode,
          routeByExitCode,
          includeOutput,
          includeInput,
          captureStderr,
          maxOutputLines,
          maxOutputBytes);
    }
  }
}
