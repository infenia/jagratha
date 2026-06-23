# Move Control Message Classes to Core Module

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move control message classes from `messaging` module to `core` module, leaving only the core Message abstraction in messaging.

**Architecture:** `messaging` module contains only the fundamental Message<T> contract. Control message types (ControlCommand, ControlHeartbeat, etc.) move to `core.model.control` package since they are execution control directives internal to the orchestration layer, not part of the public messaging protocol.

**Dependency chain after refactoring:**
```
core → messaging (for Message<T> interface)
web → core (includes control messages transitively)
plugins → plugin-api → messaging (Message interface only)
plugin-api → control interfaces (ControlSignalProcessor) stay in plugin-api
```

**Tech Stack:**
- Gradle 9.0 (Kotlin DSL)
- Java 25
- Spring Boot 4.0.2 (WebFlux only)

## Global Constraints

- All Java files must have Apache 2.0 license header (managed by Spotless)
- Follow Google Java Style Guide (2-space indentation, 100-char line limit)
- Code must pass: Spotless, Checkstyle, PMD, SpotBugs, JaCoCo (80%+ coverage)
- Use Conventional Commits for all commit messages

---

## File Structure

**Files to move:**
- `messaging/src/main/java/com/infenia/yukta/message/control/ControlCommand.java` → `core/src/main/java/com/infenia/yukta/core/model/control/ControlCommand.java`
- `messaging/src/main/java/com/infenia/yukta/message/control/ControlConfiguration.java` → `core/src/main/java/com/infenia/yukta/core/model/control/ControlConfiguration.java`
- `messaging/src/main/java/com/infenia/yukta/message/control/ControlError.java` → `core/src/main/java/com/infenia/yukta/core/model/control/ControlError.java`
- `messaging/src/main/java/com/infenia/yukta/message/control/ControlHeartbeat.java` → `core/src/main/java/com/infenia/yukta/core/model/control/ControlHeartbeat.java`
- `messaging/src/main/java/com/infenia/yukta/message/control/ControlStatistics.java` → `core/src/main/java/com/infenia/yukta/core/model/control/ControlStatistics.java`
- `messaging/src/main/java/com/infenia/yukta/message/control/ExecutionControlCommand.java` → `core/src/main/java/com/infenia/yukta/core/model/control/ExecutionControlCommand.java`

**Files to delete from messaging:**
- `messaging/src/main/java/com/infenia/yukta/message/control/` (entire directory after copying)

**Files to modify:**
- `messaging/build.gradle.kts` — Remove control coverage exceptions
- `core/build.gradle.kts` — Add control coverage exceptions
- All files in `core`, `web`, `plugins` that import from `com.infenia.yukta.message.control` → `com.infenia.yukta.core.model.control`

---

## Task 1: Create core control package and copy classes

**Files:**
- Create: `core/src/main/java/com/infenia/yukta/core/model/control/`
- Copy: 6 control classes from messaging with updated package

**Interfaces:**
- Produces: Control classes in core module with updated package name

- [ ] **Step 1: Create control package directory in core**

```bash
mkdir -p /media/arun/Infenia/Infenia/Development/Public/yukta/core/src/main/java/com/infenia/yukta/core/model/control
```

- [ ] **Step 2: Copy ControlCommand.java**

Read: `/media/arun/Infenia/Infenia/Development/Public/yukta/messaging/src/main/java/com/infenia/yukta/message/control/ControlCommand.java`

Update package from `com.infenia.yukta.message.control` to `com.infenia.yukta.core.model.control`

Write to: `/media/arun/Infenia/Infenia/Development/Public/yukta/core/src/main/java/com/infenia/yukta/core/model/control/ControlCommand.java`

- [ ] **Step 3: Copy ControlConfiguration.java**

Read: `/media/arun/Infenia/Infenia/Development/Public/yukta/messaging/src/main/java/com/infenia/yukta/message/control/ControlConfiguration.java`

Update package to `com.infenia.yukta.core.model.control`

Write to: `/media/arun/Infenia/Infenia/Development/Public/yukta/core/src/main/java/com/infenia/yukta/core/model/control/ControlConfiguration.java`

- [ ] **Step 4: Copy ControlError.java**

Read: `/media/arun/Infenia/Infenia/Development/Public/yukta/messaging/src/main/java/com/infenia/yukta/message/control/ControlError.java`

Update package to `com.infenia.yukta.core.model.control`

Write to: `/media/arun/Infenia/Infenia/Development/Public/yukta/core/src/main/java/com/infenia/yukta/core/model/control/ControlError.java`

- [ ] **Step 5: Copy ControlHeartbeat.java**

Read: `/media/arun/Infenia/Infenia/Development/Public/yukta/messaging/src/main/java/com/infenia/yukta/message/control/ControlHeartbeat.java`

Update package to `com.infenia.yukta.core.model.control`

Write to: `/media/arun/Infenia/Infenia/Development/Public/yukta/core/src/main/java/com/infenia/yukta/core/model/control/ControlHeartbeat.java`

- [ ] **Step 6: Copy ControlStatistics.java**

Read: `/media/arun/Infenia/Infenia/Development/Public/yukta/messaging/src/main/java/com/infenia/yukta/message/control/ControlStatistics.java`

Update package to `com.infenia.yukta.core.model.control`

Write to: `/media/arun/Infenia/Infenia/Development/Public/yukta/core/src/main/java/com/infenia/yukta/core/model/control/ControlStatistics.java`

- [ ] **Step 7: Copy ExecutionControlCommand.java**

Read: `/media/arun/Infenia/Infenia/Development/Public/yukta/messaging/src/main/java/com/infenia/yukta/message/control/ExecutionControlCommand.java`

Update package to `com.infenia.yukta.core.model.control`

Write to: `/media/arun/Infenia/Infenia/Development/Public/yukta/core/src/main/java/com/infenia/yukta/core/model/control/ExecutionControlCommand.java`

- [ ] **Step 8: Verify all files created**

```bash
ls -la /media/arun/Infenia/Infenia/Development/Public/yukta/core/src/main/java/com/infenia/yukta/core/model/control/
```

Expected: 6 files present

---

## Task 2: Delete control classes from messaging module

**Files:**
- Delete: `messaging/src/main/java/com/infenia/yukta/message/control/` (entire directory)

**Interfaces:**
- Consumes: Control classes in messaging
- Produces: Messaging module without control package

- [ ] **Step 1: Delete control directory from messaging**

```bash
rm -rf /media/arun/Infenia/Infenia/Development/Public/yukta/messaging/src/main/java/com/infenia/yukta/message/control
```

- [ ] **Step 2: Verify deletion**

```bash
ls /media/arun/Infenia/Infenia/Development/Public/yukta/messaging/src/main/java/com/infenia/yukta/message/control 2>&1
```

Expected: `No such file or directory`

- [ ] **Step 3: Verify messaging module still has core message classes**

```bash
ls /media/arun/Infenia/Infenia/Development/Public/yukta/messaging/src/main/java/com/infenia/yukta/message/
```

Expected: Message.java, DefaultMessage.java, MessageMapper.java present

---

## Task 3: Update build.gradle.kts files

**Files:**
- Modify: `messaging/build.gradle.kts` — Remove control coverage exceptions
- Modify: `core/build.gradle.kts` — Add control coverage exceptions

**Interfaces:**
- Consumes: Current build configurations
- Produces: Updated coverage configurations reflecting new locations

- [ ] **Step 1: Update messaging/build.gradle.kts**

Read: `/media/arun/Infenia/Infenia/Development/Public/yukta/messaging/build.gradle.kts`

Remove the line:
```kotlin
    exceptions.put("com.infenia.yukta.message.control.*", lowCoverage)
```

- [ ] **Step 2: Update core/build.gradle.kts**

Read: `/media/arun/Infenia/Infenia/Development/Public/yukta/core/build.gradle.kts`

Add to coverageConfig exceptions:
```kotlin
    exceptions.put("com.infenia.yukta.core.model.control.*", lowCoverage)
```

---

## Task 4: Update all imports from com.infenia.yukta.message.control to com.infenia.yukta.core.model.control

**Files:**
- Modify: All Java files in core, web, and plugins that import control classes

**Interfaces:**
- Consumes: Files with old control imports
- Produces: Files with updated control imports

- [ ] **Step 1: Find all files with control imports**

```bash
grep -r "com.infenia.yukta.message.control" /media/arun/Infenia/Infenia/Development/Public/yukta/{core,web,plugins,mcp,cli}/src --include="*.java" | cut -d: -f1 | sort -u
```

- [ ] **Step 2: Update all imports**

For each file found, replace:
- `import com.infenia.yukta.message.control.*;` → `import com.infenia.yukta.core.model.control.*;`
- `import com.infenia.yukta.message.control.ControlCommand;` → `import com.infenia.yukta.model.control.ControlCommand;`
- (and similar for other control classes)

- [ ] **Step 3: Verify no old imports remain**

```bash
grep -r "com.infenia.yukta.message.control" /media/arun/Infenia/Infenia/Development/Public/yukta --include="*.java" | wc -l
```

Expected: 0

---

## Task 5: Format code and run quality checks

**Files:**
- All modified files will be reformatted

**Interfaces:**
- Consumes: Updated source code
- Produces: Code passing all quality gates

- [ ] **Step 1: Run Spotless**

```bash
cd /media/arun/Infenia/Infenia/Development/Public/yukta && ./gradlew spotlessApply
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Run quality checks**

```bash
cd /media/arun/Infenia/Infenia/Development/Public/yukta && ./gradlew check -x test
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run tests**

```bash
cd /media/arun/Infenia/Infenia/Development/Public/yukta && ./gradlew test
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Run full build**

```bash
cd /media/arun/Infenia/Infenia/Development/Public/yukta && ./gradlew clean build
```

Expected: BUILD SUCCESSFUL

---

## Task 6: Commit changes

**Files:**
- All new and modified files from Tasks 1-5

**Interfaces:**
- Consumes: All changes
- Produces: Single commit with control class relocation

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
cd /media/arun/Infenia/Infenia/Development/Public/yukta && git commit -m "refactor: move control message classes from messaging to core

- Move com.infenia.yukta.message.control.* to com.infenia.yukta.core.model.control.*
- Control messages are execution directives, not messaging infrastructure
- messaging module now contains only Message<T> abstraction
- core module houses control model classes alongside orchestration logic
- Update all imports across core, web, plugins, mcp modules
- Improves architectural separation: messaging (transport) vs control (execution)"
```

- [ ] **Step 4: Verify commit**

```bash
cd /media/arun/Infenia/Infenia/Development/Public/yukta && git log --oneline -1
```

Expected: Latest commit shows refactor message

---

## Verification Checklist

- [ ] 6 control classes copied to core module
- [ ] Control package deleted from messaging module
- [ ] messaging/build.gradle.kts updated (removed control exceptions)
- [ ] core/build.gradle.kts updated (added control exceptions)
- [ ] All imports updated to use `com.infenia.yukta.core.model.control`
- [ ] No remaining imports of `com.infenia.yukta.message.control`
- [ ] `./gradlew clean build` passes
- [ ] All tests pass
- [ ] Code coverage requirements met
- [ ] Spotless, Checkstyle, PMD, SpotBugs all pass
- [ ] Git commit created with proper message
