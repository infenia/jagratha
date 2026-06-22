# Task 3: Copy control message classes to messaging module

**Goal:** Move all control message classes from `plugin-api` to new `messaging` module with updated package name

**What to do:**
1. Read these 6 files from `plugin-api/src/main/java/com/infenia/yukta/plugin/message/control/`:
   - `ControlCommand.java`
   - `ControlConfiguration.java`
   - `ControlError.java`
   - `ControlHeartbeat.java`
   - `ControlStatistics.java`
   - `ExecutionControlCommand.java`

2. Copy them to `messaging/src/main/java/com/infenia/yukta/message/control/` with package name updated from `com.infenia.yukta.plugin.message.control` to `com.infenia.yukta.message.control`

3. Verify all 6 files exist in the new location

**Files to copy:**
- `plugin-api/src/main/java/com/infenia/yukta/plugin/message/control/ControlCommand.java`
- `plugin-api/src/main/java/com/infenia/yukta/plugin/message/control/ControlConfiguration.java`
- `plugin-api/src/main/java/com/infenia/yukta/plugin/message/control/ControlError.java`
- `plugin-api/src/main/java/com/infenia/yukta/plugin/message/control/ControlHeartbeat.java`
- `plugin-api/src/main/java/com/infenia/yukta/plugin/message/control/ControlStatistics.java`
- `plugin-api/src/main/java/com/infenia/yukta/plugin/message/control/ExecutionControlCommand.java`

**Destination package:** `com.infenia.yukta.message.control` (remove `.plugin` from package path)

**Changes to make:**
- Replace package declaration: `package com.infenia.yukta.plugin.message.control;` with `package com.infenia.yukta.message.control;`
- Keep all other content identical (including any internal imports of other message classes - those will be updated in later tasks)

**Expected output:**
- 6 control classes in messaging module under `src/main/java/com/infenia/yukta/message/control/`

**Verification:**
```bash
ls -la /media/arun/Infenia/Infenia/Development/Public/yukta/messaging/src/main/java/com/infenia/yukta/message/control/
```

Should show 6 files: ControlCommand.java, ControlConfiguration.java, ControlError.java, ControlHeartbeat.java, ControlStatistics.java, ExecutionControlCommand.java

**Global constraints:**
- All Java files must have Apache 2.0 license header (managed by Spotless)
- Follow Google Java Style Guide (2-space indentation, 100-char line limit)
- Code must pass: Spotless, Checkstyle, PMD, SpotBugs, JaCoCo (80%+ coverage)
- Use Conventional Commits for all commit messages
