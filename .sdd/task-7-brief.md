# Task 7: Update all plugin modules to use messaging

**Goal:** Update all plugin modules to depend on messaging and change all imports from `com.infenia.yukta.plugin.message` to `com.infenia.yukta.message`

**What to do:**

1. Find all plugin modules that need updating:
   - Run: `find /media/arun/Infenia/Infenia/Development/Public/yukta/plugins -name build.gradle.kts`

2. For each plugin module's build.gradle.kts:
   - Add `api(project(":messaging"))` to the dependencies block

3. Find all Java files in plugins that import from `com.infenia.yukta.plugin.message`:
   - Run: `grep -r "com.infenia.yukta.plugin.message" /media/arun/Infenia/Infenia/Development/Public/yukta/plugins/src --include="*.java" | cut -d: -f1 | sort -u`

4. For each file found, update the imports:
   - Change: `import com.infenia.yukta.plugin.message` → `import com.infenia.yukta.message`

5. Verify all plugins compile successfully

**Plugin modules to update (expected):**
- `plugins/triggers/api-trigger/build.gradle.kts`
- `plugins/triggers/constant-source/build.gradle.kts`
- `plugins/triggers/auto-trigger/build.gradle.kts`
- `plugins/processors/process-executor/build.gradle.kts`
- `plugins/processors/internal/internal-core/build.gradle.kts`
- `plugins/terminals/console-terminal/build.gradle.kts`

**Import pattern changes:**
- `import com.infenia.yukta.plugin.message.*;` → `import com.infenia.yukta.message.*;`
- `import com.infenia.yukta.plugin.message.control.*;` → `import com.infenia.yukta.message.control.*;`

**Verification:**
```bash
./gradlew :plugins:triggers:api-trigger:compileJava :plugins:triggers:constant-source:compileJava :plugins:triggers:auto-trigger:compileJava :plugins:processors:process-executor:compileJava :plugins:processors:internal:internal-core:compileJava :plugins:terminals:console-terminal:compileJava
```

All should output: BUILD SUCCESSFUL

**Global constraints:**
- All Java files must have Apache 2.0 license header (managed by Spotless)
- Follow Google Java Style Guide (2-space indentation, 100-char line limit)
- Code must pass: Spotless, Checkstyle, PMD, SpotBugs, JaCoCo (80%+ coverage)
- Use Conventional Commits for all commit messages
