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
package com.infenia.yukta.service.workflow.store;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;

/** Unit tests for {@link WorkflowDefinitionStoreConfiguration}. */
@SpringBootTest
@NoArgsConstructor
class WorkflowDefinitionStoreConfigurationTest {

  /** Injected store instance. */
  @Autowired private WorkflowDefinitionStore workflowDefinitionStore;

  @Test
  void inMemoryWorkflowDefinitionStore_isCreated_whenNoOtherBeanPresent() {
    // Given & When
    // Spring context is initialized with default configuration

    // Then
    assertThat(workflowDefinitionStore).isNotNull();
    assertThat(workflowDefinitionStore).isInstanceOf(InMemoryWorkflowDefinitionStore.class);
  }

  @Test
  void inMemoryWorkflowDefinitionStore_createsValidInstance() {
    // Given & When
    final WorkflowDefinitionStoreConfiguration config = new WorkflowDefinitionStoreConfiguration();
    final WorkflowDefinitionStore store = config.inMemoryWorkflowDefinitionStore();

    // Then
    assertThat(store).isNotNull();
    assertThat(store).isInstanceOf(InMemoryWorkflowDefinitionStore.class);
  }

  @Test
  void configuration_classHasValidSpringAnnotations() {
    // Given
    final WorkflowDefinitionStoreConfiguration config = new WorkflowDefinitionStoreConfiguration();

    // When & Then
    assertThat(config).isNotNull();
    assertThat(config.getClass().isAnnotationPresent(Configuration.class)).isTrue();
  }

  @Test
  void inMemoryWorkflowDefinitionStore_multipleCallsReturnDifferentInstances() {
    // Given
    final WorkflowDefinitionStoreConfiguration config = new WorkflowDefinitionStoreConfiguration();

    // When
    final WorkflowDefinitionStore store1 = config.inMemoryWorkflowDefinitionStore();
    final WorkflowDefinitionStore store2 = config.inMemoryWorkflowDefinitionStore();

    // Then
    assertThat(store1).isNotNull();
    assertThat(store2).isNotNull();
    assertThat(store1).isNotSameAs(store2);
  }

  @Test
  void beanMethodCanBeCalledDirectly() {
    // Given
    final WorkflowDefinitionStoreConfiguration config = new WorkflowDefinitionStoreConfiguration();

    // When
    final WorkflowDefinitionStore store = config.inMemoryWorkflowDefinitionStore();

    // Then
    assertThat(store).isNotNull();
    assertThat(store).isInstanceOf(WorkflowDefinitionStore.class);
  }
}
