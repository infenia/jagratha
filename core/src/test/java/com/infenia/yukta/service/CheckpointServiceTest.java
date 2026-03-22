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

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infenia.yukta.plugin.message.DefaultMessage;
import com.infenia.yukta.plugin.message.Message;
import com.infenia.yukta.plugin.store.CheckpointStore;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Unit tests for CheckpointService. */
@ExtendWith(MockitoExtension.class)
class CheckpointServiceTest {

  @Mock private CheckpointStore store;

  @Mock private ControlBusService controlBus;

  private CheckpointService service;
  private static final String EXECUTION_ID = "exec-001";
  private static final String NODE_ID = "node-001";

  @BeforeEach
  void setup() {
    service = new CheckpointService(store, controlBus);
  }

  @Test
  void testHasCheckpoint() {
    when(store.hasCheckpoint(EXECUTION_ID, NODE_ID)).thenReturn(Mono.just(true));

    StepVerifier.create(service.hasCheckpoint(EXECUTION_ID, NODE_ID))
        .expectNext(true)
        .verifyComplete();

    verify(store).hasCheckpoint(EXECUTION_ID, NODE_ID);
  }

  @Test
  void testHasCheckpointNotFound() {
    when(store.hasCheckpoint(EXECUTION_ID, NODE_ID)).thenReturn(Mono.just(false));

    StepVerifier.create(service.hasCheckpoint(EXECUTION_ID, NODE_ID))
        .expectNext(false)
        .verifyComplete();
  }

  @Test
  void testGetOrEmpty() {
    final Message<?> msg =
        new DefaultMessage<>(
            UUID.randomUUID().toString(),
            null,
            null,
            null,
            0,
            null,
            java.util.Map.of(),
            java.util.List.of(),
            null,
            0,
            0,
            0,
            false,
            null,
            null,
            null,
            0,
            "test-payload",
            java.time.Instant.now(),
            null,
            NODE_ID);

    when(store.loadCheckpoint(EXECUTION_ID, NODE_ID)).thenReturn(Mono.just(msg));

    StepVerifier.create(service.getOrEmpty(EXECUTION_ID, NODE_ID)).expectNext(msg).verifyComplete();

    verify(store).loadCheckpoint(EXECUTION_ID, NODE_ID);
  }

  @Test
  void testGetOrEmptyNotFound() {
    when(store.loadCheckpoint(EXECUTION_ID, NODE_ID)).thenReturn(Mono.empty());

    StepVerifier.create(service.getOrEmpty(EXECUTION_ID, NODE_ID)).expectComplete().verify();
  }

  @Test
  void testCheckpoint() {
    final Message<?> msg =
        new DefaultMessage<>(
            UUID.randomUUID().toString(),
            null,
            null,
            null,
            0,
            null,
            java.util.Map.of(),
            java.util.List.of(),
            null,
            0,
            0,
            0,
            false,
            null,
            null,
            null,
            0,
            "test-payload",
            java.time.Instant.now(),
            null,
            NODE_ID);

    when(store.saveCheckpoint(EXECUTION_ID, NODE_ID, msg)).thenReturn(Mono.empty());
    when(controlBus.emit(any())).thenReturn(Mono.empty());

    StepVerifier.create(service.checkpoint(EXECUTION_ID, NODE_ID, msg)).expectComplete().verify();

    verify(store).saveCheckpoint(EXECUTION_ID, NODE_ID, msg);
    verify(controlBus).emit(any());
  }

  @Test
  void testCheckpointWithoutControlBus() {
    service = new CheckpointService(store, null);
    final Message<?> msg =
        new DefaultMessage<>(
            UUID.randomUUID().toString(),
            null,
            null,
            null,
            0,
            null,
            java.util.Map.of(),
            java.util.List.of(),
            null,
            0,
            0,
            0,
            false,
            null,
            null,
            null,
            0,
            "test-payload",
            java.time.Instant.now(),
            null,
            NODE_ID);

    when(store.saveCheckpoint(EXECUTION_ID, NODE_ID, msg)).thenReturn(Mono.empty());

    StepVerifier.create(service.checkpoint(EXECUTION_ID, NODE_ID, msg)).expectComplete().verify();

    verify(store).saveCheckpoint(EXECUTION_ID, NODE_ID, msg);
  }

  @Test
  void testClearCheckpoint() {
    when(store.clearCheckpoint(EXECUTION_ID, NODE_ID)).thenReturn(Mono.empty());

    StepVerifier.create(service.clearCheckpoint(EXECUTION_ID, NODE_ID)).expectComplete().verify();

    verify(store).clearCheckpoint(EXECUTION_ID, NODE_ID);
  }

  @Test
  void testClearAllCheckpoints() {
    when(store.clearAllCheckpoints(EXECUTION_ID)).thenReturn(Mono.empty());

    StepVerifier.create(service.clearAllCheckpoints(EXECUTION_ID)).expectComplete().verify();

    verify(store).clearAllCheckpoints(EXECUTION_ID);
  }
}
