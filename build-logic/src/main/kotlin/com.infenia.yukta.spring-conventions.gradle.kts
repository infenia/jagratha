// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Infenia Private Limited

plugins {
    id("com.infenia.yukta.java-conventions")
    id("com.infenia.yukta.quality-conventions")
    id("com.infenia.yukta.jacoco-conventions")
    id("io.spring.dependency-management")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.findVersion("springBoot").get().requiredVersion}")
        mavenBom("org.springframework.ai:spring-ai-bom:${libs.findVersion("springAi").get().requiredVersion}")
    }
}

dependencies {
    testImplementation(libs.findLibrary("spring-boot-starter-test").get())
    testImplementation(libs.findLibrary("reactor-test").get())
}

configurations.all {
    // Patch CVE-2026-59901, CVE-2026-55831, CVE-2026-55833, CVE-2026-56745, CVE-2026-56816
    // Force Netty codec versions to 4.2.16.Final
    val nettyVersion = libs.findVersion("netty").get().requiredVersion
    resolutionStrategy.force(
        "io.netty:netty-codec-compression:$nettyVersion",
        "io.netty:netty-codec-http:$nettyVersion",
        "io.netty:netty-codec-http3:$nettyVersion"
    )
}
