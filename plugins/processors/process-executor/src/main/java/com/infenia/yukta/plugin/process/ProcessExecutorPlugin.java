// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Infenia Private Limited
package com.infenia.yukta.plugin.process;

import com.infenia.yukta.message.DefaultMessage;
import com.infenia.yukta.message.Message;
import com.infenia.yukta.plugin.exception.WorkflowExecutionException;
import com.infenia.yukta.plugin.type.ProcessorPlugin;
import com.infenia.yukta.util.MapUtils;
import com.infenia.yukta.util.VariableResolver;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Processor plugin for executing external processes. supports variable resolution in command,
 * environment, and working directory.
 */
@SuppressWarnings("PMD")
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessExecutorPlugin implements ProcessorPlugin {

  private static final String TYPE = "PROCESS_EXECUTOR";
  private static final String EXIT_CODE_KEY = "exitCode";
  private static final String COMMAND = "command";

  private final ProcessExecutorGateway gateway;
  private final VariableResolver resolver;

  @Override
  public String getType() {
    return TYPE;
  }

  @Override
  public String getDescription() {
    return "Executes external processes and returns exit code. Supports multi-OS "
        + "(Linux, macOS, Windows) with shell wrapping and proper cleanup on timeout/error.";
  }

  @Override
  public String getUsagePattern() {
    // CHECKSTYLE.OFF: Indentation
    // CHECKSTYLE.OFF: TextBlockGoogleStyleFormatting
    return """
    Configuration Parameters:

    REQUIRED:
    - command: List<String> (SpEL supported)
      Command and arguments to execute
      Example: ["mvn", "clean", "test"] or ["./gradlew", "build"]

    OPTIONAL:
    - workingDir: String (SpEL supported, default: current directory)
      Working directory for process execution
    - timeout: Long (SpEL supported, default: 300 seconds)
      Maximum execution time in seconds
    - env: Map<String, String> (SpEL supported, default: empty)
      Environment variables for the process
    - useShell: Boolean (default: false)
      Execute command via shell (sh on Unix, cmd.exe on Windows)

    OUTPUT:
    - Message payload contains exit code (0 = success, non-zero = failure)
    - Error details are logged
    """;
  }

  @Override
  public Flux<Message<?>> process(final Flux<Message<?>> input, final Map<String, Object> config) {
    log.atDebug().log("Process executor starting: processing input stream");
    return input.flatMapSequential(message -> executeProcess(message, config));
  }

  private Flux<Message<?>> executeProcess(
      final Message<?> message, final Map<String, Object> config) {
    log.atDebug().log("Starting executeProcess for message");
    return resolveConfig(config)
        .flatMapMany(resolvedConfig -> executeWithResolvedConfig(message, resolvedConfig))
        .doOnError(
            error ->
                log.atError()
                    .setCause(error)
                    .log(
                        "Process executor configuration resolution failed: {}",
                        error.getMessage()));
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private Flux<Message<?>> executeWithResolvedConfig(
      final Message<?> message, final Map<String, Object> resolvedConfig) {
    @SuppressWarnings("unchecked")
    final List<String> command = (List<String>) resolvedConfig.get("command");
    final String workingDir = (String) resolvedConfig.get("workingDir");
    final long timeout = (long) resolvedConfig.getOrDefault("timeout", 300L);
    @SuppressWarnings("unchecked")
    final Map<String, String> env =
        (Map<String, String>) resolvedConfig.getOrDefault("env", Map.of());
    final boolean useShell = (boolean) resolvedConfig.getOrDefault("useShell", false);

    log.atInfo().log(
        "Starting process execution: command={}, timeout={}s, workingDir={}",
        command,
        timeout,
        workingDir);

    return (Flux)
        gateway
            .executeStream(command, workingDir, timeout, env, useShell)
            .collectList()
            .map(list -> String.join("\n", list))
            .doOnNext(
                output -> log.atInfo().setMessage("Process output:\n{}").addArgument(output).log())
            .map(_ -> createExitCodeMessage(message))
            .doOnSuccess(_ -> log.atInfo().log("Process completed successfully"))
            .onErrorMap(error -> handleExecutionError(command, workingDir, error))
            .flux();
  }

  private Throwable handleExecutionError(
      final List<String> command, final String workingDir, final Throwable error) {
    log.atError()
        .setMessage(
            "Process execution failed: command={}, workingDir={}, error type={}, error message={}")
        .addArgument(command)
        .addArgument(workingDir)
        .addArgument(error.getClass().getSimpleName())
        .addArgument(error.getMessage())
        .setCause(error)
        .log();
    if (error instanceof final WorkflowExecutionException wee) {
      log.atError()
          .setMessage("WorkflowExecutionException details: {}")
          .addArgument(wee.getMessage())
          .log();
    }
    return error;
  }

  @SuppressWarnings("unchecked")
  private Mono<Map<String, Object>> resolveConfig(final Map<String, Object> config) {
    return Mono.zip(
            resolveCommand((List<Object>) config.get(COMMAND)),
            resolveWorkingDir(config),
            resolveTimeout(config),
            resolveEnv((Map<String, Object>) config.getOrDefault("env", Map.of())),
            resolveUseShell(config))
        .map(this::buildResolvedConfig);
  }

  private Mono<String> resolveWorkingDir(final Map<String, Object> config) {
    return resolveValue(config.get("workingDir")).map(Object::toString).defaultIfEmpty("");
  }

  private Mono<Long> resolveTimeout(final Map<String, Object> config) {
    return resolveValue(config.getOrDefault("timeout", 300L))
        .map(v -> MapUtils.convert(v, Long.class))
        .defaultIfEmpty(300L);
  }

  private Mono<Boolean> resolveUseShell(final Map<String, Object> config) {
    return resolveValue(config.getOrDefault("useShell", false))
        .map(v -> MapUtils.convert(v, Boolean.class))
        .defaultIfEmpty(false);
  }

  @SuppressWarnings("rawtypes")
  private Map<String, Object> buildResolvedConfig(final reactor.util.function.Tuple5 tuple) {
    final Map<String, Object> resolved = new HashMap<>();
    @SuppressWarnings("unchecked")
    final List<String> command = (List<String>) tuple.getT1();
    final String workingDir = (String) tuple.getT2();
    final Long timeout = (Long) tuple.getT3();
    @SuppressWarnings("unchecked")
    final Map<String, String> env = (Map<String, String>) tuple.getT4();
    final Boolean useShell = (Boolean) tuple.getT5();

    resolved.put("command", command);
    if (!workingDir.isEmpty()) {
      resolved.put("workingDir", workingDir);
    }
    resolved.put("timeout", timeout);
    resolved.put("env", env);
    resolved.put("useShell", useShell);
    return resolved;
  }

  private Mono<List<String>> resolveCommand(final List<Object> command) {
    if (command == null || command.isEmpty()) {
      log.atWarn().log("Command resolution failed: command is mandatory");
      return Mono.error(new IllegalArgumentException("command is mandatory"));
    }
    return Flux.fromIterable(command)
        .flatMapSequential(this::resolveValue)
        .map(Object::toString)
        .collectList();
  }

  private Mono<Map<String, String>> resolveEnv(final Map<String, Object> env) {
    if (env == null || env.isEmpty()) {
      return Mono.just(Map.of());
    }
    return Flux.fromIterable(env.entrySet())
        .flatMapSequential(
            entry ->
                resolveValue(entry.getValue())
                    .map(val -> Map.entry(entry.getKey(), val.toString())))
        .collectMap(Map.Entry::getKey, Map.Entry::getValue)
        .doOnNext(
            resolved ->
                log.atDebug().log(
                    "Process executor environment variables resolved: {} variable(s)",
                    resolved.size()));
  }

  private Mono<Object> resolveValue(final Object value) {
    if (value == null) {
      return Mono.empty();
    }
    return resolver.resolve(value);
  }

  private Message<?> createExitCodeMessage(final Message<?> original) {
    final Map<String, Object> newMetadata = new HashMap<>(original.getMetadata());
    newMetadata.put(EXIT_CODE_KEY, 0);
    log.atDebug().log("Exit code stored in metadata: 0");
    return DefaultMessage.from(original, original.getPayload()).withMetadata(newMetadata);
  }

  @Override
  public Mono<Void> validateConfig(final Map<String, Object> config) {
    if (config.get("command") == null) {
      log.atWarn().log("Process executor configuration validation failed: command is mandatory");
      return Mono.error(new IllegalArgumentException("command is mandatory"));
    }
    return Mono.empty();
  }
}
