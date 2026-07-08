# License Compliance Strategy

This document outlines the multi-layer Apache 2.0 license compliance checking for the Yukta project.

## Overview

The project uses a comprehensive 4-layer approach to ensure all code and dependencies comply with Apache License 2.0:

1. **Spotless** - Java file header validation
2. **REUSE** - Comprehensive SPDX license header checking
3. **Gradle License Report** - Dependency license analysis
4. **Trivy** - Vulnerability & license scanning

## Layer 1: Spotless (File Headers)

### What it does
- Enforces Apache 2.0 license headers on Java, Kotlin Gradle, and XML files
- Automatically adds missing headers before commit
- Validates formatting and import ordering

### Configuration
- Located in: `build-logic/src/main/kotlin/com.infenia.yukta.quality-conventions.gradle.kts`
- License header template: `config/license/header.txt`
- Runs automatically as part of the build

### Usage

**Check current headers:**
```bash
./gradlew spotlessCheck
```

**Auto-fix missing/incorrect headers:**
```bash
./gradlew spotlessApply
```

**Before every commit**, run:
```bash
./gradlew spotlessApply
```

## Layer 2: REUSE Software Compliance

### What it does
- Verifies all files have proper SPDX license identifiers or license files
- Checks `.reuse/` directory for license text files
- Ensures project-wide compliance with REUSE best practices

### Configuration
- GitHub Action: `.github/workflows/license-compliance.yml` → `reuse-compliance` job
- Runs on: push to main, PRs, manual trigger
- No local configuration needed - REUSE action handles validation

### SPDX Headers Format

For files that should have headers, add SPDX identifier:

**Java files (already enforced by Spotless):**
```java
/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Copyright 2026 Infenia Private Limited
 * Licensed under the Apache License, Version 2.0...
 */
```

**Markdown, YAML, other text files:**
```markdown
<!--
SPDX-License-Identifier: Apache-2.0
Copyright 2026 Infenia Private Limited
-->
```

### License Files

Place license text files in `.reuse/licenses/`:
```bash
.reuse/
  licenses/
    Apache-2.0.txt    # Full Apache 2.0 text
    MIT.txt           # If you use MIT-licensed dependencies
    (other licenses...)
```

### Usage

**Check locally** (requires `reuse` CLI):
```bash
# Install: pip install fsfe-reuse
reuse lint
```

**CI runs it automatically** in `license-compliance.yml`.

## Layer 3: Gradle Dependency License Report

### What it does
- Scans all transitive dependencies for their licenses
- Generates JSON, CSV, and HTML reports
- Identifies Apache-2.0 incompatible licenses (e.g., GPL)

### Configuration
- Added to: `build-logic/src/main/kotlin/com.infenia.yukta.quality-conventions.gradle.kts`
- Plugin: `com.github.jk1.dependency-license-report:2.8`
- Runs on: `./gradlew generateLicenseReport`

### Usage

**Generate license reports for all modules:**
```bash
./gradlew generateLicenseReport
```

**View reports:**
```bash
# HTML report (open in browser)
build/reports/licenses/index.html

# JSON report (machine-readable)
find . -path "*/build/reports/licenses/license-*.json"

# CSV report (spreadsheet format)
find . -path "*/build/reports/licenses/license-*.csv"
```

**Check for GPL licenses** (incompatible with Apache 2.0):
```bash
./gradlew generateLicenseReport && \
  find . -path "*/build/reports/licenses/*.html" -exec grep -l "GPL" {} \;
```

### Handling Incompatible Licenses

If you find an incompatible license:

1. **GPL licenses** are NOT compatible with Apache 2.0
   - Action: Replace the dependency or get legal approval
   
2. **AGPL licenses** require source code release - usually incompatible
   - Action: Replace the dependency or get legal approval
   
3. **Proprietary licenses** may not be usable
   - Action: Contact the vendor or replace

### CI Integration

GitHub Action `.github/workflows/license-compliance.yml` → `gradle-license-report` job:
- Generates reports for all PRs
- Uploads to artifacts
- Comments summary on PRs

## Layer 4: Trivy License Scanning

### What it does
- Scans filesystem for license issues in compiled artifacts
- Identifies license conflicts and vulnerabilities
- Generates SARIF reports for GitHub Security tab

### Configuration
- Added to: `.github/workflows/ci.yml`
- Runs on: every push and PR

### Usage

**Run locally:**
```bash
# Filesystem scan with license check
trivy fs --license-full --severity MEDIUM,HIGH,CRITICAL .

# Generate SARIF report
trivy fs --format sarif --output trivy-license-results.sarif --license-full .
```

**In CI:**
- Runs automatically on every push/PR
- Uploads SARIF to GitHub Security tab
- Visible in "Security" → "Code scanning alerts"

## Workflow for Contributors

### Before committing:

1. **Apply Spotless formatting:**
   ```bash
   ./gradlew spotlessApply
   ```

2. **Check headers (optional, Spotless does this):**
   ```bash
   ./gradlew spotlessCheck
   ```

### Before submitting a PR:

1. **Ensure no new GPL/AGPL dependencies added:**
   ```bash
   ./gradlew generateLicenseReport
   # Review build/reports/licenses/index.html
   ```

2. **Commit and push** - CI will:
   - Run REUSE compliance check
   - Generate dependency license reports
   - Run Trivy license scan
   - Comment results on PR

### If a check fails:

**REUSE check fails:**
- Add SPDX headers to files
- Add license files to `.reuse/licenses/`
- Run `reuse lint` to verify

**License report shows incompatible license:**
- Review `build/reports/licenses/index.html`
- Replace the incompatible dependency
- Or request legal approval

**Trivy license scan fails:**
- Review the GitHub Security tab
- Address any license conflicts in dependencies

## Checking the Project

### Full compliance check:

```bash
# 1. Apply Spotless
./gradlew spotlessApply

# 2. Check all headers
./gradlew spotlessCheck

# 3. Generate license reports
./gradlew generateLicenseReport

# 4. Review results
open build/reports/licenses/index.html
```

### Compliance dashboard:

- **GitHub:** Check "Security" tab → "Code scanning" for Trivy results
- **CI Artifacts:** Each run uploads license reports to GitHub Actions artifacts
- **Local:** `build/reports/licenses/` after running `generateLicenseReport`

## Related Documents

- [SECURITY.md](./SECURITY.md) - Security policy and vulnerability reporting
- [CONTRIBUTING.md](./CONTRIBUTING.md) - Contributing guidelines
- [Apache License 2.0](./LICENSE) - Full license text
- [Spotless Configuration](./build-logic/src/main/kotlin/com.infenia.yukta.quality-conventions.gradle.kts)
- [REUSE Software](https://reuse.software/) - SPDX compliance standard
- [Trivy Documentation](https://aquasecurity.github.io/trivy/) - Security scanner

## FAQ

**Q: Why 4 layers?**
A: Defense in depth. Each layer catches different issues:
- Spotless: Missing headers on source files
- REUSE: Missing headers on all files (configs, scripts, etc.)
- Gradle Report: Incompatible transitive dependencies
- Trivy: Runtime license conflicts and vulnerabilities

**Q: Is GPL allowed?**
A: No. GPL and Apache 2.0 are incompatible. GPL-licensed code cannot be combined with Apache 2.0 in a single distribution.

**Q: Can I use MIT/BSD/ISC licensed dependencies?**
A: Yes. These are Apache 2.0 compatible. The license reports will show them.

**Q: What if I need to use a GPL dependency?**
A: Contact the legal team. Options:
1. Replace with a compatible alternative
2. Isolate it (e.g., separate plugin, optional module)
3. Get explicit license from GPL vendor for dual licensing

**Q: How often should I check?**
A: Always run `spotlessApply` before committing. Run license checks before submitting PRs. CI will verify everything on push.

**Q: Where are the license reports?**
A: `build/reports/licenses/` after running `generateLicenseReport`. GitHub CI uploads them as artifacts.
