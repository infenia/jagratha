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
package com.infenia.yukta.message.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infenia.yukta.message.DefaultMessage;
import com.infenia.yukta.message.Message;
import com.infenia.yukta.message.store.MessageStore;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PersistingNodeMessageChannelTest {

  @Mock private MessageStore messageStore;

  private PersistingNodeMessageChannel channel;

  @BeforeEach
  void setUp() {
    channel = new PersistingNodeMessageChannel(messageStore);
    when(messageStore.store(any())).thenReturn(Mono.empty());
  }

  @Test
  void inboundPassesThroughUnchanged() {
    final Message<?> msg = DefaultMessage.create(UUID.randomUUID(), "data");
    final Flux<Message<?>> upstream = Flux.just(msg);

    StepVerifier.create(channel.inbound("node-1", "exec-1", upstream))
        .assertNext(m -> assertThat(m.getPayload()).isEqualTo("data"))
        .verifyComplete();
  }

  @Test
  void outboundPersistsMessageBeforeEmitting() {
    final Message<?> msg = DefaultMessage.create(UUID.randomUUID(), "data");
    final Flux<Message<?>> pluginOutput = Flux.just(msg);

    StepVerifier.create(channel.outbound("node-1", "exec-1", pluginOutput))
        .assertNext(m -> assertThat(m.getPayload()).isEqualTo("data"))
        .verifyComplete();

    verify(messageStore).store(msg);
  }

  @Test
  void outboundHandlesMultipleMessages() {
    final Message<?> msg1 = DefaultMessage.create(UUID.randomUUID(), "data1");
    final Message<?> msg2 = DefaultMessage.create(UUID.randomUUID(), "data2");
    final Flux<Message<?>> pluginOutput = Flux.just(msg1, msg2);

    StepVerifier.create(channel.outbound("node-1", "exec-1", pluginOutput))
        .expectNextCount(2)
        .verifyComplete();

    verify(messageStore).store(msg1);
    verify(messageStore).store(msg2);
  }

  @Test
  void outboundPropagatesStoreError() {
    final Message<?> msg = DefaultMessage.create(UUID.randomUUID(), "data");
    final RuntimeException storeError = new RuntimeException("Store failed");
    when(messageStore.store(any())).thenReturn(Mono.error(storeError));
    final Flux<Message<?>> pluginOutput = Flux.just(msg);

    StepVerifier.create(channel.outbound("node-1", "exec-1", pluginOutput))
        .expectError(RuntimeException.class)
        .verify();
  }
}
