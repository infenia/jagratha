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

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Thread-safe registry of all currently running workflow executions.
 *
 * <p>The orchestrator registers an {@link ExecutionControl} on start and unregisters it in the
 * {@code doFinally} callback. The {@code DirectiveDispatcher} queries this registry to locate the
 * execution that a control command targets.
 */
@Component
@NoArgsConstructor
public class ExecutionControlRegistry {

  private final Map<String, ExecutionControl> byExecutionId = new ConcurrentHashMap<>();

  /**
   * Registers a new execution.
   *
   * @param control the execution handle to register
   */
  public void register(final ExecutionControl control) {
    byExecutionId.put(control.executionId(), control);
  }

  /**
   * Removes the execution handle for the given execution ID.
   *
   * @param executionId the execution to deregister
   */
  public void unregister(final String executionId) {
    byExecutionId.remove(executionId);
  }

  /**
   * Looks up an execution by its unique identifier.
   *
   * @param executionId the execution identifier
   * @return an Optional containing the handle, or empty if not found
   */
  public Optional<ExecutionControl> findByExecutionId(final String executionId) {
    return Optional.ofNullable(byExecutionId.get(executionId));
  }

  /**
   * Finds the active execution for a given workflow and session.
   *
   * <p>If more than one execution is active for the same workflow (which should not happen in
   * normal operation), the first found is returned.
   *
   * @param sessionId the session identifier
   * @param workflowId the workflow identifier
   * @return an Optional containing the active handle, or empty if no match
   */
  public Optional<ExecutionControl> findActiveByWorkflow(
      final String sessionId, final String workflowId) {
    return byExecutionId.values().stream()
        .filter(c -> c.sessionId().equals(sessionId) && c.workflowId().equals(workflowId))
        .findFirst();
  }
}
