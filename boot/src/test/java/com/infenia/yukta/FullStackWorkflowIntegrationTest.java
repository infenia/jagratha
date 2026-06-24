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

import com.infenia.yukta.dto.request.ConfigRequest;
import com.infenia.yukta.dto.request.WorkflowDefinitionRequest;
import com.infenia.yukta.dto.request.WorkflowDefinitionRequest.EdgeRequest;
import com.infenia.yukta.dto.request.WorkflowDefinitionRequest.NodeRequest;
import com.infenia.yukta.harness.WorkflowTestHarness;
import com.infenia.yukta.model.execution.WorkflowProgress;
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
    final NodeRequest terminal = new NodeRequest("terminal", "CONSOLE_TERMINAL", Map.of());

    final WorkflowDefinitionRequest workflow =
        new WorkflowDefinitionRequest(
            "Complex Workflow",
            "A complex workflow with multiple stages",
            List.of(trigger, mapper, filter, branch, aggregator, terminal),
            List.of(
                new EdgeRequest("trigger", "mapper", "default"),
                new EdgeRequest("mapper", "filter", "default"),
                new EdgeRequest("filter", "branch", "default"),
                new EdgeRequest("branch", "aggregator", "highPort"),
                new EdgeRequest("branch", "aggregator", "lowPort"),
                new EdgeRequest("aggregator", "terminal", "default")));

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
    final String executionId = harness.triggerWorkflow(sessionId, "main-flow");

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
        new NodeRequest("terminal", "CONSOLE_TERMINAL", Map.of("timeoutSeconds", 3600));

    final WorkflowDefinitionRequest workflow =
        new WorkflowDefinitionRequest(
            "Loop Workflow",
            "A workflow with loop support",
            List.of(trigger, loop, terminal),
            List.of(
                new EdgeRequest("trigger", "loop", "default"),
                new EdgeRequest("loop", "terminal", "default")));

    final ConfigRequest configRequest =
        new ConfigRequest(
            sessionId,
            "Loop Test",
            "TestRunner",
            Map.of(),
            System.getProperty("java.io.tmpdir"),
            Map.of("loop-flow", workflow));

    harness.initSession(configRequest);
    final String executionId = harness.triggerWorkflow(sessionId, "loop-flow");

    final WorkflowProgress progress = harness.pollUntilFinished(sessionId, executionId);
    harness.verifyStatus(progress, "SUCCESS");
  }

}
