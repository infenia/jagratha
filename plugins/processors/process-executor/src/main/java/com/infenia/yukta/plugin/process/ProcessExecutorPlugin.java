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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple5;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Processor plugin for executing external processes.
 *
 * <p>Supports variable resolution in command, environment, and working directory. The output
 * message payload is configurable via {@code outputFormat} (structured map, raw stdout, parsed
 * JSON, or legacy passthrough) and failure handling via {@code failureMode} (fail the node or emit
 * the result with the real exit code so downstream nodes can route on it).
 */
@SuppressWarnings("PMD")
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessExecutorPlugin implements ProcessorPlugin {

  private static final String TYPE = "PROCESS_EXECUTOR";

  private static final String COMMAND = "command";
  private static final String TIMEOUT = "timeout";
  private static final String OUTPUT_FORMAT = "outputFormat";
  private static final String FAILURE_MODE = "failureMode";

  private static final String EXIT_CODE_KEY = "exitCode";
  private static final String OUTPUT_KEY = "output";

  private static final long DEFAULT_TIMEOUT_SECONDS = 300L;
  private static final int DEFAULT_MAX_OUTPUT_LINES = 10_000;
  private static final long DEFAULT_MAX_OUTPUT_BYTES = 10L * 1024 * 1024;

  private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

  private final ProcessExecutorGateway gateway;
  private final VariableResolver resolver;

  @Override
  public String getType() {
    return TYPE;
  }

  @Override
  public String getDescription() {
    return "Executes external processes and emits a configurable result (exit code, output, "
        + "duration). Supports multi-OS (Linux, macOS, Windows) with shell wrapping, stderr "
        + "capture, output caps, and proper cleanup on timeout/error.";
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

    OPTIONAL (execution):
    - workingDir: String (SpEL supported, default: current directory)
    - timeout: Long (SpEL supported, default: 300 seconds, must be positive)
    - env: Map<String, String> (SpEL supported, default: empty)
    - useShell: Boolean (default: false)
      Execute command via shell (sh on Unix, cmd.exe on Windows)
    - captureStderr: Boolean (default: false)
      Capture stderr separately instead of merging it into stdout
    - maxOutputLines: Integer (default: 10000, 0 = unlimited)
    - maxOutputBytes: Long (default: 10485760, 0 = unlimited)
      Output beyond the caps is discarded and the result is flagged as truncated

    OPTIONAL (output):
    - outputFormat: STRUCTURED | RAW | JSON | PASSTHROUGH (default: STRUCTURED)
      STRUCTURED: payload = {exitCode, success, timedOut, durationMillis, outputTruncated,
                             stdout?, stderr?, input?}
      RAW: payload = process stdout as a single string
      JSON: STRUCTURED plus "output" = stdout parsed as JSON (null if the process failed)
      PASSTHROUGH: input payload forwarded unchanged (legacy behavior)
    - failureMode: ERROR | CONTINUE (default: ERROR)
      ERROR: non-zero exit code or timeout fails the workflow node
      CONTINUE: the result message is emitted with the real exit code so downstream
                nodes can route on it (e.g. payload.exitCode == 0)
    - includeOutput: Boolean (default: true)
      Include stdout/stderr text in STRUCTURED/JSON payloads
    - includeInput: Boolean (default: false)
      Include the original input payload under "input" in STRUCTURED/JSON payloads

    OUTPUT:
    - Message metadata always contains "exitCode" (real exit code; -1 on timeout)
    - JSON parse failures follow failureMode (ERROR: node fails; CONTINUE: "parseError" is set)
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
        .flatMapMany(resolvedConfig -> executeWithConfig(message, resolvedConfig))
        .doOnError(
            error ->
                log.atError()
                    .setCause(error)
                    .log("Process executor failed: {}", error.getMessage()));
  }

  private Flux<Message<?>> executeWithConfig(
      final Message<?> message, final ProcessExecutorConfig config) {
    final ProcessExecutionSpec spec =
        ProcessExecutionSpec.builder()
            .command(config.command())
            .workingDir(config.workingDir())
            .timeoutSeconds(config.timeout())
            .env(config.env())
            .useShell(config.useShell())
            .captureStderr(config.captureStderr())
            .maxOutputLines(config.maxOutputLines())
            .maxOutputBytes(config.maxOutputBytes())
            .build();

    log.atInfo().log(
        "Starting process execution: command={}, timeout={}s, workingDir={}",
        config.command(),
        config.timeout(),
        config.workingDir());

    return gateway
        .executeForResult(spec)
        .flatMapMany(result -> mapResult(message, config, result))
        .onErrorMap(error -> handleExecutionError(config, error));
  }

  private Flux<Message<?>> mapResult(
      final Message<?> message,
      final ProcessExecutorConfig config,
      final ProcessExecutionResult result) {
    if (!result.isSuccess() && config.failureMode() == FailureMode.ERROR) {
      return Flux.error(executionFailure(config, result));
    }
    log.atInfo().log(
        "Process completed: exitCode={}, timedOut={}, durationMillis={}",
        result.exitCode(),
        result.timedOut(),
        result.durationMillis());
    return Flux.defer(
        () -> Flux.just(outputMessage(message, result, buildPayload(message, config, result))));
  }

  private Object buildPayload(
      final Message<?> message,
      final ProcessExecutorConfig config,
      final ProcessExecutionResult result) {
    return switch (config.outputFormat()) {
      case RAW -> result.stdout();
      case PASSTHROUGH -> message.getPayload();
      case STRUCTURED -> structuredPayload(message, config, result);
      case JSON -> jsonPayload(message, config, result);
    };
  }

  private Map<String, Object> structuredPayload(
      final Message<?> message,
      final ProcessExecutorConfig config,
      final ProcessExecutionResult result) {
    final Map<String, Object> payload = new LinkedHashMap<>();
    payload.put(EXIT_CODE_KEY, result.exitCode());
    payload.put("success", result.isSuccess());
    payload.put("timedOut", result.timedOut());
    payload.put("durationMillis", result.durationMillis());
    payload.put("outputTruncated", result.outputTruncated());
    if (config.includeOutput()) {
      payload.put("stdout", result.stdout());
      payload.put("stderr", result.stderr());
    }
    if (config.includeInput()) {
      payload.put("input", message.getPayload());
    }
    return payload;
  }

  private Map<String, Object> jsonPayload(
      final Message<?> message,
      final ProcessExecutorConfig config,
      final ProcessExecutionResult result) {
    final Map<String, Object> payload = structuredPayload(message, config, result);
    if (!result.isSuccess()) {
      payload.put(OUTPUT_KEY, null);
      return payload;
    }
    try {
      payload.put(OUTPUT_KEY, JSON_MAPPER.readValue(result.stdout(), Object.class));
    } catch (JacksonException e) {
      if (config.failureMode() == FailureMode.ERROR) {
        throw new WorkflowExecutionException(
            "Failed to parse process output as JSON: " + e.getMessage(), e);
      }
      log.atWarn()
          .setMessage("Process output is not valid JSON; continuing with parseError set")
          .setCause(e)
          .log();
      payload.put(OUTPUT_KEY, null);
      payload.put("parseError", e.getMessage());
    }
    return payload;
  }

  private Message<?> outputMessage(
      final Message<?> original, final ProcessExecutionResult result, final Object payload) {
    final Map<String, Object> newMetadata = new HashMap<>(original.getMetadata());
    newMetadata.put(EXIT_CODE_KEY, result.exitCode());
    log.atDebug().log("Exit code stored in metadata: {}", result.exitCode());
    return DefaultMessage.from(original, payload).withMetadata(newMetadata);
  }

  private WorkflowExecutionException executionFailure(
      final ProcessExecutorConfig config, final ProcessExecutionResult result) {
    if (result.timedOut()) {
      return new WorkflowExecutionException("Process timed out after " + config.timeout() + "s");
    }
    final StringBuilder message =
        new StringBuilder("Process failed with exit code ")
            .append(result.exitCode())
            .append("\n--- Output ---\n")
            .append(result.stdout());
    if (!result.stderrLines().isEmpty()) {
      message.append("\n--- Stderr ---\n").append(result.stderr());
    }
    return new WorkflowExecutionException(message.toString());
  }

  private Throwable handleExecutionError(
      final ProcessExecutorConfig config, final Throwable error) {
    log.atError()
        .setMessage(
            "Process execution failed: command={}, workingDir={}, error type={}, error message={}")
        .addArgument(config.command())
        .addArgument(config.workingDir())
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
  private Mono<ProcessExecutorConfig> resolveConfig(final Map<String, Object> config) {
    return Mono.zip(
            resolveCommand((List<Object>) config.get(COMMAND)),
            resolveWorkingDir(config),
            resolveTimeout(config),
            resolveEnv((Map<String, Object>) config.getOrDefault("env", Map.of())),
            resolveUseShell(config))
        .map(tuple -> buildConfig(tuple, config));
  }

  private ProcessExecutorConfig buildConfig(
      final Tuple5<List<String>, String, Long, Map<String, String>, Boolean> tuple,
      final Map<String, Object> config) {
    final long timeout = tuple.getT3();
    if (timeout <= 0) {
      throw new IllegalArgumentException("timeout must be positive, got: " + timeout);
    }
    final String workingDir = tuple.getT2();
    return ProcessExecutorConfig.builder()
        .command(tuple.getT1())
        .workingDir(workingDir.isEmpty() ? null : workingDir)
        .timeout(timeout)
        .env(tuple.getT4())
        .useShell(tuple.getT5())
        .outputFormat(OutputFormat.from(config.get(OUTPUT_FORMAT)))
        .failureMode(FailureMode.from(config.get(FAILURE_MODE)))
        .includeOutput(booleanOption(config, "includeOutput", true))
        .includeInput(booleanOption(config, "includeInput", false))
        .captureStderr(booleanOption(config, "captureStderr", false))
        .maxOutputLines(
            MapUtils.convert(
                config.getOrDefault("maxOutputLines", DEFAULT_MAX_OUTPUT_LINES), Integer.class))
        .maxOutputBytes(
            MapUtils.convert(
                config.getOrDefault("maxOutputBytes", DEFAULT_MAX_OUTPUT_BYTES), Long.class))
        .build();
  }

  private boolean booleanOption(
      final Map<String, Object> config, final String key, final boolean defaultValue) {
    return MapUtils.convert(config.getOrDefault(key, defaultValue), Boolean.class);
  }

  private Mono<String> resolveWorkingDir(final Map<String, Object> config) {
    return resolveValue(config.get("workingDir")).map(Object::toString).defaultIfEmpty("");
  }

  private Mono<Long> resolveTimeout(final Map<String, Object> config) {
    return resolveValue(config.getOrDefault(TIMEOUT, DEFAULT_TIMEOUT_SECONDS))
        .map(v -> MapUtils.convert(v, Long.class))
        .defaultIfEmpty(DEFAULT_TIMEOUT_SECONDS);
  }

  private Mono<Boolean> resolveUseShell(final Map<String, Object> config) {
    return resolveValue(config.getOrDefault("useShell", false))
        .map(v -> MapUtils.convert(v, Boolean.class))
        .defaultIfEmpty(false);
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

  @Override
  public Mono<Void> validateConfig(final Map<String, Object> config) {
    if (config.get(COMMAND) == null) {
      log.atWarn().log("Process executor configuration validation failed: command is mandatory");
      return Mono.error(new IllegalArgumentException("command is mandatory"));
    }
    final Object timeout = config.get(TIMEOUT);
    if (timeout instanceof final Number number && number.longValue() <= 0) {
      log.atWarn().log("Process executor configuration validation failed: non-positive timeout");
      return Mono.error(new IllegalArgumentException("timeout must be positive, got: " + timeout));
    }
    try {
      OutputFormat.from(config.get(OUTPUT_FORMAT));
      FailureMode.from(config.get(FAILURE_MODE));
    } catch (IllegalArgumentException e) {
      log.atWarn().log("Process executor configuration validation failed: {}", e.getMessage());
      return Mono.error(e);
    }
    return Mono.empty();
  }
}
