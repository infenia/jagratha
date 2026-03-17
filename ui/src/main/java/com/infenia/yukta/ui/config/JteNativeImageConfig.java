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
 * Configuration for JTE template engine in native images. GraalVM native image configuration is
 * handled by native-image.properties and reflect-config.json. The spring property
 * gg.jte.usePrecompiledTemplates=true (in application-prod.yaml) ensures precompiled templates are
 * used instead of dynamic compilation.
 */
@Configuration
public class JteNativeImageConfig {
  // Configuration is applied via application-prod.yaml and native-image property files
  // No bean override needed - Spring Boot JTE starter handles it automatically
}
