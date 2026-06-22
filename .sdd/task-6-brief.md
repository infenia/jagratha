# Task 6: Update web module dependencies and imports

**Goal:** Update web module to depend on messaging and change all imports from `com.infenia.yukta.plugin.message` to `com.infenia.yukta.message`

**What to do:**
1. Update `web/build.gradle.kts`:
   - Add `api(project(":messaging"))` to the dependencies block

2. Find all Java files in web that import from `com.infenia.yukta.plugin.message`:
   - Run: `grep -r "com.infenia.yukta.plugin.message" /media/arun/Infenia/Infenia/Development/Public/yukta/web/src --include="*.java" | cut -d: -f1 | sort -u`

3. For each file found, update the imports:
   - Change: `import com.infenia.yukta.plugin.message` → `import com.infenia.yukta.message`

4. Verify web compiles successfully

**Files to modify:**
- `web/build.gradle.kts` - Add messaging dependency
- All Java files with `com.infenia.yukta.plugin.message` imports

**Import pattern changes:**
- `import com.infenia.yukta.plugin.message.Message;` → `import com.infenia.yukta.message.Message;`
- `import com.infenia.yukta.plugin.message.control.*;` → `import com.infenia.yukta.message.control.*;`

**Verification:**
```bash
./gradlew :web:compileJava
```

Should output: BUILD SUCCESSFUL

**Build.gradle.kts changes:**

In dependencies block, add:
```kotlin
    api(project(":messaging"))
```

**Global constraints:**
- All Java files must have Apache 2.0 license header (managed by Spotless)
- Follow Google Java Style Guide (2-space indentation, 100-char line limit)
- Code must pass: Spotless, Checkstyle, PMD, SpotBugs, JaCoCo (80%+ coverage)
- Use Conventional Commits for all commit messages
