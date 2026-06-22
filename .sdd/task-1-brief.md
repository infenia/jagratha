# Task 1: Create the messaging module structure

**Goal:** Set up the new `messaging` Gradle module with directory structure and register it in settings.gradle.kts

**What to do:**
1. Create `/media/arun/Infenia/Infenia/Development/Public/yukta/messaging/build.gradle.kts` with the provided configuration
2. Create directory structure: `messaging/src/main/java/com/infenia/yukta/message/` and `messaging/src/main/java/com/infenia/yukta/message/control/` and `messaging/src/test/java/com/infenia/yukta/message/`
3. Update `settings.gradle.kts` to add `include("messaging")` after `includeBuild("build-logic")` and before `include("plugin-api")`
4. Verify Gradle recognizes the new module with `./gradlew projects | grep messaging`

**Files to create:**
- `messaging/build.gradle.kts`

**Files to modify:**
- `settings.gradle.kts`

**Expected output:**
- New `messaging` module visible in Gradle project list
- Build configuration in place with proper dependencies and coverage config

**Build.gradle.kts content:**

```kotlin
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
    api(libs.spring.boot.starter.webflux)
}

coverageConfig {
    val baselineCoverage = mapOf(
        "LINE" to 0.8,
        "BRANCH" to 0.5,
        "CLASS" to 0.8,
        "INSTRUCTION" to 0.8,
        "METHOD" to 0.8
    )

    val lowCoverage = mapOf(
        "LINE" to 0.0,
        "BRANCH" to 0.0,
        "CLASS" to 0.0,
        "INSTRUCTION" to 0.0,
        "METHOD" to 0.0
    )

    exceptions.put("com.infenia.yukta.message.DefaultMessage", mapOf(
        "LINE" to 0.9,
        "BRANCH" to 0.5,
        "CLASS" to 0.8,
        "INSTRUCTION" to 0.9,
        "METHOD" to 0.9
    ))
    exceptions.put("com.infenia.yukta.message.control.*", lowCoverage)
}
```

**Global constraints:**
- All Java files must have Apache 2.0 license header (managed by Spotless)
- Follow Google Java Style Guide (2-space indentation, 100-char line limit)
- Code must pass: Spotless, Checkstyle, PMD, SpotBugs, JaCoCo (80%+ coverage)
- Use Conventional Commits for all commit messages
