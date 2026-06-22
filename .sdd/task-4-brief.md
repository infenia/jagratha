# Task 4: Update plugin-api to remove message classes and add messaging dependency

**Goal:** Remove all message-related files from plugin-api, add messaging module dependency, and update coverage configuration

**What to do:**
1. Update `plugin-api/build.gradle.kts`:
   - Add `api(project(":messaging"))` to dependencies block (after checking the existing structure)
   - Remove coverage exceptions for message classes (lines mentioning `com.infenia.yukta.plugin.message`)

2. Delete message directories from plugin-api:
   - Delete: `plugin-api/src/main/java/com/infenia/yukta/plugin/message/` (entire directory)
   - Delete: `plugin-api/src/test/java/com/infenia/yukta/plugin/message/` (entire directory)

3. Verify deletion and that plugin-api can still compile

**Files to modify:**
- `plugin-api/build.gradle.kts`

**Files to delete:**
- `plugin-api/src/main/java/com/infenia/yukta/plugin/message/` (entire tree)
- `plugin-api/src/test/java/com/infenia/yukta/plugin/message/` (entire tree)

**Build.gradle.kts changes:**

In dependencies block, add:
```kotlin
api(project(":messaging"))
```

In coverageConfig section, REMOVE these lines:
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

**Expected output:**
- plugin-api/build.gradle.kts updated with messaging dependency
- All message package directories removed from plugin-api
- plugin-api can compile (may have import errors in other modules, but plugin-api itself should compile)

**Verification:**
```bash
ls /media/arun/Infenia/Infenia/Development/Public/yukta/plugin-api/src/main/java/com/infenia/yukta/plugin/message 2>&1
```

Should output: `No such file or directory`

```bash
./gradlew :plugin-api:compileJava
```

Should succeed or show import errors only in other modules

**Global constraints:**
- All Java files must have Apache 2.0 license header (managed by Spotless)
- Follow Google Java Style Guide (2-space indentation, 100-char line limit)
- Code must pass: Spotless, Checkstyle, PMD, SpotBugs, JaCoCo (80%+ coverage)
- Use Conventional Commits for all commit messages
