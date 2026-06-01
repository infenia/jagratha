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

    implementation(libs.picocli)
    implementation(libs.spring.boot.starter)
    implementation(libs.jackson.databind)

    annotationProcessor(libs.picocli.codegen)
    annotationProcessor(libs.spring.boot.configuration.processor)

    testImplementation(libs.spring.boot.starter.test)
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Aproject=${project.group}/${project.name}")
}

// Disable quality checks for CLI module - baseline to be improved
tasks.named("pmdMain") {
    enabled = false
}
tasks.named("checkstyleMain") {
    enabled = false
}
tasks.named("spotbugsMain") {
    enabled = false
}

coverageConfig {
    val cliCoverage = mapOf(
        "LINE" to 0.75,
        "BRANCH" to 0.70,
        "CLASS" to 0.80,
        "INSTRUCTION" to 0.75,
        "METHOD" to 0.75
    )

    exceptions.put("com.infenia.yukta.cli.YuktaCli", cliCoverage)
    exceptions.put("com.infenia.yukta.cli.CliRunner", cliCoverage)
    exceptions.put("com.infenia.yukta.cli.command.ControlCommand", cliCoverage)
    exceptions.put("com.infenia.yukta.cli.command.control.NodesCommand", cliCoverage)
    exceptions.put("com.infenia.yukta.cli.command.control.GetNodesCommand", cliCoverage)
    exceptions.put("com.infenia.yukta.cli.command.control.GetAllNodesCommand", cliCoverage)
    exceptions.put("com.infenia.yukta.cli.command.control.HeartbeatCommand", cliCoverage)
    exceptions.put("com.infenia.yukta.cli.command.control.SendCommandCommand", cliCoverage)
    exceptions.put("com.infenia.yukta.cli.command.control.ProgressCommand", cliCoverage)
    exceptions.put("com.infenia.yukta.cli.command.control.ProgressStreamCommand", cliCoverage)
    exceptions.put("com.infenia.yukta.cli.command.control.LogsStreamCommand", cliCoverage)
    exceptions.put("com.infenia.yukta.cli.command.control.HistoryCommand", cliCoverage)
}
