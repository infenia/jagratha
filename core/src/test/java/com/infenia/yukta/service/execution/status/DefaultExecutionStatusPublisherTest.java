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
package com.infenia.yukta.service.execution.status;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

@DisplayName("DefaultExecutionStatusPublisher")
class DefaultExecutionStatusPublisherTest {

  private DefaultExecutionStatusPublisher publisher;

  @BeforeEach
  void setUp() {
    publisher = new DefaultExecutionStatusPublisher();
  }

  @Test
  @DisplayName("publishStatus should complete successfully")
  void testPublishStatusCompletes() {
    final ExecutionStatusEvent event =
        new ExecutionStatusEvent(
            "exec-123",
            "node-456",
            "workflow-789",
            "session-001",
            "RUNNING",
            "test-module",
            null,
            null,
            Instant.now());

    StepVerifier.create(publisher.publishStatus(event))
        .expectComplete()
        .verifyThenAssertThat()
        .hasNotDroppedElements();
  }

  @Test
  @DisplayName("statusStream should be callable without hanging")
  void testStatusStreamIsCallable() {
    final var stream = publisher.statusStream();
    StepVerifier.create(stream.take(0))
        .expectComplete()
        .verifyThenAssertThat()
        .hasNotDroppedElements();
  }

  @Test
  @DisplayName("publishStatus followed by statusStream subscription")
  void testEventCapture() {
    final ExecutionStatusEvent event =
        ExecutionStatusEvent.of(
            "exec-123", "node-1", "workflow-789", "session-001", "SUCCESS", "module-1", null, null);

    final AtomicReference<ExecutionStatusEvent> captured = new AtomicReference<>();

    StepVerifier.create(
            publisher
                .publishStatus(event)
                .then(publisher.statusStream().take(1).doOnNext(captured::set)))
        .expectComplete()
        .verifyThenAssertThat()
        .hasNotDroppedElements();
  }
}
