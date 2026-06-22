# Task 2: Copy Message interface and implementation classes

**Goal:** Move core message abstraction classes (Message, DefaultMessage, MessageMapper) from `plugin-api` to new `messaging` module with updated package name

**What to do:**
1. Read these files from `plugin-api/src/main/java/com/infenia/yukta/plugin/message/`:
   - `Message.java`
   - `DefaultMessage.java`
   - `MessageMapper.java`

2. Copy them to `messaging/src/main/java/com/infenia/yukta/message/` with package name updated from `com.infenia.yukta.plugin.message` to `com.infenia.yukta.message`

3. Copy test file from `plugin-api/src/test/java/com/infenia/yukta/plugin/message/DefaultMessageTest.java` to `messaging/src/test/java/com/infenia/yukta/message/DefaultMessageTest.java` with package updated

4. Verify all 4 files exist in the new location

**Files to copy:**
- Source: `plugin-api/src/main/java/com/infenia/yukta/plugin/message/Message.java`
- Source: `plugin-api/src/main/java/com/infenia/yukta/plugin/message/DefaultMessage.java`
- Source: `plugin-api/src/main/java/com/infenia/yukta/plugin/message/MessageMapper.java`
- Source: `plugin-api/src/test/java/com/infenia/yukta/plugin/message/DefaultMessageTest.java`

**Destination package:** `com.infenia.yukta.message` (remove `.plugin` from package path)

**Changes to make:**
- Replace package declaration: `package com.infenia.yukta.plugin.message;` with `package com.infenia.yukta.message;`
- Replace package declaration: `package com.infenia.yukta.plugin.message.control;` with `package com.infenia.yukta.message.control;` (for control classes in Task 3)
- Keep all other content identical

**Expected output:**
- 3 main classes (Message.java, DefaultMessage.java, MessageMapper.java) in messaging module
- 1 test class (DefaultMessageTest.java) in messaging module

**Verification:**
```bash
ls -la /media/arun/Infenia/Infenia/Development/Public/yukta/messaging/src/main/java/com/infenia/yukta/message/
```

Should show: Message.java, DefaultMessage.java, MessageMapper.java

```bash
ls -la /media/arun/Infenia/Infenia/Development/Public/yukta/messaging/src/test/java/com/infenia/yukta/message/
```

Should show: DefaultMessageTest.java

**Global constraints:**
- All Java files must have Apache 2.0 license header (managed by Spotless)
- Follow Google Java Style Guide (2-space indentation, 100-char line limit)
- Code must pass: Spotless, Checkstyle, PMD, SpotBugs, JaCoCo (80%+ coverage)
- Use Conventional Commits for all commit messages
