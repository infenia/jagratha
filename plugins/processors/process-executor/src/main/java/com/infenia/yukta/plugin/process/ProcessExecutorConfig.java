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
 * @param command the resolved command and arguments
 * @param workingDir the resolved working directory, or null for the current directory
 * @param timeout the resolved timeout in seconds (positive)
 * @param env the resolved environment variables
 * @param useShell whether to execute the command via an OS shell
 * @param outputFormat the shape of the output message payload
 * @param failureMode how to react to a non-zero exit code or timeout
 * @param includeOutput whether structured payloads embed stdout/stderr text
 * @param includeInput whether structured payloads embed the original input payload
 * @param captureStderr whether stderr is captured separately instead of merged into stdout
 * @param maxOutputLines maximum output lines retained per stream (0 = unlimited)
 * @param maxOutputBytes maximum output bytes retained per stream (0 = unlimited)
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
    boolean includeOutput,
    boolean includeInput,
    boolean captureStderr,
    int maxOutputLines,
    long maxOutputBytes) {}
