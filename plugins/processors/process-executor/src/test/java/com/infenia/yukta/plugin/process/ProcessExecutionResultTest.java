// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Infenia Private Limited
package com.infenia.yukta.plugin.process;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Test;

@SuppressWarnings("PMD")
@NoArgsConstructor
class ProcessExecutionResultTest {

  @Test
  void builder_nullLineLists_normalizedToEmpty() {
    final ProcessExecutionResult result = ProcessExecutionResult.builder().build();

    assertThat(result.stdoutLines()).isEmpty();
    assertThat(result.stderrLines()).isEmpty();
    assertThat(result.stdout()).isEmpty();
    assertThat(result.stderr()).isEmpty();
  }

  @Test
  void builder_lineLists_defensivelyCopied() {
    final List<String> stdout = new ArrayList<>(List.of("out"));
    final List<String> stderr = new ArrayList<>(List.of("err"));

    final ProcessExecutionResult result =
        ProcessExecutionResult.builder().stdoutLines(stdout).stderrLines(stderr).build();
    stdout.add("mutated");
    stderr.add("mutated");

    assertThat(result.stdoutLines()).containsExactly("out");
    assertThat(result.stderrLines()).containsExactly("err");
  }

  @Test
  void isSuccess_zeroExitWithinTimeout_true() {
    final ProcessExecutionResult result = ProcessExecutionResult.builder().exitCode(0).build();

    assertThat(result.isSuccess()).isTrue();
  }

  @Test
  void isSuccess_nonZeroExit_false() {
    final ProcessExecutionResult result = ProcessExecutionResult.builder().exitCode(7).build();

    assertThat(result.isSuccess()).isFalse();
  }

  @Test
  void isSuccess_timedOut_false() {
    final ProcessExecutionResult result =
        ProcessExecutionResult.builder()
            .exitCode(ProcessExecutionResult.TIMEOUT_EXIT_CODE)
            .timedOut(true)
            .build();

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.exitCode()).isEqualTo(-1);
  }

  @Test
  void stdoutAndStderr_joinLinesWithNewlines() {
    final ProcessExecutionResult result =
        ProcessExecutionResult.builder()
            .stdoutLines(List.of("a", "b"))
            .stderrLines(List.of("x", "y"))
            .durationMillis(12L)
            .outputTruncated(true)
            .build();

    assertThat(result.stdout()).isEqualTo("a\nb");
    assertThat(result.stderr()).isEqualTo("x\ny");
    assertThat(result.durationMillis()).isEqualTo(12L);
    assertThat(result.outputTruncated()).isTrue();
  }
}
