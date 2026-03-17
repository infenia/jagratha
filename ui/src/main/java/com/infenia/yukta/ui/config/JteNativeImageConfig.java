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
package com.infenia.yukta.ui.config;

import org.springframework.context.annotation.Configuration;

/**
 * JTE Native Image Configuration Marker. This class marks that JTE is configured for native images.
 * The native image build uses the prod profile which enables: - gg.jte.usePrecompiledTemplates=true
 * - gg.jte.developmentMode=false - GraalVM native-image.properties forces
 * -Dspring.profiles.active=prod
 *
 * <p>This ensures JTE uses precompiled template bytecode instead of attempting dynamic compilation.
 */
@Configuration
public class JteNativeImageConfig {
  // Configuration is applied via:
  // 1. boot/build.gradle.kts: buildArgs.add("-Dspring.profiles.active=prod")
  // 2. boot/src/main/resources/META-INF/native-image/native-image.properties:
  // Args=-Dspring.profiles.active=prod
  // 3. application-prod.yaml: gg.jte.usePrecompiledTemplates=true
}
