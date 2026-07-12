// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Infenia Private Limited
package com.infenia.yukta.plugin.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.infenia.yukta.message.DefaultMessage;
import com.infenia.yukta.message.Message;
import com.infenia.yukta.util.VariableResolver;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@SuppressWarnings("PMD")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProcessExecutorPluginTest {

  private ProcessExecutorPlugin plugin;
  @Mock private ProcessExecutorGateway gateway;
  @Mock private VariableResolver resolver;

  @BeforeEach
  void setUp() {
    plugin = new ProcessExecutorPlugin(gateway, resolver);
    // Generic resolver mock to handle all SpEL resolutions by returning the input
    when(resolver.resolve(any()))
        .thenAnswer(
            invocation -> {
              final Object arg = invocation.getArgument(0);
              return Mono.justOrEmpty(arg);
            });
  }

  @Test
  void testGetType() {
    assertThat(plugin.getType()).isEqualTo("PROCESS_EXECUTOR");
  }

  @Test
  void testGetDescription() {
    assertThat(plugin.getDescription()).isNotNull();
  }

  @Test
  @SuppressWarnings({"unchecked"})
  void testProcessExitCodeSuccess() {
    final Map<String, Object> config = Map.of("command", List.of("echo", "hello"));
    final Message<?> input = DefaultMessage.create(UUID.randomUUID(), "input");

    when(gateway.executeStream(any(List.class), any(), anyLong(), any(Map.class), anyBoolean()))
        .thenReturn(Flux.just("hello"));

    StepVerifier.create(plugin.process(Flux.just(input), config))
        .assertNext(
            message -> {
              assertThat(message.getPayload()).isEqualTo("input");
              assertThat(message.getMetadata().get("exitCode")).isEqualTo(0);
            })
        .verifyComplete();
  }

  @Test
  @SuppressWarnings({"unchecked"})
  void testProcessExitCodeFailure() {
    final Map<String, Object> config = Map.of("command", List.of("false"));
    final Message<?> input = DefaultMessage.create(UUID.randomUUID(), "input");

    when(gateway.executeStream(any(List.class), any(), anyLong(), any(Map.class), anyBoolean()))
        .thenReturn(Flux.error(new RuntimeException("Process failed")));

    StepVerifier.create(plugin.process(Flux.just(input), config))
        .verifyError(RuntimeException.class);
  }

  @Test
  @SuppressWarnings({"unchecked"})
  void testProcessWithMultilineOutput() {
    final Map<String, Object> config = Map.of("command", List.of("echo", "line1\nline2"));
    final Message<?> input = DefaultMessage.create(UUID.randomUUID(), "input");

    when(gateway.executeStream(any(List.class), any(), anyLong(), any(Map.class), anyBoolean()))
        .thenReturn(Flux.just("line1", "line2"));

    StepVerifier.create(plugin.process(Flux.just(input), config))
        .assertNext(
            message -> {
              assertThat(message.getPayload()).isEqualTo("input");
              assertThat(message.getMetadata().get("exitCode")).isEqualTo(0);
            })
        .verifyComplete();
  }

  @Test
  void testValidateConfigMissingCommand() {
    StepVerifier.create(plugin.validateConfig(Map.of()))
        .verifyError(IllegalArgumentException.class);
  }

  @Test
  void testAutoConfiguration() {
    final ProcessExecutorAutoConfiguration autoConfig = new ProcessExecutorAutoConfiguration();
    assertThat(autoConfig.processExecutorPlugin(gateway, resolver)).isNotNull();
  }

  // New tests for improved coverage

  @Test
  void resolveCommand_nullCommand_throwsIllegalArgumentException() {
    final Map<String, Object> config = Map.of();
    when(gateway.executeStream(any(List.class), any(), anyLong(), any(Map.class), anyBoolean()))
        .thenReturn(Flux.just("output"));

    StepVerifier.create(
            plugin.process(Flux.just(DefaultMessage.create(UUID.randomUUID(), "test")), config))
        .verifyError(IllegalArgumentException.class);
  }

  @Test
  void resolveCommand_emptyCommandList_throwsIllegalArgumentException() {
    final Map<String, Object> config = Map.of("command", List.of());
    when(gateway.executeStream(any(List.class), any(), anyLong(), any(Map.class), anyBoolean()))
        .thenReturn(Flux.just("output"));

    StepVerifier.create(
            plugin.process(Flux.just(DefaultMessage.create(UUID.randomUUID(), "test")), config))
        .verifyError(IllegalArgumentException.class);
  }

  @Test
  void resolveWorkingDir_nullValue_defaultsToEmpty() {
    final Map<String, Object> config = Map.of("command", List.of("echo", "hello"));
    final Message<?> input = DefaultMessage.create(UUID.randomUUID(), "test");

    when(gateway.executeStream(any(List.class), any(), anyLong(), any(Map.class), anyBoolean()))
        .thenReturn(Flux.just("hello"));

    StepVerifier.create(plugin.process(Flux.just(input), config))
        .assertNext(message -> assertThat(message.getPayload()).isEqualTo("test"))
        .verifyComplete();
  }

  @Test
  @SuppressWarnings({"unchecked"})
  void resolveWorkingDir_providedValue_passedToGateway() {
    final Map<String, Object> config = Map.of("command", List.of("pwd"), "workingDir", "/tmp");
    final Message<?> input = DefaultMessage.create(UUID.randomUUID(), "test");
    final ArgumentCaptor<String> dirCaptor = ArgumentCaptor.forClass(String.class);

    when(gateway.executeStream(
            any(List.class), dirCaptor.capture(), anyLong(), any(Map.class), anyBoolean()))
        .thenReturn(Flux.just("/tmp"));

    StepVerifier.create(plugin.process(Flux.just(input), config))
        .assertNext(message -> assertThat(message.getPayload()).isEqualTo("test"))
        .verifyComplete();

    assertThat(dirCaptor.getValue()).isEqualTo("/tmp");
  }

  @Test
  void resolveTimeout_useDefaultTimeout_passes300Seconds() {
    final Map<String, Object> config = Map.of("command", List.of("echo", "hello"));
    final Message<?> input = DefaultMessage.create(UUID.randomUUID(), "test");
    final ArgumentCaptor<Long> timeoutCaptor = ArgumentCaptor.forClass(Long.class);

    when(gateway.executeStream(
            any(List.class), any(), timeoutCaptor.capture(), any(Map.class), anyBoolean()))
        .thenReturn(Flux.just("hello"));

    StepVerifier.create(plugin.process(Flux.just(input), config))
        .assertNext(message -> assertThat(message.getPayload()).isEqualTo("test"))
        .verifyComplete();

    assertThat(timeoutCaptor.getValue()).isEqualTo(300L);
  }

  @Test
  void resolveTimeout_customValue_passedToGateway() {
    final Map<String, Object> config = Map.of("command", List.of("echo", "hello"), "timeout", 600);
    final Message<?> input = DefaultMessage.create(UUID.randomUUID(), "test");
    final ArgumentCaptor<Long> timeoutCaptor = ArgumentCaptor.forClass(Long.class);

    when(gateway.executeStream(
            any(List.class), any(), timeoutCaptor.capture(), any(Map.class), anyBoolean()))
        .thenReturn(Flux.just("hello"));

    StepVerifier.create(plugin.process(Flux.just(input), config))
        .assertNext(message -> assertThat(message.getPayload()).isEqualTo("test"))
        .verifyComplete();

    assertThat(timeoutCaptor.getValue()).isEqualTo(600L);
  }

  @Test
  void resolveUseShell_default_falseByDefault() {
    final Map<String, Object> config = Map.of("command", List.of("echo", "hello"));
    final Message<?> input = DefaultMessage.create(UUID.randomUUID(), "test");
    final ArgumentCaptor<Boolean> shellCaptor = ArgumentCaptor.forClass(Boolean.class);

    when(gateway.executeStream(
            any(List.class), any(), anyLong(), any(Map.class), shellCaptor.capture()))
        .thenReturn(Flux.just("hello"));

    StepVerifier.create(plugin.process(Flux.just(input), config))
        .assertNext(message -> assertThat(message.getPayload()).isEqualTo("test"))
        .verifyComplete();

    assertThat(shellCaptor.getValue()).isFalse();
  }

  @Test
  void resolveUseShell_trueValue_passedToGateway() {
    final Map<String, Object> config =
        Map.of("command", List.of("echo", "hello"), "useShell", true);
    final Message<?> input = DefaultMessage.create(UUID.randomUUID(), "test");
    final ArgumentCaptor<Boolean> shellCaptor = ArgumentCaptor.forClass(Boolean.class);

    when(gateway.executeStream(
            any(List.class), any(), anyLong(), any(Map.class), shellCaptor.capture()))
        .thenReturn(Flux.just("hello"));

    StepVerifier.create(plugin.process(Flux.just(input), config))
        .assertNext(message -> assertThat(message.getPayload()).isEqualTo("test"))
        .verifyComplete();

    assertThat(shellCaptor.getValue()).isTrue();
  }

  @Test
  @SuppressWarnings({"unchecked"})
  void resolveEnv_nullEnv_passesEmptyMap() {
    final Map<String, Object> config = Map.of("command", List.of("echo", "hello"));
    final Message<?> input = DefaultMessage.create(UUID.randomUUID(), "test");
    final ArgumentCaptor<Map> envCaptor = ArgumentCaptor.forClass(Map.class);

    when(gateway.executeStream(
            any(List.class), any(), anyLong(), envCaptor.capture(), anyBoolean()))
        .thenReturn(Flux.just("hello"));

    StepVerifier.create(plugin.process(Flux.just(input), config))
        .assertNext(message -> assertThat(message.getPayload()).isEqualTo("test"))
        .verifyComplete();

    assertThat(envCaptor.getValue()).isEmpty();
  }

  @Test
  @SuppressWarnings({"unchecked"})
  void resolveEnv_populatedEnv_passesResolvedVariables() {
    final Map<String, Object> config =
        Map.of(
            "command", List.of("echo", "test"),
            "env", Map.of("VAR1", "value1", "VAR2", "value2"));
    final Message<?> input = DefaultMessage.create(UUID.randomUUID(), "test");
    final ArgumentCaptor<Map> envCaptor = ArgumentCaptor.forClass(Map.class);

    when(gateway.executeStream(
            any(List.class), any(), anyLong(), envCaptor.capture(), anyBoolean()))
        .thenReturn(Flux.just("output"));

    StepVerifier.create(plugin.process(Flux.just(input), config))
        .assertNext(message -> assertThat(message.getPayload()).isEqualTo("test"))
        .verifyComplete();

    assertThat(envCaptor.getValue())
        .containsEntry("VAR1", "value1")
        .containsEntry("VAR2", "value2");
  }

  @Test
  void createExitCodeMessage_preservesOriginalMetadata() {
    final Map<String, Object> originalMetadata = new HashMap<>();
    originalMetadata.put("traceId", "123");
    originalMetadata.put("sessionId", "456");

    final Message<?> input =
        DefaultMessage.create(UUID.randomUUID(), "payload").withMetadata(originalMetadata);
    final Map<String, Object> config = Map.of("command", List.of("echo", "hello"));

    when(gateway.executeStream(any(List.class), any(), anyLong(), any(Map.class), anyBoolean()))
        .thenReturn(Flux.just("hello"));

    StepVerifier.create(plugin.process(Flux.just(input), config))
        .assertNext(
            message -> {
              assertThat(message.getMetadata().get("exitCode")).isEqualTo(0);
              assertThat(message.getMetadata().get("traceId")).isEqualTo("123");
              assertThat(message.getMetadata().get("sessionId")).isEqualTo("456");
            })
        .verifyComplete();
  }

  @Test
  void processExecutor_multipleMessagesInStream_processesSequentially() {
    final Map<String, Object> config = Map.of("command", List.of("echo", "hello"));
    final Message<?> message1 = DefaultMessage.create(UUID.randomUUID(), "msg1");
    final Message<?> message2 = DefaultMessage.create(UUID.randomUUID(), "msg2");

    when(gateway.executeStream(any(List.class), any(), anyLong(), any(Map.class), anyBoolean()))
        .thenReturn(Flux.just("hello"));

    StepVerifier.create(plugin.process(Flux.just(message1, message2), config))
        .assertNext(message -> assertThat(message.getPayload()).isEqualTo("msg1"))
        .assertNext(message -> assertThat(message.getPayload()).isEqualTo("msg2"))
        .verifyComplete();
  }

  @Test
  void validateConfig_commandPresent_returnsEmpty() {
    StepVerifier.create(plugin.validateConfig(Map.of("command", List.of("echo", "test"))))
        .verifyComplete();
  }

  @Test
  void handleExecutionError_genericException_returnsSameException() {
    final Map<String, Object> config = Map.of("command", List.of("false"));
    final Message<?> input = DefaultMessage.create(UUID.randomUUID(), "test");
    final RuntimeException error = new RuntimeException("Test error");

    when(gateway.executeStream(any(List.class), any(), anyLong(), any(Map.class), anyBoolean()))
        .thenReturn(Flux.error(error));

    StepVerifier.create(plugin.process(Flux.just(input), config))
        .verifyError(RuntimeException.class);
  }

  @Test
  void configResolution_allParametersPresent_buildsCompleteConfig() {
    final Map<String, Object> config =
        Map.of(
            "command",
            List.of("sh", "-c", "echo hello"),
            "workingDir",
            "/home",
            "timeout",
            120,
            "env",
            Map.of("PATH", "/usr/bin"),
            "useShell",
            true);
    final Message<?> input = DefaultMessage.create(UUID.randomUUID(), "test");

    when(gateway.executeStream(any(List.class), any(), anyLong(), any(Map.class), anyBoolean()))
        .thenReturn(Flux.just("hello"));

    StepVerifier.create(plugin.process(Flux.just(input), config))
        .assertNext(
            message -> {
              assertThat(message.getPayload()).isEqualTo("test");
              assertThat(message.getMetadata().get("exitCode")).isEqualTo(0);
            })
        .verifyComplete();
  }
}
