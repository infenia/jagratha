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

    // Default plugins

    implementation(libs.spring.boot.starter.webflux)
    implementation(libs.spring.boot.starter.webclient)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.mapstruct)

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

coverageConfig {
    exceptions.put("com.infenia.yukta.service.TaskTrackerService", mapOf("LINE" to 0.99, "BRANCH" to 0.94, "INSTRUCTION" to 0.98, "METHOD" to 0.97))
    exceptions.put("com.infenia.yukta.service.TaskTrackerService.*WorkflowState", mapOf("BRANCH" to 0.91))
    exceptions.put("com.infenia.yukta.service.WorkflowOrchestrator", mapOf("LINE" to 0.0, "BRANCH" to 0.0, "INSTRUCTION" to 0.0, "METHOD" to 0.0, "CLASS" to 0.0))
    exceptions.put("com.infenia.yukta.service.WorkflowService", mapOf("LINE" to 0.80, "BRANCH" to 0.0, "INSTRUCTION" to 0.85, "METHOD" to 0.73))
    exceptions.put("com.infenia.yukta.service.control.directive.DirectiveDispatcher", mapOf("LINE" to 0.0, "BRANCH" to 0.0, "INSTRUCTION" to 0.0, "METHOD" to 0.0, "CLASS" to 0.0))
    exceptions.put("com.infenia.yukta.service.control.ExecutionControlRegistry", mapOf("LINE" to 0.80, "BRANCH" to 0.70, "INSTRUCTION" to 0.80, "METHOD" to 0.80))
    // Control bus infrastructure - new refactoring
    exceptions.put("com.infenia.yukta.service.orchestrator.WorkflowOrchestrator", mapOf("LINE" to 0.0, "BRANCH" to 0.0, "INSTRUCTION" to 0.0, "METHOD" to 0.0, "CLASS" to 0.0))
    exceptions.put("com.infenia.yukta.service.control.ControlBusService", mapOf("LINE" to 0.0, "BRANCH" to 0.0, "INSTRUCTION" to 0.0, "METHOD" to 0.0, "CLASS" to 0.0))
    exceptions.put("com.infenia.yukta.service.control.gateway.DefaultControlBusGateway", mapOf("LINE" to 0.0, "BRANCH" to 0.0, "INSTRUCTION" to 0.0, "METHOD" to 0.0, "CLASS" to 0.0))
    exceptions.put("com.infenia.yukta.service.orchestrator.stream.StreamBuilder", mapOf("LINE" to 0.0, "BRANCH" to 0.0, "INSTRUCTION" to 0.0, "METHOD" to 0.0, "CLASS" to 0.0))
    exceptions.put("com.infenia.yukta.service.orchestrator.stream.StreamTopologyDecorator", mapOf("LINE" to 0.90, "BRANCH" to 0.90, "INSTRUCTION" to 0.90, "METHOD" to 0.90))
    exceptions.put("com.infenia.yukta.service.orchestrator.strategy.TriggerNodeAssemblerStrategy", mapOf("LINE" to 0.0, "BRANCH" to 0.0, "INSTRUCTION" to 0.0, "METHOD" to 0.0, "CLASS" to 0.0))
    exceptions.put("com.infenia.yukta.service.orchestrator.strategy.ProcessorNodeAssemblerStrategy", mapOf("LINE" to 0.0, "BRANCH" to 0.0, "INSTRUCTION" to 0.0, "METHOD" to 0.0, "CLASS" to 0.0))
    exceptions.put("com.infenia.yukta.service.orchestrator.strategy.TerminalNodeAssemblerStrategy", mapOf("LINE" to 0.0, "BRANCH" to 0.0, "INSTRUCTION" to 0.0, "METHOD" to 0.0, "CLASS" to 0.0))
    exceptions.put("com.infenia.yukta.service.orchestrator.strategy.StreamAssemblyHelper", mapOf("LINE" to 0.0, "BRANCH" to 0.0, "INSTRUCTION" to 0.0, "METHOD" to 0.0, "CLASS" to 0.0))
    exceptions.put("com.infenia.yukta.service.orchestrator.tracker.DefaultTaskTrackerServiceService", mapOf("LINE" to 0.90, "BRANCH" to 0.90, "INSTRUCTION" to 0.90, "METHOD" to 0.90))
    exceptions.put("com.infenia.yukta.service.orchestrator.tracker.DefaultTaskTrackerServiceService.*", mapOf("LINE" to 0.0, "BRANCH" to 0.90, "INSTRUCTION" to 0.0, "METHOD" to 0.0))
    exceptions.put("com.infenia.yukta.mapper.AppConfigMapperImpl", mapOf("LINE" to 0.60, "BRANCH" to 0.50, "INSTRUCTION" to 0.60, "METHOD" to 0.70))
    // Control processor implementations - orchestrator revamp
    exceptions.put("com.infenia.yukta.service.control.processor.EnableStepModeCommandProcessor", mapOf("LINE" to 0.0, "BRANCH" to 0.0, "INSTRUCTION" to 0.0, "METHOD" to 0.0, "CLASS" to 0.0))
    exceptions.put("com.infenia.yukta.service.control.processor.DisableStepModeCommandProcessor", mapOf("LINE" to 0.0, "BRANCH" to 0.0, "INSTRUCTION" to 0.0, "METHOD" to 0.0, "CLASS" to 0.0))
    exceptions.put("com.infenia.yukta.service.control.processor.ResumeNodeCommandProcessor", mapOf("LINE" to 0.0, "BRANCH" to 0.0, "INSTRUCTION" to 0.0, "METHOD" to 0.0, "CLASS" to 0.0))
    exceptions.put("com.infenia.yukta.service.control.processor.RestartCommandProcessor", mapOf("LINE" to 0.0, "BRANCH" to 0.0, "INSTRUCTION" to 0.0, "METHOD" to 0.0, "CLASS" to 0.0))
    exceptions.put("com.infenia.yukta.service.control.processor.StepNodeCommandProcessor", mapOf("LINE" to 0.0, "BRANCH" to 0.0, "INSTRUCTION" to 0.0, "METHOD" to 0.0, "CLASS" to 0.0))
    exceptions.put("com.infenia.yukta.service.control.processor.RestartFromNodeCommandProcessor", mapOf("LINE" to 0.0, "BRANCH" to 0.0, "INSTRUCTION" to 0.0, "METHOD" to 0.0, "CLASS" to 0.0))
    exceptions.put("com.infenia.yukta.service.control.processor.ResumeWorkflowCommandProcessor", mapOf("LINE" to 0.0, "BRANCH" to 0.0, "INSTRUCTION" to 0.0, "METHOD" to 0.0, "CLASS" to 0.0))
    exceptions.put("com.infenia.yukta.service.control.processor.PauseNodeCommandProcessor", mapOf("LINE" to 0.0, "BRANCH" to 0.0, "INSTRUCTION" to 0.0, "METHOD" to 0.0, "CLASS" to 0.0))
    exceptions.put("com.infenia.yukta.service.control.processor.StopNodeCommandProcessor", mapOf("LINE" to 0.0, "BRANCH" to 0.0, "INSTRUCTION" to 0.0, "METHOD" to 0.0, "CLASS" to 0.0))
    exceptions.put("com.infenia.yukta.service.control.processor.SkipNodeCommandProcessor", mapOf("LINE" to 0.0, "BRANCH" to 0.0, "INSTRUCTION" to 0.0, "METHOD" to 0.0, "CLASS" to 0.0))
    exceptions.put("com.infenia.yukta.service.orchestrator.preparator.WorkflowPreparator", mapOf("LINE" to 0.0, "BRANCH" to 0.0, "INSTRUCTION" to 0.0, "METHOD" to 0.0, "CLASS" to 0.0))
    exceptions.put("com.infenia.yukta.service.control.WorkflowExecutionSnapshot", mapOf("LINE" to 0.0, "BRANCH" to 0.50, "INSTRUCTION" to 0.90, "METHOD" to 0.0, "CLASS" to 0.0))
    exceptions.put("com.infenia.yukta.exception.GlobalExceptionHandler", mapOf("LINE" to 0.50, "BRANCH" to 0.30, "INSTRUCTION" to 0.60, "METHOD" to 0.70, "CLASS" to 0.0))
    exceptions.put("com.infenia.yukta.service.orchestrator.validator.WorkflowValidator", mapOf("LINE" to 0.90, "BRANCH" to 0.90, "INSTRUCTION" to 0.90, "METHOD" to 0.0, "CLASS" to 0.0))
    exceptions.put("com.infenia.yukta.service.session.InMemorySessionConfigStore", mapOf("LINE" to 0.90, "BRANCH" to 0.60, "INSTRUCTION" to 0.90, "METHOD" to 0.0, "CLASS" to 0.0))
    exceptions.put("com.infenia.yukta.service.control.command.PrepareWorkflowCommand", mapOf("LINE" to 0.0, "BRANCH" to 0.0, "INSTRUCTION" to 0.0, "METHOD" to 0.0, "CLASS" to 0.0))
    exceptions.put("com.infenia.yukta.service.gateway.AbstractMessagingGateway", mapOf("LINE" to 0.0, "BRANCH" to 0.90, "INSTRUCTION" to 0.0, "METHOD" to 0.0, "CLASS" to 0.0))
    exceptions.put("com.infenia.yukta.service.orchestrator.compiler.HeartbeatBuilder", mapOf("LINE" to 0.0, "BRANCH" to 0.0, "INSTRUCTION" to 0.0, "METHOD" to 0.0, "CLASS" to 0.0))
    exceptions.put("com.infenia.yukta.service.orchestrator.compiler.WorkflowCompiler", mapOf("LINE" to 0.0, "BRANCH" to 0.0, "INSTRUCTION" to 0.0, "METHOD" to 0.0, "CLASS" to 0.0))
    exceptions.put("com.infenia.yukta.service.workflow.store.PreparedWorkflowCache", mapOf("LINE" to 0.60, "BRANCH" to 0.70, "INSTRUCTION" to 0.70, "METHOD" to 0.70, "CLASS" to 1.0))
    exceptions.put("com.infenia.yukta.service.execution.status.ExecutionStatusEvent", mapOf("LINE" to 0.0, "BRANCH" to 0.0, "INSTRUCTION" to 0.0, "METHOD" to 0.0, "CLASS" to 0.0))
    exceptions.put("com.infenia.yukta.service.control.processor.PauseWorkflowCommandProcessor", mapOf("LINE" to 0.0, "BRANCH" to 0.0, "INSTRUCTION" to 0.0, "METHOD" to 0.0, "CLASS" to 0.0))
}
