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
import com.infenia.yukta.plugin.WorkflowExecutionException;
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
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Splitter breaks a single composite message into individual messages. */
@Slf4j
@Component
@SuppressWarnings({
  "PMD.OnlyOneReturn",
  "PMD.AvoidCatchingGenericException",
  "PMD.TooManyMethods",
  "PMD.ExceptionAsFlowControl",
  "PMD.AvoidInstanceofChecksInCatchClause",
  "PMD.CyclomaticComplexity"
})
public class SplitterProcessor implements ProcessorPlugin {

  private static final String TYPE = "SPLITTER";
  private static final String CFG_ITEMS = "itemsPath";
  private static final String CFG_MAP = "headerMapping";
  private static final String CFG_PAR = "parallel";
  private static final String CFG_CONC = "concurrency";
  private static final String CFG_STRICT = "strictMode";
  private static final String CFG_ERR = "errorPort";

  private static final int DEF_CONC = 1024;
  private static final String ERR_FAIL = "Splitter failed";

  /** Default constructor. */
  public SplitterProcessor() {
    super();
  }

  @Override
  public String getDescription() {
    return "Splits composite message into individual items.";
  }

  @Override
  public String getUsagePattern() {
    return "Configure itemsPath, parallel, concurrency.";
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
                </svg>
                <div class="text-[10px] text-slate-500 font-medium uppercase">Splitter</div>
            </div>
            """,
            140,
            80));
  }

  @Override
  public List<String> getOutputPorts(final Map<String, Object> config) {
    final List<String> ports = new ArrayList<>(List.of("default"));
    final String err = (String) config.get(CFG_ERR);
    if (err != null && !err.isBlank()) {
      ports.add(err);
    }
    return ports;
  }

  @Override
  public Mono<Void> validateConfig(final Map<String, Object> cfg) {
    if (cfg.get(CFG_ITEMS) == null) {
      return Mono.error(new IllegalArgumentException("itemsPath mandatory"));
    }
    return Mono.empty();
  }

  @Override
  public Mono<Void> prepare(final Map<String, Object> cfg) {
    SpelUtils.preParse((String) cfg.get(CFG_ITEMS));
    final Object m = cfg.get(CFG_MAP);
    if (m instanceof Map<?, ?> map) {
      map.values().stream()
          .filter(String.class::isInstance)
          .map(String.class::cast)
          .forEach(SpelUtils::preParse);
    }
    return Mono.empty();
  }

  @Override
  public Flux<Message<?>> process(final Flux<Message<?>> in, final Map<String, Object> cfg) {
    final String p = (String) cfg.get(CFG_ITEMS);
    final boolean par = (Boolean) cfg.getOrDefault(CFG_PAR, true);
    final int c = ((Number) cfg.getOrDefault(CFG_CONC, DEF_CONC)).intValue();
    return par ? in.flatMap(msg -> split(msg, p, cfg), c) : in.concatMap(msg -> split(msg, p, cfg));
  }

  private Flux<Message<?>> split(
      final Message<?> parent, final String path, final Map<String, Object> cfg) {
    return Flux.deferContextual(
        ctx -> {
          final String nodeId = ctx.getOrDefault("nodeId", "unknown");
          final boolean strict = (Boolean) cfg.getOrDefault(CFG_STRICT, true);
          final String errPort = (String) cfg.get(CFG_ERR);
          try {
            final Map<String, Object> vars =
                Map.of("payload", parent.getPayload(), "metadata", parent.getMetadata());
            final Object items = SpelUtils.evaluateSync(path, parent, vars);
            if (items == null) {
              return handleNull(parent, nodeId, errPort, strict);
            }
            final List<Message<?>> res = new ArrayList<>();
            final Iterator<?> it = coerce(items);
            final int size = (items instanceof Collection) ? ((Collection<?>) items).size() : 0;
            int i = 1;
            while (it.hasNext()) {
              final Object item = it.next();
              res.add(create(parent, item, i, it.hasNext() ? size : i, cfg, nodeId, vars));
              i++;
            }
            return Flux.fromIterable(res);
          } catch (final Exception e) {
            return handleErr(parent, nodeId, errPort, strict, e);
          }
        });
  }

  private Flux<Message<?>> handleNull(
      final Message<?> p, final String n, final String ep, final boolean s) {
    if (s) {
      return handleErr(p, n, ep, true, new WorkflowExecutionException("itemsPath null"));
    }
    return Flux.empty();
  }

  private Iterator<?> coerce(final Object items) {
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

  private Message<?> create(
      final Message<?> p,
      final Object item,
      final int idx,
      final int total,
      final Map<String, Object> cfg,
      final String nodeId,
      final Map<String, Object> vars) {
    Message<?> c =
        DefaultMessage.create(null, item)
            .withTraceId(p.getTraceId())
            .withPriority(p.getPriority())
            .withCorrelationId(p.getMessageId())
            .withSequence(p.getMessageId(), idx, total)
            .withMetadata(p.getMetadata())
            .withReplyTo(p.getReplyTo())
            .withTimestamp(Instant.ofEpochMilli(p.getTimestamp()));
    if (p.getExpiration() > 0) {
      c = c.withExpiration(p.getExpiration());
    }
    if (p.getFormatIndicator() != null) {
      c = c.withFormatIndicator(p.getFormatIndicator());
    }
    @SuppressWarnings("unchecked")
    final Map<String, String> mapping = (Map<String, String>) cfg.get(CFG_MAP);
    if (mapping != null) {
      c = applyMappings(p, c, mapping, cfg, vars);
    }
    return c.withAddedHistory(nodeId).withSourcePort("default");
  }

  private Message<?> applyMappings(
      final Message<?> p,
      final Message<?> c,
      final Map<String, String> mapping,
      final Map<String, Object> cfg,
      final Map<String, Object> vars) {
    Message<?> res = c;
    final boolean strict = (Boolean) cfg.getOrDefault(CFG_STRICT, true);
    for (final Map.Entry<String, String> entry : mapping.entrySet()) {
      try {
        final Object val = SpelUtils.evaluateSync(entry.getValue(), p, vars);
        if (val == null && strict) {
          throw new WorkflowExecutionException("Mapping null");
        }
        if (val != null) {
          res = res.withHeader(entry.getKey(), val);
        }
      } catch (final Exception e) {
        if (strict) {
          throw (e instanceof WorkflowExecutionException)
              ? (WorkflowExecutionException) e
              : new WorkflowExecutionException("Mapping failed", e);
        }
      }
    }
    return res;
  }

  private Flux<Message<?>> handleErr(
      final Message<?> p, final String n, final String ep, final boolean s, final Throwable e) {
    if (log.isErrorEnabled()) {
      log.error("Splitter failed: {}", e.getMessage());
    }
    if (ep != null && !ep.isBlank()) {
      return Flux.just(
          p.withSourcePort(ep).withAddedHistory(n).withFailure(null, ERR_FAIL, e.getMessage()));
    }
    if (!s) {
      return Flux.empty();
    }
    return Flux.error(
        (e instanceof WorkflowExecutionException)
            ? e
            : new WorkflowExecutionException(ERR_FAIL, e));
  }
}
