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
package com.infenia.yukta.plugin.message.control;

import com.infenia.yukta.plugin.core.NodeState;
import java.time.Instant;

/**
 * Control event indicating a node state transition.
 *
 * @param nodeId the node identifier
 * @param executionId the execution identifier
 * @param previousState the previous node state
 * @param newState the new node state
 * @param timestamp when the transition occurred
 */
public record NodeStateEvent(
    String nodeId,
    String executionId,
    NodeState previousState,
    NodeState newState,
    Instant timestamp) {}
