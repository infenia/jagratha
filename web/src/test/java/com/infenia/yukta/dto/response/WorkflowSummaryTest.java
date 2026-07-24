// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Infenia Private Limited
package com.infenia.yukta.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Tests for WorkflowSummary. */
class WorkflowSummaryTest {

  @Test
  void constructor_validInputs_createsRecord() {
    final WorkflowSummary summary =
        new WorkflowSummary("wf1", "desc", 5, 4, "SUCCESS");

    assertThat(summary.workflowId()).isEqualTo("wf1");
    assertThat(summary.description()).isEqualTo("desc");
    assertThat(summary.nodeCount()).isEqualTo(5);
    assertThat(summary.edgeCount()).isEqualTo(4);
    assertThat(summary.status()).isEqualTo("SUCCESS");
  }

  @Test
  void constructor_withNullStatus_preservesNull() {
    final WorkflowSummary summary = new WorkflowSummary("wf2", "test", 2, 1, null);

    assertThat(summary.status()).isNull();
  }

  @Test
  void equals_sameValues_returnsTrue() {
    final WorkflowSummary summary1 = new WorkflowSummary("wf1", "desc", 5, 4, "SUCCESS");
    final WorkflowSummary summary2 = new WorkflowSummary("wf1", "desc", 5, 4, "SUCCESS");

    assertThat(summary1).isEqualTo(summary2);
  }

  @Test
  void equals_differentValues_returnsFalse() {
    final WorkflowSummary summary1 = new WorkflowSummary("wf1", "desc", 5, 4, "SUCCESS");
    final WorkflowSummary summary2 = new WorkflowSummary("wf2", "desc", 5, 4, "SUCCESS");

    assertThat(summary1).isNotEqualTo(summary2);
  }

  @Test
  void hashCode_consistent() {
    final WorkflowSummary summary1 = new WorkflowSummary("wf1", "desc", 5, 4, "SUCCESS");
    final WorkflowSummary summary2 = new WorkflowSummary("wf1", "desc", 5, 4, "SUCCESS");

    assertThat(summary1.hashCode()).isEqualTo(summary2.hashCode());
  }

  @Test
  void toString_containsAllFields() {
    final WorkflowSummary summary = new WorkflowSummary("wf1", "desc", 5, 4, "SUCCESS");
    final String str = summary.toString();

    assertThat(str).contains("WorkflowSummary").contains("wf1").contains("SUCCESS");
  }
}
