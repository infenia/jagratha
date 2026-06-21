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
package com.infenia.yukta.service.session;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

/**
 * Tests for SessionConfigStoreFactory with in-memory store type. Verifies that conditional bean
 * creation correctly instantiates InMemorySessionConfigStore when the store-type property is set to
 * "in-memory".
 */
@SpringBootTest
@TestPropertySource(properties = "yukta.session.store-type=in-memory")
@DisplayName("SessionConfigStore with in-memory store type")
class SessionConfigStoreFactoryTest {

  @Autowired private ApplicationContext context;

  @Autowired private SessionConfigStoreFactory factory;

  @Test
  @DisplayName("should create InMemorySessionConfigStore bean when store-type is in-memory")
  void testInMemoryStoreCreated() {
    // Verify InMemorySessionConfigStore bean exists
    InMemorySessionConfigStore store = context.getBean(InMemorySessionConfigStore.class);
    assertNotNull(store, "InMemorySessionConfigStore bean should exist");

    // Verify FileSessionConfigStore bean does NOT exist
    assertThrows(
        NoSuchBeanDefinitionException.class,
        () -> context.getBean(FileSessionConfigStore.class),
        "FileSessionConfigStore bean should not exist when store-type=in-memory");
  }

  @Test
  @DisplayName("should return InMemorySessionConfigStore from factory")
  void testFactoryReturnsInMemoryStore() {
    SessionConfigStore store = factory.getStore();
    assertNotNull(store, "Factory should return a non-null store");
    assertInstanceOf(
        InMemorySessionConfigStore.class,
        store,
        "Factory should return InMemorySessionConfigStore instance");
  }
}
