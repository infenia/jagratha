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
package com.infenia.yukta;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.*;

@Isolated
class YuktaApplicationTest {

  private static final String NATIVE_IMAGE_PROPERTY = "org.graalvm.nativeimage.imagecode";

  private String originalNativeImageProperty;

  @BeforeEach
  void setUp() {
    originalNativeImageProperty = System.getProperty(NATIVE_IMAGE_PROPERTY);
  }

  @AfterEach
  void tearDown() {
    if (originalNativeImageProperty != null) {
      System.setProperty(NATIVE_IMAGE_PROPERTY, originalNativeImageProperty);
    } else {
      System.clearProperty(NATIVE_IMAGE_PROPERTY);
    }
  }

    @Test
    void main() {
        assertDoesNotThrow(() -> YuktaApplication.main(new String[] {"--server.port=0"}));
    }

  @Test
  void testIsNativeImageWhenPropertyNotSet() {
    System.clearProperty(NATIVE_IMAGE_PROPERTY);
    assertFalse(isNativeImageReflection());
  }

  @Test
  void testIsNativeImageWhenRuntimeProperty() {
    System.setProperty(NATIVE_IMAGE_PROPERTY, "runtime");
    assertTrue(isNativeImageReflection());
  }

  @Test
  void testIsNativeImageWhenBuildtimeProperty() {
    System.setProperty(NATIVE_IMAGE_PROPERTY, "buildtime");
    assertFalse(isNativeImageReflection());
  }

  @Test
  void testIsNativeImageWhenOtherProperty() {
    System.setProperty(NATIVE_IMAGE_PROPERTY, "other");
    assertFalse(isNativeImageReflection());
  }

  @Test
  void testHasProfileArgumentWithActiveProfileFlag() {
    String[] args = {"--spring.profiles.active=prod"};
    assertTrue(hasProfileArgumentReflection(args));
  }

  @Test
  void testHasProfileArgumentWithDFlag() {
    String[] args = {"-Dspring.profiles.active=dev"};
    assertTrue(hasProfileArgumentReflection(args));
  }

  @Test
  void testHasProfileArgumentWithMultipleArgs() {
    String[] args = {"--server.port=8080", "--spring.profiles.active=test"};
    assertTrue(hasProfileArgumentReflection(args));
  }

  @Test
  void testHasProfileArgumentWithActiveProfileFlagAndValue() {
    String[] args = {"--spring.profiles.active=production"};
    assertTrue(hasProfileArgumentReflection(args));
  }

  @Test
  void testHasProfileArgumentWithDFlagAndValue() {
    String[] args = {"-Dspring.profiles.active=development"};
    assertTrue(hasProfileArgumentReflection(args));
  }

  @Test
  void testHasProfileArgumentWithNoProfile() {
    String[] args = {"--server.port=8080", "--logging.level=debug"};
    assertFalse(hasProfileArgumentReflection(args));
  }

  @Test
  void testHasProfileArgumentWithEmptyArgs() {
    String[] args = {};
    assertFalse(hasProfileArgumentReflection(args));
  }

  @Test
  void testHasProfileArgumentWithSimilarFlag() {
    String[] args = {"--spring.profiles.include=extra"};
    assertFalse(hasProfileArgumentReflection(args));
  }

  @Test
  void testHasProfileArgumentLoopsAllArgs() {
    String[] args = {
      "--server.port=8080", "--logging.level=debug", "--spring.profiles.active=final"
    };
    assertTrue(hasProfileArgumentReflection(args));
  }

  @Test
  void testHasProfileArgumentBreaksOnFirstMatch() {
    String[] args = {"--spring.profiles.active=first", "--spring.profiles.active=second"};
    assertTrue(hasProfileArgumentReflection(args));
  }

  private boolean isNativeImageReflection() {
    try {
      var method = YuktaApplication.class.getDeclaredMethod("isNativeImage");
      method.setAccessible(true);
      return (boolean) method.invoke(null);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private boolean hasProfileArgumentReflection(String[] args) {
    try {
      var method =
          YuktaApplication.class.getDeclaredMethod("hasProfileArgument", String[].class);
      method.setAccessible(true);
      return (boolean) method.invoke(null, (Object) args);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
