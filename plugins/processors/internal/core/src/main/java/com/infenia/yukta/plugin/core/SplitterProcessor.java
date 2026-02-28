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

import com.infenia.yukta.plugin.DefaultMessage;
import com.infenia.yukta.plugin.Message;
import com.infenia.yukta.plugin.ProcessorPlugin;
import com.infenia.yukta.plugin.UiDesign;
import com.infenia.yukta.util.SpelUtils;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Splitter breaks a single composite message into a sequence of individual messages.
 */
@Slf4j
@Component
@SuppressWarnings({"PMD.OnlyOneReturn", "PMD.AvoidCatchingGenericException", "PMD.CognitiveComplexity"})
public class SplitterProcessor implements ProcessorPlugin {

  private static final String TYPE = "SPLITTER";

  private static final String CONFIG_ITEMS_PATH = "itemsPath";
  private static final String CONFIG_HEADER_MAPPING = "headerMapping";
  private static final String CONFIG_PARALLEL = "parallel";
  private static final String CONFIG_CONCURRENCY = "concurrency";
  private static final String CONFIG_STRICT = "strictMode";
  private static final String CONFIG_ERROR_PORT = "errorPort";

  private static final int DEFAULT_CONCURRENCY = 1024;
  private static final String ERR_PREFIX = "WorkflowExecutionException: ";

  /** Default constructor. */
  public SplitterProcessor() {
    super();
  }

  @Override
  public String getDescription() {
    return "Breaks a single composite message into a sequence of individual messages based on a"
        + " collection field.";
  }

  @Override
  public String getUsagePattern() {
    return "Configure with:\n"
        + "- itemsPath: SpEL expression to locate the collection (e.g., '#payload.items').\n"
        + "- headerMapping: Map of parent payload fields to be promoted to child metadata.\n"
        + "- parallel: Boolean. If true (default), dispatches child messages concurrently.\n"
        + "- concurrency: Throttling level for parallel dispatch (default 1024).\n"
        + "- errorPort: Optional port for failures.\n"
        + "- strictMode: Boolean. If true (default), throws exception on failure.";
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
            <div class="flex flex-col items-center justify-center h-full space-y-1 relative">
                <svg class="w-8 h-8 text-indigo-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 7h8M8 12h8m-8 5h8M4 4h16v16H4z"/>
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1" d="M12 7v10" opacity="0.3"/>
                </svg>
                <div class="text-[10px] text-slate-500 font-medium uppercase">Splitter</div>
            </div>
            """,
            140,
            80));
  }

  @Override
  public List<String> getOutputPorts(final Map<String, Object> config) {
    final List<String> ports = new ArrayList<>();
    ports.add("default");
    final String errorPort = (String) config.get(CONFIG_ERROR_PORT);
    if (errorPort != null && !errorPort.isBlank()) {
      ports.add(errorPort);
    }
    return ports;
  }

  @Override
  public Mono<Void> validateConfig(final Map<String, Object> config) {
    if (config.get(CONFIG_ITEMS_PATH) == null) {
      return Mono.error(new IllegalArgumentException("itemsPath is mandatory for Splitter"));
    }
    return Mono.empty();
  }

  @Override
  public Mono<Void> prepare(final Map<String, Object> config) {
    SpelUtils.preParse((String) config.get(CONFIG_ITEMS_PATH));
    final Object mapping = config.get(CONFIG_HEADER_MAPPING);
    if (mapping instanceof Map<?, ?> map) {
      map.values().stream()
          .filter(String.class::isInstance)
          .map(String.class::cast)
          .forEach(SpelUtils::preParse);
    }
    return Mono.empty();
  }

  @Override
  public Flux<Message<?>> process(final Flux<Message<?>> input, final Map<String, Object> config) {
    final String itemsPath = (String) config.get(CONFIG_ITEMS_PATH);
    @SuppressWarnings("unchecked")
    final Map<String, String> headerMapping = (Map<String, String>) config.get(CONFIG_HEADER_MAPPING);
    final boolean parallel = (Boolean) config.getOrDefault(CONFIG_PARALLEL, true);
    final int concurrency = ((Number) config.getOrDefault(CONFIG_CONCURRENCY, DEFAULT_CONCURRENCY)).intValue();
    final boolean strictMode = (Boolean) config.getOrDefault(CONFIG_STRICT, true);
    final String errorPort = (String) config.get(CONFIG_ERROR_PORT);

    if (parallel) {
      return input.flatMap(
          msg -> split(msg, itemsPath, headerMapping, strictMode, errorPort), concurrency);
    }
    return input.concatMap(msg -> split(msg, itemsPath, headerMapping, strictMode, errorPort));
  }

  private Flux<Message<?>> split(
      final Message<?> parent,
      final String itemsPath,
      final Map<String, String> headerMapping,
      final boolean strictMode,
      final String errorPort) {

    return Flux.deferContextual(
        ctx -> {
          final String nodeId = ctx.getOrDefault("nodeId", "unknown");
          try {
            final Object items = evaluateItems(parent, itemsPath);
            if (items == null) {
              if (strictMode) {
                throw new RuntimeException("itemsPath resolved to null and strictMode is enabled");
              }
              return Flux.empty();
            }

            final int size = (items instanceof Collection) ? ((Collection<?>) items).size() : 0;
            final Iterator<?> iterator = coerceToIterator(items);

            final AtomicInteger index = new AtomicInteger(1);

            final Stream<Message<?>> messageStream =
                StreamSupport.stream(
                        Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED), false)
                    .map(
                        item -> {
                          final int currentIdx = index.getAndIncrement();
                          final boolean last = !iterator.hasNext();
                          return createChildMessage(
                              parent,
                              item,
                              currentIdx,
                              last ? currentIdx : size,
                              headerMapping,
                              nodeId,
                              strictMode);
                        });

            return Flux.fromStream(messageStream)
                .onErrorResume(e -> handleSplitError(parent, nodeId, errorPort, strictMode, e));

          } catch (final Exception e) {
            return handleSplitError(parent, nodeId, errorPort, strictMode, e);
          }
        });
  }

  private Object evaluateItems(final Message<?> message, final String itemsPath) {
    final Map<String, Object> variables = Map.of(
        "payload", message.getPayload(),
        "metadata", message.getMetadata());
    return SpelUtils.evaluateSync(itemsPath, message, variables);
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
    if (items.getClass().isArray()) {
      return Arrays.asList((Object[]) items).iterator();
    }
    return Collections.singletonList(items).iterator();
  }

  private Message<?> createChildMessage(
      final Message<?> parent,
      final Object item,
      final int index,
      final int total,
      final Map<String, String> headerMapping,
      final String nodeId,
      final boolean strictMode) {

    Message<?> child =
        DefaultMessage.create(null, item)
            .withTraceId(parent.getTraceId())
            .withPriority(parent.getPriority())
            .withCorrelationId(parent.getMessageId())
            .withSequence(parent.getMessageId(), index, total)
            .withMetadata(parent.getMetadata())
            .withReplyTo(parent.getReplyTo())
            .withTimestamp(Instant.ofEpochMilli(parent.getTimestamp()));

    if (parent.getExpiration() > 0) {
      child = child.withExpiration(parent.getExpiration());
    }
    if (parent.getFormatIndicator() != null) {
      child = child.withFormatIndicator(parent.getFormatIndicator());
    }

    if (headerMapping != null) {
      for (final Map.Entry<String, String> entry : headerMapping.entrySet()) {
        final String targetHeader = entry.getKey();
        final String sourcePath = entry.getValue();
        try {
          final Object value = SpelUtils.evaluateSync(sourcePath, parent, Map.of("payload", parent.getPayload(), "metadata", parent.getMetadata()));
          if (value == null && strictMode) {
             throw new RuntimeException("Header mapping for '" + targetHeader + "' resolved to null");
          }
          if (value != null) {
            child = child.withHeader(targetHeader, value);
          }
        } catch (final Exception e) {
          if (strictMode) {
            throw new RuntimeException("Failed to evaluate header mapping for '" + targetHeader + "': " + e.getMessage(), e);
          }
        }
      }
    }

    return child.withAddedHistory(nodeId).withSourcePort("default");
  }

  private Flux<Message<?>> handleSplitError(
      final Message<?> parent,
      final String nodeId,
      final String errorPort,
      final boolean strictMode,
      final Throwable err) {

    if (log.isErrorEnabled()) {
      log.error("Splitter failed for message {}: {}", parent.getMessageId(), err.getMessage());
    }

    if (errorPort != null && !errorPort.isBlank()) {
      return Flux.just(
          parent
              .withSourcePort(errorPort)
              .withAddedHistory(nodeId)
              .withFailure(null, "Splitter failed", err.getMessage()));
    }

    if (!strictMode) {
      return Flux.empty();
    }

    return Flux.error(new RuntimeException(ERR_PREFIX + "Splitter failed", err));
  }
}
