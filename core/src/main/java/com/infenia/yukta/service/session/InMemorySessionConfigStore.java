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
import com.infenia.yukta.model.session.SessionConfigData;
import com.infenia.yukta.service.workflow.store.WorkflowDefinitionStore;
import com.infenia.yukta.validation.ProjectPath;
import com.infenia.yukta.validation.SessionId;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * In-memory implementation of SessionConfigStore. All configuration is session-based and updated
 * via API.
 */
@Slf4j
@Component
@Validated
@RequiredArgsConstructor
@SuppressWarnings({"PMD.UseConcurrentHashMap", "PMD.TooManyMethods"})
public class InMemorySessionConfigStore implements SessionConfigStore {

  private final SessionConfigProperties props;
  private final WorkflowDefinitionStore workflowDefinitionStore;

  private final Map<String, String> projectPaths = new ConcurrentHashMap<>();
  private final Map<String, String> initiators = new ConcurrentHashMap<>();
  private final Map<String, String> initiatedTimes = new ConcurrentHashMap<>();
  private final Map<String, Map<String, String>> tagsMap = new ConcurrentHashMap<>();
  private final Map<String, String> descriptions = new ConcurrentHashMap<>();

  @Override
  public Mono<Void> applySessionConfig(@Valid final SessionConfigData data) {
    return Mono.fromRunnable(
            () -> {
              final String sessionId = data.sessionId();
              projectPaths.put(sessionId, data.projectPath());
              descriptions.put(sessionId, data.description());
              initiators.put(sessionId, data.initiator());
              initiatedTimes.put(sessionId, Instant.now().toString());
              tagsMap.put(sessionId, data.tags());
              log.atInfo()
                  .addKeyValue("sessionId", sessionId)
                  .addKeyValue("projectPath", data.projectPath())
                  .addKeyValue("initiator", data.initiator())
                  .addKeyValue("tagCount", data.tags().size())
                  .log("Applied session configuration");
            })
        .then(
            data.workflows().isEmpty()
                ? Mono.empty()
                : Flux.fromIterable(data.workflows().values())
                    .flatMap(def -> workflowDefinitionStore.save(data.sessionId(), def))
                    .then(
                        Mono.fromRunnable(
                            () -> log.atDebug()
                                .addKeyValue("sessionId", data.sessionId())
                                .addKeyValue("workflowCount", data.workflows().size())
                                .log("Saved workflows for session"))));
  }

  @Override
  public Mono<String> getProjectPath(@SessionId final String sessionId) {
    final String result = projectPaths.get(sessionId);
    return Mono.just(result != null ? result : "");
  }

  @Override
  public Mono<Void> setProjectPath(
      @SessionId final String sessionId, @ProjectPath final String path) {
    projectPaths.put(sessionId, path);
    log.atDebug()
        .addKeyValue("sessionId", sessionId)
        .addKeyValue("projectPath", path)
        .log("Set project path for session");
    return Mono.empty();
  }

  @Override
  public Mono<Long> getExecutionTimeout(@SessionId final String sessionId) {
    return Mono.just(props.getExecutionTimeoutSeconds());
  }

  @Override
  public Mono<String> getFileLogDir(final String sessionId) {
    return Mono.just(props.getBaseDir() + "/" + props.getFileLogSubDir());
  }

  @Override
  public Mono<String> getResultLogDir(final String sessionId) {
    return Mono.just(props.getBaseDir() + "/" + props.getResultLogSubDir());
  }

  @Override
  public Mono<String> getDescription(@SessionId final String sessionId) {
    final String result = descriptions.get(sessionId);
    return Mono.just(result != null ? result : "");
  }

  @Override
  public Mono<Void> setDescription(@SessionId final String sessionId, final String description) {
    if (description != null) {
      final boolean isNew = descriptions.putIfAbsent(sessionId, description) == null;
      log.atDebug()
          .addKeyValue("sessionId", sessionId)
          .addKeyValue("isNew", isNew)
          .log("Set description for session");
    }
    return Mono.empty();
  }

  @Override
  public Mono<String> getInitiator(@SessionId final String sessionId) {
    final String result = initiators.get(sessionId);
    return Mono.just(result != null ? result : "");
  }

  @Override
  public Mono<Void> setInitiator(@SessionId final String sessionId, final String initiator) {
    if (initiator != null) {
      final boolean isNew = initiators.putIfAbsent(sessionId, initiator) == null;
      log.atDebug()
          .addKeyValue("sessionId", sessionId)
          .addKeyValue("initiator", initiator)
          .addKeyValue("isNew", isNew)
          .log("Set initiator for session");
    }
    return Mono.empty();
  }

  @Override
  public Mono<String> getInitiatedTime(@SessionId final String sessionId) {
    final String result = initiatedTimes.get(sessionId);
    return Mono.just(result != null ? result : "");
  }

  @Override
  public Mono<Void> setInitiatedTime(
      @SessionId final String sessionId, final String initiatedTime) {
    if (initiatedTime != null) {
      final boolean isNew = initiatedTimes.putIfAbsent(sessionId, initiatedTime) == null;
      log.atDebug()
          .addKeyValue("sessionId", sessionId)
          .addKeyValue("isNew", isNew)
          .log("Set initiated time for session");
    }
    return Mono.empty();
  }

  @Override
  public Mono<Map<String, String>> getTags(@SessionId final String sessionId) {
    final Map<String, String> result = tagsMap.get(sessionId);
    return Mono.just(result != null ? result : Map.of());
  }

  @Override
  public Mono<Void> setTags(@SessionId final String sessionId, final Map<String, String> tags) {
    if (tags != null) {
      final boolean isNew = tagsMap.putIfAbsent(sessionId, Map.copyOf(tags)) == null;
      log.atDebug()
          .addKeyValue("sessionId", sessionId)
          .addKeyValue("tagCount", tags.size())
          .addKeyValue("isNew", isNew)
          .log("Set tags for session");
    }
    return Mono.empty();
  }

  @Override
  public Mono<Map<String, Object>> getAllConfigs(@SessionId final String sessionId) {
    return sessionExists(sessionId)
        ? Mono.zip(
            arr -> {
              final Map<String, Object> configs = new java.util.LinkedHashMap<>();
              configs.put("projectPath", arr[0]);
              configs.put("executionTimeout", arr[1]);
              configs.put("fileLogDir", arr[2]);
              configs.put("resultLogDir", arr[3]);
              configs.put("initiator", arr[4]);
              configs.put("initiatedTime", arr[5]);
              configs.put("tags", arr[6]);
              configs.put("description", arr[7]);
              log.atDebug()
                  .addKeyValue("sessionId", sessionId)
                  .addKeyValue("configCount", configs.size())
                  .log("Retrieved all configurations for session");
              return configs;
            },
            getProjectPath(sessionId),
            getExecutionTimeout(sessionId),
            getFileLogDir(sessionId),
            getResultLogDir(sessionId),
            getInitiator(sessionId),
            getInitiatedTime(sessionId),
            getTags(sessionId),
            getDescription(sessionId))
        : Mono.empty();
  }

  private boolean sessionExists(final String sessionId) {
    return projectPaths.containsKey(sessionId)
        || initiators.containsKey(sessionId)
        || initiatedTimes.containsKey(sessionId)
        || tagsMap.containsKey(sessionId)
        || descriptions.containsKey(sessionId);
  }

  @Override
  public Flux<String> getSessionIds() {
    final java.util.Set<String> allSessions = new java.util.HashSet<>();
    allSessions.addAll(projectPaths.keySet());
    allSessions.addAll(initiators.keySet());
    allSessions.addAll(initiatedTimes.keySet());
    allSessions.addAll(tagsMap.keySet());
    allSessions.addAll(descriptions.keySet());
    log.atDebug()
        .addKeyValue("sessionCount", allSessions.size())
        .log("Retrieved all active session IDs");
    return Flux.fromIterable(allSessions);
  }
}
