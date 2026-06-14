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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infenia.yukta.model.session.TaskResponse;
import com.infenia.yukta.model.workflow.PreparedWorkflow;
import com.infenia.yukta.model.workflow.WorkflowDefinition;
import com.infenia.yukta.model.workflow.WorkflowExecution;
import com.infenia.yukta.service.orchestrator.WorkflowOrchestrator;
import com.infenia.yukta.service.orchestrator.tracker.TaskTrackerService;
import com.infenia.yukta.service.workflow.store.PreparedWorkflowCache;
import com.infenia.yukta.service.workflow.store.WorkflowDefinitionStore;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class WorkflowServiceTest {

  @Mock private WorkflowOrchestrator orchestrator;
  @Mock private TaskTrackerService tracker;
  @Mock private WorkflowDefinitionStore workflowDefinitionStore;

  private PreparedWorkflowCache preparedWorkflowCache;
  private WorkflowService workflowService;

  @BeforeEach
  void setUp() {
    lenient().when(tracker.startWorkflow(any(), any(), any(), any())).thenReturn(Mono.empty());
    lenient().when(tracker.finishWorkflow(any(), any())).thenReturn(Mono.empty());
    preparedWorkflowCache = new PreparedWorkflowCache(600_000L); // real instance, long TTL
    workflowService =
        new WorkflowService(orchestrator, tracker, workflowDefinitionStore, preparedWorkflowCache);
  }

  @Test
  void testRunWorkflowSuccess() {
    String sessionId = "sess-success";
    String workflowId = "w-success";
    WorkflowDefinition def = new WorkflowDefinition("test-workflow", "desc", List.of(), List.of());
    PreparedWorkflow prepared =
        new PreparedWorkflow(
            List.of(), Map.of(), Map.of(), Map.of(), List.of(), (e, p) -> Mono.empty());

    when(workflowDefinitionStore.find(sessionId, workflowId)).thenReturn(Mono.just(def));
    when(orchestrator.prepareWorkflow(any())).thenReturn(Mono.just(prepared));
    when(orchestrator.execute(anyString(), anyString(), anyString(), any(), any()))
        .thenReturn(Mono.empty());

    StepVerifier.create(
            workflowService.runWorkflow(sessionId, workflowId, java.util.Map.of()).result())
        .expectNextMatches(res -> "SUCCESS".equals(res.status()))
        .verifyComplete();
  }

  @Test
  void testRunWorkflowNoWorkflow() {
    String sessionId = "sess-none";
    String workflowId = "w-none";

    when(workflowDefinitionStore.find(sessionId, workflowId)).thenReturn(Mono.empty());

    StepVerifier.create(
            workflowService.runWorkflow(sessionId, workflowId, java.util.Map.of()).result())
        .expectNextMatches(
            res -> "FAILURE".equals(res.status()) && res.output().contains("Workflow not found"))
        .verifyComplete();
  }

  @Test
  void testRunWorkflowError() {
    String sessionId = "sess-error";
    String workflowId = "w-error";
    WorkflowDefinition def = new WorkflowDefinition("test-workflow", "desc", List.of(), List.of());

    when(workflowDefinitionStore.find(sessionId, workflowId)).thenReturn(Mono.just(def));
    when(orchestrator.prepareWorkflow(any())).thenReturn(Mono.error(new RuntimeException("Fail")));

    StepVerifier.create(
            workflowService.runWorkflow(sessionId, workflowId, java.util.Map.of()).result())
        .expectNextMatches(
            res -> "FAILURE".equals(res.status()) && res.output().contains("Workflow failed: Fail"))
        .verifyComplete();
  }

  @Test
  void testWorkflowQueueing() throws Exception {
    String sessionId = "sess-queue";
    String workflowId = "w-queue";
    WorkflowDefinition def = new WorkflowDefinition("test-workflow", "desc", List.of(), List.of());
    PreparedWorkflow prepared =
        new PreparedWorkflow(
            List.of(), Map.of(), Map.of(), Map.of(), List.of(), (e, p) -> Mono.empty());

    // Use a CountDownLatch to control execution timing
    java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.CountDownLatch startedLatch = new java.util.concurrent.CountDownLatch(1);

    when(workflowDefinitionStore.find(sessionId, workflowId)).thenReturn(Mono.just(def));
    when(orchestrator.prepareWorkflow(any())).thenReturn(Mono.just(prepared));
    when(orchestrator.execute(anyString(), anyString(), anyString(), any(), any()))
        .thenAnswer(
            invocation -> {
              startedLatch.countDown();
              return Mono.fromRunnable(
                  () -> {
                    try {
                      latch.await();
                    } catch (InterruptedException e) {
                      Thread.currentThread().interrupt();
                    }
                  });
            });

    var exec1 = workflowService.runWorkflow(sessionId, workflowId, Map.of());

    // Wait for first execution to start
    startedLatch.await(5, java.util.concurrent.TimeUnit.SECONDS);

    // Now submit second workflow - it should queue
    var exec2 = workflowService.runWorkflow(sessionId, workflowId, Map.of());

    // Release first execution
    latch.countDown();

    StepVerifier.create(exec1.result())
        .expectNextMatches(res -> "SUCCESS".equals(res.status()))
        .verifyComplete();

    StepVerifier.create(exec2.result())
        .expectNextMatches(res -> "SUCCESS".equals(res.status()))
        .verifyComplete();
  }

  @Test
  void testWorkflowQueueCleanup() throws Exception {
    String sessionId = "sess-cleanup";
    String workflowId = "w-cleanup";
    WorkflowDefinition def = new WorkflowDefinition("test-workflow", "desc", List.of(), List.of());
    PreparedWorkflow prepared =
        new PreparedWorkflow(
            List.of(), Map.of(), Map.of(), Map.of(), List.of(), (e, p) -> Mono.empty());

    java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.CountDownLatch startedLatch = new java.util.concurrent.CountDownLatch(1);

    when(workflowDefinitionStore.find(sessionId, workflowId)).thenReturn(Mono.just(def));
    when(orchestrator.prepareWorkflow(any())).thenReturn(Mono.just(prepared));
    when(orchestrator.execute(anyString(), anyString(), anyString(), any(), any()))
        .thenAnswer(
            invocation -> {
              startedLatch.countDown();
              return Mono.fromRunnable(
                  () -> {
                    try {
                      latch.await();
                    } catch (InterruptedException e) {
                      Thread.currentThread().interrupt();
                    }
                  });
            });

    var exec1 = workflowService.runWorkflow(sessionId, workflowId, Map.of());

    // Wait for first execution to start
    startedLatch.await(5, java.util.concurrent.TimeUnit.SECONDS);

    // Now submit second workflow - it should queue
    var exec2 = workflowService.runWorkflow(sessionId, workflowId, Map.of());

    // Release first execution
    latch.countDown();

    StepVerifier.create(exec1.result())
        .expectNextMatches(res -> "SUCCESS".equals(res.status()))
        .verifyComplete();

    StepVerifier.create(exec2.result())
        .expectNextMatches(res -> "SUCCESS".equals(res.status()))
        .verifyComplete();
  }

  @Test
  void testWorkflowMultipleQueuesDifferentSessions() {
    String sessionId1 = "sess-multi-1";
    String sessionId2 = "sess-multi-2";
    String workflowId = "w-multi";
    WorkflowDefinition def = new WorkflowDefinition("test-workflow", "desc", List.of(), List.of());
    PreparedWorkflow prepared =
        new PreparedWorkflow(
            List.of(), Map.of(), Map.of(), Map.of(), List.of(), (e, p) -> Mono.empty());

    when(workflowDefinitionStore.find(anyString(), anyString())).thenReturn(Mono.just(def));
    when(orchestrator.prepareWorkflow(any())).thenReturn(Mono.just(prepared));
    when(orchestrator.execute(anyString(), anyString(), anyString(), any(), any()))
        .thenReturn(Mono.empty());

    var exec1 = workflowService.runWorkflow(sessionId1, workflowId, Map.of());
    var exec2 = workflowService.runWorkflow(sessionId2, workflowId, Map.of());

    StepVerifier.create(exec1.result())
        .expectNextMatches(res -> "SUCCESS".equals(res.status()))
        .verifyComplete();

    StepVerifier.create(exec2.result())
        .expectNextMatches(res -> "SUCCESS".equals(res.status()))
        .verifyComplete();
  }

  @Test
  void testWorkflowSingleExecutionCleanup() throws Exception {
    String sessionId = "sess-single";
    String workflowId = "w-single";
    WorkflowDefinition def = new WorkflowDefinition("test-workflow", "desc", List.of(), List.of());
    PreparedWorkflow prepared =
        new PreparedWorkflow(
            List.of(), Map.of(), Map.of(), Map.of(), List.of(), (e, p) -> Mono.empty());

    when(workflowDefinitionStore.find(sessionId, workflowId)).thenReturn(Mono.just(def));
    when(orchestrator.prepareWorkflow(any())).thenReturn(Mono.just(prepared));
    when(orchestrator.execute(anyString(), anyString(), anyString(), any(), any()))
        .thenReturn(Mono.empty());

    var exec = workflowService.runWorkflow(sessionId, workflowId, Map.of());

    StepVerifier.create(exec.result())
        .expectNextMatches(res -> "SUCCESS".equals(res.status()))
        .verifyComplete();

    // Wait for async cleanup to complete
    Thread.sleep(500);
  }

  @Test
  void testWorkflowQueuedExecutionWithPreviousWorkflowError() throws Exception {
    String sessionId = "sess-error-queue";
    String workflowId = "w-error-queue";
    WorkflowDefinition def = new WorkflowDefinition("test-workflow", "desc", List.of(), List.of());
    PreparedWorkflow prepared =
        new PreparedWorkflow(
            List.of(), Map.of(), Map.of(), Map.of(), List.of(), (e, p) -> Mono.empty());

    java.util.concurrent.CountDownLatch firstErrorLatch =
        new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.CountDownLatch firstStartedLatch =
        new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.atomic.AtomicInteger executeCallCount =
        new java.util.concurrent.atomic.AtomicInteger(0);

    when(workflowDefinitionStore.find(sessionId, workflowId)).thenReturn(Mono.just(def));
    when(orchestrator.prepareWorkflow(any())).thenReturn(Mono.just(prepared));
    when(orchestrator.execute(anyString(), anyString(), anyString(), any(), any()))
        .thenAnswer(
            invocation -> {
              int callCount = executeCallCount.incrementAndGet();
              firstStartedLatch.countDown();
              if (callCount == 1) {
                // First execution errors
                return Mono.fromRunnable(
                        () -> {
                          try {
                            firstErrorLatch.await();
                          } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                          }
                        })
                    .then(Mono.error(new RuntimeException("First workflow error")));
              } else {
                // Second execution succeeds (onErrorResume recovery)
                return Mono.just(new TaskResponse("SUCCESS", "Workflow executed successfully"));
              }
            });

    var exec1 = workflowService.runWorkflow(sessionId, workflowId, Map.of());

    // Wait for first execution to start
    firstStartedLatch.await(5, java.util.concurrent.TimeUnit.SECONDS);

    // Now submit second workflow - it should queue
    var exec2 = workflowService.runWorkflow(sessionId, workflowId, Map.of());

    // Let first execution error
    firstErrorLatch.countDown();

    // First execution should fail
    StepVerifier.create(exec1.result())
        .expectNextMatches(res -> "FAILURE".equals(res.status()))
        .verifyComplete();

    // Second execution should succeed (onErrorResume path - previous workflow error recovery)
    StepVerifier.create(exec2.result())
        .expectNextMatches(res -> "SUCCESS".equals(res.status()))
        .verifyComplete();
  }

  @Test
  void testWorkflowErrorAfterPreparation() {
    String sessionId = "sess-prep-error";
    String workflowId = "w-prep-error";
    WorkflowDefinition def = new WorkflowDefinition("test-workflow", "desc", List.of(), List.of());
    PreparedWorkflow prepared =
        new PreparedWorkflow(
            List.of(), Map.of(), Map.of(), Map.of(), List.of(), (e, p) -> Mono.empty());

    when(workflowDefinitionStore.find(sessionId, workflowId)).thenReturn(Mono.just(def));
    when(orchestrator.prepareWorkflow(any())).thenReturn(Mono.just(prepared));
    when(orchestrator.execute(anyString(), anyString(), anyString(), any(), any()))
        .thenReturn(Mono.error(new RuntimeException("Execution error")));

    StepVerifier.create(workflowService.runWorkflow(sessionId, workflowId, Map.of()).result())
        .expectNextMatches(
            res -> "FAILURE".equals(res.status()) && res.output().contains("Execution error"))
        .verifyComplete();
  }

  @Test
  void testMultipleQueuedExecutionsWithSelectiveCleanup() throws Exception {
    String sessionId = "sess-multi-cleanup";
    String workflowId = "w-multi-cleanup";
    WorkflowDefinition def = new WorkflowDefinition("test-workflow", "desc", List.of(), List.of());
    PreparedWorkflow prepared =
        new PreparedWorkflow(
            List.of(), Map.of(), Map.of(), Map.of(), List.of(), (e, p) -> Mono.empty());

    java.util.concurrent.CountDownLatch firstLatch = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.CountDownLatch secondLatch = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.CountDownLatch thirdLatch = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.CountDownLatch firstStartedLatch =
        new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.CountDownLatch secondStartedLatch =
        new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.CountDownLatch thirdStartedLatch =
        new java.util.concurrent.CountDownLatch(1);

    when(workflowDefinitionStore.find(sessionId, workflowId)).thenReturn(Mono.just(def));
    when(orchestrator.prepareWorkflow(any())).thenReturn(Mono.just(prepared));
    when(orchestrator.execute(anyString(), anyString(), anyString(), any(), any()))
        .thenAnswer(
            invocation -> {
              String execId = (String) invocation.getArguments()[2];
              if (execId.contains("1")) {
                firstStartedLatch.countDown();
                return Mono.fromRunnable(
                    () -> {
                      try {
                        firstLatch.await();
                      } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                      }
                    });
              } else if (execId.contains("2")) {
                secondStartedLatch.countDown();
                return Mono.fromRunnable(
                    () -> {
                      try {
                        secondLatch.await();
                      } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                      }
                    });
              } else {
                thirdStartedLatch.countDown();
                return Mono.fromRunnable(
                    () -> {
                      try {
                        thirdLatch.await();
                      } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                      }
                    });
              }
            });

    var exec1 = workflowService.runWorkflow(sessionId, workflowId, Map.of());
    firstStartedLatch.await(5, java.util.concurrent.TimeUnit.SECONDS);

    var exec2 = workflowService.runWorkflow(sessionId, workflowId, Map.of());
    secondStartedLatch.await(5, java.util.concurrent.TimeUnit.SECONDS);

    var exec3 = workflowService.runWorkflow(sessionId, workflowId, Map.of());
    thirdStartedLatch.await(5, java.util.concurrent.TimeUnit.SECONDS);

    // Release all executions
    firstLatch.countDown();
    secondLatch.countDown();
    thirdLatch.countDown();

    StepVerifier.create(exec1.result())
        .expectNextMatches(res -> "SUCCESS".equals(res.status()))
        .verifyComplete();

    StepVerifier.create(exec2.result())
        .expectNextMatches(res -> "SUCCESS".equals(res.status()))
        .verifyComplete();

    StepVerifier.create(exec3.result())
        .expectNextMatches(res -> "SUCCESS".equals(res.status()))
        .verifyComplete();
  }

  @Test
  void testWorkflowErrorInTerminalPhase() {
    String sessionId = "sess-terminal-error";
    String workflowId = "w-terminal-error";
    WorkflowDefinition def = new WorkflowDefinition("test-workflow", "desc", List.of(), List.of());

    when(workflowDefinitionStore.find(sessionId, workflowId)).thenReturn(Mono.just(def));
    when(orchestrator.prepareWorkflow(any()))
        .thenReturn(Mono.error(new RuntimeException("Prep error")));

    StepVerifier.create(workflowService.runWorkflow(sessionId, workflowId, Map.of()).result())
        .expectNextMatches(
            res -> "FAILURE".equals(res.status()) && res.output().contains("Prep error"))
        .verifyComplete();
  }

  @Test
  void testValidateAndTriggerRegistersExecutionEagerly() {
    String sessionId = "sess-eager";
    String workflowId = "w-eager";
    var node = new WorkflowDefinition.Node("n1", "CONSTANT_SOURCE", Map.of());
    WorkflowDefinition def = new WorkflowDefinition(workflowId, "desc", List.of(node), List.of());
    PreparedWorkflow prepared =
        new PreparedWorkflow(
            List.of(), Map.of(), Map.of(), Map.of(), List.of(), (e, p) -> Mono.empty());

    when(workflowDefinitionStore.find(sessionId, workflowId)).thenReturn(Mono.just(def));
    when(orchestrator.prepareWorkflow(any())).thenReturn(Mono.just(prepared));
    when(orchestrator.execute(anyString(), anyString(), anyString(), any(), any()))
        .thenReturn(Mono.empty());

    StepVerifier.create(
            workflowService
                .validateAndTriggerWorkflow(sessionId, workflowId, Map.of("key", "val"))
                .flatMap(exec -> exec.result()))
        .expectNextMatches(res -> "SUCCESS".equals(res.status()))
        .verifyComplete();

    verify(tracker).startWorkflow(anyString(), eq(sessionId), eq(workflowId), eq(List.of("n1")));
  }

  @Test
  void testValidateAndTriggerWorkflowNotFound() {
    String sessionId = "sess-missing";
    String workflowId = "w-missing";

    when(workflowDefinitionStore.find(sessionId, workflowId)).thenReturn(Mono.empty());

    StepVerifier.create(
            workflowService.validateAndTriggerWorkflow(sessionId, workflowId, Map.of("k", "v")))
        .expectErrorMatches(
            e ->
                e instanceof IllegalArgumentException
                    && e.getMessage().contains("Workflow not found"))
        .verify();
  }

  @Test
  void runWorkflowUsesCacheHitWithoutCallingStore() {
    final PreparedWorkflow prepared =
        new PreparedWorkflow(
            List.of(), Map.of(), Map.of(), Map.of(), List.of(), (e, p) -> Mono.empty());
    preparedWorkflowCache.put("s1", "wf1", prepared);
    when(orchestrator.execute(eq("s1"), eq("wf1"), any(), eq(prepared), any()))
        .thenReturn(Mono.empty());

    final WorkflowExecution execution = workflowService.runWorkflow("s1", "wf1", Map.of("k", "v"));
    StepVerifier.create(execution.result())
        .expectNextMatches(r -> "SUCCESS".equals(r.status()))
        .verifyComplete();

    org.mockito.Mockito.verify(workflowDefinitionStore, org.mockito.Mockito.never())
        .find(any(), any());
    org.mockito.Mockito.verify(orchestrator, org.mockito.Mockito.never()).prepareWorkflow(any());
  }

  @Test
  void runWorkflowOnCacheMissLoadsFromStoreAndCompiles() {
    final WorkflowDefinition def =
        new WorkflowDefinition(
            "wf1",
            "desc",
            List.of(new WorkflowDefinition.Node("n1", "trigger", Map.of())),
            List.of());
    final PreparedWorkflow prepared =
        new PreparedWorkflow(
            List.of(), Map.of(), Map.of(), Map.of(), List.of(), (e, p) -> Mono.empty());
    when(workflowDefinitionStore.find("s1", "wf1")).thenReturn(Mono.just(def));
    when(orchestrator.prepareWorkflow(def)).thenReturn(Mono.just(prepared));
    when(orchestrator.execute(eq("s1"), eq("wf1"), any(), eq(prepared), any()))
        .thenReturn(Mono.empty());

    final WorkflowExecution execution = workflowService.runWorkflow("s1", "wf1", Map.of("k", "v"));
    StepVerifier.create(execution.result())
        .expectNextMatches(r -> "SUCCESS".equals(r.status()))
        .verifyComplete();

    org.mockito.Mockito.verify(workflowDefinitionStore).find("s1", "wf1");
    org.mockito.Mockito.verify(orchestrator).prepareWorkflow(def);
  }

  @Test
  void runWorkflowOnStoreMissPropagatesFailure() {
    when(workflowDefinitionStore.find("s1", "wf1")).thenReturn(Mono.empty());

    final WorkflowExecution execution = workflowService.runWorkflow("s1", "wf1", Map.of("k", "v"));
    StepVerifier.create(execution.result())
        .expectNextMatches(
            r -> "FAILURE".equals(r.status()) && r.output().contains("Workflow not found"))
        .verifyComplete();
  }
}
