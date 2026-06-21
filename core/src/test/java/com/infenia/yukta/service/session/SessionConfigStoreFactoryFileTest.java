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
 * Tests for SessionConfigStoreFactory with file store type. Verifies that conditional bean creation
 * correctly instantiates FileSessionConfigStore when the store-type property is set to "file".
 */
@SpringBootTest
@TestPropertySource(properties = "yukta.session.store-type=file")
@DisplayName("SessionConfigStore with file store type")
class SessionConfigStoreFactoryFileTest {

  @Autowired private ApplicationContext context;

  @Test
  @DisplayName("should create FileSessionConfigStore bean when store-type is file")
  void testFileStoreCreated() {
    // Verify FileSessionConfigStore bean exists
    FileSessionConfigStore store = context.getBean(FileSessionConfigStore.class);
    assertNotNull(store, "FileSessionConfigStore bean should exist");

    // Verify InMemorySessionConfigStore bean does NOT exist
    assertThrows(
        NoSuchBeanDefinitionException.class,
        () -> context.getBean(InMemorySessionConfigStore.class),
        "InMemorySessionConfigStore bean should not exist when store-type=file");
  }

  @Test
  @DisplayName("should inject FileSessionConfigStore bean")
  void testFileStoreInjected() {
    SessionConfigStore store = context.getBean(SessionConfigStore.class);
    assertNotNull(store, "SessionConfigStore bean should exist");
    assertInstanceOf(
        FileSessionConfigStore.class,
        store,
        "Injected store should be FileSessionConfigStore instance");
  }
}
