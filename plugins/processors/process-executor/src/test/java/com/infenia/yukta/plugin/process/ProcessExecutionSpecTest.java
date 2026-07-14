// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Infenia Private Limited
package com.infenia.yukta.plugin.process;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Test;

@SuppressWarnings("PMD")
@NoArgsConstructor
class ProcessExecutionSpecTest {

  @Test
  void builder_minimal_appliesDocumentedDefaults() {
    final ProcessExecutionSpec spec = ProcessExecutionSpec.builder().build();

    assertThat(spec.command()).isEmpty();
    assertThat(spec.env()).isEmpty();
    assertThat(spec.timeoutSeconds()).isEqualTo(ProcessExecutionSpec.DEFAULT_TIMEOUT_SECONDS);
    assertThat(spec.maxOutputLines()).isEqualTo(ProcessExecutionSpec.UNLIMITED);
    assertThat(spec.maxOutputBytes()).isEqualTo(ProcessExecutionSpec.UNLIMITED);
    assertThat(spec.workingDir()).isNull();
    assertThat(spec.stdin()).isNull();
    assertThat(spec.useShell()).isFalse();
    assertThat(spec.captureStderr()).isFalse();
  }

  @Test
  void builder_explicitValues_retained() {
    final ProcessExecutionSpec spec =
        ProcessExecutionSpec.builder()
            .command(List.of("echo", "hi"))
            .workingDir("/tmp")
            .timeoutSeconds(10L)
            .env(Map.of("K", "V"))
            .useShell(true)
            .stdin("input")
            .captureStderr(true)
            .maxOutputLines(5)
            .maxOutputBytes(1024L)
            .build();

    assertThat(spec.command()).containsExactly("echo", "hi");
    assertThat(spec.workingDir()).isEqualTo("/tmp");
    assertThat(spec.timeoutSeconds()).isEqualTo(10L);
    assertThat(spec.env()).containsEntry("K", "V");
    assertThat(spec.useShell()).isTrue();
    assertThat(spec.stdin()).isEqualTo("input");
    assertThat(spec.captureStderr()).isTrue();
    assertThat(spec.maxOutputLines()).isEqualTo(5);
    assertThat(spec.maxOutputBytes()).isEqualTo(1024L);
  }

  @Test
  void builder_negativeTimeout_fallsBackToDefault() {
    final ProcessExecutionSpec spec = ProcessExecutionSpec.builder().timeoutSeconds(-5L).build();

    assertThat(spec.timeoutSeconds()).isEqualTo(ProcessExecutionSpec.DEFAULT_TIMEOUT_SECONDS);
  }

  @Test
  void builder_negativeCaps_normalizedToUnlimited() {
    final ProcessExecutionSpec spec =
        ProcessExecutionSpec.builder().maxOutputLines(-1).maxOutputBytes(-1L).build();

    assertThat(spec.maxOutputLines()).isEqualTo(ProcessExecutionSpec.UNLIMITED);
    assertThat(spec.maxOutputBytes()).isEqualTo(ProcessExecutionSpec.UNLIMITED);
  }

  @Test
  void constructor_defensivelyCopiesCollections() {
    final List<String> command = new ArrayList<>(List.of("echo"));
    final Map<String, String> env = new HashMap<>(Map.of("K", "V"));

    final ProcessExecutionSpec spec =
        ProcessExecutionSpec.builder().command(command).env(env).build();
    command.add("mutated");
    env.put("K2", "V2");

    assertThat(spec.command()).containsExactly("echo");
    assertThat(spec.env()).containsOnlyKeys("K");
  }
}
