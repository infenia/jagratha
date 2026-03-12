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
package com.infenia.yukta.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.infenia.yukta.plugin.message.DefaultMessage;
import com.infenia.yukta.plugin.message.Message;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

/**
 * Unit tests for ControlBusService focusing on exception handling and internal behavior.
 *
 * <p>These tests use mocking and reflection to test edge cases that are difficult to trigger
 * through normal integration testing.
 */
class ControlBusServiceUnitTest {

  @Test
  void testEmitWhenSinkThrowsRuntimeException()
      throws NoSuchFieldException, IllegalAccessException {
    final ControlBusService service = new ControlBusService(100, 50, 256, List.of());
    service.init();

    // Mock the sink to throw RuntimeException on emitNext
    @SuppressWarnings("unchecked")
    final Sinks.Many<Message<?>> mockSink = mock(Sinks.Many.class);
    doThrow(new RuntimeException("Simulated sink error")).when(mockSink).emitNext(any(), any());

    // Use reflection to replace the real sink with the mock
    final Field sinkField = ControlBusService.class.getDeclaredField("controlSink");
    sinkField.setAccessible(true);
    sinkField.set(service, mockSink);

    final Message<?> msg = DefaultMessage.create(null, "test").withSourceNodeId("test");

    // Verify that emit properly handles the exception and propagates error
    StepVerifier.create(service.emit(msg))
        .expectErrorMatches(
            e ->
                e instanceof IllegalStateException
                    && e.getMessage().contains("Control bus emit failed"))
        .verify();
  }

  @Test
  void testEmitWhenSinkThrowsIllegalStateException()
      throws NoSuchFieldException, IllegalAccessException {
    final ControlBusService service = new ControlBusService(100, 50, 256, List.of());
    service.init();

    // Mock the sink to throw IllegalStateException on emitNext
    @SuppressWarnings("unchecked")
    final Sinks.Many<Message<?>> mockSink = mock(Sinks.Many.class);
    doThrow(new IllegalStateException("Sink closed")).when(mockSink).emitNext(any(), any());

    // Use reflection to replace the real sink with the mock
    final Field sinkField = ControlBusService.class.getDeclaredField("controlSink");
    sinkField.setAccessible(true);
    sinkField.set(service, mockSink);

    final Message<?> msg = DefaultMessage.create(null, "test").withSourceNodeId("test");

    // Verify that emit properly handles the exception and propagates error
    StepVerifier.create(service.emit(msg))
        .expectErrorMatches(
            e ->
                e instanceof IllegalStateException
                    && e.getMessage().contains("Control bus emit failed"))
        .verify();
  }
}
