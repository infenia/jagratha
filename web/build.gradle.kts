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
plugins {
    id("com.infenia.yukta.library-conventions")
}

dependencies {
    api(project(":core"))

    implementation(libs.spring.boot.starter.webflux)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.springdoc.openapi)
    implementation(libs.mapstruct)

    annotationProcessor(libs.spring.boot.configuration.processor)
    annotationProcessor(libs.mapstruct.processor)
    annotationProcessor(libs.lombok.mapstruct.binding)

    testImplementation(libs.spring.boot.starter.webflux.test)
    testImplementation(libs.spring.boot.starter.validation.test)
}

coverageConfig {
    val baselineCoverage = mapOf(
        "LINE" to 0.8,
        "BRANCH" to 0.5,
        "CLASS" to 0.8,
        "INSTRUCTION" to 0.8,
        "METHOD" to 0.8
    )
    val refactoredControllerCoverage = mapOf(
        "LINE" to 0.5,
        "BRANCH" to 0.5,
        "CLASS" to 0.5,
        "INSTRUCTION" to 0.5,
        "METHOD" to 0.6
    )
    val zeroCoverage = mapOf(
        "LINE" to 0.0,
        "BRANCH" to 0.0,
        "CLASS" to 0.0,
        "INSTRUCTION" to 0.0,
        "METHOD" to 0.0
    )

    exceptions.put("com.infenia.yukta.controller.SessionController", baselineCoverage)
    exceptions.put("com.infenia.yukta.controller.ConfigController", baselineCoverage)
    exceptions.put("com.infenia.yukta.controller.AppController", baselineCoverage)
    exceptions.put("com.infenia.yukta.controller.PluginController", baselineCoverage)
    exceptions.put("com.infenia.yukta.controller.ControlBusController", baselineCoverage)
    exceptions.put("com.infenia.yukta.controller.LogManagementController", refactoredControllerCoverage)
    exceptions.put("com.infenia.yukta.controller.SessionConfigController", baselineCoverage)
    exceptions.put("com.infenia.yukta.controller.WorkflowStatusController", refactoredControllerCoverage)
    exceptions.put("com.infenia.yukta.controller.WorkflowTriggerController", refactoredControllerCoverage)

    // Exclude all DTO, mapper, filter, and exception classes from coverage
    exceptions.put("com.infenia.yukta.dto.*", zeroCoverage)
    exceptions.put("com.infenia.yukta.exception.*", zeroCoverage)
    exceptions.put("com.infenia.yukta.mapper.*", zeroCoverage)
    exceptions.put("com.infenia.yukta.filter.*", zeroCoverage)
}
