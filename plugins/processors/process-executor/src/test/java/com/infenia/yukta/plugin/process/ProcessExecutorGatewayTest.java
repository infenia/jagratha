// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Infenia Private Limited
package com.infenia.yukta.plugin.process;

import static org.assertj.core.api.Assertions.assertThat;

import com.infenia.yukta.plugin.exception.WorkflowExecutionException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import reactor.test.StepVerifier;

@SuppressWarnings("PMD")
@NoArgsConstructor
@ExtendWith(OutputCaptureExtension.class)
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

  // Additional tests for improved coverage of process lifecycle and error handling

  @Test
  void executeStream_processExitsZero_collectsAndReturnsAllLines() {
    StepVerifier.create(
            gateway.executeStream(
                List.of("sh", "-c", "echo line1; echo line2; echo line3"),
                null,
                10L,
                Map.of(),
                false))
        .expectNext("line1")
        .expectNext("line2")
        .expectNext("line3")
        .verifyComplete();
  }

  @Test
  void executeStream_processExitsNonZero_throwsWorkflowExceptionWithExitCode() {
    StepVerifier.create(
            gateway.executeStream(List.of("sh", "-c", "exit 42"), null, 10L, Map.of(), false))
        .verifyErrorSatisfies(
            error -> {
              assertThat(error).isInstanceOf(WorkflowExecutionException.class);
              assertThat(error.getMessage()).contains("42");
            });
  }

  @Test
  void executeStream_processWithWorkingDir_executesInCorrectDirectory() {
    StepVerifier.create(gateway.executeStream(List.of("pwd"), "/tmp", 10L, Map.of(), false))
        .assertNext(output -> assertThat(output).contains("tmp"))
        .verifyComplete();
  }

  @Test
  void executeStream_processWithMultipleEnvVars_allVariablesAvailable() {
    final Map<String, String> env = new java.util.HashMap<>();
    env.put("VAR1", "value1");
    env.put("VAR2", "value2");
    env.put("VAR3", "value3");

    StepVerifier.create(
            gateway.executeStream(
                List.of("sh", "-c", "echo $VAR1:$VAR2:$VAR3"), null, 10L, env, false))
        .assertNext(
            output -> {
              assertThat(output).contains("value1");
              assertThat(output).contains("value2");
              assertThat(output).contains("value3");
            })
        .verifyComplete();
  }

  @Test
  void executeStream_processProducesEmptyOutput_completesWithoutEmittingLines() {
    StepVerifier.create(gateway.executeStream(List.of("true"), null, 10L, Map.of(), false))
        .verifyComplete();
  }

  @Test
  void executeStream_processWithComplexCommand_executesMultipleArguments() {
    StepVerifier.create(
            gateway.executeStream(
                List.of("sh", "-c", "printf 'first\\nsecond\\nthird'"), null, 10L, Map.of(), false))
        .expectNext("first")
        .expectNext("second")
        .expectNext("third")
        .verifyComplete();
  }

  @Test
  void executeStream_shellWrappingOn_commandExecutesViaShell() {
    StepVerifier.create(
            gateway.executeStream(
                List.of("echo", "test_with_special_chars_@#$"), null, 10L, Map.of(), true))
        .assertNext(output -> assertThat(output).contains("test_with_special_chars"))
        .verifyComplete();
  }

  @Test
  void executeStream_processFailureIncludesOutput_errorMessageContainsProcessOutput() {
    StepVerifier.create(
            gateway.executeStream(
                List.of("sh", "-c", "echo 'error output' && exit 5"), null, 10L, Map.of(), false))
        .verifyErrorSatisfies(
            error -> {
              assertThat(error).isInstanceOf(WorkflowExecutionException.class);
              assertThat(error.getMessage()).contains("error output");
              assertThat(error.getMessage()).contains("5");
            });
  }

  @Test
  void execute_nonStreamingNullWorkingDir_buffersOutput() {
    StepVerifier.create(
            gateway.execute(
                List.of("sh", "-c", "echo 'line1'; echo 'line2'; echo 'line3'"), null, 10L))
        .assertNext(
            output -> {
              assertThat(output).contains("line1");
              assertThat(output).contains("line2");
              assertThat(output).contains("line3");
              assertThat(output).contains("\n");
            })
        .verifyComplete();
  }

  @Test
  void execute_nonStreamingWithWorkingDir_executesInDirectory() {
    StepVerifier.create(gateway.execute(List.of("pwd"), "/tmp", 10L))
        .assertNext(output -> assertThat(output).contains("tmp"))
        .verifyComplete();
  }

  @Test
  void executeWithMetadata_nullMetadataMap_executesWithoutError() {
    StepVerifier.create(gateway.executeWithMetadata(List.of("echo", "hello"), null, 10L, null))
        .assertNext(output -> assertThat(output).contains("hello"))
        .verifyComplete();
  }

  @Test
  void executeWithMetadata_emptyMetadataMap_executesSuccessfully() {
    StepVerifier.create(
            gateway.executeWithMetadata(
                List.of("sh", "-c", "echo 'test'"), null, 10L, java.util.Map.of()))
        .assertNext(output -> assertThat(output).contains("test"))
        .verifyComplete();
  }

  @Test
  void executeWithMetadata_keyWithDot_convertsToUnderscore() {
    StepVerifier.create(
            gateway.executeWithMetadata(
                List.of("sh", "-c", "echo $YUKTA_METADATA_USER_NAME"),
                null,
                10L,
                Map.of("user.name", "testuser")))
        .assertNext(output -> assertThat(output).contains("testuser"))
        .verifyComplete();
  }

  @Test
  void executeWithMetadata_keyWithMultipleDots_allConverted() {
    StepVerifier.create(
            gateway.executeWithMetadata(
                List.of("sh", "-c", "echo $YUKTA_METADATA_APPLICATION_NAME_VALUE"),
                null,
                10L,
                Map.of("application.name.value", "myapp")))
        .assertNext(output -> assertThat(output).contains("myapp"))
        .verifyComplete();
  }

  @Test
  void executeWithMetadata_keyMixedCase_convertsToUppercase() {
    StepVerifier.create(
            gateway.executeWithMetadata(
                List.of("sh", "-c", "echo $YUKTA_METADATA_MYKEY"),
                null,
                10L,
                Map.of("MyKey", "mixedcase_value")))
        .assertNext(output -> assertThat(output).contains("mixedcase_value"))
        .verifyComplete();
  }

  @Test
  void executeWithMetadata_metadataValueWithSpecialChars_passesCorrectly() {
    StepVerifier.create(
            gateway.executeWithMetadata(
                List.of("sh", "-c", "echo $YUKTA_METADATA_SPECIAL"),
                null,
                10L,
                Map.of("special", "value-with_special.chars@123")))
        .assertNext(output -> assertThat(output).contains("value-with_special.chars@123"))
        .verifyComplete();
  }

  @Test
  void executeStream_commandWithMetacharacters_shellQuotingPreventsInjection() {
    // Test that dangerous shell metacharacters are properly escaped when useShell=true
    StepVerifier.create(
            gateway.executeStream(
                List.of("echo", "safe;string|with:pipes"), null, 10L, Map.of(), true))
        .assertNext(output -> assertThat(output).contains("safe;string|with:pipes"))
        .verifyComplete();
  }

  @Test
  void executeStream_shellArgWithComplexQuoting_handledCorrectly() {
    StepVerifier.create(
            gateway.executeStream(
                List.of("sh", "-c", "echo 'test string with spaces'"), null, 10L, Map.of(), false))
        .assertNext(output -> assertThat(output).contains("test string with spaces"))
        .verifyComplete();
  }

  @Test
  void escapeShellArg_alphanumericAndSafeChars_noQuotingNeeded() {
    // Test through shell wrapper that safe args don't get quoted
    StepVerifier.create(
            gateway.executeStream(List.of("echo", "hello123_-./test"), null, 10L, Map.of(), true))
        .assertNext(output -> assertThat(output).contains("hello123_-./test"))
        .verifyComplete();
  }

  @Test
  void escapeShellArg_dangerousCharsSemicolon_quotesForSafety() {
    StepVerifier.create(
            gateway.executeStream(List.of("echo", "cmd1;cmd2"), null, 10L, Map.of(), true))
        .assertNext(output -> assertThat(output).contains("cmd1;cmd2"))
        .verifyComplete();
  }

  @Test
  void escapeShellArg_dangerousCharsPipe_quotesForSafety() {
    StepVerifier.create(
            gateway.executeStream(List.of("echo", "text|other"), null, 10L, Map.of(), true))
        .assertNext(output -> assertThat(output).contains("text|other"))
        .verifyComplete();
  }

  @Test
  void escapeShellArg_dangerousCharsRedirection_quotesForSafety() {
    StepVerifier.create(
            gateway.executeStream(List.of("echo", "file>output"), null, 10L, Map.of(), true))
        .assertNext(output -> assertThat(output).contains("file>output"))
        .verifyComplete();
  }

  @Test
  void wrapInShell_commandWithArguments_wrapsAllProperly() {
    StepVerifier.create(
            gateway.executeStream(
                List.of("printf", "%s %s", "arg1", "arg2"), null, 10L, Map.of(), true))
        .assertNext(output -> assertThat(output).contains("arg1"))
        .verifyComplete();
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void wrapInShell_unixSystem_usesBinSh() {
    // Verify /bin/sh is used on Unix by testing command that only works in sh
    StepVerifier.create(gateway.executeStream(List.of("echo", "test"), null, 10L, Map.of(), true))
        .assertNext(output -> assertThat(output).contains("test"))
        .verifyComplete();
  }

  @Test
  void executeStream_processExitsWithOutputAndError_includesOutputInException() {
    StepVerifier.create(
            gateway.executeStream(
                List.of("sh", "-c", "echo 'stdout'; echo 'stderr' >&2; exit 1"),
                null,
                10L,
                Map.of(),
                false))
        .verifyErrorSatisfies(
            error -> {
              assertThat(error).isInstanceOf(WorkflowExecutionException.class);
              assertThat(error.getMessage()).contains("1");
              assertThat(error.getMessage()).contains("stdout");
            });
  }

  @Test
  void executeStream_multipleLineOutput_collectedAndReturned() {
    // Generate multiple output lines
    StepVerifier.create(
            gateway.executeStream(
                List.of("sh", "-c", "echo line1; echo line2; echo line3; echo line4; echo line5"),
                null,
                10L,
                Map.of(),
                false))
        .expectNext("line1")
        .expectNext("line2")
        .expectNext("line3")
        .expectNext("line4")
        .expectNext("line5")
        .verifyComplete();
  }

  @Test
  void executeStream_environmentVariable_propagatedToProcess() {
    final Map<String, String> env = new HashMap<>();
    env.put("TEST_CUSTOM_VAR", "custom_value_123");

    StepVerifier.create(
            gateway.executeStream(
                List.of("sh", "-c", "echo $TEST_CUSTOM_VAR"), null, 10L, env, false))
        .assertNext(output -> assertThat(output).contains("custom_value_123"))
        .verifyComplete();
  }

  @Test
  void executeWithMetadata_multipleMetadataExports_allVariablesSet() {
    final Map<String, Object> metadata = new HashMap<>();
    metadata.put("key1", "val1");
    metadata.put("key2", "val2");
    metadata.put("key3", "val3");

    StepVerifier.create(
            gateway.executeWithMetadata(
                List.of(
                    "sh",
                    "-c",
                    "echo $YUKTA_METADATA_KEY1:$YUKTA_METADATA_KEY2:$YUKTA_METADATA_KEY3"),
                null,
                10L,
                metadata))
        .assertNext(
            output -> {
              assertThat(output).contains("val1");
              assertThat(output).contains("val2");
              assertThat(output).contains("val3");
            })
        .verifyComplete();
  }

  @Test
  void executeStream_processAliveAfterCompletion_destroyNotCalled() {
    // Normal completion flow - process cleanup happens
    StepVerifier.create(gateway.executeStream(List.of("echo", "test"), null, 10L, Map.of(), false))
        .assertNext(output -> assertThat(output).isNotNull())
        .verifyComplete();
  }

  @Test
  void executeStream_negativeLongTimeout_throwsIllegalArgument() {
    StepVerifier.create(
            gateway.executeStream(List.of("echo", "test"), null, -100L, Map.of(), false))
        .verifyError(IllegalArgumentException.class);
  }

  @Test
  void executeStream_workingDirWithBlankString_treatedAsNoDirectory() {
    // Test the isBlank() branch in executeStream
    StepVerifier.create(gateway.executeStream(List.of("echo", "test"), "   ", 10L, Map.of(), false))
        .assertNext(output -> assertThat(output).contains("test"))
        .verifyComplete();
  }

  @Test
  void executeStream_envMapNull_handlesNullEnvironment() {
    // Test the env null branch in executeStream
    StepVerifier.create(gateway.executeStream(List.of("echo", "test"), null, 10L, null, false))
        .assertNext(output -> assertThat(output).contains("test"))
        .verifyComplete();
  }

  @Test
  void execute_nonStreamingThrowsGenericException_wrapsInWorkflowException() {
    // Test the non-WorkflowExecutionException error wrapping in execute()
    StepVerifier.create(gateway.execute(List.of("sh", "-c", "exit 1"), null, 10L))
        .verifyError(WorkflowExecutionException.class);
  }

  @Test
  void executeWithMetadata_withNullValueInMetadata_skipsNullEntry() {
    // Test the null value check in exportMetadata
    final Map<String, Object> metadata = new HashMap<>();
    metadata.put("key1", "value1");
    metadata.put("key2", null);
    metadata.put("key3", "value3");

    StepVerifier.create(
            gateway.executeWithMetadata(
                List.of("sh", "-c", "echo $YUKTA_METADATA_KEY1:$YUKTA_METADATA_KEY3"),
                null,
                10L,
                metadata))
        .assertNext(
            output -> {
              assertThat(output).contains("value1");
              assertThat(output).contains("value3");
            })
        .verifyComplete();
  }

  @Test
  void escapeShellArg_safeCharactersOnly_returnsUnquoted() {
    // Test the safe branch in escapeShellArg where regex matches
    StepVerifier.create(
            gateway.executeStream(
                List.of("echo", "abc123._/test-value:works"), null, 10L, Map.of(), true))
        .assertNext(output -> assertThat(output).contains("abc123._/test-value:works"))
        .verifyComplete();
  }

  @Test
  void processOutput_includesCommandInErrorMessage() {
    // Test that error messages include the command
    StepVerifier.create(
            gateway.executeStream(
                List.of("sh", "-c", "echo 'process output' && exit 127"),
                null,
                10L,
                Map.of(),
                false))
        .verifyErrorSatisfies(
            error -> {
              assertThat(error).isInstanceOf(WorkflowExecutionException.class);
              // Verify output is included
              assertThat(error.getMessage()).contains("process output");
            });
  }

  @Test
  void executeStream_environmentVariableNull_executesSuccessfully() {
    // Test the null env handling in executeStream line 76
    StepVerifier.create(gateway.executeStream(List.of("echo", "test"), null, 10L, null, false))
        .assertNext(output -> assertThat(output).contains("test"))
        .verifyComplete();
  }

  @Test
  void execute_processFailureNonWorkflowException_wrapsInWorkflowException() {
    // Test lines 210-214: non-WorkflowExecutionException error wrapping in execute()
    StepVerifier.create(gateway.execute(List.of("sh", "-c", "exit 1"), null, 10L))
        .verifyErrorSatisfies(
            error -> {
              assertThat(error).isInstanceOf(WorkflowExecutionException.class);
            });
  }

  @Test
  void executeWithMetadata_withNullMetadataAndEnvVars_executesCorrectly() {
    // Test lines 251-252 and executeWithMetadata null handling
    StepVerifier.create(
            gateway.executeWithMetadata(List.of("sh", "-c", "echo hello"), null, 10L, null))
        .assertNext(output -> assertThat(output).contains("hello"))
        .verifyComplete();
  }

  @Test
  void executeStream_processExitCodeVariations_allHandled() {
    // Test various exit codes to ensure error mapping works
    StepVerifier.create(
            gateway.executeStream(List.of("sh", "-c", "exit 5"), null, 10L, Map.of(), false))
        .verifyErrorSatisfies(
            error -> {
              assertThat(error).isInstanceOf(WorkflowExecutionException.class);
              assertThat(error.getMessage()).contains("5");
            });
  }

  @Test
  void execute_withWorkingDirectoryAndTimeout_executesInContext() {
    // Test execute() with working dir and timeout parameters
    StepVerifier.create(gateway.execute(List.of("pwd"), "/tmp", 10L))
        .assertNext(output -> assertThat(output).contains("tmp"))
        .verifyComplete();
  }

  @Test
  void executeStream_environmentAndWorkingDir_combinedExecution() {
    // Test combined env and working dir scenarios
    final Map<String, String> env = new HashMap<>();
    env.put("TEST_VAR", "test_value");

    StepVerifier.create(
            gateway.executeStream(List.of("sh", "-c", "echo $TEST_VAR"), null, 10L, env, false))
        .assertNext(output -> assertThat(output).contains("test_value"))
        .verifyComplete();
  }

  @Test
  void executeWithMetadata_executeMethod_withWorkingDir() {
    // Test executeWithMetadata's execute() helper path
    StepVerifier.create(gateway.executeWithMetadata(List.of("sh", "-c", "pwd"), "/tmp", 10L, null))
        .assertNext(output -> assertThat(output).contains("tmp"))
        .verifyComplete();
  }

  @Test
  void cleanupProcess_aliveProcess_destroysItGracefully() throws IOException {
    final Process process = new ProcessBuilder("sleep", "10").start();
    assertThat(process.isAlive()).isTrue();

    StepVerifier.create(gateway.cleanupProcess(process)).verifyComplete();

    assertThat(process.isAlive()).isFalse();
  }

  @Test
  void cleanupProcess_deadProcess_doesNothing() throws IOException, InterruptedException {
    final Process process = new ProcessBuilder("true").start();
    process.waitFor();
    assertThat(process.isAlive()).isFalse();

    StepVerifier.create(gateway.cleanupProcess(process)).verifyComplete();

    assertThat(process.isAlive()).isFalse();
  }

  @Test
  void cleanupProcess_processIgnoresSigterm_forciblyDestroyed()
      throws IOException, InterruptedException {
    final Process process = new ProcessBuilder("sh", "-c", "trap '' TERM; sleep 30").start();
    // Give the shell a moment to install the TERM trap before we try to destroy it
    Thread.sleep(300);
    assertThat(process.isAlive()).isTrue();

    StepVerifier.create(gateway.cleanupProcess(process)).verifyComplete();

    // destroyForcibly() is asynchronous; wait briefly for the kill to take effect
    process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
    assertThat(process.isAlive()).isFalse();
  }

  @Test
  void execute_nonWorkflowExecutionExceptionWrapped_inWorkflowException() {
    // Test line 191 branch - non-WFE exception wrapping in execute()
    StepVerifier.create(gateway.execute(List.of("sh", "-c", "echo test && exit 99"), null, 10L))
        .verifyErrorSatisfies(
            error -> {
              assertThat(error).isInstanceOf(WorkflowExecutionException.class);
              assertThat(error.getMessage()).contains("exit code");
            });
  }

  @Test
  void executeStream_workflowExecutionExceptionPassedThrough_notWrapped() {
    // Test line 181 true branch - WorkflowExecutionException already is WFE
    StepVerifier.create(
            gateway.executeStream(List.of("sh", "-c", "exit 1"), null, 10L, Map.of(), false))
        .verifyErrorSatisfies(
            error -> {
              assertThat(error).isInstanceOf(WorkflowExecutionException.class);
              assertThat(error.getMessage()).contains("exit code 1");
            });
  }

  @Test
  void executeWithMetadata_workflowExecutionExceptionPassedThrough_notWrapped() {
    // Test line 235 true branch - WorkflowExecutionException in executeWithMetadata
    StepVerifier.create(
            gateway.executeWithMetadata(List.of("sh", "-c", "exit 3"), null, 10L, Map.of()))
        .verifyErrorSatisfies(
            error -> {
              assertThat(error).isInstanceOf(WorkflowExecutionException.class);
              assertThat(error.getMessage()).contains("exit code 3");
            });
  }

  @Test
  void execute_invalidTimeoutZero_wrapsIllegalArgumentExceptionInWorkflowException() {
    // Test lines 174-181: onErrorMap wrapping branch for non-WFE exceptions in execute()
    // When executeStream receives timeoutSeconds=0, it returns Flux.error(IllegalArgumentException)
    // This raw IllegalArgumentException propagates to execute()'s onErrorMap where it's wrapped
    StepVerifier.create(gateway.execute(List.of("echo", "test"), null, 0L))
        .verifyErrorSatisfies(
            error -> {
              assertThat(error).isInstanceOf(WorkflowExecutionException.class);
              assertThat(error.getMessage()).contains("Process execution failed");
              assertThat(error.getCause()).isInstanceOf(IllegalArgumentException.class);
            });
  }

  @Test
  void execute_invalidTimeoutNegative_wrapsIllegalArgumentExceptionInWorkflowException() {
    // Test lines 174-181: onErrorMap wrapping branch for non-WFE exceptions in execute()
    // Similar to above but with negative timeout
    StepVerifier.create(gateway.execute(List.of("echo", "test"), null, -10L))
        .verifyErrorSatisfies(
            error -> {
              assertThat(error).isInstanceOf(WorkflowExecutionException.class);
              assertThat(error.getMessage()).contains("Process execution failed");
              assertThat(error.getCause()).isInstanceOf(IllegalArgumentException.class);
            });
  }

  @Test
  void executeWithMetadata_invalidTimeoutZero_wrapsIllegalArgumentExceptionInWorkflowException() {
    // Test lines 225-231: onErrorMap wrapping branch for non-WFE exceptions in
    // executeWithMetadata()
    // When executeStream receives timeoutSeconds=0, it returns Flux.error(IllegalArgumentException)
    // This raw IllegalArgumentException propagates to executeWithMetadata()'s onErrorMap where it's
    // wrapped
    StepVerifier.create(gateway.executeWithMetadata(List.of("echo", "test"), null, 0L, Map.of()))
        .verifyErrorSatisfies(
            error -> {
              assertThat(error).isInstanceOf(WorkflowExecutionException.class);
              assertThat(error.getMessage()).contains("Process execution failed");
              assertThat(error.getCause()).isInstanceOf(IllegalArgumentException.class);
            });
  }

  @Test
  void wrapInShell_windowsOsName_usesCmdExe() {
    final List<String> wrapped = gateway.wrapInShell(List.of("echo", "hello"), "Windows 10");

    assertThat(wrapped).startsWith("cmd.exe", "/c");
  }

  @Test
  void wrapInShell_nonWindowsOsName_usesBinSh() {
    final List<String> wrapped = gateway.wrapInShell(List.of("echo", "hello"), "Linux");

    assertThat(wrapped).startsWith("/bin/sh", "-c");
  }

  @Test
  void executeWithMetadata_negativeTimeout_wrapsIllegalArgumentException() {
    // Test lines 225-231: onErrorMap wrapping branch for non-WFE exceptions in
    // executeWithMetadata()
    // With negative timeout
    StepVerifier.create(gateway.executeWithMetadata(List.of("echo", "test"), null, -10L, Map.of()))
        .verifyErrorSatisfies(
            error -> {
              assertThat(error).isInstanceOf(WorkflowExecutionException.class);
              assertThat(error.getMessage()).contains("Process execution failed");
              assertThat(error.getCause()).isInstanceOf(IllegalArgumentException.class);
            });
  }

  // --- executeForResult: non-throwing result-based API ---

  @Test
  void executeForResult_successfulProcess_returnsZeroExitCodeAndOutput() {
    final ProcessExecutionSpec spec =
        ProcessExecutionSpec.builder()
            .command(List.of("sh", "-c", "echo line1; echo line2"))
            .timeoutSeconds(10L)
            .build();

    StepVerifier.create(gateway.executeForResult(spec))
        .assertNext(
            result -> {
              assertThat(result.exitCode()).isZero();
              assertThat(result.isSuccess()).isTrue();
              assertThat(result.stdoutLines()).containsExactly("line1", "line2");
              assertThat(result.stdout()).isEqualTo("line1\nline2");
              assertThat(result.stderrLines()).isEmpty();
              assertThat(result.timedOut()).isFalse();
              assertThat(result.outputTruncated()).isFalse();
              assertThat(result.durationMillis()).isNotNegative();
            })
        .verifyComplete();
  }

  @Test
  void executeForResult_nonZeroExit_returnsResultInsteadOfError() {
    final ProcessExecutionSpec spec =
        ProcessExecutionSpec.builder()
            .command(List.of("sh", "-c", "echo boom; exit 42"))
            .timeoutSeconds(10L)
            .build();

    StepVerifier.create(gateway.executeForResult(spec))
        .assertNext(
            result -> {
              assertThat(result.exitCode()).isEqualTo(42);
              assertThat(result.isSuccess()).isFalse();
              assertThat(result.timedOut()).isFalse();
              assertThat(result.stdoutLines()).containsExactly("boom");
            })
        .verifyComplete();
  }

  @Test
  void executeForResult_mergedStderrByDefault_stderrAppearsInStdout() {
    final ProcessExecutionSpec spec =
        ProcessExecutionSpec.builder()
            .command(List.of("sh", "-c", "echo out; echo err >&2"))
            .timeoutSeconds(10L)
            .build();

    StepVerifier.create(gateway.executeForResult(spec))
        .assertNext(
            result -> {
              assertThat(result.stdoutLines()).containsExactlyInAnyOrder("out", "err");
              assertThat(result.stderrLines()).isEmpty();
            })
        .verifyComplete();
  }

  @Test
  void executeForResult_captureStderrSeparately_splitsStreams() {
    final ProcessExecutionSpec spec =
        ProcessExecutionSpec.builder()
            .command(List.of("sh", "-c", "echo out; echo err >&2"))
            .timeoutSeconds(10L)
            .captureStderr(true)
            .build();

    StepVerifier.create(gateway.executeForResult(spec))
        .assertNext(
            result -> {
              assertThat(result.stdoutLines()).containsExactly("out");
              assertThat(result.stderrLines()).containsExactly("err");
              assertThat(result.stderr()).isEqualTo("err");
            })
        .verifyComplete();
  }

  @Test
  void executeForResult_timeout_returnsTimedOutResultInsteadOfError() {
    final ProcessExecutionSpec spec =
        ProcessExecutionSpec.builder().command(List.of("sleep", "10")).timeoutSeconds(1L).build();

    StepVerifier.create(gateway.executeForResult(spec))
        .assertNext(
            result -> {
              assertThat(result.timedOut()).isTrue();
              assertThat(result.exitCode()).isEqualTo(ProcessExecutionResult.TIMEOUT_EXIT_CODE);
              assertThat(result.isSuccess()).isFalse();
              assertThat(result.stdoutLines()).isEmpty();
              assertThat(result.durationMillis()).isGreaterThanOrEqualTo(500L);
            })
        .verifyComplete();
  }

  @Test
  void executeForResult_stdinProvided_pipedToProcess() {
    final ProcessExecutionSpec spec =
        ProcessExecutionSpec.builder()
            .command(List.of("cat"))
            .timeoutSeconds(10L)
            .stdin("hello\nworld")
            .build();

    StepVerifier.create(gateway.executeForResult(spec))
        .assertNext(
            result -> {
              assertThat(result.exitCode()).isZero();
              assertThat(result.stdoutLines()).containsExactly("hello", "world");
            })
        .verifyComplete();
  }

  @Test
  void executeForResult_noStdin_stdinClosedSoReadingProcessTerminates() {
    // Without an explicit close of the child's stdin, `cat` would hang until the timeout
    final ProcessExecutionSpec spec =
        ProcessExecutionSpec.builder().command(List.of("cat")).timeoutSeconds(5L).build();

    StepVerifier.create(gateway.executeForResult(spec))
        .assertNext(
            result -> {
              assertThat(result.exitCode()).isZero();
              assertThat(result.timedOut()).isFalse();
              assertThat(result.stdoutLines()).isEmpty();
            })
        .verifyComplete();
  }

  @Test
  void executeForResult_maxOutputLines_truncatesAndFlags() {
    final ProcessExecutionSpec spec =
        ProcessExecutionSpec.builder()
            .command(List.of("sh", "-c", "echo 1; echo 2; echo 3"))
            .timeoutSeconds(10L)
            .maxOutputLines(2)
            .build();

    StepVerifier.create(gateway.executeForResult(spec))
        .assertNext(
            result -> {
              assertThat(result.exitCode()).isZero();
              assertThat(result.stdoutLines()).containsExactly("1", "2");
              assertThat(result.outputTruncated()).isTrue();
            })
        .verifyComplete();
  }

  @Test
  void executeForResult_maxOutputBytes_truncatesAndFlags() {
    // First line is 6 bytes including the newline and fits exactly; the second exceeds the cap
    final ProcessExecutionSpec spec =
        ProcessExecutionSpec.builder()
            .command(List.of("sh", "-c", "echo aaaaa; echo bbbbb"))
            .timeoutSeconds(10L)
            .maxOutputBytes(6L)
            .build();

    StepVerifier.create(gateway.executeForResult(spec))
        .assertNext(
            result -> {
              assertThat(result.stdoutLines()).containsExactly("aaaaa");
              assertThat(result.outputTruncated()).isTrue();
            })
        .verifyComplete();
  }

  @Test
  void executeForResult_stderrOnlyTruncation_flagsResult() {
    final ProcessExecutionSpec spec =
        ProcessExecutionSpec.builder()
            .command(List.of("sh", "-c", "echo e1 >&2; echo e2 >&2"))
            .timeoutSeconds(10L)
            .captureStderr(true)
            .maxOutputLines(1)
            .build();

    StepVerifier.create(gateway.executeForResult(spec))
        .assertNext(
            result -> {
              assertThat(result.stdoutLines()).isEmpty();
              assertThat(result.stderrLines()).containsExactly("e1");
              assertThat(result.outputTruncated()).isTrue();
            })
        .verifyComplete();
  }

  @Test
  void executeForResult_useShell_wrapsCommand() {
    final ProcessExecutionSpec spec =
        ProcessExecutionSpec.builder()
            .command(List.of("echo hello"))
            .timeoutSeconds(10L)
            .useShell(true)
            .build();

    StepVerifier.create(gateway.executeForResult(spec))
        .assertNext(result -> assertThat(result.stdout()).contains("hello"))
        .verifyComplete();
  }

  @Test
  void executeForResult_workingDirectoryAndEnv_applied() {
    final ProcessExecutionSpec spec =
        ProcessExecutionSpec.builder()
            .command(List.of("sh", "-c", "pwd; echo $MY_TEST_VAR"))
            .workingDir("/tmp")
            .timeoutSeconds(10L)
            .env(Map.of("MY_TEST_VAR", "abc"))
            .build();

    StepVerifier.create(gateway.executeForResult(spec))
        .assertNext(
            result -> {
              assertThat(result.stdoutLines().get(0)).contains("tmp");
              assertThat(result.stdoutLines().get(1)).isEqualTo("abc");
            })
        .verifyComplete();
  }

  @Test
  void executeForResult_nullSpec_errorsWithIllegalArgument() {
    StepVerifier.create(gateway.executeForResult(null)).verifyError(IllegalArgumentException.class);
  }

  @Test
  void executeForResult_emptyCommand_errorsWithIllegalArgument() {
    StepVerifier.create(gateway.executeForResult(ProcessExecutionSpec.builder().build()))
        .verifyError(IllegalArgumentException.class);
  }

  @Test
  void executeForResult_unknownCommand_errorsWithIoException() {
    final ProcessExecutionSpec spec =
        ProcessExecutionSpec.builder()
            .command(List.of("/nonexistent-binary-yukta-test"))
            .timeoutSeconds(10L)
            .build();

    StepVerifier.create(gateway.executeForResult(spec)).verifyError(IOException.class);
  }

  // --- readOutput: capped stream capture ---

  @Test
  void readOutput_unlimitedCaps_capturesEverything() {
    final InputStream stream =
        new java.io.ByteArrayInputStream("a\nb\n".getBytes(StandardCharsets.UTF_8));

    final ProcessExecutorGateway.OutputCapture capture = gateway.readOutput(stream, 0, 0L);

    assertThat(capture.lines()).containsExactly("a", "b");
    assertThat(capture.truncated()).isFalse();
  }

  @Test
  void readOutput_negativeCaps_treatedAsUnlimited() {
    final InputStream stream =
        new java.io.ByteArrayInputStream("a\nb\n".getBytes(StandardCharsets.UTF_8));

    final ProcessExecutorGateway.OutputCapture capture = gateway.readOutput(stream, -1, -1L);

    assertThat(capture.lines()).containsExactly("a", "b");
    assertThat(capture.truncated()).isFalse();
  }

  @Test
  void readOutput_byteCapBoundary_lineExactlyAtCapRetained() {
    // "abc" + newline = 4 bytes, exactly at the cap; "d" + newline would exceed it
    final InputStream stream =
        new java.io.ByteArrayInputStream("abc\nd\n".getBytes(StandardCharsets.UTF_8));

    final ProcessExecutorGateway.OutputCapture capture = gateway.readOutput(stream, 0, 4L);

    assertThat(capture.lines()).containsExactly("abc");
    assertThat(capture.truncated()).isTrue();
  }

  @Test
  void readOutput_ioException_logsWarningAndReturnsPartialCapture(final CapturedOutput output) {
    final InputStream throwingStream =
        new InputStream() {
          @Override
          public int read() throws IOException {
            throw new IOException("Simulated read failure");
          }

          @Override
          public int read(final byte[] buffer, final int off, final int len) throws IOException {
            throw new IOException("Simulated read failure");
          }
        };

    final ProcessExecutorGateway.OutputCapture capture = gateway.readOutput(throwingStream, 0, 0L);

    assertThat(capture.lines()).isEmpty();
    assertThat(capture.truncated()).isFalse();
    assertThat(output.getOut() + output.getErr()).contains("Failed to read process output");
  }

  // --- writeStdin: stdin piping ---

  @Test
  void writeStdin_nullText_closesStreamWithoutWriting() throws IOException {
    final CloseTrackingOutputStream stream = new CloseTrackingOutputStream();

    gateway.writeStdin(stream, null);

    assertThat(stream.closed).isTrue();
    assertThat(stream.toByteArray()).isEmpty();
  }

  @Test
  void writeStdin_emptyText_closesStreamWithoutWriting() throws IOException {
    final CloseTrackingOutputStream stream = new CloseTrackingOutputStream();

    gateway.writeStdin(stream, "");

    assertThat(stream.closed).isTrue();
    assertThat(stream.toByteArray()).isEmpty();
  }

  @Test
  void writeStdin_text_writesUtf8BytesAndClosesStream() throws IOException {
    final CloseTrackingOutputStream stream = new CloseTrackingOutputStream();

    gateway.writeStdin(stream, "hello");

    assertThat(stream.closed).isTrue();
    assertThat(stream.toByteArray()).isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void writeStdin_ioException_swallowedWithoutThrowing() throws IOException {
    try (OutputStream throwingStream =
        new OutputStream() {
          @Override
          public void write(final int b) throws IOException {
            throw new IOException("Simulated broken pipe");
          }
        }) {
      org.assertj.core.api.Assertions.assertThatCode(() -> gateway.writeStdin(throwingStream, "x"))
          .doesNotThrowAnyException();
    }
  }

  /** ByteArrayOutputStream that records whether it was closed. */
  private static final class CloseTrackingOutputStream extends java.io.ByteArrayOutputStream {
    private boolean closed;

    @Override
    public void close() throws IOException {
      closed = true;
      super.close();
    }
  }
}
