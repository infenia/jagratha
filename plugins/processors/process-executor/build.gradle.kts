// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Infenia Private Limited
plugins {
    id("com.infenia.yukta.library-conventions")
}

version = "1.0.0"

dependencies {
    api(project(":messaging"))
    implementation(project(":plugin-api"))
    implementation(project(":core"))
    implementation(libs.spring.boot.starter.webflux)
}

coverageConfig {
    exceptions.put(
        "com.infenia.yukta.plugin.process.ProcessExecutorGateway",
        mapOf(
            "LINE" to 0.90,
            "BRANCH" to 0.90,
            "INSTRUCTION" to 0.90,
            "METHOD" to 0.90
        )
    )
}
