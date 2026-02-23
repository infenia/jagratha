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
package com.infenia.jagratha.plugin.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.infenia.jagratha.plugin.Message;
import com.infenia.jagratha.plugin.ProcessorPlugin;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Mapper processor transforms message payloads using PROJECTION, TEMPLATE, or SCRIPT modes.
 */
@Slf4j
@Component
@SuppressWarnings({"PMD.OnlyOneReturn", "PMD.LawOfDemeter", "PMD.AvoidThrowingRawExceptionTypes", "PMD.AtLeastOneConstructor"})
public class MapperProcessor implements ProcessorPlugin {

  private static final String TYPE = "MAPPER";

  private static final String MODE_PROJECTION = "PROJECTION";
  private static final String MODE_TEMPLATE = "TEMPLATE";
  private static final String MODE_SCRIPT = "SCRIPT";

  private static final String CONFIG_MODE = "mode";
  private static final String CONFIG_MAPPING = "mapping";
  private static final String CONFIG_DROP_ORIGINAL = "dropOriginal";
  private static final String CONFIG_STRICT_MODE = "strictMode";

  private static final String ERR_PREFIX = "WorkflowExecutionException: ";

  private static final Engine JS_ENGINE = Engine.newBuilder()
      .option("engine.WarnInterpreterOnly", "false")
      .build();

  private final Handlebars handlebars = new Handlebars();
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final DefaultConversionService conversionService = new DefaultConversionService();

  private final Map<String, Template> templateCache = new ConcurrentHashMap<>();
  private final Map<String, Source> jsSourceCache = new ConcurrentHashMap<>();

  /** Default constructor. */
  public MapperProcessor() {
    super();
  }

  @Override
  public String getType() {
    return TYPE;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Mono<Void> initialize(final Map<String, Object> config) {
    final String mode = (String) config.get(CONFIG_MODE);
    final Object mapping = config.get(CONFIG_MAPPING);

    if (MODE_PROJECTION.equals(mode) && mapping instanceof Map) {
      ((Map<String, String>) mapping).values().forEach(SpelUtils::preParse);
    } else if (MODE_TEMPLATE.equals(mode)) {
      initializeTemplates(mapping);
    } else if (MODE_SCRIPT.equals(mode) && mapping instanceof String) {
      jsSourceCache.computeIfAbsent((String) mapping,
          s -> Source.newBuilder("js", s, "mapper.js").buildLiteral());
    }
    return Mono.empty();
  }

  @SuppressWarnings("unchecked")
  private void initializeTemplates(final Object mapping) {
    if (mapping instanceof Map) {
      ((Map<String, String>) mapping).values().forEach(this::compileTemplate);
    } else if (mapping instanceof String) {
      compileTemplate((String) mapping);
    }
  }

  private void compileTemplate(final String templateStr) {
    templateCache.computeIfAbsent(templateStr, t -> {
      try {
        return handlebars.compileInline(t);
      } catch (Exception e) {
        throw new RuntimeException("Failed to compile Handlebars template", e);
      }
    });
  }

  @Override
  @SuppressWarnings("unchecked")
  public Flux<Message> process(final Flux<Message> input, final Map<String, Object> config) {
    final String mode = (String) config.get(CONFIG_MODE);
    final Object mapping = config.get(CONFIG_MAPPING);
    final boolean dropOriginal = (Boolean) config.getOrDefault(CONFIG_DROP_ORIGINAL, true);
    final boolean strictMode = (Boolean) config.getOrDefault(CONFIG_STRICT_MODE, true);

    return input.flatMap(message -> {
      try {
        final Object resultPayload =
            switch (mode) {
              case MODE_PROJECTION -> executeProjection(
                  message, (Map<String, String>) mapping, dropOriginal, strictMode);
              case MODE_TEMPLATE -> executeTemplate(message, mapping, dropOriginal, strictMode);
              case MODE_SCRIPT -> executeScript(message, (String) mapping, dropOriginal, strictMode);
              default -> throw new IllegalArgumentException("Unsupported Mapper mode: " + mode);
            };

        return Flux.just(new Message(
            message.id(),
            message.traceId(),
            message.metadata(),
            resultPayload,
            message.timestamp(),
            message.sourcePort(),
            message.sourceNodeId()
        ));
      } catch (Exception e) {
        log.error("Mapping failed for message {}: {}", message.id(), e.getMessage());
        return Flux.error(new RuntimeException(ERR_PREFIX + "Mapping failed: " + e.getMessage(), e));
      }
    });
  }

  private Object executeProjection(final Message message, final Map<String, String> mapping,
                                   final boolean dropOriginal, final boolean strictMode) {
    final Map<String, Object> result = dropOriginal ? new HashMap<>() : asMutableMap(message.payload());
    for (final Map.Entry<String, String> entry : mapping.entrySet()) {
      try {
        final Object value = SpelUtils.evaluateSync(entry.getValue(), message);
        if (value != null || strictMode) {
          setNestedValue(result, entry.getKey(), value);
        }
      } catch (Exception e) {
        if (strictMode) {
          throw e;
        }
        log.warn("Projection failed for {}: {}", entry.getKey(), e.getMessage());
      }
    }
    return result;
  }

  @SuppressWarnings("unchecked")
  private Object executeTemplate(
      final Message message,
      final Object mapping,
      final boolean dropOriginal,
      final boolean strictMode) {
    Object result = null;
    if (mapping instanceof Map) {
      final Map<String, String> mappingMap = (Map<String, String>) mapping;
      final Map<String, Object> mapResult =
          dropOriginal ? new HashMap<>() : asMutableMap(message.payload());
      for (final Map.Entry<String, String> entry : mappingMap.entrySet()) {
        try {
          final Template template = templateCache.get(entry.getValue());
          if (template == null) {
            throw new RuntimeException("Template not found in cache for key: " + entry.getValue());
          }
          final String value = template.apply(message);
          setNestedValue(mapResult, entry.getKey(), value);
        } catch (Exception e) {
          if (strictMode) {
            throw new RuntimeException("Template application failed", e);
          }
          log.warn("Template failed for {}: {}", entry.getKey(), e.getMessage());
        }
      }
      result = mapResult;
    } else if (mapping instanceof String) {
      try {
        final Template template = templateCache.get((String) mapping);
        if (template == null) {
          throw new RuntimeException("Template not found in cache: " + mapping);
        }
        result = template.apply(message);
      } catch (Exception e) {
        if (strictMode) {
          throw new RuntimeException("Template application failed", e);
        }
      }
    } else {
      throw new IllegalArgumentException("Invalid mapping for TEMPLATE mode");
    }
    return result;
  }

  @SuppressWarnings("unchecked")
  private Object executeScript(
      final Message message,
      final String script,
      final boolean dropOriginal,
      final boolean strictMode) {
    final Source source = jsSourceCache.get(script);
    Object finalResult;
    try (Context context =
        Context.newBuilder("js")
            .engine(JS_ENGINE)
            .allowHostAccess(HostAccess.ALL)
            .allowHostClassLookup(s -> true)
            .build()) {
      final Value bindings = context.getBindings("js");
      bindings.putMember("message", message);
      bindings.putMember("payload", message.payload());
      bindings.putMember("metadata", message.metadata());

      final Value scriptResult = context.eval(source);
      final Object resultObj = detachValue(scriptResult);

      if (dropOriginal) {
        finalResult = resultObj;
      } else {
        final Map<String, Object> original = asMutableMap(message.payload());
        if (resultObj instanceof Map) {
          original.putAll((Map<String, Object>) resultObj);
        }
        finalResult = original;
      }
    } catch (Exception e) {
      if (strictMode) {
        throw e;
      }
      log.warn("Script execution failed: {}", e.getMessage());
      finalResult = dropOriginal ? null : message.payload();
    }
    return finalResult;
  }

  private Object detachValue(final Value value) {
    if (value.isNull()) {
      return null;
    }
    if (value.isNumber()) {
      return value.as(Number.class);
    }
    if (value.isString()) {
      return value.asString();
    }
    if (value.isBoolean()) {
      return value.asBoolean();
    }
    if (value.hasArrayElements()) {
      final List<Object> list = new ArrayList<>((int) value.getArraySize());
      for (int i = 0; i < value.getArraySize(); i++) {
        list.add(detachValue(value.getArrayElement(i)));
      }
      return list;
    }
    if (value.hasMembers()) {
      final Map<String, Object> map = new HashMap<>();
      for (final String key : value.getMemberKeys()) {
        map.put(key, detachValue(value.getMember(key)));
      }
      return map;
    }
    return value.as(Object.class);
  }

  @SuppressWarnings("unchecked")
  private void setNestedValue(final Map<String, Object> map, final String path, final Object value) {
    final String[] parts = path.split("\\.");
    Map<String, Object> current = map;
    for (int i = 0; i < parts.length - 1; i++) {
      final String part = parts[i];
      final Object next = current.computeIfAbsent(part, k -> new HashMap<String, Object>());
      if (next instanceof Map) {
        current = (Map<String, Object>) next;
      } else {
        final Map<String, Object> newMap = new HashMap<>();
        current.put(part, newMap);
        current = newMap;
      }
    }
    current.put(parts[parts.length - 1], value);
  }

  private Map<String, Object> asMutableMap(final Object payload) {
    Map<String, Object> result;
    if (payload instanceof Map) {
      result = new HashMap<>((Map<String, Object>) payload);
    } else if (payload == null) {
      result = new HashMap<>();
    } else {
      result = objectMapper.convertValue(payload, new TypeReference<Map<String, Object>>() {});
    }
    return result;
  }

  @Override
  public Mono<Void> validateConfig(final Map<String, Object> config) {
    final String mode = (String) config.get(CONFIG_MODE);
    if (mode == null || (!MODE_PROJECTION.equals(mode) && !MODE_TEMPLATE.equals(mode) && !MODE_SCRIPT.equals(mode))) {
      return Mono.error(new IllegalArgumentException("Invalid or missing mode. Must be PROJECTION, TEMPLATE, or SCRIPT"));
    }
    if (config.get(CONFIG_MAPPING) == null) {
      return Mono.error(new IllegalArgumentException("mapping is mandatory"));
    }
    return Mono.empty();
  }
}
