// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Infenia Private Limited
package com.infenia.yukta.plugin.process;

import java.util.List;
import java.util.Map;

/**
 * Immutable specification describing how an external process should be executed.
 *
 * <p>Instances are created via the generated {@link #builder()}. Unset numeric fields are
 * normalized in the compact constructor: a non-positive {@code timeoutSeconds} falls back to {@link
 * #DEFAULT_TIMEOUT_SECONDS}, and non-positive output caps mean {@link #UNLIMITED}.
 *
 * <p>Record components:
 *
 * <ul>
 *   <li><code>command</code> — the command and its arguments to execute (never null after
 *       construction)
 *   <li><code>workingDir</code> — the working directory, or null/blank for the current directory
 *   <li><code>timeoutSeconds</code> — the maximum wall-clock execution time in seconds
 *   <li><code>env</code> — additional environment variables for the process (never null after
 *       construction)
 *   <li><code>useShell</code> — whether to wrap the command in an OS shell (sh/cmd.exe)
 *   <li><code>stdin</code> — text to write to the process standard input, or null for none; the
 *       input stream is always closed after writing so commands reading stdin terminate
 *       deterministically
 *   <li><code>captureStderr</code> — whether to capture stderr separately; when false, stderr is
 *       merged into stdout
 *   <li><code>maxOutputLines</code> — maximum number of output lines to retain per stream ({@link
 *       #UNLIMITED} for no cap); excess output is drained but discarded
 *   <li><code>maxOutputBytes</code> — maximum number of output bytes to retain per stream ({@link
 *       #UNLIMITED} for no cap); excess output is drained but discarded
 * </ul>
 */
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

  public static Builder builder() {
    return new Builder();
  }

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

  public static final class Builder {
    private List<String> command;
    private String workingDir;
    private long timeoutSeconds;
    private Map<String, String> env;
    private boolean useShell;
    private String stdin;
    private boolean captureStderr;
    private int maxOutputLines;
    private long maxOutputBytes;

    private Builder() {}

    public Builder command(List<String> command) {
      this.command = command;
      return this;
    }

    public Builder workingDir(String workingDir) {
      this.workingDir = workingDir;
      return this;
    }

    public Builder timeoutSeconds(long timeoutSeconds) {
      this.timeoutSeconds = timeoutSeconds;
      return this;
    }

    public Builder env(Map<String, String> env) {
      this.env = env;
      return this;
    }

    public Builder useShell(boolean useShell) {
      this.useShell = useShell;
      return this;
    }

    public Builder stdin(String stdin) {
      this.stdin = stdin;
      return this;
    }

    public Builder captureStderr(boolean captureStderr) {
      this.captureStderr = captureStderr;
      return this;
    }

    public Builder maxOutputLines(int maxOutputLines) {
      this.maxOutputLines = maxOutputLines;
      return this;
    }

    public Builder maxOutputBytes(long maxOutputBytes) {
      this.maxOutputBytes = maxOutputBytes;
      return this;
    }

    public ProcessExecutionSpec build() {
      return new ProcessExecutionSpec(
          command,
          workingDir,
          timeoutSeconds,
          env,
          useShell,
          stdin,
          captureStderr,
          maxOutputLines,
          maxOutputBytes);
    }
  }
}
