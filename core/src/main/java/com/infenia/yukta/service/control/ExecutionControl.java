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

import com.infenia.yukta.model.workflow.PreparedWorkflow;
import java.util.Map;
import reactor.core.publisher.Sinks;

/**
 * Runtime handle for a live workflow execution.
 *
 * <p>Registered in {@link ExecutionControlRegistry} when an execution starts and removed when it
 * finishes. The {@code DirectiveDispatcher} looks up this record to apply stop, restart, or
 * restart-from-node directives without coupling to the orchestrator's internal state.
 *
 * @param sessionId the session that owns this execution
 * @param workflowId the workflow being executed
 * @param executionId the unique execution identifier
 * @param prepared the prepared workflow (definition, plugin cache, topology)
 * @param payload the original trigger payload used to start the execution
 * @param stopSink completing this sink cancels the execution via {@code Mono.firstWithSignal}
 */
public record ExecutionControl(
    String sessionId,
    String workflowId,
    String executionId,
    PreparedWorkflow prepared,
    Map<String, Object> payload,
    Sinks.One<Void> stopSink) {

  /** Compact constructor: makes payload immutable. */
  public ExecutionControl {
    payload = Map.copyOf(payload);
  }
}
