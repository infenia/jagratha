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
package com.infenia.yukta.plugin.scripting.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.infenia.yukta.plugin.exception.WorkflowExecutionException;
import com.infenia.yukta.plugin.message.DefaultMessage;
import com.infenia.yukta.plugin.message.Message;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class ShellScriptProcessorTest {

  private ShellScriptProcessor processor;

  @BeforeEach
  void setUp() {
    processor = new ShellScriptProcessor();
  }

  @Test
  void testSimpleEcho() {
    final Map<String, Object> config = Map.of("script", "echo 'Hello World'");
    final Message<?> msg = DefaultMessage.create(UUID.randomUUID(), "input");

    StepVerifier.create(processor.process(Flux.just(msg), config))
        .expectNextMatches(
            m -> {
              assertEquals("Hello World", ((String) m.getPayload()).trim());
              return true;
            })
        .verifyComplete();
  }

  @Test
  void testMetadataEnvironmentVariables() {
    final Map<String, Object> config = Map.of("script", "echo $YUKTA_METADATA_TEST_KEY");
    final Message<?> msg =
        DefaultMessage.create(UUID.randomUUID(), "input")
            .withMetadata(Map.of("test.key", "test-value"));

    StepVerifier.create(processor.process(Flux.just(msg), config))
        .expectNextMatches(
            m -> {
              assertEquals("test-value", ((String) m.getPayload()).trim());
              return true;
            })
        .verifyComplete();
  }

  @Test
  void testExitCodeFailure() {
    final Map<String, Object> config = Map.of("script", "exit 1");
    final Message<?> msg = DefaultMessage.create(UUID.randomUUID(), "input");

    StepVerifier.create(processor.process(Flux.just(msg), config))
        .expectErrorMatches(
            e -> e instanceof WorkflowExecutionException && e.getMessage().contains("exit code 1"))
        .verify();
  }

  /*
  @Test
  void testTimeout() {
    final Map<String, Object> config = Map.of("script", "sleep 2", "timeout", 1);
    final Message<?> msg = DefaultMessage.create(UUID.randomUUID(), "input");

    StepVerifier.create(processor.process(Flux.just(msg), config))
        .expectError()
        .verify(Duration.ofSeconds(5));
  }
  */

  @Test
  void testMissingScript() {
    final Map<String, Object> config = Map.of();
    final Message<?> msg = DefaultMessage.create(UUID.randomUUID(), "input");

    StepVerifier.create(processor.process(Flux.just(msg), config))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void testType() {
    assertEquals("SHELL_SCRIPT", processor.getType());
  }

  @Test
  void testValidateConfig() {
    StepVerifier.create(processor.validateConfig(Map.of("script", "echo 1"))).verifyComplete();
    StepVerifier.create(processor.validateConfig(Map.of()))
        .expectError(IllegalArgumentException.class)
        .verify();
  }
}
