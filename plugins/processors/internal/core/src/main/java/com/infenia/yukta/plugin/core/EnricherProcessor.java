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
package com.infenia.yukta.plugin.core;

import com.infenia.yukta.plugin.Message;
import com.infenia.yukta.plugin.MessagingGateway;
import com.infenia.yukta.plugin.ProcessorPlugin;
import com.infenia.yukta.plugin.UiDesign;
import com.infenia.yukta.plugin.WorkflowExecutionException;
import com.infenia.yukta.util.MapUtils;
import com.infenia.yukta.util.SpelUtils;
import com.infenia.yukta.util.VariableResolver;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Implementation of the Content Enricher pattern.
 * Retrieves additional data from an external resource and appends it to the message payload.
 */
@Slf4j
@Component
@SuppressWarnings({"PMD.OnlyOneReturn", "PMD.AvoidCatchingGenericException", "PMD.LawOfDemeter"})
public class EnricherProcessor implements ProcessorPlugin {

  private static final String TYPE = "ENRICHER";

  private static final String CONFIG_SOURCE_TYPE = "sourceType";
  private static final String CONFIG_RESOURCE_REF = "resourceRef";
  private static final String CONFIG_LOOKUP_KEY = "lookupKey";
  private static final String CONFIG_TARGET_PATH = "targetPath";
  private static final String CONFIG_MAPPING = "mapping";
  private static final String CONFIG_ERROR_POLICY = "errorPolicy";
  private static final String CONFIG_ERROR_PORT = "errorPort";
  private static final String CONFIG_STRICT_MODE = "strictMode";

  private static final String SOURCE_ENVIRONMENT = "ENVIRONMENT";
  private static final String SOURCE_COMPUTATION = "COMPUTATION";
  private static final String SOURCE_EXTERNAL = "EXTERNAL";

  private static final String POLICY_FAIL = "FAIL";
  private static final String POLICY_IGNORE = "IGNORE";
  private static final String POLICY_ROUTE = "ROUTE";

  private final VariableResolver variableResolver;
  private final ApplicationContext applicationContext;

  /**
   * Constructs a new EnricherProcessor.
   *
   * @param variableResolver the variable resolver for environment lookups
   * @param applicationContext the application context for resolving gateways
   */
  public EnricherProcessor(
      final VariableResolver variableResolver, final ApplicationContext applicationContext) {
    this.variableResolver = variableResolver;
    this.applicationContext = applicationContext;
  }

  @Override
  public String getType() {
    return TYPE;
  }

  @Override
  public String getDescription() {
    return "Retrieves additional data from an external resource and appends it to the message payload.";
  }

  @Override
  public String getUsagePattern() {
    return "Configure with:\n"
        + "- sourceType: 'ENVIRONMENT', 'COMPUTATION', or 'EXTERNAL' (default).\n"
        + "- resourceRef: The ID of the MessagingGateway or service to invoke (Required for EXTERNAL).\n"
        + "- lookupKey: SpEL expression for lookup key or computation. Defaults to #payload or #payload.id.\n"
        + "- targetPath: Dot-notation path for injection. Defaults to 'payload'.\n"
        + "- mapping: Map of targetField -> sourceSpEL (e.g., {'name': '#result.fullName'}).\n"
        + "- errorPolicy: 'FAIL' (default), 'IGNORE', or 'ROUTE'.\n"
        + "- errorPort: Port for ROUTE policy. Defaults to 'error'.\n"
        + "- strictMode: Boolean. If true (default), fails if no data is returned.";
  }

  @Override
  public Optional<UiDesign> getUiDesign() {
    return Optional.of(
        new UiDesign(
            """
            <div class="flex items-center justify-center w-full h-full bg-slate-50 border-2 border-slate-200 rounded-xl">
              <svg class="w-8 h-8 text-slate-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v3m0 0v3m0-3h3m-3 0H9m12 0a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              <span class="ml-2 font-heading text-xs font-bold text-slate-700 uppercase">Enricher</span>
            </div>
            """,
            140,
            80));
  }

  @Override
  public List<String> getOutputPorts(final Map<String, Object> config) {
    final String errorPolicy = (String) config.getOrDefault(CONFIG_ERROR_POLICY, POLICY_FAIL);
    if (POLICY_ROUTE.equalsIgnoreCase(errorPolicy)) {
      final String errorPort = (String) config.getOrDefault(CONFIG_ERROR_PORT, "error");
      return List.of("default", errorPort);
    }
    return List.of("default");
  }

  @Override
  public Mono<Void> initialize(final Map<String, Object> config) {
    final String lookupKey = (String) config.get(CONFIG_LOOKUP_KEY);
    SpelUtils.preParse(lookupKey);

    @SuppressWarnings("unchecked")
    final Map<String, String> mapping = (Map<String, String>) config.get(CONFIG_MAPPING);
    if (mapping != null) {
      mapping.values().forEach(SpelUtils::preParse);
    }
    return Mono.empty();
  }

  @Override
  public Flux<Message<?>> process(final Flux<Message<?>> input, final Map<String, Object> config) {
    final String sourceType = (String) config.getOrDefault(CONFIG_SOURCE_TYPE, SOURCE_EXTERNAL);
    final String resourceRef = (String) config.get(CONFIG_RESOURCE_REF);
    final String targetPath = (String) config.getOrDefault(CONFIG_TARGET_PATH, "payload");
    final String errorPolicy = (String) config.getOrDefault(CONFIG_ERROR_POLICY, POLICY_FAIL);
    final String errorPort = (String) config.getOrDefault(CONFIG_ERROR_PORT, "error");
    final boolean strictMode = (Boolean) config.getOrDefault(CONFIG_STRICT_MODE, true);

    return input.flatMap(
        message ->
            Mono.deferContextual(
                ctx -> {
                  final String nodeId = ctx.getOrDefault("nodeId", "unknown");
                  return enrich(message, nodeId, sourceType, resourceRef, targetPath, config)
                      .flatMap(
                          result ->
                              applyResult(message, nodeId, result, targetPath, config, strictMode))
                      .switchIfEmpty(
                          Mono.defer(
                              () ->
                                  applyResult(
                                      message, nodeId, null, targetPath, config, strictMode)))
                      .onErrorResume(
                          e -> handleEnrichmentError(message, nodeId, e, errorPolicy, errorPort));
                }));
  }

  private Mono<Object> enrich(
      final Message<?> message,
      final String nodeId,
      final String sourceType,
      final String resourceRef,
      final String targetPath,
      final Map<String, Object> config) {

    final String lookupKeyExpr = (String) config.get(CONFIG_LOOKUP_KEY);
    final Mono<Object> lookupKeyMono = resolveLookupKey(message, lookupKeyExpr);

    return switch (sourceType.toUpperCase()) {
      case SOURCE_ENVIRONMENT -> lookupKeyMono.flatMap(
          key -> variableResolver.resolve("${" + key + "}"));
      case SOURCE_COMPUTATION -> lookupKeyMono;
      case SOURCE_EXTERNAL -> lookupKeyMono.flatMap(key -> invokeGateway(message, resourceRef, key));
      default -> Mono.error(new IllegalArgumentException("Unsupported sourceType: " + sourceType));
    };
  }

  private Mono<Object> resolveLookupKey(final Message<?> message, final String expression) {
    if (expression == null || expression.isBlank()) {
      final Object payload = message.getPayload();
      if (payload instanceof String || payload instanceof Number || payload instanceof Boolean) {
        return Mono.just(payload);
      }
      return SpelUtils.evaluate(
          "#payload.id",
          message,
          Map.of("payload", payload != null ? payload : Collections.emptyMap()));
    }
    return SpelUtils.evaluate(
        expression,
        message,
        Map.of(
            "payload",
            message.getPayload() != null ? message.getPayload() : Collections.emptyMap(),
            "metadata",
            message.getMetadata()));
  }

  private Mono<Object> invokeGateway(
      final Message<?> message, final String resourceRef, final Object lookupKey) {
    if (resourceRef == null || resourceRef.isBlank()) {
      return Mono.error(
          new IllegalArgumentException("resourceRef is mandatory for EXTERNAL source"));
    }

    try {
      final MessagingGateway gateway =
          applicationContext.getBean(resourceRef, MessagingGateway.class);
      final Message<Object> request = message.withPayload(lookupKey);
      return gateway.sendAndReceive(request).map(Message::getPayload);
    } catch (final Exception e) {
      return Mono.error(
          new WorkflowExecutionException("Failed to invoke gateway: " + resourceRef, e));
    }
  }

  private Mono<Message<?>> applyResult(
      final Message<?> message,
      final String nodeId,
      final Object result,
      final String targetPath,
      final Map<String, Object> config,
      final boolean strictMode) {

    if (result == null && strictMode) {
      return Mono.error(
          new WorkflowExecutionException("Enrichment returned no data in strict mode"));
    }

    if (result == null) {
      return Mono.just(message.withSourcePort("default").withAddedHistory(nodeId));
    }

    @SuppressWarnings("unchecked")
    final Map<String, String> mapping = (Map<String, String>) config.get(CONFIG_MAPPING);
    final Object dataToInject = mapResult(result, mapping, message);

    if ("payload".equalsIgnoreCase(targetPath)) {
      return Mono.just(
          message.withPayload(dataToInject).withSourcePort("default").withAddedHistory(nodeId));
    }

    final Map<String, Object> mutablePayload = MapUtils.asMutableMap(message.getPayload());
    String path = targetPath;
    if (path.startsWith("payload.")) {
      path = path.substring(8);
    }
    MapUtils.setNestedValue(mutablePayload, path, dataToInject);

    return Mono.just(
        message.withPayload(mutablePayload).withSourcePort("default").withAddedHistory(nodeId));
  }

  private Object mapResult(
      final Object result, final Map<String, String> mapping, final Message<?> message) {
    if (mapping == null || mapping.isEmpty()) {
      return result;
    }

    final Map<String, Object> mappedData = MapUtils.asMutableMap(Collections.emptyMap());
    final Map<String, Object> variables =
        Map.of(
            "payload", message.getPayload(),
            "metadata", message.getMetadata(),
            "result", result);

    mapping.forEach(
        (targetField, expression) -> {
          final Object value = SpelUtils.evaluateSync(expression, message, variables);
          MapUtils.setNestedValue(mappedData, targetField, value);
        });

    return mappedData;
  }

  private Mono<Message<?>> handleEnrichmentError(
      final Message<?> message,
      final String nodeId,
      final Throwable error,
      final String errorPolicy,
      final String errorPort) {

    if (log.isErrorEnabled()) {
      log.error("Enrichment failed for node {}: {}", nodeId, error.getMessage());
    }

    return switch (errorPolicy.toUpperCase()) {
      case POLICY_IGNORE -> Mono.just(
          message.withSourcePort("default").withAddedHistory(nodeId));
      case POLICY_ROUTE -> Mono.just(
          message
              .withSourcePort(errorPort != null ? errorPort : "error")
              .withAddedHistory(nodeId)
              .withHeader("error_message", error.getMessage()));
      case POLICY_FAIL -> Mono.error(
          error instanceof WorkflowExecutionException
              ? error
              : new WorkflowExecutionException("Enrichment failed", error));
      default -> Mono.error(new IllegalArgumentException("Unsupported errorPolicy: " + errorPolicy));
    };
  }

  @Override
  public Mono<Void> validateConfig(final Map<String, Object> config) {
    final String sourceType = (String) config.getOrDefault(CONFIG_SOURCE_TYPE, SOURCE_EXTERNAL);
    if (SOURCE_EXTERNAL.equalsIgnoreCase(sourceType)) {
      final String resourceRef = (String) config.get(CONFIG_RESOURCE_REF);
      if (resourceRef == null || resourceRef.isBlank()) {
        return Mono.error(new IllegalArgumentException("resourceRef is mandatory for EXTERNAL source"));
      }
    }

    final String errorPolicy = (String) config.getOrDefault(CONFIG_ERROR_POLICY, POLICY_FAIL);
    if (!POLICY_FAIL.equalsIgnoreCase(errorPolicy)
        && !POLICY_IGNORE.equalsIgnoreCase(errorPolicy)
        && !POLICY_ROUTE.equalsIgnoreCase(errorPolicy)) {
      return Mono.error(new IllegalArgumentException("Unsupported errorPolicy: " + errorPolicy));
    }

    return Mono.empty();
  }
}
