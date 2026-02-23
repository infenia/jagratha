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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.infenia.jagratha.plugin.Message;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class MapperProcessorTest {

  private MapperProcessor processor;
  private UUID traceId;

  @BeforeEach
  void setUp() {
    processor = new MapperProcessor();
    traceId = UUID.randomUUID();
  }

  @Test
  @SuppressWarnings("unchecked")
  void testProjectionMode() {
    final Map<String, Object> config =
        Map.of(
            "mode",
            "PROJECTION",
            "mapping",
            Map.of(
                "fullName", "payload.firstName + ' ' + payload.lastName",
                "nested.age", "payload.age"),
            "dropOriginal",
            true);

    final Message message =
        Message.create(
            traceId,
            Map.of(
                "firstName", "John",
                "lastName", "Doe",
                "age", 30));

    processor.initialize(config).block();

    StepVerifier.create(processor.process(Flux.just(message), config))
        .assertNext(
            result -> {
              assertTrue(result.payload() instanceof Map);
              final Map<String, Object> payload = (Map<String, Object>) result.payload();
              assertEquals("John Doe", payload.get("fullName"));
              assertTrue(payload.get("nested") instanceof Map);
              assertEquals(30, ((Map<String, Object>) payload.get("nested")).get("age"));
              assertEquals(message.id(), result.id());
              assertEquals(traceId, result.traceId());
            })
        .verifyComplete();
  }

  @Test
  void testTemplateModeString() {
    final Map<String, Object> config =
        Map.of(
            "mode", "TEMPLATE",
            "mapping", "Hello {{payload.name}}!",
            "dropOriginal", true);

    final Message message = Message.create(traceId, Map.of("name", "World"));

    processor.initialize(config).block();

    StepVerifier.create(processor.process(Flux.just(message), config))
        .assertNext(
            result -> {
              assertEquals("Hello World!", result.payload());
            })
        .verifyComplete();
  }

  @Test
  @SuppressWarnings("unchecked")
  void testTemplateModeMap() {
    final Map<String, Object> config =
        Map.of(
            "mode",
            "TEMPLATE",
            "mapping",
            Map.of("greeting", "Hello {{payload.name}}!"),
            "dropOriginal",
            true);

    final Message message = Message.create(traceId, Map.of("name", "World"));

    processor.initialize(config).block();

    StepVerifier.create(processor.process(Flux.just(message), config))
        .assertNext(
            result -> {
              assertTrue(result.payload() instanceof Map);
              final Map<String, Object> payload = (Map<String, Object>) result.payload();
              assertEquals("Hello World!", payload.get("greeting"));
            })
        .verifyComplete();
  }

  @Test
  @SuppressWarnings("unchecked")
  void testScriptMode() {
    final Map<String, Object> config =
        Map.of(
            "mode", "SCRIPT",
            "mapping", "({ result: payload.a + payload.b })",
            "dropOriginal", true);

    final Message message = Message.create(traceId, Map.of("a", 10, "b", 20));

    processor.initialize(config).block();

    StepVerifier.create(processor.process(Flux.just(message), config))
        .assertNext(
            result -> {
              assertTrue(result.payload() instanceof Map);
              final Map<String, Object> payload = (Map<String, Object>) result.payload();
              assertEquals(30.0, ((Number) payload.get("result")).doubleValue());
            })
        .verifyComplete();
  }

  @Test
  @SuppressWarnings("unchecked")
  void testEnrichMode() {
    final Map<String, Object> config =
        Map.of("mode", "PROJECTION", "mapping", Map.of("extra", "'new'"), "dropOriginal", false);

    final Message message = Message.create(traceId, Map.of("existing", "value"));

    processor.initialize(config).block();

    StepVerifier.create(processor.process(Flux.just(message), config))
        .assertNext(
            result -> {
              final Map<String, Object> payload = (Map<String, Object>) result.payload();
              assertEquals("value", payload.get("existing"));
              assertEquals("new", payload.get("extra"));
            })
        .verifyComplete();
  }

  @Test
  @SuppressWarnings("unchecked")
  void testStrictModeFalse() {
    final Map<String, Object> config =
        Map.of(
            "mode",
            "PROJECTION",
            "mapping",
            Map.of(
                "present", "'ok'",
                "absent", "payload.missingField"),
            "strictMode",
            false,
            "dropOriginal",
            true);

    final Message message = Message.create(traceId, Map.of("some", "data"));

    processor.initialize(config).block();

    StepVerifier.create(processor.process(Flux.just(message), config))
        .assertNext(
            result -> {
              final Map<String, Object> payload = (Map<String, Object>) result.payload();
              assertEquals("ok", payload.get("present"));
              assertTrue(!payload.containsKey("absent"));
            })
        .verifyComplete();
  }

  @Test
  void testMetadataPreservation() {
    final Map<String, Object> config =
        Map.of("mode", "PROJECTION", "mapping", Map.of("a", "1"), "dropOriginal", true);

    final Map<String, Object> metadata = Map.of("key", "val");
    final Message message =
        new Message(
            UUID.randomUUID(),
            traceId,
            metadata,
            "payload",
            java.time.Instant.now(),
            "port1",
            "node1");

    processor.initialize(config).block();

    StepVerifier.create(processor.process(Flux.just(message), config))
        .assertNext(
            result -> {
              assertEquals(metadata, result.metadata());
              assertEquals("port1", result.sourcePort());
              assertEquals("node1", result.sourceNodeId());
              assertEquals(traceId, result.traceId());
            })
        .verifyComplete();
  }
}
