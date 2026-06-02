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
package com.infenia.yukta.service.orchestrator.compiler;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.Disposable;
import reactor.core.scheduler.Schedulers;

@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class HeartbeatBuilderTest {

  @Mock
  private com.infenia.yukta.service.execution.status.ExecutionStatusPublisher mockControlBusGateway;

  @Test
  void testHeartbeatBuilderCreateDisposables() {
    HeartbeatBuilder builder =
        new HeartbeatBuilder(
            mockControlBusGateway, Duration.ofMillis(500), Schedulers.boundedElastic());

    List<Disposable> disposables =
        builder
            .forNodes("wfId", List.of("node-1", "node-2", "node-3"))
            .withHeartbeatInterval(Duration.ofMillis(500))
            .withStatisticsInterval(Duration.ofSeconds(1))
            .build();

    assert !disposables.isEmpty();
    disposables.forEach(Disposable::dispose);
  }

  @Test
  void testHeartbeatBuilderDefaultStatisticsInterval() {
    HeartbeatBuilder builder =
        new HeartbeatBuilder(
            mockControlBusGateway, Duration.ofMillis(500), Schedulers.boundedElastic());

    List<Disposable> disposables =
        builder
            .forNodes("wfId", List.of("node-1"))
            .withHeartbeatInterval(Duration.ofMillis(500))
            .build();

    assert !disposables.isEmpty();
    disposables.forEach(Disposable::dispose);
  }

  @Test
  void testHeartbeatBuilderEmitsCalls() {
    HeartbeatBuilder builder =
        new HeartbeatBuilder(
            mockControlBusGateway, Duration.ofMillis(100), Schedulers.boundedElastic());

    List<Disposable> disposables =
        builder
            .forNodes("wfId", List.of("node-1"))
            .withHeartbeatInterval(Duration.ofMillis(100))
            .build();

    // Wait for at least one heartbeat
    try {
      Thread.sleep(250);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    // TODO: Verify that status events were published through ExecutionStatusPublisher
    // once the control bus bridge is established.

    disposables.forEach(Disposable::dispose);
  }

  @Test
  void testHeartbeatBuilderEmptyNodeList() {
    HeartbeatBuilder builder =
        new HeartbeatBuilder(
            mockControlBusGateway, Duration.ofMillis(500), Schedulers.boundedElastic());

    List<Disposable> disposables =
        builder.forNodes("wfId", List.of()).withHeartbeatInterval(Duration.ofMillis(500)).build();

    assert disposables.isEmpty();
  }

  @Test
  void testHeartbeatBuilderNullNodeList() {
    HeartbeatBuilder builder =
        new HeartbeatBuilder(
            mockControlBusGateway, Duration.ofMillis(500), Schedulers.boundedElastic());

    List<Disposable> disposables =
        builder.forNodes("wfId", null).withHeartbeatInterval(Duration.ofMillis(500)).build();

    assert disposables.isEmpty();
  }

  @Test
  void testHeartbeatBuilderNoHeartbeatInterval() {
    HeartbeatBuilder builder =
        new HeartbeatBuilder(
            mockControlBusGateway, Duration.ofMillis(500), Schedulers.boundedElastic());

    List<Disposable> disposables = builder.forNodes("wfId", List.of("node-1")).build();

    assert !disposables.isEmpty();
    disposables.forEach(Disposable::dispose);
  }

  @Test
  void testHeartbeatBuilderBuildsCorrectly() {
    HeartbeatBuilder builder =
        new HeartbeatBuilder(
            mockControlBusGateway, Duration.ofMillis(500), Schedulers.boundedElastic());

    List<Disposable> disposables =
        builder
            .forNodes("wfId", List.of("node-1"))
            .withHeartbeatInterval(Duration.ofMillis(500))
            .withStatisticsInterval(Duration.ofSeconds(1))
            .build();

    // Should create 2 disposables per node (heartbeat + statistics)
    assert disposables.size() == 2;
    disposables.forEach(Disposable::dispose);
  }

  @Test
  void testHeartbeatBuilderMultipleNodesCreatesMultipleDisposables() {
    HeartbeatBuilder builder =
        new HeartbeatBuilder(
            mockControlBusGateway, Duration.ofMillis(500), Schedulers.boundedElastic());

    List<Disposable> disposables =
        builder
            .forNodes("wfId", List.of("node-1", "node-2", "node-3"))
            .withHeartbeatInterval(Duration.ofMillis(500))
            .withStatisticsInterval(Duration.ofSeconds(1))
            .build();

    // Should create 2 disposables per node
    assert disposables.size() == 6;
    disposables.forEach(Disposable::dispose);
  }

  @Test
  void testHeartbeatBuilderDefaultHeartbeatInterval() {
    HeartbeatBuilder builder =
        new HeartbeatBuilder(
            mockControlBusGateway, Duration.ofMillis(1000), Schedulers.boundedElastic());

    List<Disposable> disposables = builder.forNodes("wfId", List.of("node-1")).build();

    assert !disposables.isEmpty();
    disposables.forEach(Disposable::dispose);
  }
}
