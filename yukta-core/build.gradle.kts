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
    `java-library`
    id("com.infenia.yukta.java-conventions")
    id("com.infenia.yukta.quality-conventions")
    id("com.infenia.yukta.jacoco-conventions")
    alias(libs.plugins.spring.dependency.management)
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.springBoot.get()}")
    }
}

dependencies {
    api(project(":yukta-plugin-api"))

    // Default plugins
    implementation(project(":plugins:build-tools:gradle"))
    implementation(project(":plugins:file-update"))

    implementation(libs.spring.boot.starter.webflux)
    implementation(libs.spring.boot.starter.webclient)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.mapstruct)

    implementation(libs.spring.ai.mcp.server)
    implementation(libs.springdoc.openapi)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    annotationProcessor(libs.spring.boot.configuration.processor)
    annotationProcessor(libs.mapstruct.processor)
    annotationProcessor(libs.lombok.mapstruct.binding)

    testImplementation(libs.spring.boot.starter.webflux.test)
    testImplementation(libs.spring.boot.starter.webclient.test)
    testImplementation(libs.spring.boot.starter.validation.test)
    testImplementation(libs.spring.boot.starter.actuator.test)
    testImplementation(libs.spring.boot.starter.jackson.test)
    testImplementation(libs.reactor.test)
}

coverageConfig {
    val zeroCoverage = mapOf(
        "LINE" to 0.0,
        "BRANCH" to 0.0,
        "CLASS" to 0.0,
        "INSTRUCTION" to 0.0,
        "METHOD" to 0.0
    )

    exceptions.put("com.infenia.yukta.mapper.*Impl", zeroCoverage)
    exceptions.put("com.infenia.yukta.model.*", zeroCoverage)
    exceptions.put("com.infenia.yukta.service.aggregate.InMemoryAggregateStore*", zeroCoverage)
    exceptions.put("com.infenia.yukta.service.resequence.InMemoryResequencerStore*", zeroCoverage)
    exceptions.put("com.infenia.yukta.service.resequence.ResequencerStore*", zeroCoverage)
    exceptions.put("com.infenia.yukta.mcp.AppMcpTools", zeroCoverage)
    exceptions.put("com.infenia.yukta.service.TaskTrackerService*", zeroCoverage)
    exceptions.put("com.infenia.yukta.service.WorkflowService", zeroCoverage)
    exceptions.put("com.infenia.yukta.service.NoOpSecretProvider", zeroCoverage)
    exceptions.put("com.infenia.yukta.service.SessionService", zeroCoverage)
    exceptions.put("com.infenia.yukta.service.LogRetrievalService", zeroCoverage)
    exceptions.put("com.infenia.yukta.service.WorkflowRegistry", zeroCoverage)
    exceptions.put("com.infenia.yukta.service.WorkflowValidator", zeroCoverage)
    exceptions.put("com.infenia.yukta.service.ControlBusService", zeroCoverage)
    exceptions.put("com.infenia.yukta.service.DefaultControlBusGateway", zeroCoverage)
    exceptions.put("com.infenia.yukta.service.WorkflowOrchestrator*", zeroCoverage)
    exceptions.put("com.infenia.yukta.service.join.InMemoryJoinStore*", zeroCoverage)
    exceptions.put("com.infenia.yukta.service.join.JoinStore*", zeroCoverage)
    exceptions.put("com.infenia.yukta.config.AppConfigService", zeroCoverage)
    exceptions.put("com.infenia.yukta.config.AppConfiguration", zeroCoverage)
    exceptions.put("com.infenia.yukta.controller.SessionController", zeroCoverage)
    exceptions.put("com.infenia.yukta.controller.ConfigController", zeroCoverage)
    exceptions.put("gg.jte.generated.**", zeroCoverage)

    // Current coverage gaps to be addressed later
    exceptions.put("com.infenia.yukta.exception.GlobalExceptionHandler", mapOf("BRANCH" to 0.6))
    exceptions.put("com.infenia.yukta.util.VariableResolver", mapOf("BRANCH" to 0.8))
    exceptions.put("com.infenia.yukta.util.MapUtils", mapOf("BRANCH" to 0.9))
}
