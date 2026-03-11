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
package com.infenia.yukta.service.session;

import com.infenia.yukta.config.SessionConfigProperties;
import com.infenia.yukta.model.WorkflowDefinition;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.test.StepVerifier;
import tools.jackson.databind.ObjectMapper;

class FileSessionConfigStoreTest {

  @TempDir Path tempDir;

  private FileSessionConfigStore configStore;
  private SessionConfigProperties props;

  @BeforeEach
  void setUp() {
    props = new SessionConfigProperties();
    props.setBaseDir(tempDir.toString());
    props.setFileLogSubDir("modified-files");
    props.setResultLogSubDir("results");
    props.setExecutionTimeoutSeconds(3600L);
    configStore = new FileSessionConfigStore(props, new ObjectMapper());
  }

  @Test
  void testDefaultValues() {
    String sessionId = "sess-1";
    StepVerifier.create(configStore.getProjectPath(sessionId)).expectNext("").verifyComplete();
    StepVerifier.create(configStore.getWorkflows(sessionId)).expectNext(Map.of()).verifyComplete();
    StepVerifier.create(configStore.getExecutionTimeout(sessionId))
        .expectNext(3600L)
        .verifyComplete();
    StepVerifier.create(configStore.getInitiator(sessionId)).expectNext("").verifyComplete();
    StepVerifier.create(configStore.getInitiatedTime(sessionId)).expectNext("").verifyComplete();
    StepVerifier.create(configStore.getTags(sessionId)).expectNext(Map.of()).verifyComplete();
    StepVerifier.create(configStore.getDescription(sessionId)).expectNext("").verifyComplete();
  }

  @Test
  void testProjectPathPersistence() {
    String sessionId = "sess-1";
    String projectPath = "/api/project";

    StepVerifier.create(configStore.setProjectPath(sessionId, projectPath)).verifyComplete();

    StepVerifier.create(configStore.getProjectPath(sessionId))
        .expectNext(projectPath)
        .verifyComplete();
  }

  @Test
  void testWorkflowsPersistence() {
    String sessionId = "sess-1";
    WorkflowDefinition workflow =
        new WorkflowDefinition(
            "desc", List.of(new WorkflowDefinition.Node("n1", "gradle", Map.of())), List.of());

    StepVerifier.create(configStore.setWorkflows(sessionId, Map.of("w1", workflow)))
        .verifyComplete();

    StepVerifier.create(configStore.getWorkflow(sessionId, "w1"))
        .expectNext(workflow)
        .verifyComplete();
  }

  @Test
  void testMetadataPersistence() {
    String sessionId = "sess-1";
    String initiator = "John Doe";
    String time = "2026-02-21T21:00:00Z";
    String description = "Sample Session";
    Map<String, String> tags = Map.of("clientId", "c1");

    StepVerifier.create(configStore.setInitiator(sessionId, initiator)).verifyComplete();
    StepVerifier.create(configStore.setDescription(sessionId, description)).verifyComplete();
    StepVerifier.create(configStore.setInitiatedTime(sessionId, time)).verifyComplete();
    StepVerifier.create(configStore.setTags(sessionId, tags)).verifyComplete();

    StepVerifier.create(configStore.getInitiator(sessionId)).expectNext(initiator).verifyComplete();
    StepVerifier.create(configStore.getInitiatedTime(sessionId)).expectNext(time).verifyComplete();
    StepVerifier.create(configStore.getDescription(sessionId))
        .expectNext(description)
        .verifyComplete();
    StepVerifier.create(configStore.getTags(sessionId)).expectNext(tags).verifyComplete();
  }

  @Test
  void testPutIfAbsentBehaviorForMetadata() {
    String sessionId = "sess-1";
    String initiator = "John Doe";

    StepVerifier.create(configStore.setInitiator(sessionId, initiator)).verifyComplete();
    StepVerifier.create(configStore.getInitiator(sessionId)).expectNext(initiator).verifyComplete();

    // Try to set again - should not override
    StepVerifier.create(configStore.setInitiator(sessionId, "Other")).verifyComplete();
    StepVerifier.create(configStore.getInitiator(sessionId)).expectNext(initiator).verifyComplete();
  }

  @Test
  void testGetAllConfigs() throws IOException {
    String sessionId = "sess-meta";
    configStore.setInitiator(sessionId, "Jules").block();
    configStore.setInitiatedTime(sessionId, "now").block();
    configStore.setTags(sessionId, Map.of("k", "v")).block();
    configStore.setDescription(sessionId, "Sample").block();
    configStore.setProjectPath(sessionId, "/meta/path").block();
    WorkflowDefinition workflow = new WorkflowDefinition("w", List.of(), List.of());
    configStore.setWorkflows(sessionId, Map.of("w1", workflow)).block();

    StepVerifier.create(configStore.getAllConfigs(sessionId))
        .expectNextMatches(
            map ->
                "Jules".equals(map.get("initiator"))
                    && "now".equals(map.get("initiatedTime"))
                    && Map.of("k", "v").equals(map.get("tags"))
                    && "Sample".equals(map.get("description"))
                    && "/meta/path".equals(map.get("projectPath"))
                    && Map.of("w1", workflow).equals(map.get("workflows")))
        .verifyComplete();
  }

  @Test
  void testFileStorageStructure() throws IOException {
    String sessionId = "sess-1";
    configStore.setProjectPath(sessionId, "/test/path").block();

    Path sessionsDir = tempDir.resolve("sessions");
    Path sessionFile = sessionsDir.resolve(sessionId + ".json");

    assert Files.exists(sessionFile);
    String content = Files.readString(sessionFile);
    assert content.contains("sessionId");
    assert content.contains("sess-1");
  }

  @Test
  void testSetNullMetadata() {
    String sessionId = "sess-null";
    StepVerifier.create(configStore.setInitiator(sessionId, null)).verifyComplete();
    StepVerifier.create(configStore.setInitiatedTime(sessionId, null)).verifyComplete();
    StepVerifier.create(configStore.setTags(sessionId, null)).verifyComplete();
    StepVerifier.create(configStore.setDescription(sessionId, null)).verifyComplete();

    StepVerifier.create(configStore.getInitiator(sessionId)).expectNext("").verifyComplete();
    StepVerifier.create(configStore.getInitiatedTime(sessionId)).expectNext("").verifyComplete();
    StepVerifier.create(configStore.getTags(sessionId)).expectNext(Map.of()).verifyComplete();
    StepVerifier.create(configStore.getDescription(sessionId)).expectNext("").verifyComplete();
  }
}
