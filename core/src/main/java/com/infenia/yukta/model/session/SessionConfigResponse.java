// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Infenia Private Limited
package com.infenia.yukta.model.session;

import com.infenia.yukta.model.workflow.WorkflowDefinition;
import java.util.Map;

/**
 * Response record containing complete session configuration data.
 *
 * @param sessionId the session identifier
 * @param description a human-readable description of the session
 * @param initiator the initiator name
 * @param tags additional tags for the session
 * @param projectPath the project path
 * @param workflows map of workflow definitions (DAGs)
 */
public record SessionConfigResponse(
    String sessionId,
    String description,
    String initiator,
    Map<String, String> tags,
    String projectPath,
    Map<String, WorkflowDefinition> workflows) {
  /** Compact constructor for defensive copying and immutability. */
  public SessionConfigResponse {
    tags = tags == null ? Map.of() : Map.copyOf(tags);
    workflows = workflows != null ? Map.copyOf(workflows) : Map.of();
  }
}
