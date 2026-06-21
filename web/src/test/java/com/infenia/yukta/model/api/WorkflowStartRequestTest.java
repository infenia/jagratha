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
package com.infenia.yukta.model.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WorkflowStartRequestTest {
  @Test
  void shouldInitializeCorrectly() {
    WorkflowStartRequest request = new WorkflowStartRequest("session-1", "workflow-1");

    assertEquals("session-1", request.sessionId());
    assertEquals("workflow-1", request.workflowId());
  }

  @Test
  void testEqualsAndHashCode() {
    WorkflowStartRequest req1 = new WorkflowStartRequest("s", "w");
    WorkflowStartRequest req2 = new WorkflowStartRequest("s", "w");
    WorkflowStartRequest req3 = new WorkflowStartRequest("s2", "w");

    assertEquals(req1, req2);
    assertEquals(req1.hashCode(), req2.hashCode());
    assertNotEquals(req1, req3);
  }

  @Test
  void testToString() {
    WorkflowStartRequest request = new WorkflowStartRequest("s", "w");
    String toString = request.toString();
    assertTrue(toString.contains("sessionId=s"));
    assertTrue(toString.contains("workflowId=w"));
  }
}
