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
package com.infenia.yukta.logging.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LogSearchCriteriaTest {

  @Test
  void testCreateSearchCriteria() {
    final LogSearchCriteria criteria =
        new LogSearchCriteria("exec-123", "plugin-id", LogStream.STDOUT, 100, 0);

    assertThat(criteria.executionId()).isEqualTo("exec-123");
    assertThat(criteria.pluginId()).isEqualTo("plugin-id");
    assertThat(criteria.stream()).isEqualTo(LogStream.STDOUT);
    assertThat(criteria.limit()).isEqualTo(100);
    assertThat(criteria.offset()).isEqualTo(0);
  }

  @Test
  void testNullPluginIdAndStreamAreAllowed() {
    final LogSearchCriteria criteria = new LogSearchCriteria("exec-123", null, null, 500, 10);

    assertThat(criteria.executionId()).isEqualTo("exec-123");
    assertThat(criteria.pluginId()).isNull();
    assertThat(criteria.stream()).isNull();
    assertThat(criteria.limit()).isEqualTo(500);
    assertThat(criteria.offset()).isEqualTo(10);
  }

  @Test
  void testOfFactoryMethod() {
    final LogSearchCriteria criteria = LogSearchCriteria.of("exec-123");

    assertThat(criteria.executionId()).isEqualTo("exec-123");
    assertThat(criteria.pluginId()).isNull();
    assertThat(criteria.stream()).isNull();
    assertThat(criteria.limit()).isEqualTo(LogSearchCriteria.DEFAULT_LIMIT);
    assertThat(criteria.offset()).isEqualTo(0);
  }

  @Test
  void testEqualsAndHashCode() {
    final LogSearchCriteria criteria1 =
        new LogSearchCriteria("exec-123", "plugin-id", LogStream.STDOUT, 100, 0);
    final LogSearchCriteria criteria2 =
        new LogSearchCriteria("exec-123", "plugin-id", LogStream.STDOUT, 100, 0);

    assertThat(criteria1).isEqualTo(criteria2);
    assertThat(criteria1.hashCode()).isEqualTo(criteria2.hashCode());
  }

  @Test
  void testToString() {
    final LogSearchCriteria criteria =
        new LogSearchCriteria("exec-123", "plugin-id", LogStream.STDOUT, 100, 0);

    final String str = criteria.toString();
    assertThat(str).contains("exec-123");
    assertThat(str).contains("plugin-id");
  }
}
