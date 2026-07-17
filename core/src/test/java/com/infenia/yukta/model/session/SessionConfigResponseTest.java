// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Infenia Private Limited
package com.infenia.yukta.model.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.infenia.yukta.model.workflow.WorkflowDefinition;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class SessionConfigResponseTest {

  @Test
  void testSessionConfigResponse_withNullTags_defaultsToEmpty() {
    final var response =
        new SessionConfigResponse("id", "desc", "init", null, "path", Map.of());
    assertThat(response.tags()).isEmpty();
  }

  @Test
  void testSessionConfigResponse_withNullWorkflows_defaultsToEmpty() {
    final var response =
        new SessionConfigResponse("id", "desc", "init", Map.of(), "path", null);
    assertThat(response.workflows()).isEmpty();
  }

  @Test
  void testSessionConfigResponse_defensivelyCopesTags() {
    final var tags = new HashMap<String, String>();
    tags.put("env", "test");
    final var response =
        new SessionConfigResponse("id", "desc", "init", tags, "path", Map.of());
    tags.put("extra", "value");
    assertThat(response.tags()).hasSize(1).containsEntry("env", "test");
  }

  @Test
  void testSessionConfigResponse_defensivelyCopiresWorkflows() {
    final var workflows = new HashMap<String, WorkflowDefinition>();
    final var response =
        new SessionConfigResponse("id", "desc", "init", Map.of(), "path", workflows);
    workflows.put(
        "w1", new WorkflowDefinition("w1", "d", List.of(), List.of()));
    assertThat(response.workflows()).isEmpty();
  }
}
