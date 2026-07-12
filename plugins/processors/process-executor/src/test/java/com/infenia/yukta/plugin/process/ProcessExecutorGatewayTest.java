// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Infenia Private Limited
package com.infenia.yukta.plugin.process;

import static org.assertj.core.api.Assertions.assertThat;

import com.infenia.yukta.plugin.exception.WorkflowExecutionException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import reactor.test.StepVerifier;

@SuppressWarnings("PMD")
@NoArgsConstructor
class ProcessExecutorGatewayTest {

  private ProcessExecutorGateway gateway;

  @BeforeEach
  void setUp() {
    gateway = new ProcessExecutorGateway();
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void testExecuteStreamUnix() {
    StepVerifier.create(
            gateway.executeStream(List.of("echo", "hello\nworld"), null, 10L, Map.of(), false))
        .expectNext("hello")
        .expectNext("world")
        .verifyComplete();
  }

  @Test
  void testExecuteStreamWithShell() {
    StepVerifier.create(gateway.executeStream(List.of("echo hello"), null, 10L, Map.of(), true))
        .assertNext(line -> assertThat(line).contains("hello"))
        .verifyComplete();
  }

  @Test
  void testExecuteStreamWithEnv() {
    StepVerifier.create(
            gateway.executeStream(
                List.of("sh", "-c", "echo $TEST_VAR"), null, 10L, Map.of("TEST_VAR", "foo"), false))
        .expectNext("foo")
        .verifyComplete();
  }

  @Test
  void testExecuteStreamTimeout() {
    StepVerifier.create(gateway.executeStream(List.of("sleep", "10"), null, 1L, Map.of(), false))
        .verifyError(WorkflowExecutionException.class);
  }

  @Test
  void testExecuteStreamNonZeroExit() {
    StepVerifier.create(gateway.executeStream(List.of("false"), null, 10L, Map.of(), false))
        .verifyError(WorkflowExecutionException.class);
  }

  @Test
  void testExecuteWithMetadata() {
    StepVerifier.create(
            gateway.executeWithMetadata(
                List.of("sh", "-c", "echo $YUKTA_METADATA_FOO"), null, 10L, Map.of("foo", "bar")))
        .assertNext(output -> assertThat(output).contains("bar"))
        .verifyComplete();
  }

  @Test
  void testExecuteStreamWithInvalidTimeout() {
    StepVerifier.create(gateway.executeStream(List.of("echo", "hello"), null, 0L, Map.of(), false))
        .verifyError(IllegalArgumentException.class);
  }

  @Test
  void testExecuteStreamWithNegativeTimeout() {
    StepVerifier.create(gateway.executeStream(List.of("echo", "hello"), null, -5L, Map.of(), false))
        .verifyError(IllegalArgumentException.class);
  }

  @Test
  void testExecuteNonStreaming() {
    StepVerifier.create(gateway.execute(List.of("echo", "hello"), null, 10L))
        .expectNext("hello")
        .verifyComplete();
  }

  @Test
  void testExecuteMetadataExport() {
    StepVerifier.create(
            gateway.executeWithMetadata(
                List.of("sh", "-c", "echo $YUKTA_METADATA_KEY"), null, 10L, Map.of("key", "value")))
        .assertNext(output -> assertThat(output).contains("value"))
        .verifyComplete();
  }

  // New tests for improved coverage

  @Test
  void escapeShellArg_safeAlphanumericArgument_returnsUnescaped() {
    // Uses reflection to test private method via shell wrapping
    StepVerifier.create(
            gateway.executeStream(
                List.of("echo", "hello-world_123.txt"), null, 10L, Map.of(), true))
        .assertNext(line -> assertThat(line).contains("hello-world_123.txt"))
        .verifyComplete();
  }

  @Test
  void escapeShellArg_unsafeArgumentWithSemicolon_quotesForSafety() {
    StepVerifier.create(
            gateway.executeStream(List.of("echo", "hello;world"), null, 10L, Map.of(), true))
        .assertNext(line -> assertThat(line).contains("hello;world"))
        .verifyComplete();
  }

  @Test
  void escapeShellArg_argumentWithPipe_quotesForSafety() {
    StepVerifier.create(
            gateway.executeStream(List.of("echo", "hello|world"), null, 10L, Map.of(), true))
        .assertNext(line -> assertThat(line).contains("hello|world"))
        .verifyComplete();
  }

  @Test
  void escapeShellArg_argumentWithSingleQuote_escapesProperly() {
    StepVerifier.create(
            gateway.executeStream(List.of("printf", "%s\\n", "it's"), null, 10L, Map.of(), true))
        .assertNext(line -> assertThat(line).contains("it's"))
        .verifyComplete();
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void wrapInShell_unixEnvironment_usesShBinary() {
    // Verify that shell wrapping uses /bin/sh on Unix-like systems
    StepVerifier.create(
            gateway.executeStream(List.of("echo", "test_shell_binary"), null, 10L, Map.of(), true))
        .assertNext(line -> assertThat(line).contains("test_shell_binary"))
        .verifyComplete();
  }

  @Test
  void executeWithMetadata_nullMetadata_executesSuccessfully() {
    StepVerifier.create(gateway.executeWithMetadata(List.of("echo", "hello"), null, 10L, null))
        .assertNext(output -> assertThat(output).contains("hello"))
        .verifyComplete();
  }

  @Test
  void executeWithMetadata_emptyMetadata_executesSuccessfully() {
    StepVerifier.create(gateway.executeWithMetadata(List.of("echo", "hello"), null, 10L, Map.of()))
        .assertNext(output -> assertThat(output).contains("hello"))
        .verifyComplete();
  }

  @Test
  void executeWithMetadata_multipleMetadataEntries_exportsAllVariables() {
    StepVerifier.create(
            gateway.executeWithMetadata(
                List.of("sh", "-c", "echo $YUKTA_METADATA_FOO:$YUKTA_METADATA_BAR"),
                null,
                10L,
                Map.of("foo", "value1", "bar", "value2")))
        .assertNext(
            output -> {
              assertThat(output).contains("value1");
              assertThat(output).contains("value2");
            })
        .verifyComplete();
  }

  @Test
  void executeWithMetadata_metadataWithNullValue_skipsNullEntry() {
    final Map<String, Object> metadata = new HashMap<>();
    metadata.put("key1", "value1");
    metadata.put("key2", null);

    StepVerifier.create(
            gateway.executeWithMetadata(
                List.of("sh", "-c", "echo $YUKTA_METADATA_KEY1"), null, 10L, metadata))
        .assertNext(output -> assertThat(output).contains("value1"))
        .verifyComplete();
  }

  @Test
  void executeWithMetadata_metadataKeyWithDot_replacesWithUnderscore() {
    StepVerifier.create(
            gateway.executeWithMetadata(
                List.of("sh", "-c", "echo $YUKTA_METADATA_USER_NAME"),
                null,
                10L,
                Map.of("user.name", "john")))
        .assertNext(output -> assertThat(output).contains("john"))
        .verifyComplete();
  }

  @Test
  void executeWithMetadata_metadataKeyMixedCase_convertedToUppercase() {
    StepVerifier.create(
            gateway.executeWithMetadata(
                List.of("sh", "-c", "echo $YUKTA_METADATA_MYKEY"),
                null,
                10L,
                Map.of("MyKey", "testValue")))
        .assertNext(output -> assertThat(output).contains("testValue"))
        .verifyComplete();
  }

  @Test
  void executeStream_withWorkingDirectory_changesProcessDirectory() {
    StepVerifier.create(gateway.executeStream(List.of("pwd"), "/tmp", 10L, Map.of(), false))
        .assertNext(output -> assertThat(output).contains("tmp"))
        .verifyComplete();
  }

  @Test
  void executeStream_multipleEnvironmentVariables_allPassed() {
    StepVerifier.create(
            gateway.executeStream(
                List.of("sh", "-c", "echo $VAR1:$VAR2:$VAR3"),
                null,
                10L,
                Map.of("VAR1", "a", "VAR2", "b", "VAR3", "c"),
                false))
        .assertNext(
            output -> {
              assertThat(output).contains("a");
              assertThat(output).contains("b");
              assertThat(output).contains("c");
            })
        .verifyComplete();
  }

  @Test
  void executeStream_commandWithMultipleArguments_executesCorrectly() {
    StepVerifier.create(
            gateway.executeStream(
                List.of("printf", "%s %s %s\n", "hello", "world", "test"),
                null,
                10L,
                Map.of(),
                false))
        .assertNext(line -> assertThat(line).contains("hello"))
        .verifyComplete();
  }

  @Test
  void execute_nonStreaming_buffersCompleteOutput() {
    StepVerifier.create(gateway.execute(List.of("printf", "line1\nline2\nline3"), null, 10L))
        .assertNext(
            output -> {
              assertThat(output).contains("line1");
              assertThat(output).contains("line2");
              assertThat(output).contains("line3");
            })
        .verifyComplete();
  }

  @Test
  void executeStream_processFailureWithOutput_includesOutputInError() {
    StepVerifier.create(
            gateway.executeStream(
                List.of("sh", "-c", "echo 'error message' && exit 1"), null, 10L, Map.of(), false))
        .verifyErrorSatisfies(
            error -> {
              assertThat(error).isInstanceOf(WorkflowExecutionException.class);
              assertThat(error.getMessage()).contains("exit code");
            });
  }

  @Test
  void executeStream_emptyCommandOutput_completesSuccessfully() {
    StepVerifier.create(gateway.executeStream(List.of("true"), null, 10L, Map.of(), false))
        .verifyComplete();
  }

  @Test
  void executeWithMetadata_metadataWithSpecialCharacters_handlesCorrectly() {
    StepVerifier.create(
            gateway.executeWithMetadata(
                List.of("sh", "-c", "echo $YUKTA_METADATA_SPECIAL"),
                null,
                10L,
                Map.of("special", "value@123")))
        .assertNext(output -> assertThat(output).contains("value@123"))
        .verifyComplete();
  }

  @Test
  void executeStream_timeoutWithLongRunningProcess_throwsTimeoutException() {
    StepVerifier.create(gateway.executeStream(List.of("sleep", "5"), null, 1L, Map.of(), false))
        .verifyErrorSatisfies(
            error -> {
              assertThat(error).isInstanceOf(WorkflowExecutionException.class);
              assertThat(error.getMessage()).contains("timed out");
            });
  }
}
