// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Infenia Private Limited
package com.infenia.yukta.plugin.process;

import java.util.List;
import java.util.Map;
import lombok.Builder;

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
@Builder
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

  /** Defensively copies mutable collections to prevent external mutations. */
  @SuppressWarnings("PMD.PublicMemberInNonPublicType")
  public ProcessExecutorConfig {
    command = command == null ? List.of() : List.copyOf(command);
    env = env == null ? Map.of() : Map.copyOf(env);
  }
}
