# Messaging Module Extraction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract the message package (`com.infenia.yukta.plugin.message.*`) from `plugin-api` into a new `messaging` module to separate messaging infrastructure from plugin abstractions.

**Architecture:** Create a new `messaging` module that contains the Message interface hierarchy (core messaging contract). The module will have minimal dependencies (only Spring WebFlux). `plugin-api` will depend on `messaging` for the Message abstraction, and all consumers (`core`, plugins, web) will depend on both modules.

**Tech Stack:**
- Gradle 9.0 (Kotlin DSL)
- Java 25
- Spring Boot 4.0.2 (WebFlux only)
- Lombok (for records)

## Global Constraints

- All Java files must have Apache 2.0 license header (managed by Spotless)
- Follow Google Java Style Guide (2-space indentation, 100-char line limit)
- Code must pass: Spotless, Checkstyle, PMD, SpotBugs, JaCoCo (80%+ coverage)
- Use Conventional Commits for all commit messages

---

## File Structure

**New module created:**
- `messaging/build.gradle.kts` — Gradle configuration (minimal dependencies)
- `messaging/src/main/java/com/infenia/yukta/message/` — All message classes
- `messaging/src/test/java/com/infenia/yukta/message/` — All message tests

**Files to modify:**
- `settings.gradle.kts` — Add `messaging` module
- `plugin-api/build.gradle.kts` — Remove message classes, add `messaging` dependency
- `core/build.gradle.kts` — Add explicit `messaging` dependency
- `web/build.gradle.kts` — Add explicit `messaging` dependency
- All import statements in codebase (from `com.infenia.yukta.plugin.message` to `com.infenia.yukta.message`)

---

## Task 1: Create the messaging module structure

**Files:**
- Create: `messaging/build.gradle.kts`
- Create: `messaging/src/main/java/com/infenia/yukta/message/`
- Create: `messaging/src/test/java/com/infenia/yukta/message/`

**Interfaces:**
- Produces: A new Gradle module with proper build configuration that will contain all message classes.

- [ ] **Step 1: Create messaging module build.gradle.kts**

Create file `/media/arun/Infenia/Infenia/Development/Public/yukta/messaging/build.gradle.kts`:

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

- [ ] **Step 2: Create directory structure**

```bash
mkdir -p /media/arun/Infenia/Infenia/Development/Public/yukta/messaging/src/main/java/com/infenia/yukta/message
mkdir -p /media/arun/Infenia/Infenia/Development/Public/yukta/messaging/src/main/java/com/infenia/yukta/message/control
mkdir -p /media/arun/Infenia/Infenia/Development/Public/yukta/messaging/src/test/java/com/infenia/yukta/message
```

- [ ] **Step 3: Verify directory structure**

```bash
ls -la /media/arun/Infenia/Infenia/Development/Public/yukta/messaging/
```

Expected: `build.gradle.kts`, `src/` directory visible.

- [ ] **Step 4: Add messaging module to settings.gradle.kts**

In `/media/arun/Infenia/Infenia/Development/Public/yukta/settings.gradle.kts`, after `include("plugin-api")` add:

```kotlin
include("messaging")
```

Final result should have this order:
```kotlin
rootProject.name = "yukta"

includeBuild("build-logic")

include("messaging")
include("plugin-api")
// ... rest of modules
```

- [ ] **Step 5: Verify Gradle recognizes the module**

```bash
cd /media/arun/Infenia/Infenia/Development/Public/yukta && ./gradlew projects | grep messaging
```

Expected: Output includes `:messaging` project.

---

## Task 2: Copy Message interface and implementation

**Files:**
- Copy: `plugin-api/src/main/java/com/infenia/yukta/plugin/message/Message.java` → `messaging/src/main/java/com/infenia/yukta/message/Message.java`
- Copy: `plugin-api/src/main/java/com/infenia/yukta/plugin/message/DefaultMessage.java` → `messaging/src/main/java/com/infenia/yukta/message/DefaultMessage.java`
- Copy: `plugin-api/src/main/java/com/infenia/yukta/plugin/message/MessageMapper.java` → `messaging/src/main/java/com/infenia/yukta/message/MessageMapper.java`
- Copy test: `plugin-api/src/test/java/com/infenia/yukta/plugin/message/DefaultMessageTest.java` → `messaging/src/test/java/com/infenia/yukta/message/DefaultMessageTest.java`

**Interfaces:**
- Consumes: Files from plugin-api message package
- Produces: Updated versions with new package name (`com.infenia.yukta.message`)

- [ ] **Step 1: Read Message.java from plugin-api**

Read: `/media/arun/Infenia/Infenia/Development/Public/yukta/plugin-api/src/main/java/com/infenia/yukta/plugin/message/Message.java`

- [ ] **Step 2: Update package and write to messaging module**

Update package from `com.infenia.yukta.plugin.message` to `com.infenia.yukta.message`. Keep all content identical.

Write to: `/media/arun/Infenia/Infenia/Development/Public/yukta/messaging/src/main/java/com/infenia/yukta/message/Message.java`

- [ ] **Step 3: Copy DefaultMessage.java**

Read: `/media/arun/Infenia/Infenia/Development/Public/yukta/plugin-api/src/main/java/com/infenia/yukta/plugin/message/DefaultMessage.java`

Update package to `com.infenia.yukta.message`.

Write to: `/media/arun/Infenia/Infenia/Development/Public/yukta/messaging/src/main/java/com/infenia/yukta/message/DefaultMessage.java`

- [ ] **Step 4: Copy MessageMapper.java**

Read: `/media/arun/Infenia/Infenia/Development/Public/yukta/plugin-api/src/main/java/com/infenia/yukta/plugin/message/MessageMapper.java`

Update package to `com.infenia.yukta.message`.

Write to: `/media/arun/Infenia/Infenia/Development/Public/yukta/messaging/src/main/java/com/infenia/yukta/message/MessageMapper.java`

- [ ] **Step 5: Copy DefaultMessageTest.java**

Read: `/media/arun/Infenia/Infenia/Development/Public/yukta/plugin-api/src/test/java/com/infenia/yukta/plugin/message/DefaultMessageTest.java`

Update package to `com.infenia.yukta.message`.

Write to: `/media/arun/Infenia/Infenia/Development/Public/yukta/messaging/src/test/java/com/infenia/yukta/message/DefaultMessageTest.java`

- [ ] **Step 6: Verify files created**

```bash
ls -la /media/arun/Infenia/Infenia/Development/Public/yukta/messaging/src/main/java/com/infenia/yukta/message/
```

Expected: `Message.java`, `DefaultMessage.java`, `MessageMapper.java` present.

---

## Task 3: Copy control message classes to messaging module

**Files:**
- Copy: `plugin-api/src/main/java/com/infenia/yukta/plugin/message/control/*.java` → `messaging/src/main/java/com/infenia/yukta/message/control/`

**Interfaces:**
- Consumes: All control message classes from plugin-api
- Produces: Control classes in messaging module with updated package name

- [ ] **Step 1: Copy ControlCommand.java**

Read: `/media/arun/Infenia/Infenia/Development/Public/yukta/plugin-api/src/main/java/com/infenia/yukta/plugin/message/control/ControlCommand.java`

Update package from `com.infenia.yukta.plugin.message.control` to `com.infenia.yukta.message.control`.

Write to: `/media/arun/Infenia/Infenia/Development/Public/yukta/messaging/src/main/java/com/infenia/yukta/message/control/ControlCommand.java`

- [ ] **Step 2: Copy ControlConfiguration.java**

Read: `/media/arun/Infenia/Infenia/Development/Public/yukta/plugin-api/src/main/java/com/infenia/yukta/plugin/message/control/ControlConfiguration.java`

Update package to `com.infenia.yukta.message.control`.

Write to: `/media/arun/Infenia/Infenia/Development/Public/yukta/messaging/src/main/java/com/infenia/yukta/message/control/ControlConfiguration.java`

- [ ] **Step 3: Copy ControlError.java**

Read: `/media/arun/Infenia/Infenia/Development/Public/yukta/plugin-api/src/main/java/com/infenia/yukta/plugin/message/control/ControlError.java`

Update package to `com.infenia.yukta.message.control`.

Write to: `/media/arun/Infenia/Infenia/Development/Public/yukta/messaging/src/main/java/com/infenia/yukta/message/control/ControlError.java`

- [ ] **Step 4: Copy ControlHeartbeat.java**

Read: `/media/arun/Infenia/Infenia/Development/Public/yukta/plugin-api/src/main/java/com/infenia/yukta/plugin/message/control/ControlHeartbeat.java`

Update package to `com.infenia.yukta.message.control`.

Write to: `/media/arun/Infenia/Infenia/Development/Public/yukta/messaging/src/main/java/com/infenia/yukta/message/control/ControlHeartbeat.java`

- [ ] **Step 5: Copy ControlStatistics.java**

Read: `/media/arun/Infenia/Infenia/Development/Public/yukta/plugin-api/src/main/java/com/infenia/yukta/plugin/message/control/ControlStatistics.java`

Update package to `com.infenia.yukta.message.control`.

Write to: `/media/arun/Infenia/Infenia/Development/Public/yukta/messaging/src/main/java/com/infenia/yukta/message/control/ControlStatistics.java`

- [ ] **Step 6: Copy ExecutionControlCommand.java**

Read: `/media/arun/Infenia/Infenia/Development/Public/yukta/plugin-api/src/main/java/com/infenia/yukta/plugin/message/control/ExecutionControlCommand.java`

Update package to `com.infenia.yukta.message.control`.

Write to: `/media/arun/Infenia/Infenia/Development/Public/yukta/messaging/src/main/java/com/infenia/yukta/message/control/ExecutionControlCommand.java`

- [ ] **Step 7: Verify all control files copied**

```bash
ls -la /media/arun/Infenia/Infenia/Development/Public/yukta/messaging/src/main/java/com/infenia/yukta/message/control/
```

Expected: 6 files (ControlCommand.java, ControlConfiguration.java, ControlError.java, ControlHeartbeat.java, ControlStatistics.java, ExecutionControlCommand.java).

---

## Task 4: Update plugin-api to remove message classes and add messaging dependency

**Files:**
- Modify: `plugin-api/build.gradle.kts` — Add messaging dependency, update coverage config
- Delete: Message-related files from plugin-api

**Interfaces:**
- Consumes: Current plugin-api structure with messaging classes
- Produces: plugin-api without messaging classes, with messaging dependency added

- [ ] **Step 1: Update plugin-api build.gradle.kts to add messaging dependency**

Read: `/media/arun/Infenia/Infenia/Development/Public/yukta/plugin-api/build.gradle.kts`

Replace the `dependencies` block to add `api(project(":messaging"))` and keep webflux:

```kotlin
dependencies {
    api(project(":messaging"))
    api(libs.spring.boot.starter.webflux)
}
```

Update `coverageConfig` to remove message-related exceptions (lines 42-49):

Remove these lines:
```kotlin
    exceptions.put("com.infenia.yukta.plugin.message.DefaultMessage", mapOf(
        "LINE" to 0.9,
        "BRANCH" to 0.5,
        "CLASS" to 0.8,
        "INSTRUCTION" to 0.9,
        "METHOD" to 0.9
    ))
    exceptions.put("com.infenia.yukta.plugin.message.control.*", lowCoverage)
```

- [ ] **Step 2: Delete message files from plugin-api**

```bash
rm -rf /media/arun/Infenia/Infenia/Development/Public/yukta/plugin-api/src/main/java/com/infenia/yukta/plugin/message
rm -rf /media/arun/Infenia/Infenia/Development/Public/yukta/plugin-api/src/test/java/com/infenia/yukta/plugin/message
```

- [ ] **Step 3: Verify deletion**

```bash
ls /media/arun/Infenia/Infenia/Development/Public/yukta/plugin-api/src/main/java/com/infenia/yukta/plugin/message 2>&1
```

Expected: `No such file or directory`

- [ ] **Step 4: Test plugin-api compilation**

```bash
cd /media/arun/Infenia/Infenia/Development/Public/yukta && ./gradlew :plugin-api:compileJava
```

Expected: BUILD SUCCESSFUL (or may fail if other modules haven't been updated yet, which is fine for this step)

---

## Task 5: Update core module dependencies and imports

**Files:**
- Modify: `core/build.gradle.kts` — Add explicit messaging dependency
- Modify: All Java files in core that import from `com.infenia.yukta.plugin.message` → `com.infenia.yukta.message`

**Interfaces:**
- Consumes: Current core with plugin.message imports
- Produces: core with message imports updated and proper dependencies

- [ ] **Step 1: Add messaging dependency to core/build.gradle.kts**

Read: `/media/arun/Infenia/Infenia/Development/Public/yukta/core/build.gradle.kts`

Find the dependencies block and add (after the existing `api(project(":plugin-api"))` line):

```kotlin
dependencies {
    api(project(":plugin-api"))
    api(project(":messaging"))
    // ... rest of dependencies
}
```

- [ ] **Step 2: Find all core files that import plugin.message**

```bash
grep -r "com.infenia.yukta.plugin.message" /media/arun/Infenia/Infenia/Development/Public/yukta/core/src --include="*.java" | cut -d: -f1 | sort -u
```

Note the files for the next steps.

- [ ] **Step 3: Update imports in all core files**

For each file found in Step 2, use Find & Replace to change:
- `import com.infenia.yukta.plugin.message` → `import com.infenia.yukta.message`

Example: In `/media/arun/Infenia/Infenia/Development/Public/yukta/core/src/main/java/com/infenia/yukta/service/DefaultWorkflowGateway.java`:

Replace: `import com.infenia.yukta.plugin.message;`
With: `import com.infenia.yukta.message;`

- [ ] **Step 4: Test core compilation**

```bash
cd /media/arun/Infenia/Infenia/Development/Public/yukta && ./gradlew :core:compileJava
```

Expected: BUILD SUCCESSFUL

---

## Task 6: Update web module dependencies and imports

**Files:**
- Modify: `web/build.gradle.kts` — Add explicit messaging dependency  
- Modify: All Java files in web that import from `com.infenia.yukta.plugin.message` → `com.infenia.yukta.message`

**Interfaces:**
- Consumes: Current web with plugin.message imports
- Produces: web with message imports updated and proper dependencies

- [ ] **Step 1: Add messaging dependency to web/build.gradle.kts**

Read: `/media/arun/Infenia/Infenia/Development/Public/yukta/web/build.gradle.kts`

Find the dependencies block and add:

```kotlin
dependencies {
    api(project(":core"))
    api(project(":messaging"))
    // ... rest of dependencies
}
```

- [ ] **Step 2: Find all web files that import plugin.message**

```bash
grep -r "com.infenia.yukta.plugin.message" /media/arun/Infenia/Infenia/Development/Public/yukta/web/src --include="*.java" | cut -d: -f1 | sort -u
```

- [ ] **Step 3: Update imports in all web files**

For each file, replace `import com.infenia.yukta.plugin.message` → `import com.infenia.yukta.message`

- [ ] **Step 4: Test web compilation**

```bash
cd /media/arun/Infenia/Infenia/Development/Public/yukta && ./gradlew :web:compileJava
```

Expected: BUILD SUCCESSFUL

---

## Task 7: Update all plugin modules to use messaging

**Files:**
- Modify: All plugin modules' `build.gradle.kts` — Add messaging dependency
- Modify: All Java files in plugins that import from `com.infenia.yukta.plugin.message` → `com.infenia.yukta.message`

**Interfaces:**
- Consumes: Current plugin modules with plugin.message imports
- Produces: Plugin modules with message imports updated

- [ ] **Step 1: List all plugin modules**

```bash
find /media/arun/Infenia/Infenia/Development/Public/yukta/plugins -name build.gradle.kts | xargs dirname
```

- [ ] **Step 2: Add messaging to each plugin's build.gradle.kts**

For each plugin module, add to dependencies:

```kotlin
api(project(":messaging"))
```

Example paths:
- `plugins/triggers/api-trigger/build.gradle.kts`
- `plugins/triggers/constant-source/build.gradle.kts`
- `plugins/triggers/auto-trigger/build.gradle.kts`
- `plugins/processors/process-executor/build.gradle.kts`
- `plugins/processors/internal/internal-core/build.gradle.kts`
- `plugins/terminals/console-terminal/build.gradle.kts`

- [ ] **Step 3: Update all plugin imports**

```bash
grep -r "com.infenia.yukta.plugin.message" /media/arun/Infenia/Infenia/Development/Public/yukta/plugins/src --include="*.java" | cut -d: -f1 | sort -u
```

For each file, replace imports as before.

- [ ] **Step 4: Test all plugins compile**

```bash
cd /media/arun/Infenia/Infenia/Development/Public/yukta && ./gradlew :plugins:triggers:api-trigger:compileJava :plugins:triggers:constant-source:compileJava :plugins:triggers:auto-trigger:compileJava :plugins:processors:process-executor:compileJava :plugins:processors:internal:internal-core:compileJava :plugins:terminals:console-terminal:compileJava
```

Expected: All BUILD SUCCESSFUL

---

## Task 8: Update remaining modules (mcp, cli, ui, boot, web)

**Files:**
- Modify: `mcp/build.gradle.kts`, `cli/build.gradle.kts`, `cli-boot/build.gradle.kts` — Add messaging dependency
- Modify: All Java files in these modules that import from `com.infenia.yukta.plugin.message` → `com.infenia.yukta.message`

**Interfaces:**
- Consumes: Current module state with plugin.message imports
- Produces: All modules updated with proper imports and dependencies

- [ ] **Step 1: Check which modules use message imports**

```bash
grep -r "com.infenia.yukta.plugin.message" /media/arun/Infenia/Infenia/Development/Public/yukta/{mcp,cli,cli-boot,ui,boot}/src --include="*.java" 2>/dev/null | cut -d: -f1 | sort -u
```

- [ ] **Step 2: Add messaging dependency to build.gradle.kts for modules that need it**

For each module that has message imports, add to `build.gradle.kts`:

```kotlin
api(project(":messaging"))
```

- [ ] **Step 3: Update all imports in found files**

Replace `import com.infenia.yukta.plugin.message` → `import com.infenia.yukta.message`

- [ ] **Step 4: Test all module compilation**

```bash
cd /media/arun/Infenia/Infenia/Development/Public/yukta && ./gradlew :mcp:compileJava :cli:compileJava :cli-boot:compileJava :boot:compileJava
```

Expected: BUILD SUCCESSFUL

---

## Task 9: Format code and run full quality checks

**Files:**
- All modified files will be reformatted

**Interfaces:**
- Consumes: All modified source code
- Produces: Code passing Spotless, Checkstyle, PMD, SpotBugs, JaCoCo

- [ ] **Step 1: Run Spotless formatter**

```bash
cd /media/arun/Infenia/Infenia/Development/Public/yukta && ./gradlew spotlessApply
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Run full check suite**

```bash
cd /media/arun/Infenia/Infenia/Development/Public/yukta && ./gradlew check -x test
```

Expected: BUILD SUCCESSFUL (all quality gates pass)

- [ ] **Step 3: Run tests**

```bash
cd /media/arun/Infenia/Infenia/Development/Public/yukta && ./gradlew test
```

Expected: BUILD SUCCESSFUL, all tests pass (including DefaultMessageTest in messaging module)

- [ ] **Step 4: Run full build**

```bash
cd /media/arun/Infenia/Infenia/Development/Public/yukta && ./gradlew clean build
```

Expected: BUILD SUCCESSFUL

---

## Task 10: Commit changes

**Files:**
- All new and modified files from Tasks 1-9

**Interfaces:**
- Consumes: All changes from previous tasks
- Produces: Single commit with message module extraction

- [ ] **Step 1: Check git status**

```bash
cd /media/arun/Infenia/Infenia/Development/Public/yukta && git status
```

- [ ] **Step 2: Stage all changes**

```bash
cd /media/arun/Infenia/Infenia/Development/Public/yukta && git add -A
```

- [ ] **Step 3: Create commit**

```bash
cd /media/arun/Infenia/Infenia/Development/Public/yukta && git commit -m "refactor: extract messaging module from plugin-api

- Create new messaging module with Message interface hierarchy
- Move com.infenia.yukta.plugin.message.* to com.infenia.yukta.message.*
- Update all imports across core, web, plugins, mcp, cli modules
- messaging module has minimal dependencies (Spring WebFlux only)
- plugin-api now depends on messaging for Message abstraction
- Improves separation of concerns: plugin contracts vs messaging infrastructure"
```

- [ ] **Step 4: Verify commit created**

```bash
cd /media/arun/Infenia/Infenia/Development/Public/yukta && git log --oneline -1
```

Expected: Latest commit shows refactor message

---

## Verification Checklist

- [ ] `messaging` module exists with all Message classes
- [ ] `plugin-api` no longer contains message package
- [ ] `plugin-api` depends on `messaging`
- [ ] All imports updated from `com.infenia.yukta.plugin.message` → `com.infenia.yukta.message`
- [ ] `./gradlew clean build` passes
- [ ] All tests pass
- [ ] Code coverage requirements met for messaging module
- [ ] Spotless, Checkstyle, PMD, SpotBugs all pass
- [ ] Git commit created with proper message

