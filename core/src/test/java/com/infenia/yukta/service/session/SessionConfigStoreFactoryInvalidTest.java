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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Tests for SessionConfigStoreFactory with invalid store type. Verifies that the application
 * startup fails gracefully when an unsupported store-type value is provided, ensuring operators are
 * alerted to misconfiguration.
 *
 * <p>Design Requirement: "Verify application startup fails gracefully with invalid `store-type`
 * value" (Design Document Section 3: Error Handling)
 */
@DisplayName("SessionConfigStore with invalid store type")
class SessionConfigStoreFactoryInvalidTest {

  private static class MinimalTestApplication {
    @Configuration
    @EnableAutoConfiguration
    @ComponentScan(
        basePackages = {
          "com.infenia.yukta.service.session",
          "com.infenia.yukta.config",
          "com.infenia.yukta.service.workflow.store"
        })
    static class TestConfig {}
  }

  @Test
  @DisplayName(
      "should fail Spring context initialization with UnsatisfiedDependencyException when "
          + "store-type is invalid")
  void testInvalidStoreTypeFailsContextInitialization() {
    // Design Requirement: Error Handling
    // When store-type is set to an unsupported value (e.g., "invalid"):
    // 1. InMemorySessionConfigStore is NOT created
    //    (@ConditionalOnProperty requires havingValue="in-memory")
    // 2. FileSessionConfigStore is NOT created
    //    (@ConditionalOnProperty requires havingValue="file")
    // 3. SessionConfigStoreFactory requires a SessionConfigStore bean via constructor injection
    // 4. No matching bean exists → Spring fails with UnsatisfiedDependencyException
    //
    // This test verifies that invalid configuration causes expected startup failure,
    // alerting the user (operator) to the misconfiguration.

    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () -> {
              // Create a minimal Spring Boot application with only session components
              final SpringApplication app =
                  new SpringApplication(MinimalTestApplication.TestConfig.class);
              app.setDefaultProperties(
                  java.util.Collections.singletonMap("yukta.session.store-type", "invalid"));
              app.run();
            },
            "Expected exception when no store bean matches invalid store-type");

    // Verify the exception (could be BeanCreationException or UnsatisfiedDependencyException)
    // and that it mentions SessionConfigStore to aid debugging
    final String exceptionMessage = exception.getMessage() + " " + exception.getCause();
    assertTrue(
        exceptionMessage.contains("SessionConfigStore")
            || exceptionMessage.contains("sessionConfigStore"),
        String.format(
            "Exception should mention 'SessionConfigStore' to identify the problem: %s",
            exceptionMessage));
  }
}
