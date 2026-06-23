# Task 6: Final commit of control class relocation

**Goal:** Create a comprehensive commit capturing all the work from moving control classes from messaging to core module

**What to do:**

1. Check git status to see all pending changes:
   ```bash
   cd /media/arun/Infenia/Infenia/Development/Public/yukta && git status
   ```

2. Stage all changes:
   ```bash
   cd /media/arun/Infenia/Infenia/Development/Public/yukta && git add -A
   ```

3. Create a comprehensive commit with the full refactoring message:
   ```bash
   cd /media/arun/Infenia/Infenia/Development/Public/yukta && git commit -m "refactor: move control message classes from messaging to core

- Move com.infenia.yukta.message.control.* to com.infenia.yukta.core.model.control.*
- Control messages are execution control directives, not messaging infrastructure
- messaging module now contains only Message<T> abstraction (core messaging protocol)
- core module houses control model classes alongside orchestration logic
- Update all imports across core, web, plugins, mcp modules
- Improve architectural separation: messaging (transport) vs control (execution)
- Move ExecutionControlCommand to plugin-api to resolve circular dependency

Co-Authored-By: Claude Haiku 4.5 <noreply@anthropic.com>"
   ```

4. Verify commit was created:
   ```bash
   cd /media/arun/Infenia/Infenia/Development/Public/yukta && git log --oneline -1
   ```

**Expected output:**
- Single clean commit with all changes
- Git log showing the refactoring message

**Commit details:**
- Control classes moved from messaging to core
- Deleted from messaging module
- Updated build.gradle.kts files
- Updated import statements across the codebase
- ExecutionControlCommand moved to plugin-api as architectural fix

**Verification:**
```bash
git diff --stat HEAD~1 HEAD
```

Should show summary of all files changed, added, and deleted

**Global constraints:**
- All Java files must have Apache 2.0 license header (managed by Spotless)
- Follow Google Java Style Guide (2-space indentation, 100-char line limit)
- Code must pass: Spotless, Checkstyle, PMD, SpotBugs, JaCoCo (80%+ coverage)
- Use Conventional Commits for all commit messages (refactor: ...)
