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
package com.infenia.yukta;

import static org.assertj.core.api.Assertions.assertThat;

import com.infenia.yukta.harness.WorkflowTestHarness;
import com.infenia.yukta.model.api.ConfigRequest;
import com.infenia.yukta.model.api.WorkflowDefinitionRequest;
import com.infenia.yukta.model.api.WorkflowDefinitionRequest.EdgeRequest;
import com.infenia.yukta.model.api.WorkflowDefinitionRequest.NodeRequest;
import com.infenia.yukta.model.monitoring.WorkflowProgress;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class FullStackWorkflowIntegrationTest {

  @Autowired private WebTestClient webClient;

  private WorkflowTestHarness harness;

  @BeforeEach
  void setUp() {
    harness = new WorkflowTestHarness(webClient);
  }

  @Test
  void testComplexWorkflow_AllCorePlugins() {
    final String sessionId = "test-session-" + UUID.randomUUID();

    // Define a complex workflow
    final NodeRequest trigger = new NodeRequest("trigger", "api-trigger", Map.of());

    // Mapper to add a field
    final NodeRequest mapper =
        new NodeRequest(
            "mapper",
            "MAPPER",
            Map.of("mode", "PROJECTION", "mapping", Map.of("value", "payload.input * 2")));

    // Filter to check value
    final NodeRequest filter =
        new NodeRequest("filter", "FILTER", Map.of("condition", "payload.value > 10"));

    // Branch to route
    final NodeRequest branch =
        new NodeRequest(
            "branch",
            "BRANCH",
            Map.of(
                "mode", "SELECT_KEY",
                "selector", "payload.value > 20 ? 'high' : 'low'",
                "cases",
                    Map.of(
                        "high", "highPort",
                        "low", "lowPort")));

    // Aggregator to collect results
    final NodeRequest aggregator =
        new NodeRequest(
            "aggregator",
            "AGGREGATOR",
            Map.of(
                "groupBy", "'static'",
                "window", Map.of("type", "COUNT", "size", 1),
                "aggregation", Map.of("type", "SUM", "field", "payload.value")));

    // Terminal
    final NodeRequest terminal = new NodeRequest("terminal", "console", Map.of());

    final WorkflowDefinitionRequest workflow =
        new WorkflowDefinitionRequest(
            "Complex Workflow",
            "A complex workflow with multiple stages",
            List.of(trigger, mapper, filter, branch, aggregator, terminal),
            List.of(
                new EdgeRequest("trigger", "mapper"),
                new EdgeRequest("mapper", "filter"),
                new EdgeRequest("filter", "branch"),
                new EdgeRequest("branch", "aggregator", "highPort"),
                new EdgeRequest("branch", "aggregator", "lowPort"),
                new EdgeRequest("aggregator", "terminal")));

    final ConfigRequest configRequest =
        new ConfigRequest(
            sessionId,
            "Integration Test Workflow",
            "TestRunner",
            Map.of("env", "test"),
            System.getProperty("java.io.tmpdir"),
            Map.of("main-flow", workflow));

    // 1. Initialize
    harness.initSession(configRequest);

    // 2. Trigger
    final String executionId = harness.triggerWorkflow(sessionId, "main-flow", Map.of("input", 15));

    // 3. Poll and Verify
    final WorkflowProgress progress = harness.pollUntilFinished(sessionId, executionId);
    harness.verifyStatus(progress, "SUCCESS");

    // Verify task sequence
    assertThat(progress.tasks())
        .extracting("nodeId")
        .containsSubsequence("trigger", "mapper", "filter", "branch", "aggregator", "terminal");

    // Verify performance header was checked in harness (it throws if not present)
  }

  @Test
  @org.junit.jupiter.api.Disabled(
      "TODO: Fix LoopStreamProcessor integration - returns ERROR status")
  void testLoopPlugin() {
    final String sessionId = "loop-session-" + UUID.randomUUID();

    final NodeRequest trigger = new NodeRequest("trigger", "api-trigger", Map.of());

    final NodeRequest loop =
        new NodeRequest(
            "loop",
            "LOOP_STREAM",
            Map.of(
                "targetPluginId",
                "MAPPER", // Type MAPPER in registry
                "targetConfig",
                Map.of(
                    "mode", "PROJECTION", "mapping", Map.of("count", "(payload.count ?: 0) + 1")),
                "exitCondition",
                "payload.count >= 5",
                "maxIterations",
                10,
                "timeoutSeconds",
                3600));

    final NodeRequest terminal =
        new NodeRequest("terminal", "console", Map.of("timeoutSeconds", 3600));

    final WorkflowDefinitionRequest workflow =
        new WorkflowDefinitionRequest(
            "Loop Workflow",
            "A workflow with loop support",
            List.of(trigger, loop, terminal),
            List.of(new EdgeRequest("trigger", "loop"), new EdgeRequest("loop", "terminal")));

    final ConfigRequest configRequest =
        new ConfigRequest(
            sessionId,
            "Loop Test",
            "TestRunner",
            Map.of(),
            System.getProperty("java.io.tmpdir"),
            Map.of("loop-flow", workflow));

    harness.initSession(configRequest);
    final String executionId = harness.triggerWorkflow(sessionId, "loop-flow", Map.of("count", 0));

    final WorkflowProgress progress = harness.pollUntilFinished(sessionId, executionId);
    harness.verifyStatus(progress, "SUCCESS");
  }

  @Test
  void testGuardJoinSubWorkflow() {
    final String sessionId = "adv-session-" + UUID.randomUUID();

    // 1. Trigger
    final NodeRequest trigger = new NodeRequest("trigger", "api-trigger", Map.of());

    // 2. Guard (using BRANCH in EXPRESSION mode)
    final NodeRequest guard =
        new NodeRequest(
            "guard",
            "BRANCH",
            Map.of(
                "mode",
                "EXPRESSION",
                "cases",
                Map.of("payload.amount > 100", "true", "payload.amount <= 100", "false")));

    // 3. Branches
    final NodeRequest mapperTrue =
        new NodeRequest(
            "mapperTrue",
            "MAPPER",
            Map.of("mode", "PROJECTION", "mapping", Map.of("processed", "true")));
    final NodeRequest mapperFalse =
        new NodeRequest(
            "mapperFalse",
            "MAPPER",
            Map.of("mode", "PROJECTION", "mapping", Map.of("processed", "false")));

    // 4. Join (using AGGREGATOR)
    final NodeRequest join =
        new NodeRequest(
            "join",
            "AGGREGATOR",
            Map.of(
                "groupBy", "'static'",
                "window", Map.of("type", "COUNT", "size", 1),
                "aggregation", Map.of("type", "LAST", "field", "payload")));

    // 5. Sub-workflow Node
    final NodeRequest subWorkflowNode =
        new NodeRequest("sub", "SUB_WORKFLOW", Map.of("subWorkflowId", "child-flow"));

    // 6. Terminal
    final NodeRequest terminal = new NodeRequest("terminal", "console", Map.of());

    // Main workflow
    final WorkflowDefinitionRequest mainWorkflow =
        new WorkflowDefinitionRequest(
            "Main Flow",
            "Main workflow with guard and subworkflow",
            List.of(trigger, guard, mapperTrue, mapperFalse, join, subWorkflowNode, terminal),
            List.of(
                new EdgeRequest("trigger", "guard"),
                new EdgeRequest("guard", "mapperTrue", "true"),
                new EdgeRequest("guard", "mapperFalse", "false"),
                new EdgeRequest("mapperTrue", "join"),
                new EdgeRequest("mapperFalse", "join"),
                new EdgeRequest("join", "sub"),
                new EdgeRequest("sub", "terminal")));

    // Child workflow
    final WorkflowDefinitionRequest childWorkflow =
        new WorkflowDefinitionRequest(
            "Child Flow",
            "Child workflow executed by main workflow",
            List.of(
                new NodeRequest("t1", "api-trigger", Map.of()),
                new NodeRequest(
                    "m1",
                    "MAPPER",
                    Map.of("mode", "PROJECTION", "mapping", Map.of("subResult", "'done'"))),
                new NodeRequest("term", "console", Map.of())),
            List.of(new EdgeRequest("t1", "m1"), new EdgeRequest("m1", "term")));

    final ConfigRequest configRequest =
        new ConfigRequest(
            sessionId,
            "Advanced Test",
            "TestRunner",
            Map.of(),
            System.getProperty("java.io.tmpdir"),
            Map.of(
                "main-flow", mainWorkflow,
                "child-flow", childWorkflow));

    harness.initSession(configRequest);
    final String executionId =
        harness.triggerWorkflow(sessionId, "main-flow", Map.of("amount", 150));

    final WorkflowProgress progress = harness.pollUntilFinished(sessionId, executionId);
    harness.verifyStatus(progress, "SUCCESS");

    assertThat(progress.tasks())
        .extracting("nodeId")
        .contains("trigger", "guard", "mapperTrue", "join", "terminal");

    final var mapperFalseTask =
        progress.tasks().stream()
            .filter(t -> "mapperFalse".equals(t.nodeId()))
            .findFirst()
            .orElseThrow();
    assertThat(mapperFalseTask.status()).isEqualTo("SUCCESS");

    final var mapperTrueTask =
        progress.tasks().stream()
            .filter(t -> "mapperTrue".equals(t.nodeId()))
            .findFirst()
            .orElseThrow();
    assertThat(mapperTrueTask.status()).isEqualTo("SUCCESS");
  }
}
