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
package com.infenia.yukta.service.control;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorkflowExecutionSnapshotTest {

  @Test
  void testSnapshotCreation() {
    final Instant now = Instant.now();
    final WorkflowExecutionSnapshot snapshot =
        new WorkflowExecutionSnapshot(
            "exec-1",
            "session-1",
            "workflow-1",
            false,
            Set.of("node-1", "node-2"),
            Set.of("node-3"),
            Set.of(),
            now,
            now);

    assertThat(snapshot.executionId()).isEqualTo("exec-1");
    assertThat(snapshot.sessionId()).isEqualTo("session-1");
    assertThat(snapshot.workflowId()).isEqualTo("workflow-1");
    assertThat(snapshot.isGlobalPaused()).isFalse();
    assertThat(snapshot.pausedNodes()).containsExactlyInAnyOrder("node-1", "node-2");
    assertThat(snapshot.skippedNodes()).containsExactly("node-3");
    assertThat(snapshot.stoppedNodes()).isEmpty();
    assertThat(snapshot.createdAt()).isEqualTo(now);
    assertThat(snapshot.lastUpdatedAt()).isEqualTo(now);
  }

  @Test
  void testSnapshotGlobalPausedTrue() {
    final Instant now = Instant.now();
    final WorkflowExecutionSnapshot snapshot =
        new WorkflowExecutionSnapshot(
            "exec-1",
            "session-1",
            "workflow-1",
            true,
            Set.of(),
            Set.of(),
            Set.of(),
            now,
            now);

    assertThat(snapshot.isGlobalPaused()).isTrue();
  }

  @Test
  void testSnapshotEmptyNodeSets() {
    final Instant now = Instant.now();
    final WorkflowExecutionSnapshot snapshot =
        new WorkflowExecutionSnapshot(
            "exec-1",
            "session-1",
            "workflow-1",
            false,
            Set.of(),
            Set.of(),
            Set.of(),
            now,
            now);

    assertThat(snapshot.pausedNodes()).isEmpty();
    assertThat(snapshot.skippedNodes()).isEmpty();
    assertThat(snapshot.stoppedNodes()).isEmpty();
  }

  @Test
  void testSnapshotMultipleStoppedNodes() {
    final Instant now = Instant.now();
    final WorkflowExecutionSnapshot snapshot =
        new WorkflowExecutionSnapshot(
            "exec-1",
            "session-1",
            "workflow-1",
            false,
            Set.of(),
            Set.of(),
            Set.of("node-1", "node-2", "node-3"),
            now,
            now);

    assertThat(snapshot.stoppedNodes()).containsExactlyInAnyOrder("node-1", "node-2", "node-3");
  }

  @Test
  void testSnapshotAllNodesWithDifferentStates() {
    final Instant created = Instant.now().minusSeconds(60);
    final Instant updated = Instant.now();

    final WorkflowExecutionSnapshot snapshot =
        new WorkflowExecutionSnapshot(
            "exec-1",
            "session-1",
            "workflow-1",
            true,
            Set.of("node-1"),
            Set.of("node-2"),
            Set.of("node-3"),
            created,
            updated);

    assertThat(snapshot.isGlobalPaused()).isTrue();
    assertThat(snapshot.pausedNodes()).containsExactly("node-1");
    assertThat(snapshot.skippedNodes()).containsExactly("node-2");
    assertThat(snapshot.stoppedNodes()).containsExactly("node-3");
    assertThat(snapshot.createdAt()).isEqualTo(created);
    assertThat(snapshot.lastUpdatedAt()).isEqualTo(updated);
    assertThat(snapshot.createdAt()).isBefore(snapshot.lastUpdatedAt());
  }

  @Test
  void testSnapshotWithSameNodeInMultipleSets() {
    final Instant now = Instant.now();
    final WorkflowExecutionSnapshot snapshot =
        new WorkflowExecutionSnapshot(
            "exec-1",
            "session-1",
            "workflow-1",
            false,
            Set.of("node-1"),
            Set.of("node-1"),
            Set.of("node-1"),
            now,
            now);

    assertThat(snapshot.pausedNodes()).contains("node-1");
    assertThat(snapshot.skippedNodes()).contains("node-1");
    assertThat(snapshot.stoppedNodes()).contains("node-1");
  }

  @Test
  void testSnapshotRecordEquality() {
    final Instant now = Instant.now();
    final WorkflowExecutionSnapshot snapshot1 =
        new WorkflowExecutionSnapshot(
            "exec-1",
            "session-1",
            "workflow-1",
            false,
            Set.of("node-1"),
            Set.of("node-2"),
            Set.of(),
            now,
            now);

    final WorkflowExecutionSnapshot snapshot2 =
        new WorkflowExecutionSnapshot(
            "exec-1",
            "session-1",
            "workflow-1",
            false,
            Set.of("node-1"),
            Set.of("node-2"),
            Set.of(),
            now,
            now);

    assertThat(snapshot1).isEqualTo(snapshot2);
  }

  @Test
  void testSnapshotRecordInequalityDifferentExecution() {
    final Instant now = Instant.now();
    final WorkflowExecutionSnapshot snapshot1 =
        new WorkflowExecutionSnapshot(
            "exec-1",
            "session-1",
            "workflow-1",
            false,
            Set.of(),
            Set.of(),
            Set.of(),
            now,
            now);

    final WorkflowExecutionSnapshot snapshot2 =
        new WorkflowExecutionSnapshot(
            "exec-2",
            "session-1",
            "workflow-1",
            false,
            Set.of(),
            Set.of(),
            Set.of(),
            now,
            now);

    assertThat(snapshot1).isNotEqualTo(snapshot2);
  }

  @Test
  void testSnapshotToString() {
    final Instant now = Instant.now();
    final WorkflowExecutionSnapshot snapshot =
        new WorkflowExecutionSnapshot(
            "exec-1",
            "session-1",
            "workflow-1",
            false,
            Set.of("node-1"),
            Set.of(),
            Set.of(),
            now,
            now);

    final String str = snapshot.toString();
    assertThat(str).contains("exec-1");
    assertThat(str).contains("session-1");
    assertThat(str).contains("workflow-1");
  }

  @Test
  void testSnapshotHashCode() {
    final Instant now = Instant.now();
    final WorkflowExecutionSnapshot snapshot1 =
        new WorkflowExecutionSnapshot(
            "exec-1",
            "session-1",
            "workflow-1",
            false,
            Set.of(),
            Set.of(),
            Set.of(),
            now,
            now);

    final WorkflowExecutionSnapshot snapshot2 =
        new WorkflowExecutionSnapshot(
            "exec-1",
            "session-1",
            "workflow-1",
            false,
            Set.of(),
            Set.of(),
            Set.of(),
            now,
            now);

    assertThat(snapshot1.hashCode()).isEqualTo(snapshot2.hashCode());
  }

  @Test
  void testSnapshotCanonicalConstructor() {
    final Instant now = Instant.now();
    final Set<String> pausedNodes = Set.of("node-1");
    final Set<String> skippedNodes = Set.of("node-2");
    final Set<String> stoppedNodes = Set.of("node-3");

    final WorkflowExecutionSnapshot snapshot =
        new WorkflowExecutionSnapshot(
            "exec-1",
            "session-1",
            "workflow-1",
            true,
            pausedNodes,
            skippedNodes,
            stoppedNodes,
            now,
            now);

    assertThat(snapshot.pausedNodes()).isSameAs(pausedNodes);
    assertThat(snapshot.skippedNodes()).isSameAs(skippedNodes);
    assertThat(snapshot.stoppedNodes()).isSameAs(stoppedNodes);
  }
}
