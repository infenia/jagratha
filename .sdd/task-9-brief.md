# Task 9: Format code and run full quality checks

**Goal:** Format all changed code with Spotless and run complete quality gate suite to ensure code meets all project standards

**What to do:**

1. Run Spotless formatter to apply Google Java Style Guide formatting:
   ```bash
   cd /media/arun/Infenia/Infenia/Development/Public/yukta && ./gradlew spotlessApply
   ```
   Expected: BUILD SUCCESSFUL

2. Run quality gates (without tests first):
   ```bash
   cd /media/arun/Infenia/Infenia/Development/Public/yukta && ./gradlew check -x test
   ```
   Expected: BUILD SUCCESSFUL
   - Checks: Spotless, Checkstyle, PMD, SpotBugs, JaCoCo

3. Run all tests to verify nothing broke:
   ```bash
   cd /media/arun/Infenia/Infenia/Development/Public/yukta && ./gradlew test
   ```
   Expected: BUILD SUCCESSFUL, all tests pass

4. Run a full clean build to ensure everything works end-to-end:
   ```bash
   cd /media/arun/Infenia/Infenia/Development/Public/yukta && ./gradlew clean build
   ```
   Expected: BUILD SUCCESSFUL

**What this verifies:**
- Code formatting follows Google Java Style Guide (100-char lines, 2-space indentation)
- All license headers are correct (Apache 2.0)
- Checkstyle rules pass
- PMD rules pass
- SpotBugs finds no bugs
- JaCoCo coverage requirements met (80%+ for most modules)
- All unit tests pass
- Full build succeeds

**Expected results:**
- All formatting applied automatically by Spotless
- All quality gates pass
- All tests pass (DefaultMessageTest in messaging module, plus all others)
- Full build completes successfully

**Potential issues to watch for:**
- Coverage thresholds might need adjustment if new code doesn't have sufficient tests
- Any new imports might trigger style issues
- Tests might fail if they depend on old package names

**Global constraints:**
- All Java files must have Apache 2.0 license header (managed by Spotless)
- Follow Google Java Style Guide (2-space indentation, 100-char line limit)
- Code must pass: Spotless, Checkstyle, PMD, SpotBugs, JaCoCo (80%+ coverage)
- Use Conventional Commits for all commit messages
