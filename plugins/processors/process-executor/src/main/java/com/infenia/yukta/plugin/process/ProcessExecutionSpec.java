// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Infenia Private Limited
package com.infenia.yukta.plugin.process;

import java.util.List;
import java.util.Map;
import lombok.Builder;

/**
 * Immutable specification describing how an external process should be executed.
 *
 * <p>Instances are created via the generated {@link #builder()}. Unset numeric fields are
 * normalized in the compact constructor: a non-positive {@code timeoutSeconds} falls back to {@link
 * #DEFAULT_TIMEOUT_SECONDS}, and non-positive output caps mean {@link #UNLIMITED}.
 *
 * @param command the command and its arguments to execute (never null after construction)
 * @param workingDir the working directory, or null/blank for the current directory
 * @param timeoutSeconds the maximum wall-clock execution time in seconds
 * @param env additional environment variables for the process (never null after construction)
 * @param useShell whether to wrap the command in an OS shell (sh/cmd.exe)
 * @param stdin text to write to the process standard input, or null for none; the input stream is
 *     always closed after writing so commands reading stdin terminate deterministically
 * @param captureStderr whether to capture stderr separately; when false, stderr is merged into
 *     stdout
 * @param maxOutputLines maximum number of output lines to retain per stream ({@link #UNLIMITED} for
 *     no cap); excess output is drained but discarded
 * @param maxOutputBytes maximum number of output bytes to retain per stream ({@link #UNLIMITED} for
 *     no cap); excess output is drained but discarded
 */
@Builder
public record ProcessExecutionSpec(
    List<String> command,
    String workingDir,
    long timeoutSeconds,
    Map<String, String> env,
    boolean useShell,
    String stdin,
    boolean captureStderr,
    int maxOutputLines,
    long maxOutputBytes) {

  /** Timeout applied when no positive timeout is specified. */
  public static final long DEFAULT_TIMEOUT_SECONDS = 300L;

  /** Cap value indicating that output should not be limited. */
  public static final int UNLIMITED = 0;

  /** Normalizes nulls and non-positive numeric fields to their documented defaults. */
  public ProcessExecutionSpec {
    command = command == null ? List.of() : List.copyOf(command);
    env = env == null ? Map.of() : Map.copyOf(env);
    if (timeoutSeconds <= 0) {
      timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
    }
    maxOutputLines = Math.max(UNLIMITED, maxOutputLines);
    maxOutputBytes = Math.max(UNLIMITED, maxOutputBytes);
  }
}
