# Task 8: Update remaining modules (mcp, cli, cli-boot, boot)

**Goal:** Update remaining modules to depend on messaging and change all imports from `com.infenia.yukta.plugin.message` to `com.infenia.yukta.message`

**What to do:**

1. Check which of these modules have message imports:
   - `mcp/`
   - `cli/`
   - `cli-boot/`
   - `boot/`
   
   Run: `grep -r "com.infenia.yukta.plugin.message" /media/arun/Infenia/Infenia/Development/Public/yukta/{mcp,cli,cli-boot,boot}/src --include="*.java" 2>/dev/null | cut -d: -f1 | sort -u`

2. For each module that has message imports:
   - Update `<module>/build.gradle.kts`:
     - Add `api(project(":messaging"))` to dependencies if not already there

3. For each Java file found with message imports, update the imports:
   - Change: `import com.infenia.yukta.plugin.message` → `import com.infenia.yukta.message`

4. Verify all affected modules compile successfully

**Expected modules with message imports (if any):**
- mcp (likely)
- cli (possibly)
- cli-boot (possibly)
- boot (unlikely, but check)

**Import pattern changes:**
- `import com.infenia.yukta.plugin.message.*;` → `import com.infenia.yukta.message.*;`
- `import com.infenia.yukta.plugin.message.control.*;` → `import com.infenia.yukta.message.control.*;`

**Verification:**
```bash
./gradlew :mcp:compileJava :cli:compileJava :cli-boot:compileJava :boot:compileJava
```

All should compile successfully

**Global constraints:**
- All Java files must have Apache 2.0 license header (managed by Spotless)
- Follow Google Java Style Guide (2-space indentation, 100-char line limit)
- Code must pass: Spotless, Checkstyle, PMD, SpotBugs, JaCoCo (80%+ coverage)
- Use Conventional Commits for all commit messages
