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
package com.infenia.jagratha.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.infenia.jagratha.model.WorkflowProgress;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class TaskTrackerServiceTest {

  private TaskTrackerService tracker;

  @BeforeEach
  void setUp() {
    tracker = new TaskTrackerService();
  }

  @Test
  void testIndependentExecutions() {
    String sessionId = "sess-1";
    String workflowId = "wf-1";
    String exec1 = "exec-1";
    String exec2 = "exec-2";

    tracker.startWorkflow(sessionId, workflowId, exec1, List.of("n1")).block();
    tracker.startWorkflow(sessionId, workflowId, exec2, List.of("n2")).block();

    WorkflowProgress p1 = tracker.getExecutionProgress(exec1);
    WorkflowProgress p2 = tracker.getExecutionProgress(exec2);
    WorkflowProgress latest = tracker.getProgress(sessionId, workflowId);

    assertNotNull(p1);
    assertNotNull(p2);
    assertNotNull(latest);
    assertEquals(exec1, p1.executionId());
    assertEquals(exec2, p2.executionId());
    assertEquals(exec2, latest.executionId());

    tracker.updateTaskStatus(exec1, "n1", "m", "SUCCESS").block();
    tracker.updateTaskStatus(exec2, "n2", "m", "FAILURE").block();

    assertEquals("SUCCESS", tracker.getExecutionProgress(exec1).tasks().get(0).status());
    assertEquals("FAILURE", tracker.getExecutionProgress(exec2).tasks().get(0).status());
  }

  @Test
  void testWorkflowTracking() {
    String sessionId = "sess-1";
    String workflowId = "wf-1";
    String executionId = "exec-1";
    List<String> nodes = List.of("node1", "node2");

    StepVerifier.create(tracker.startWorkflow(sessionId, workflowId, executionId, nodes))
        .verifyComplete();
    WorkflowProgress progress = tracker.getExecutionProgress(executionId);

    assertNotNull(progress);
    assertEquals("RUNNING", progress.status());
    assertEquals(2, progress.tasks().size());
    assertEquals("node1", progress.tasks().get(0).nodeId());
    assertEquals("PENDING", progress.tasks().get(0).status());

    StepVerifier.create(tracker.updateTaskStatus(executionId, "node1", "moduleA", "SUCCESS"))
        .verifyComplete();
    progress = tracker.getExecutionProgress(executionId);
    assertEquals("SUCCESS", progress.tasks().get(0).status());
    assertEquals("moduleA", progress.tasks().get(0).module());

    StepVerifier.create(tracker.finishWorkflow(executionId, "COMPLETED")).verifyComplete();
    progress = tracker.getExecutionProgress(executionId);
    assertEquals("COMPLETED", progress.status());
    assertNotNull(progress.endTime());
  }

  @Test
  void testLogStreaming() {
    String sessionId = "sess-1";
    String workflowId = "wf-1";
    String executionId = "exec-1";
    StepVerifier.create(tracker.startWorkflow(sessionId, workflowId, executionId, List.of()))
        .verifyComplete();

    StepVerifier.create(tracker.getLogStream(executionId))
        .then(() -> tracker.appendLog(executionId, "log line 1").subscribe())
        .expectNext("log line 1")
        .thenCancel()
        .verify();
  }

  @Test
  void testStatusStreaming() {
    String sessionId = "sess-1";
    String workflowId = "wf-1";
    String executionId = "exec-1";
    StepVerifier.create(tracker.startWorkflow(sessionId, workflowId, executionId, List.of("n1")))
        .verifyComplete();

    StepVerifier.create(tracker.getStatusStream(executionId))
        .then(() -> tracker.updateTaskStatus(executionId, "n1", "mod", "SUCCESS").subscribe())
        .assertNext(p -> assertEquals(executionId, p.executionId()))
        .thenCancel()
        .verify();
  }

  @Test
  void testRemoveSession() {
    String sessionId = "sess-1";
    String workflowId = "wf-1";
    String executionId = "exec-1";
    StepVerifier.create(tracker.startWorkflow(sessionId, workflowId, executionId, List.of()))
        .verifyComplete();
    assertEquals(1, tracker.getActiveSessions().size());
    tracker.removeSession(sessionId, workflowId);
    assertEquals(0, tracker.getActiveSessions().size());
  }
}
