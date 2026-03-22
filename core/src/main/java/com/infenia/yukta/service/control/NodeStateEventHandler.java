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

import com.infenia.yukta.plugin.core.NodeState;
import com.infenia.yukta.plugin.message.Message;
import com.infenia.yukta.plugin.message.control.NodeStateEvent;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Control signal handler for NodeStateEvent messages.
 *
 * <p>Tracks the current state of each node in an execution, storing the last known state transition
 * for monitoring and debugging purposes.
 */
@Slf4j
@Component
public class NodeStateEventHandler implements ControlSignalHandler {

  private final Map<String, NodeState> nodeStates = new ConcurrentHashMap<>();

  @Override
  public boolean canHandle(final Object payload) {
    return payload instanceof NodeStateEvent;
  }

  @Override
  public void handle(final String nodeId, final Message<?> message, final Object payload) {
    final NodeStateEvent event = (NodeStateEvent) payload;
    nodeStates.put(event.executionId() + ":" + event.nodeId(), event.newState());
    log.atDebug().log(
        "Node state transition: {} → {} (execution: {})",
        event.previousState(),
        event.newState(),
        event.executionId());
  }

  /**
   * Get the last known state for a node in an execution.
   *
   * @param executionId the execution identifier
   * @param nodeId the node identifier
   * @return the last known state, or null if not tracked
   */
  public NodeState getNodeState(@NotBlank final String executionId, @NotBlank final String nodeId) {
    return nodeStates.get(executionId + ":" + nodeId);
  }

  /**
   * Remove all state tracking for a node.
   *
   * @param nodeId the node identifier
   */
  @Override
  public void removeNode(final String nodeId) {
    nodeStates.keySet().removeIf(key -> key.endsWith(":" + nodeId));
  }
}
