/*
 * Copyright 2026 Infenia Private Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.infenia.yukta.plugin.process;

import com.infenia.yukta.plugin.message.DefaultMessage;
import com.infenia.yukta.plugin.message.Message;
import com.infenia.yukta.plugin.type.ProcessorPlugin;
import com.infenia.yukta.util.MapUtils;
import com.infenia.yukta.util.VariableResolver;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
@Slf4j
@Component
@RequiredArgsConstructor
@SuppressWarnings({"PMD.OnlyOneReturn", "PMD.UseConcurrentHashMap", "PMD.AvoidDuplicateLiterals"})
public class ProcessExecutorPlugin implements ProcessorPlugin {

  private static final String TYPE = "PROCESS_EXECUTOR";
  private static final String METADATA = "METADATA";
  private static final String PAYLOAD = "PAYLOAD";
  private static final String OUTPUT_KEY = "process.output";
  private static final String COMMAND = "command";

  private final ProcessExecutorGateway gateway;
  private final VariableResolver resolver;

  @Override
  public String getType() {
    return TYPE;
  }

  @Override
  public String getDescription() {
    return "Executes external processes with reactive streaming output. Supports multi-OS "
        + "(Linux, macOS, Windows) with shell wrapping and proper cleanup on timeout/error. "
        + "IMPORTANT: Large output in non-streaming mode may cause memory issues. "
        + "For large outputs, use streamOutput=true for per-line streaming. "
        + "SECURITY: Do not export sensitive data (passwords, API keys) via metadata.";
  }

  @Override
  public String getUsagePattern() {
    return """
            Configuration Parameters:
            
            REQUIRED:
            - command: List<String> (SpEL supported)
              Command and arguments to execute
              Example: ["mvn", "clean", "test"] or ["./gradlew", "build"]
            
            OPTIONAL:
            - workingDir: String (SpEL supported, default: current directory)
              Working directory for process execution
              Example: "/path/to/project"
            
            - timeout: Long (SpEL supported, default: 300 seconds)
              Maximum execution time in seconds. Must be positive.
              Example: 600 (for 10-minute timeout)
            
            - env: Map<String, String> (SpEL supported, default: empty)
              Environment variables for the process
              Example: {"NODE_ENV": "production", "DEBUG": "true"}
            
            - useShell: Boolean (default: false)
              Execute command via shell (sh on Unix, cmd.exe on Windows)
              Use true only if command contains pipes, redirects, or other shell features
            
            - streamOutput: Boolean (default: false)
              Stream output per line (true) vs buffer entire output (false)
              Recommended: true for long-running processes or large outputs
            
            - outputTarget: String (default: "PAYLOAD")
              Where to store output: "PAYLOAD" or "METADATA"
              PAYLOAD: Output becomes the message payload
              METADATA: Output stored in metadata["process.output"]
            
            OUTPUT BEHAVIOR:
            - streamOutput=true: Emits one message per output line (recommended)
            - streamOutput=false: Buffers entire output, emits as single message
              ⚠️  WARNING: Can cause Out-of-Memory for large outputs (>100MB)
            
            EXAMPLES:
            
            Simple command (non-streaming):
              config: {"command": ["echo", "hello"]}
            
            Streaming output (recommended for large outputs):
              config: {
                "command": ["cat", "large.log"],
                "streamOutput": true
              }
            
            With environment variables:
              config: {
                "command": ["sh", "-c", "echo $MY_VAR"],
                "env": {"MY_VAR": "secret_value"},
                "useShell": true
              }
            
            With timeout and working directory:
              config: {
                "command": ["./gradlew", "test"],
                "workingDir": "/home/project",
                "timeout": 600
              }
            
            Metadata output:
              config: {
                "command": ["git", "status"],
                "outputTarget": "METADATA"
              }
            
            ERROR HANDLING:
            - Non-zero exit codes throw WorkflowExecutionException
            - Timeout (exceeding timeout seconds) throws WorkflowExecutionException
            - Invalid config (missing command) throws IllegalArgumentException
            - Invalid outputTarget defaults to PAYLOAD with warning logged
            
            MEMORY & PERFORMANCE:
            - Streaming mode: Constant memory regardless of output size
            - Non-streaming mode: Memory consumption = output size (avoid for >100MB)
            - Timeout applies to total execution time, not per-line
            - Process cleanup is automatic (success, timeout, error)
            
            SECURITY NOTES:
            - Shell injection is prevented with proper argument escaping
            - Metadata exported as YUKTA_METADATA_* env vars (visible to child processes)
            - Do NOT export passwords, API keys, or tokens via metadata
            """;
  }

  @Override
  public Flux<Message<?>> process(final Flux<Message<?>> input, final Map<String, Object> config) {
    return input.flatMapSequential(message -> executeProcess(message, config));
  }

  private Flux<Message<?>> executeProcess(
      final Message<?> message, final Map<String, Object> config) {
    return resolveConfig(config)
        .flatMapMany(
            resolvedConfig -> {
              @SuppressWarnings("unchecked")
              final List<String> command = (List<String>) resolvedConfig.get("command");
              final String workingDir = (String) resolvedConfig.get("workingDir");
              final long timeout = (long) resolvedConfig.getOrDefault("timeout", 300L);
              @SuppressWarnings("unchecked")
              final Map<String, String> env =
                  (Map<String, String>) resolvedConfig.getOrDefault("env", Map.of());
              final boolean useShell = (boolean) resolvedConfig.getOrDefault("useShell", false);
              final boolean streamOutput =
                  (boolean) resolvedConfig.getOrDefault("streamOutput", false);
              final String outputTarget =
                  (String) resolvedConfig.getOrDefault("outputTarget", "PAYLOAD");

              log.atInfo().log(
                  "Executing process: command={}, workingDir={}, timeout={}s, streamOutput={},"
                      + " outputTarget={}",
                  command,
                  workingDir,
                  timeout,
                  streamOutput,
                  outputTarget);

              if (streamOutput) {
                return gateway
                    .executeStream(command, workingDir, timeout, env, useShell)
                    .doOnNext(line -> log.atDebug().log("Process output line: {}", line))
                    .map(line -> createOutputMessage(message, line, outputTarget))
                    .doOnComplete(
                        () -> log.atInfo().log("Process completed successfully (streaming mode)"));
              } else {
                return gateway
                    .executeStream(command, workingDir, timeout, env, useShell)
                    .collectList()
                    // Restore newlines between lines that were stripped by BufferedReader.lines()
                    .map(list -> String.join("\n", list))
                    .doOnNext(
                        output ->
                            log.atInfo().log(
                                "Process completed successfully: output size={} bytes",
                                output.length()))
                    .map(output -> createOutputMessage(message, output, outputTarget))
                    .flux()
                    .doOnError(
                        error ->
                            log.atError()
                                .setCause(error)
                                .log(
                                    "Process execution failed: command={}, workingDir={}",
                                    command,
                                    workingDir));
              }
            })
        .doOnError(
            error ->
                log.atError()
                    .setCause(error)
                    .log("Configuration resolution failed for process executor"));
  }

  @SuppressWarnings("unchecked")
  private Mono<Map<String, Object>> resolveConfig(final Map<String, Object> config) {
    return Mono.zip(
            resolveCommand((List<Object>) config.get(COMMAND)),
            resolveValue(config.get("workingDir")).map(Object::toString).defaultIfEmpty(""),
            resolveValue(config.getOrDefault("timeout", 300L))
                .map(v -> MapUtils.convert(v, Long.class))
                .defaultIfEmpty(300L),
            resolveEnv((Map<String, Object>) config.getOrDefault("env", Map.of())),
            resolveValue(config.getOrDefault("useShell", false))
                .map(v -> MapUtils.convert(v, Boolean.class))
                .defaultIfEmpty(false),
            resolveValue(config.getOrDefault("streamOutput", false))
                .map(v -> MapUtils.convert(v, Boolean.class))
                .defaultIfEmpty(false),
            resolveValue(config.getOrDefault("outputTarget", PAYLOAD))
                .map(Object::toString)
                .defaultIfEmpty(PAYLOAD))
        .map(
            tuple -> {
              final Map<String, Object> resolved = new HashMap<>();
              resolved.put("command", tuple.getT1());
              resolved.put("workingDir", tuple.getT2().isEmpty() ? null : tuple.getT2());
              resolved.put("timeout", tuple.getT3());
              resolved.put("env", tuple.getT4());
              resolved.put("useShell", tuple.getT5());
              resolved.put("streamOutput", tuple.getT6());
              resolved.put("outputTarget", tuple.getT7());
              return resolved;
            });
  }

  private Mono<List<String>> resolveCommand(final List<Object> command) {
    if (command == null || command.isEmpty()) {
      log.atWarn().log("Command resolution failed: command is mandatory");
      return Mono.error(new IllegalArgumentException("command is mandatory"));
    }
    return Flux.fromIterable(command)
        .flatMapSequential(this::resolveValue)
        .map(Object::toString)
        .collectList()
        .doOnNext(resolved -> log.atDebug().log("Command resolved successfully: {}", resolved));
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
                log.atDebug().log("Environment variables resolved: {} entries", resolved.size()));
  }

  private Mono<Object> resolveValue(final Object value) {
    if (value == null) {
      return Mono.justOrEmpty(null);
    }
    return resolver.resolve(value);
  }

  private Message<?> createOutputMessage(
      final Message<?> original, final String output, final String target) {
    final String normalizedTarget = target == null ? PAYLOAD : target.toUpperCase(Locale.ROOT);

    if (METADATA.equals(normalizedTarget)) {
      final Map<String, Object> newMetadata = new HashMap<>(original.getMetadata());
      newMetadata.put(OUTPUT_KEY, output);
      log.atDebug().log("Output stored in metadata: {}", OUTPUT_KEY);
      return DefaultMessage.from(original, original.getPayload()).withMetadata(newMetadata);
    } else if (PAYLOAD.equals(normalizedTarget)) {
      log.atDebug().log("Output stored in payload: {} bytes", output.length());
      return DefaultMessage.from(original, output);
    } else {
      log.atWarn()
          .log(
              "Invalid outputTarget: '{}'. Must be 'PAYLOAD' or 'METADATA'. Using PAYLOAD.",
              target);
      return DefaultMessage.from(original, output);
    }
  }

  @Override
  public Mono<Void> validateConfig(final Map<String, Object> config) {
    if (config.get("command") == null) {
      log.atWarn().log("Configuration validation failed: command is mandatory");
      return Mono.error(new IllegalArgumentException("command is mandatory"));
    }
    log.atDebug().log("Configuration validation passed");
    return Mono.empty();
  }
}
