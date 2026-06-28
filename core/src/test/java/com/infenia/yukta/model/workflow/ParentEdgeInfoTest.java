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
package com.infenia.yukta.model.workflow;

import lombok.NoArgsConstructor;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link ParentEdgeInfo}. */
@NoArgsConstructor
class ParentEdgeInfoTest {

  @Test
  void testConstructorAndGetters() {
    // Test with null sourcePort
    final ParentEdgeInfo info1 = new ParentEdgeInfo(0, "source1", null);
    assertEquals(0, info1.parentIndex());
    assertEquals("source1", info1.sourceNodeId());
    assertNull(info1.sourcePort());

    // Test with non-null sourcePort
    final ParentEdgeInfo info2 = new ParentEdgeInfo(1, "source2", "port1");
    assertEquals(1, info2.parentIndex());
    assertEquals("source2", info2.sourceNodeId());
    assertEquals("port1", info2.sourcePort());
  }

  @Test
  void testEqualsAndHashCode() {
    final ParentEdgeInfo info1 = new ParentEdgeInfo(0, "source", null);
    final ParentEdgeInfo info2 = new ParentEdgeInfo(0, "source", null);
    final ParentEdgeInfo info3 = new ParentEdgeInfo(0, "source", "port");
    final ParentEdgeInfo info4 = new ParentEdgeInfo(1, "source", null);
    final ParentEdgeInfo info5 = new ParentEdgeInfo(0, "different", null);

    // Same values should be equal
    assertEquals(info1, info2);
    assertEquals(info1.hashCode(), info2.hashCode());

    // Different sourcePort should not be equal
    assertNotEquals(info1, info3);
    assertNotEquals(info1.hashCode(), info3.hashCode());

    // Different parentIndex should not be equal
    assertNotEquals(info1, info4);
    assertNotEquals(info1.hashCode(), info4.hashCode());

    // Different sourceNodeId should not be equal
    assertNotEquals(info1, info5);
    assertNotEquals(info1.hashCode(), info5.hashCode());

    // Reflexive
    assertEquals(info1, info1);
    assertEquals(info1.hashCode(), info1.hashCode());
  }

  @Test
  void testToString() {
    final ParentEdgeInfo infoWithNullPort = new ParentEdgeInfo(0, "source", null);
    final ParentEdgeInfo infoWithPort = new ParentEdgeInfo(1, "source", "port");

    final String toStringWithNull = infoWithNullPort.toString();
    final String toStringWithPort = infoWithPort.toString();

    // toString should contain all field values
    assertEquals(
        "ParentEdgeInfo[parentIndex=0, sourceNodeId=source, sourcePort=null]", toStringWithNull);
    assertEquals(
        "ParentEdgeInfo[parentIndex=1, sourceNodeId=source, sourcePort=port]", toStringWithPort);
  }
}
