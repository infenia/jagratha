# ManualTrigger Plugin Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `ManualTrigger` plugin that fires a workflow with no input — it emits a single empty message and ignores anything the caller sends.

**Architecture:** A single `ManualTrigger` class in a new Gradle module `plugins/triggers/manual-trigger/`. It implements `TriggerPlugin`, has zero dependencies, and its entire `start()` is one line. All lifecycle methods inherit the default no-ops from `WorkflowPlugin`.

**Tech Stack:** Java 25, Spring Boot 4 (`@Component`), Project Reactor (`Flux`), JUnit 5 + `StepVerifier`, Lombok (`@Slf4j`), Spotless (Google Java Style).

---

## File Map

| Action | Path |
|--------|------|
| Create | `plugins/triggers/manual-trigger/build.gradle.kts` |
| Create | `plugins/triggers/manual-trigger/src/main/java/com/infenia/yukta/plugin/trigger/ManualTrigger.java` |
| Create | `plugins/triggers/manual-trigger/src/test/java/com/infenia/yukta/plugin/trigger/ManualTriggerTest.java` |
| Modify | `settings.gradle.kts` — add `include("plugins:triggers:manual-trigger")` |
| Modify | `boot/build.gradle.kts` — add dependency on `:plugins:triggers:manual-trigger` |

---

## Task 1: Scaffold the Gradle module

**Files:**
- Create: `plugins/triggers/manual-trigger/build.gradle.kts`
- Modify: `settings.gradle.kts`
- Modify: `boot/build.gradle.kts`

- [ ] **Step 1: Create the build file**

Create `plugins/triggers/manual-trigger/build.gradle.kts` with this exact content (license header required by Spotless):

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

version = "1.0.0"

dependencies {
    implementation(project(":plugin-api"))
    implementation(libs.spring.boot.starter.webflux)
}
```

- [ ] **Step 2: Register in settings.gradle.kts**

In `settings.gradle.kts`, add the new module directly after the `constant-source` line:

```kotlin
include("plugins:triggers:api-trigger")
include("plugins:triggers:constant-source")
include("plugins:triggers:manual-trigger")   // add this line
```

- [ ] **Step 3: Add to boot classpath**

In `boot/build.gradle.kts`, add the dependency after `constant-source`:

```kotlin
implementation(project(":plugins:triggers:api-trigger"))
implementation(project(":plugins:triggers:constant-source"))
implementation(project(":plugins:triggers:manual-trigger"))   // add this line
```

- [ ] **Step 4: Verify the module resolves**

```bash
./gradlew :plugins:triggers:manual-trigger:dependencies --configuration compileClasspath
```

Expected: task completes, `:plugin-api` and `spring-boot-starter-webflux` appear in the output. No errors.

---

## Task 2: Write the failing tests (TDD)

**Files:**
- Create: `plugins/triggers/manual-trigger/src/test/java/com/infenia/yukta/plugin/trigger/ManualTriggerTest.java`

- [ ] **Step 1: Create the test class**

Create the directory and file:

```
plugins/triggers/manual-trigger/src/test/java/com/infenia/yukta/plugin/trigger/ManualTriggerTest.java
```

```java
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
package com.infenia.yukta.plugin.trigger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class ManualTriggerTest {

  private final ManualTrigger trigger = new ManualTrigger();

  @Test
  void getType_returnsManual() {
    assertEquals("MANUAL", trigger.getType());
  }

  @Test
  void start_emitsExactlyOneMessage() {
    StepVerifier.create(trigger.start(Map.of()))
        .assertNext(message -> assertNotNull(message))
        .verifyComplete();
  }

  @Test
  void start_emittedMessageHasEmptyMapPayload() {
    StepVerifier.create(trigger.start(Map.of()))
        .assertNext(message -> assertEquals(Map.of(), message.getPayload()))
        .verifyComplete();
  }

  @Test
  void start_emittedMessageHasNonNullTraceId() {
    StepVerifier.create(trigger.start(Map.of()))
        .assertNext(message -> assertNotNull(message.getTraceId()))
        .verifyComplete();
  }

  @Test
  void validateConfig_completesWithoutError() {
    StepVerifier.create(trigger.validateConfig(Map.of())).verifyComplete();
  }

  @Test
  void getUiDesign_isPresentAndCorrectDimensions() {
    assertTrue(trigger.getUiDesign().isPresent());
    assertEquals(140, trigger.getUiDesign().get().width());
    assertEquals(80, trigger.getUiDesign().get().height());
  }
}
```

- [ ] **Step 2: Run tests — verify they fail**

```bash
./gradlew :plugins:triggers:manual-trigger:test
```

Expected: BUILD FAILED — `ManualTrigger` does not exist yet. Confirm the error is `cannot find symbol` for `ManualTrigger`, not a module resolution failure.

---

## Task 3: Implement ManualTrigger

**Files:**
- Create: `plugins/triggers/manual-trigger/src/main/java/com/infenia/yukta/plugin/trigger/ManualTrigger.java`

- [ ] **Step 1: Create the implementation class**

Create:
```
plugins/triggers/manual-trigger/src/main/java/com/infenia/yukta/plugin/trigger/ManualTrigger.java
```

```java
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
package com.infenia.yukta.plugin.trigger;

import com.infenia.yukta.plugin.core.UiDesign;
import com.infenia.yukta.plugin.message.DefaultMessage;
import com.infenia.yukta.plugin.message.Message;
import com.infenia.yukta.plugin.type.TriggerPlugin;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/** Fires a workflow with no input. Emits a single empty message and ignores any caller data. */
@Slf4j
@Component
public class ManualTrigger implements TriggerPlugin {

  /** Default constructor. */
  public ManualTrigger() {
    super();
  }

  @Override
  public String getType() {
    return "MANUAL";
  }

  @Override
  public String getDescription() {
    return "Fires a workflow with no input. Emits a single empty message to start execution.";
  }

  @Override
  public String getUsagePattern() {
    return "No configuration required. Call the workflow trigger endpoint to start execution.";
  }

  @Override
  public Optional<UiDesign> getUiDesign() {
    return Optional.of(
        new UiDesign(
            """
            <div class="flex items-center w-full h-full bg-green-50/50 border-2 border-green-100 rounded-xl px-4 gap-3">
                <div class="flex-shrink-0 w-8 h-8 bg-green-100 rounded-lg flex items-center justify-center text-green-500">
                    <span class="material-symbols-outlined text-xl">touch_app</span>
                </div>
                <div class="flex flex-col min-w-0">
                    <div class="text-[10px] text-green-600 font-bold uppercase tracking-wider leading-none mb-1">Manual</div>
                    <div class="text-xs font-bold text-slate-700 truncate w-full">{{nodeId}}</div>
                </div>
            </div>
            """,
            140,
            80));
  }

  @Override
  public Flux<Message<?>> start(final Map<String, Object> config) {
    log.atDebug().log("ManualTrigger firing: emitting empty message");
    return Flux.just(DefaultMessage.create(UUID.randomUUID(), Map.of()));
  }
}
```

- [ ] **Step 2: Run tests — verify they pass**

```bash
./gradlew :plugins:triggers:manual-trigger:test
```

Expected: BUILD SUCCESSFUL, all 6 tests pass.

---

## Task 4: Quality gates and commit

- [ ] **Step 1: Format code**

```bash
./gradlew :plugins:triggers:manual-trigger:spotlessApply
```

Expected: BUILD SUCCESSFUL (Spotless fixes whitespace/imports in place).

- [ ] **Step 2: Run full quality check on the new module**

```bash
./gradlew :plugins:triggers:manual-trigger:check
```

Expected: BUILD SUCCESSFUL — Checkstyle, PMD, SpotBugs, JaCoCo all pass.

- [ ] **Step 3: Verify boot still compiles with the new dependency**

```bash
./gradlew :boot:compileJava
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add \
  plugins/triggers/manual-trigger/ \
  settings.gradle.kts \
  boot/build.gradle.kts

git commit -m "feat: add ManualTrigger plugin — fires workflow with no input"
```
