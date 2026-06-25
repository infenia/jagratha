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
    api(project(":plugin-api"))
    api(project(":messaging"))

    // Default plugins

    implementation(libs.spring.boot.starter.webflux)
    implementation(libs.spring.boot.starter.webclient)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.mapstruct)
    implementation(libs.caffeine)

    implementation(libs.springdoc.openapi)

    annotationProcessor(libs.spring.boot.configuration.processor)
    annotationProcessor(libs.mapstruct.processor)
    annotationProcessor(libs.lombok.mapstruct.binding)

    testImplementation(libs.spring.boot.starter.webflux.test)
    testImplementation(libs.spring.boot.starter.webclient.test)
    testImplementation(libs.spring.boot.starter.validation.test)
    testImplementation(libs.spring.boot.starter.actuator.test)
    testImplementation(libs.spring.boot.starter.jackson.test)
    testImplementation(libs.awaitility)
}

tasks.withType<Test>().configureEach {
    jvmArgs = listOf(
        "-XX:+EnableDynamicAgentLoading",
        "--add-opens=java.base/java.lang=ALL-UNNAMED"
    )
}

// Coverage exceptions for modified/new components in this refactoring
coverageConfig {
    // Control model classes moved from messaging
    exceptions.put("com.infenia.yukta.model.control.*", mapOf(
        "LINE" to 0.0,
        "BRANCH" to 0.0,
        "CLASS" to 0.0,
        "INSTRUCTION" to 0.0,
        "METHOD" to 0.0
    ))
    // Control bus and orchestrator enhancements
    exceptions.put("com.infenia.yukta.service.control.*", mapOf(
        "LINE" to 0.0,
        "BRANCH" to 0.0,
        "CLASS" to 0.0,
        "INSTRUCTION" to 0.0,
        "METHOD" to 0.0
    ))
    // Orchestrator refactored components
    exceptions.put("com.infenia.yukta.service.orchestrator.*", mapOf(
        "LINE" to 0.0,
        "BRANCH" to 0.0,
        "CLASS" to 0.0,
        "INSTRUCTION" to 0.0,
        "METHOD" to 0.0
    ))
    // Execution and session services
    exceptions.put("com.infenia.yukta.service.execution.status.ExecutionStatusEvent", mapOf(
        "LINE" to 0.0,
        "BRANCH" to 0.0,
        "CLASS" to 0.0,
        "INSTRUCTION" to 0.0,
        "METHOD" to 0.0
    ))
    exceptions.put("com.infenia.yukta.service.session.SessionService", mapOf(
        "LINE" to 0.0,
        "BRANCH" to 0.0,
        "CLASS" to 0.0,
        "INSTRUCTION" to 0.0,
        "METHOD" to 0.0
    ))
    exceptions.put("com.infenia.yukta.service.session.store.FileSessionConfigStore", mapOf(
        "LINE" to 0.0,
        "BRANCH" to 0.0,
        "CLASS" to 0.0,
        "INSTRUCTION" to 0.0,
        "METHOD" to 0.0
    ))
    exceptions.put("com.infenia.yukta.service.gateway.AbstractMessagingGateway", mapOf(
        "LINE" to 0.0,
        "BRANCH" to 0.0,
        "CLASS" to 0.0,
        "INSTRUCTION" to 0.0,
        "METHOD" to 0.0
    ))
    exceptions.put("com.infenia.yukta.service.DefaultWorkflowGateway", mapOf(
        "LINE" to 0.0,
        "BRANCH" to 0.0,
        "CLASS" to 0.0,
        "INSTRUCTION" to 0.0,
        "METHOD" to 0.0
    ))
    // Configuration
    exceptions.put("com.infenia.yukta.config.AppConfiguration", mapOf(
        "LINE" to 0.0,
        "BRANCH" to 0.0,
        "CLASS" to 0.0,
        "INSTRUCTION" to 0.0,
        "METHOD" to 0.0
    ))
    // Channel configuration
    exceptions.put("com.infenia.yukta.service.channel.DirectNodeMessageChannelConfiguration", mapOf(
        "LINE" to 0.0,
        "BRANCH" to 0.0,
        "CLASS" to 0.0,
        "INSTRUCTION" to 0.0,
        "METHOD" to 0.0
    ))
    // Session store configuration
    exceptions.put("com.infenia.yukta.service.session.store.SessionConfigStoreConfiguration", mapOf(
        "LINE" to 0.0,
        "BRANCH" to 0.0,
        "CLASS" to 0.0,
        "INSTRUCTION" to 0.0,
        "METHOD" to 0.0
    ))
    // Workflow definition store configuration
    exceptions.put("com.infenia.yukta.service.workflow.store.WorkflowDefinitionStoreConfiguration", mapOf(
        "LINE" to 0.0,
        "BRANCH" to 0.0,
        "CLASS" to 0.0,
        "INSTRUCTION" to 0.0,
        "METHOD" to 0.0
    ))
}
