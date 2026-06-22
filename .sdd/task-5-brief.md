# Task 5: Update core module dependencies and imports

**Goal:** Update core module to depend on messaging and change all imports from `com.infenia.yukta.plugin.message` to `com.infenia.yukta.message`

**What to do:**
1. Update `core/build.gradle.kts`:
   - Add `api(project(":messaging"))` to the dependencies block (right after `api(project(":plugin-api"))`)

2. Find all Java files in core that import from `com.infenia.yukta.plugin.message`:
   - Run: `grep -r "com.infenia.yukta.plugin.message" /media/arun/Infenia/Infenia/Development/Public/yukta/core/src --include="*.java" | cut -d: -f1 | sort -u`
   - This will list all files that need import updates

3. For each file found, update the imports:
   - Change: `import com.infenia.yukta.plugin.message` → `import com.infenia.yukta.message`
   - This includes any wildcard imports like `import com.infenia.yukta.plugin.message.*`

4. Verify core compiles successfully

**Files to modify:**
- `core/build.gradle.kts` - Add messaging dependency
- All Java files with `com.infenia.yukta.plugin.message` imports (list determined by grep)

**Expected files to update (approximately 20+ files):**
Files using Message, DefaultMessage, MessageMapper, and control message classes

**Import pattern changes:**
- `import com.infenia.yukta.plugin.message.Message;` → `import com.infenia.yukta.message.Message;`
- `import com.infenia.yukta.plugin.message.control.ControlCommand;` → `import com.infenia.yukta.message.control.ControlCommand;`
- `import com.infenia.yukta.plugin.message.*;` → `import com.infenia.yukta.message.*;`

**Verification:**
```bash
./gradlew :core:compileJava
```

Should output: BUILD SUCCESSFUL

**Build.gradle.kts changes:**

In dependencies block after `api(project(":plugin-api"))`, add:
```kotlin
    api(project(":messaging"))
```

**Global constraints:**
- All Java files must have Apache 2.0 license header (managed by Spotless)
- Follow Google Java Style Guide (2-space indentation, 100-char line limit)
- Code must pass: Spotless, Checkstyle, PMD, SpotBugs, JaCoCo (80%+ coverage)
- Use Conventional Commits for all commit messages
