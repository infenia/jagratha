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

import static org.junit.jupiter.api.Assertions.*;

import com.infenia.yukta.plugin.exception.WorkflowExecutionException;
import com.infenia.yukta.plugin.message.DefaultMessage;
import com.infenia.yukta.plugin.message.Message;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class ProcessExecutorPluginTest {

  private ProcessExecutorPlugin plugin;
  private ProcessExecutorGateway gateway;

  @BeforeEach
  void setUp() {
    plugin = new ProcessExecutorPlugin();
    gateway = new ProcessExecutorGateway();
    plugin.setGateway(gateway);
  }

  @Test
  void testGetDescription() {
    assertNotNull(plugin.getDescription());
  }

  @Test
  void testGetUsagePattern() {
    assertNotNull(plugin.getUsagePattern());
  }

  @Test
  void testGetType() {
    assertEquals("PROCESS_EXECUTOR", plugin.getType());
  }

  @Test
  void testGetDefaultTimeout() {
    assertEquals(600, plugin.getDefaultTimeout().getSeconds());
  }

  @Test
  void testValidateConfigSuccess() {
    Map<String, Object> config = Map.of("executable", "echo");
    StepVerifier.create(plugin.validateConfig(config)).verifyComplete();
  }

  @Test
  void testValidateConfigMissingExecutable() {
    Map<String, Object> config = Map.of("args", List.of("test"));
    StepVerifier.create(plugin.validateConfig(config)).verifyError(IllegalArgumentException.class);
  }

  @Test
  void testValidateConfigBlankExecutable() {
    Map<String, Object> config = Map.of("executable", "");
    StepVerifier.create(plugin.validateConfig(config)).verifyError(IllegalArgumentException.class);
  }

  @Test
  void testProcessNormalOutput() {
    Map<String, Object> config = Map.of("executable", "echo", "args", List.of("hello world"));

    UUID traceId = UUID.randomUUID();
    Message inputMessage = DefaultMessage.create(traceId, "input");
    StepVerifier.create(plugin.process(Flux.just(inputMessage), config))
        .assertNext(
            message -> {
              assertEquals(traceId.toString(), message.getTraceId());
              String output = (String) message.getPayload();
              assertTrue(output.contains("hello world"), "Output should contain 'hello world'");
            })
        .verifyComplete();
  }

  @Test
  void testProcessNonZeroExit() {
    Map<String, Object> config = Map.of("executable", "sh", "args", List.of("-c", "exit 1"));

    UUID traceId = UUID.randomUUID();
    Message inputMessage = DefaultMessage.create(traceId, "input");
    StepVerifier.create(plugin.process(Flux.just(inputMessage), config))
        .verifyError(WorkflowExecutionException.class);
  }

  @Test
  void testProcessWithConfiguredTimeout() {
    // Test that timeout configuration is properly accepted
    Map<String, Object> config =
        Map.of("executable", "echo", "args", List.of("hello"), "timeout", 30L);

    UUID traceId = UUID.randomUUID();
    Message inputMessage = DefaultMessage.create(traceId, "input");
    StepVerifier.create(plugin.process(Flux.just(inputMessage), config))
        .assertNext(
            message -> {
              assertEquals(traceId.toString(), message.getTraceId());
              String output = (String) message.getPayload();
              assertTrue(output.contains("hello"), "Output should contain 'hello'");
            })
        .verifyComplete();
  }

  @Test
  void testProcessMissingExecutable() {
    Map<String, Object> config = Map.of("executable", "/non/existent/executable");

    UUID traceId = UUID.randomUUID();
    Message inputMessage = DefaultMessage.create(traceId, "input");
    StepVerifier.create(plugin.process(Flux.just(inputMessage), config))
        .verifyError(WorkflowExecutionException.class);
  }

  @Test
  void testProcessWithMetadata() {
    Map<String, Object> config =
        Map.of("executable", "sh", "args", List.of("-c", "echo $YUKTA_METADATA_FOO"));

    UUID traceId = UUID.randomUUID();
    Message inputMessage =
        DefaultMessage.create(traceId, "input").withMetadata(Map.of("foo", "bar"));
    StepVerifier.create(plugin.process(Flux.just(inputMessage), config))
        .assertNext(
            message -> {
              String output = (String) message.getPayload();
              assertTrue(output.contains("bar"), "Output should contain metadata value 'bar'");
            })
        .verifyComplete();
  }

  @Test
  void testProcessBackpressureDrop() {
    // Create a slow process that takes time to complete
    Map<String, Object> config =
        Map.of("executable", "sh", "args", List.of("-c", "sleep 2 && echo done"), "timeout", 5L);

    UUID traceId1 = UUID.randomUUID();
    UUID traceId2 = UUID.randomUUID();
    Message msg1 = DefaultMessage.create(traceId1, "payload1");
    Message msg2 = DefaultMessage.create(traceId2, "payload2");

    // Send two messages almost simultaneously.
    // The second one should be dropped because the first one is still processing
    // (onBackpressureDrop + flatMap with concurrency 1).
    StepVerifier.create(plugin.process(Flux.just(msg1, msg2), config))
        .assertNext(m -> assertEquals("done", ((String) m.getPayload()).trim()))
        .verifyComplete();
  }

  @Test
  void testAutoConfiguration() {
    ProcessExecutorAutoConfiguration config = new ProcessExecutorAutoConfiguration();
    assertNotNull(config.processExecutorPlugin());
  }

  @Test
  void testProcessWithNoArgs() {
    Map<String, Object> config = Map.of("executable", "echo");

    UUID traceId = UUID.randomUUID();
    Message inputMessage = DefaultMessage.create(traceId, "input");
    StepVerifier.create(plugin.process(Flux.just(inputMessage), config))
        .assertNext(
            message -> {
              assertNotNull(message.getPayload());
            })
        .verifyComplete();
  }

  @Test
  void testGatewayExecuteSuccess() {
    StepVerifier.create(gateway.execute(List.of("echo", "test output"), null, 10L))
        .assertNext(output -> assertTrue(output.contains("test output")))
        .verifyComplete();
  }

  @Test
  void testGatewayExecuteNonZeroExit() {
    StepVerifier.create(gateway.execute(List.of("sh", "-c", "exit 42"), null, 10L))
        .verifyError(WorkflowExecutionException.class);
  }

  @Test
  void testGatewayExecuteWithMetadata() {
    StepVerifier.create(
            gateway.executeWithMetadata(
                List.of("sh", "-c", "echo $YUKTA_METADATA_TEST"),
                null,
                10L,
                Map.of("test", "value123")))
        .assertNext(output -> assertTrue(output.contains("value123")))
        .verifyComplete();
  }

  @Test
  void testGatewayExecuteMissingExecutable() {
    StepVerifier.create(gateway.execute(List.of("/nonexistent/path/exe"), null, 10L))
        .verifyError(WorkflowExecutionException.class);
  }
}
