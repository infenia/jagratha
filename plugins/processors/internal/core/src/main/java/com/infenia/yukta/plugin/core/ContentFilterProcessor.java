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
import com.infenia.yukta.plugin.ProcessorPlugin;
import com.infenia.yukta.plugin.UiDesign;
import com.infenia.yukta.plugin.WorkflowExecutionException;
import com.infenia.yukta.util.MapUtils;
import com.infenia.yukta.util.SpelUtils;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Content Filter simplifies a message by removing unimportant, redundant, or sensitive data items.
 */
@Slf4j
@Component
@SuppressWarnings({
  "PMD.OnlyOneReturn",
  "PMD.AvoidCatchingGenericException",
  "PMD.TooManyMethods",
  "PMD.CyclomaticComplexity",
  "PMD.LawOfDemeter"
})
public class ContentFilterProcessor implements ProcessorPlugin {

  private static final String TYPE = "CONTENT-FILTER";

  private static final String MODE_INCLUDE = "INCLUDE";
  private static final String MODE_EXCLUDE = "EXCLUDE";

  private static final String CONFIG_MODE = "mode";
  private static final String CONFIG_PATHS = "paths";
  private static final String CONFIG_FLATTEN = "flatten";
  private static final String CONFIG_STRICT = "strictMode";
  private static final String CONFIG_ERROR_PORT = "errorPort";

  private static final String METADATA_PREFIX = "metadata.";
  private static final String HEADERS_PREFIX = "headers.";
  private static final String SPEL_METADATA_PREFIX = "#metadata.";
  private static final String SPEL_HEADERS_PREFIX = "#headers.";

  @Override
  public String getDescription() {
    return "Simplifies a message by removing unimportant, redundant, or sensitive data items.";
  }

  @Override
  public String getUsagePattern() {
    return "Configure with:\n"
        + "- mode: 'INCLUDE' (whitelist) or 'EXCLUDE' (blacklist).\n"
        + "- paths: List of SpEL expressions (INCLUDE) or dot-notation paths (EXCLUDE).\n"
        + "- flatten: Boolean. If true, simplifies nested structures into flat maps.\n"
        + "- strictMode: Boolean. If true (default), throws exception if a mandatory path is"
        + " missing.\n"
        + "- errorPort: Optional port to route messages that fail transformation.";
  }

  @Override
  public String getType() {
    return TYPE;
  }

  @Override
  public Optional<UiDesign> getUiDesign() {
    return Optional.of(
        new UiDesign(
            """
            <div class="flex items-center justify-center w-full h-full bg-slate-50 border-2 border-slate-200 rounded-xl">
              <svg class="w-8 h-8 text-slate-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10" />
              </svg>
              <span class="ml-2 font-heading text-xs font-bold text-slate-700 uppercase">C-Filter</span>
            </div>
            """,
            140,
            80));
  }

  @Override
  public List<String> getOutputPorts(final Map<String, Object> config) {
    final List<String> ports = new ArrayList<>(List.of("default"));
    final String errorPort = (String) config.get(CONFIG_ERROR_PORT);
    if (errorPort != null && !errorPort.isBlank()) {
      ports.add(errorPort);
    }
    return ports;
  }

  @Override
  public Mono<Void> validateConfig(final Map<String, Object> config) {
    if (config.get(CONFIG_PATHS) == null) {
      return Mono.error(new IllegalArgumentException("paths is mandatory for Content Filter"));
    }
    if (!(config.get(CONFIG_PATHS) instanceof List)) {
      return Mono.error(new IllegalArgumentException("paths must be a List"));
    }
    final String mode = (String) config.getOrDefault(CONFIG_MODE, MODE_INCLUDE);
    if (!MODE_INCLUDE.equalsIgnoreCase(mode) && !MODE_EXCLUDE.equalsIgnoreCase(mode)) {
      return Mono.error(new IllegalArgumentException("mode must be INCLUDE or EXCLUDE"));
    }
    return Mono.empty();
  }

  @Override
  public Mono<Void> prepare(final Map<String, Object> config) {
    final String mode = (String) config.getOrDefault(CONFIG_MODE, MODE_INCLUDE);
    if (MODE_INCLUDE.equalsIgnoreCase(mode)) {
      @SuppressWarnings("unchecked")
      final List<String> paths = (List<String>) config.get(CONFIG_PATHS);
      paths.forEach(SpelUtils::preParse);
    }
    return Mono.empty();
  }

  @Override
  public Flux<Message<?>> process(final Flux<Message<?>> input, final Map<String, Object> config) {
    return input.flatMap(
        message ->
            Flux.deferContextual(
                ctx -> {
                  final String nodeId = ctx.getOrDefault("nodeId", "unknown");
                  return executeFilter(message, nodeId, config);
                }));
  }

  private Flux<Message<?>> executeFilter(
      final Message<?> message, final String nodeId, final Map<String, Object> config) {
    final Object payload = message.getPayload();
    if (payload instanceof Flux) {
      return ((Flux<?>) payload)
          .flatMap(item -> applyFilterToItem(message.withPayload(item), nodeId, config));
    }
    if (isIterable(payload)) {
      final Iterator<?> iterator = coerceToIterator(payload);
      return Flux.fromIterable(() -> iterator)
          .flatMap(item -> applyFilterToItem(message.withPayload(item), nodeId, config));
    } else {
      return applyFilterToItem(message, nodeId, config).flux();
    }
  }

  private boolean isIterable(final Object payload) {
    return payload instanceof Iterable
        || payload instanceof Stream
        || (payload != null && payload.getClass().isArray());
  }

  private Iterator<?> coerceToIterator(final Object items) {
    if (items instanceof Iterable) {
      return ((Iterable<?>) items).iterator();
    }
    if (items instanceof Stream) {
      return ((Stream<?>) items).iterator();
    }
    if (items instanceof Iterator) {
      return (Iterator<?>) items;
    }
    if (items != null && items.getClass().isArray()) {
      return new Iterator<Object>() {
        private int index = 0;
        private final int length = Array.getLength(items);

        @Override
        public boolean hasNext() {
          return index < length;
        }

        @Override
        public Object next() {
          return Array.get(items, index++);
        }
      };
    }
    return Collections.singletonList(items).iterator();
  }

  private Mono<Message<?>> applyFilterToItem(
      final Message<?> message, final String nodeId, final Map<String, Object> config) {
    try {
      final String mode = (String) config.getOrDefault(CONFIG_MODE, MODE_INCLUDE);
      @SuppressWarnings("unchecked")
      final List<String> paths = (List<String>) config.get(CONFIG_PATHS);
      final boolean flatten = (Boolean) config.getOrDefault(CONFIG_FLATTEN, false);
      final boolean strictMode = (Boolean) config.getOrDefault(CONFIG_STRICT, true);

      final Message<?> result;
      if (MODE_INCLUDE.equalsIgnoreCase(mode)) {
        result = includePaths(message, paths, strictMode);
      } else {
        result = excludePaths(message, paths);
      }

      Message<?> finalResult = result;
      if (flatten && finalResult.getPayload() instanceof Map) {
        @SuppressWarnings("unchecked")
        final Map<String, Object> payloadMap = (Map<String, Object>) finalResult.getPayload();
        finalResult = finalResult.withPayload(MapUtils.flatten(payloadMap));
      }

      return Mono.just(finalResult.withAddedHistory(nodeId).withSourcePort("default"));
    } catch (final Exception e) {
      final String errorPort = (String) config.get(CONFIG_ERROR_PORT);
      final boolean strictMode = (Boolean) config.getOrDefault(CONFIG_STRICT, true);
      if (errorPort != null && !errorPort.isBlank()) {
        return Mono.just(
            message
                .withSourcePort(errorPort)
                .withAddedHistory(nodeId)
                .withFailure(null, "Content Filter failed", e.getMessage()));
      }
      if (!strictMode && !(e instanceof WorkflowExecutionException)) {
        return Mono.just(message.withAddedHistory(nodeId).withSourcePort("default"));
      }
      if (e instanceof WorkflowExecutionException) {
        return Mono.error(e);
      }
      return Mono.error(new WorkflowExecutionException("Content Filter failed", e));
    }
  }

  private Message<?> includePaths(
      final Message<?> message, final List<String> paths, final boolean strictMode) {
    boolean metadataTargeted = false;
    for (final String path : paths) {
      if (isMetadataTargeted(path)) {
        metadataTargeted = true;
        break;
      }
    }

    final Map<String, Object> whitelistedMetadata = new HashMap<>();
    final List<String> coreTechnicalHeaders =
        List.of(
            "traceId",
            "correlationId",
            "messageId",
            "timestamp",
            "sequenceId",
            "sequenceNumber",
            "sequenceSize",
            "replyTo");
    if (metadataTargeted) {
      coreTechnicalHeaders.forEach(
          h -> {
            // We need to use DefaultMessage accessors for these core fields
            final Object val = getCoreHeader(message, h);
            if (val != null) {
              whitelistedMetadata.put(h, val);
            }
          });
    }

    final Map<String, Object> variables = new HashMap<>();
    variables.put("payload", message.getPayload());
    variables.put("metadata", message.getMetadata());

    final Object payload = message.getPayload();
    Object resultPayload;

    if (payload instanceof Map || payload == null) {
      final Map<String, Object> filteredPayload = new HashMap<>();
      for (final String path : paths) {
        final String cleanPath;
        final boolean isMetadata;
        final String effectiveSpel;
        if (path.startsWith(METADATA_PREFIX)) {
          cleanPath = path.substring(METADATA_PREFIX.length());
          isMetadata = true;
          effectiveSpel = "#metadata." + cleanPath;
        } else if (path.startsWith(HEADERS_PREFIX)) {
          cleanPath = path.substring(HEADERS_PREFIX.length());
          isMetadata = true;
          effectiveSpel = "#metadata." + cleanPath;
        } else if (path.startsWith(SPEL_METADATA_PREFIX)) {
          cleanPath = path.substring(SPEL_METADATA_PREFIX.length());
          isMetadata = true;
          effectiveSpel = path;
        } else if (path.startsWith(SPEL_HEADERS_PREFIX)) {
          cleanPath = path.substring(SPEL_HEADERS_PREFIX.length());
          isMetadata = true;
          effectiveSpel = path;
        } else {
          cleanPath = path;
          isMetadata = false;
          effectiveSpel = path.startsWith("#") ? path : "#payload." + cleanPath;
        }

        if (strictMode && !pathExists(message, path)) {
          throw new WorkflowExecutionException("Mandatory path missing: " + path);
        }

        final Object value = SpelUtils.evaluateSync(effectiveSpel, message, variables);
        if (isMetadata) {
          MapUtils.setNestedValue(whitelistedMetadata, cleanPath, value);
        } else {
          String targetPath = cleanPath;
          if (targetPath.startsWith("#payload.")) {
            targetPath = targetPath.substring("#payload.".length());
          }
          MapUtils.setNestedValue(filteredPayload, targetPath, value);
        }
      }
      resultPayload = filteredPayload;
    } else {
      // For non-map payloads (like primitives in a stream), projection might just return the result
      // of the SpEL
      // If multiple paths are specified for a single primitive, it doesn't make much sense unless
      // we wrap it.
      // But usually, INCLUDE mode for non-map means "keep the whole thing" or "transform it".
      // Let's stick to: if it's not a map, we just evaluate the SpEL and return that value.
      // If multiple paths, last one wins for the payload.
      Object val = payload;
      for (final String path : paths) {
        if (!isMetadataTargeted(path)) {
          val =
              SpelUtils.evaluateSync(path.startsWith("#") ? path : "#payload", message, variables);
        }
      }
      resultPayload = val;
    }

    Message<?> result = message.withPayload(resultPayload);
    if (metadataTargeted) {
      result = result.withMetadata(whitelistedMetadata);
    }
    return result;
  }

  private boolean isMetadataTargeted(final String path) {
    return path.startsWith(METADATA_PREFIX)
        || path.startsWith(HEADERS_PREFIX)
        || path.startsWith(SPEL_METADATA_PREFIX)
        || path.startsWith(SPEL_HEADERS_PREFIX);
  }

  private Message<?> excludePaths(final Message<?> message, final List<String> paths) {
    final Map<String, Object> payloadMap = MapUtils.asMutableMap(message.getPayload());
    final Map<String, Object> metadataMap = new HashMap<>(message.getMetadata());

    for (final String path : paths) {
      if (path.startsWith(METADATA_PREFIX)) {
        MapUtils.removeNestedValue(metadataMap, path.substring(METADATA_PREFIX.length()));
      } else if (path.startsWith(HEADERS_PREFIX)) {
        MapUtils.removeNestedValue(metadataMap, path.substring(HEADERS_PREFIX.length()));
      } else if (path.startsWith(SPEL_METADATA_PREFIX)) {
        MapUtils.removeNestedValue(metadataMap, path.substring(SPEL_METADATA_PREFIX.length()));
      } else if (path.startsWith(SPEL_HEADERS_PREFIX)) {
        MapUtils.removeNestedValue(metadataMap, path.substring(SPEL_HEADERS_PREFIX.length()));
      } else {
        String cleanPath = path;
        if (cleanPath.startsWith("#payload.")) {
          cleanPath = cleanPath.substring("#payload.".length());
        }
        MapUtils.removeNestedValue(payloadMap, cleanPath);
      }
    }

    return message.withPayload(payloadMap).withMetadata(metadataMap);
  }

  private boolean pathExists(final Message<?> message, final String path) {
    if (!path.startsWith("#")
        && !path.startsWith(METADATA_PREFIX)
        && !path.startsWith(HEADERS_PREFIX)) {
      final Object payload = message.getPayload();
      if (payload instanceof Map) {
        return hasKey((Map<String, Object>) payload, path);
      }
      return payload != null;
    }

    if (path.startsWith(METADATA_PREFIX) || path.startsWith(HEADERS_PREFIX)) {
      String cleanPath =
          path.startsWith(METADATA_PREFIX)
              ? path.substring(METADATA_PREFIX.length())
              : path.substring(HEADERS_PREFIX.length());
      return hasKey(message.getMetadata(), cleanPath);
    }

    String payloadPath = path;
    if (payloadPath.startsWith("#payload.")) {
      payloadPath = payloadPath.substring("#payload.".length());
    }
    if (!payloadPath.startsWith("#")) {
      final Object payload = message.getPayload();
      if (payload instanceof Map) {
        return hasKey((Map<String, Object>) payload, payloadPath);
      }
      return payload != null;
    }

    try {
      final Map<String, Object> variables = new HashMap<>();
      variables.put("payload", message.getPayload());
      variables.put("metadata", message.getMetadata());
      final String effectiveSpel = path.startsWith("#") ? path : "#payload." + path;
      Object val = SpelUtils.evaluateSync(effectiveSpel, message, variables);
      return val != null;
    } catch (Exception e) {
      return false;
    }
  }

  private boolean hasKey(Map<String, Object> map, String path) {
    String[] parts = path.split("\\.");
    Map<String, Object> current = map;
    for (int i = 0; i < parts.length - 1; i++) {
      Object next = current.get(parts[i]);
      if (next instanceof Map) {
        @SuppressWarnings("unchecked")
        final Map<String, Object> nextMap = (Map<String, Object>) next;
        current = nextMap;
      } else {
        return false;
      }
    }
    return current != null && current.containsKey(parts[parts.length - 1]);
  }

  private Object getCoreHeader(final Message<?> message, final String header) {
    return switch (header) {
      case "traceId" -> message.getTraceId();
      case "correlationId" -> message.getCorrelationId();
      case "messageId" -> message.getMessageId();
      case "timestamp" -> message.getTimestamp();
      case "sequenceId" -> message.getSequenceId();
      case "sequenceNumber" -> message.getSequenceNumber();
      case "sequenceSize" -> message.getSequenceSize();
      case "replyTo" -> message.getReplyTo();
      default -> message.getMetadata().get(header);
    };
  }
}
