# Apache License 2.0 Compliance - Quick Start Guide

Your project now has a **4-layer compliance checking system** for Apache 2.0 licenses. This guide explains what was set up and how to use it.

## 🎯 What Was Implemented

| Layer | Tool | Purpose | When It Runs |
|-------|------|---------|--------------|
| 1️⃣ | **Spotless** | Enforces Apache headers on Java/Kotlin/XML files | Before every commit |
| 2️⃣ | **REUSE** | Verifies SPDX headers on all files | CI (every PR/push) |
| 3️⃣ | **Gradle License Report** | Scans dependencies for incompatible licenses | CI (PRs/pushes) |
| 4️⃣ | **Trivy** | Detects license conflicts in compiled code | CI (every build) |

## ⚡ Quick Commands for Daily Use

### Before committing:
```bash
# Auto-fix headers and formatting
./gradlew spotlessApply
```

### Before submitting a PR:
```bash
# Generate dependency license reports
./gradlew generateLicenseReport

# View the report (open in browser)
open build/reports/licenses/index.html  # macOS
# or
xdg-open build/reports/licenses/index.html  # Linux
```

### Check full compliance:
```bash
# Run everything at once
./gradlew spotlessApply && ./gradlew spotlessCheck && ./gradlew generateLicenseReport
```

## 📁 New Files Added

### Configuration Files:
- **`.reuse/`** - REUSE Software compliance directory
  - `licenses/Apache-2.0.txt` - Full Apache 2.0 license text
  - `README.md` - REUSE configuration guide
  
- **`.github/workflows/license-compliance.yml`** - New GitHub Action workflow
  - Runs REUSE checks
  - Generates Gradle license reports
  - Runs Trivy license scanning
  - Posts results to PRs

### Documentation:
- **`LICENSE_COMPLIANCE.md`** - Comprehensive compliance documentation
- **`APACHE_LICENSE_COMPLIANCE_GUIDE.md`** - This file

### Updated Files:
- **`build-logic/build.gradle.kts`** - Added gradle-license-report plugin dependency
- **`build-logic/src/main/kotlin/com.infenia.yukta.quality-conventions.gradle.kts`** - Added license report configuration
- **`.github/workflows/ci.yml`** - Extended Trivy to include license scanning

## 🔍 Layer Details

### Layer 1: Spotless ✓ (Already Configured)
Your Spotless setup automatically enforces Apache headers on Java/Kotlin/XML files.

**Before commit:**
```bash
./gradlew spotlessApply
```

**Status:** Automatically applied in most IDEs, enforced before commit.

### Layer 2: REUSE (New)
GitHub Action checks that all files have SPDX license identifiers.

**What it checks:**
- Every file has a license declaration
- License text files exist in `.reuse/licenses/`
- Project structure is REUSE-compliant

**If it fails on CI:**
1. Add SPDX-License-Identifier comments to files
2. Check `.reuse/README.md` for examples
3. Run `reuse lint` locally (requires `pip install fsfe-reuse`)

### Layer 3: Gradle License Report (New)
Generates detailed reports of all dependency licenses.

**Run locally:**
```bash
./gradlew generateLicenseReport
```

**Reports generated:**
- `build/reports/licenses/index.html` - Interactive HTML report
- `build/reports/licenses/license-*.json` - Machine-readable JSON
- `build/reports/licenses/license-*.csv` - Spreadsheet format

**View in browser:**
```bash
# Each module generates its own report
find . -path "*/build/reports/licenses/index.html" -exec open {} \;
```

**Check for GPL licenses** (incompatible with Apache 2.0):
```bash
./gradlew generateLicenseReport && \
  find . -path "*/build/reports/licenses/license-*.json" -exec grep -l "GPL" {} \;
```

### Layer 4: Trivy (Extended)
Trivy now scans for both vulnerabilities AND license issues.

**Run locally:**
```bash
# Full license scan
trivy fs --license-full --format table .

# Generate SARIF report (for GitHub)
trivy fs --format sarif --output trivy-license-results.sarif --license-full .
```

**In CI:**
- Runs on every push and PR
- Uploads to GitHub Security tab under "Code scanning alerts"
- Comments results on PRs

## 🚦 Compliance Workflow

### ✅ Happy Path (No Issues)
1. Make code changes
2. Run `./gradlew spotlessApply` before commit
3. Push to PR
4. CI automatically runs all 4 layers
5. All checks pass → Ready to merge

### ⚠️ If Spotless Fails
**Problem:** Headers missing or formatting wrong
```bash
# Solution: Auto-fix
./gradlew spotlessApply
git add -A
git commit --amend  # or create new commit
```

### ⚠️ If REUSE Check Fails (CI)
**Problem:** Files missing SPDX license identifiers

**Solution:** Add SPDX headers to files
```java
// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Infenia Private Limited
```

See `.reuse/README.md` for examples.

### ⚠️ If License Report Shows GPL/AGPL (CI)
**Problem:** Dependency uses GPL (incompatible with Apache 2.0)

**Solution Options:**
1. **Replace** the dependency with Apache-compatible alternative
2. **Contact legal** team for permission
3. **Isolate** the dependency (separate module, optional feature)

Example incompatible licenses:
- ❌ GPL (v2, v3, etc.)
- ❌ AGPL
- ✅ MIT, BSD, ISC
- ✅ Apache 2.0, LGPL
- ✅ CDDL, EPL, MPL

### ⚠️ If Trivy License Scan Fails (CI)
**Problem:** Runtime license conflicts detected

**Solution:**
1. Check GitHub Security tab for details
2. Review `trivy-license-results.sarif` artifact
3. Fix underlying dependency issues
4. Re-run CI

## 📊 Checking Compliance Status

### Local Status
```bash
# Check all 4 layers
./gradlew spotlessCheck              # Layer 1
reuse lint                           # Layer 2 (requires pip install fsfe-reuse)
./gradlew generateLicenseReport      # Layer 3
trivy fs --license-full .            # Layer 4
```

### GitHub Status
- **Spotless:** Part of your normal build (`./gradlew check`)
- **REUSE:** GitHub Action → "License Compliance Check" → "REUSE License Headers"
- **Gradle Report:** GitHub Action → "License Compliance Check" → "Dependency License Report"
- **Trivy:** GitHub Security tab → Code scanning alerts (Trivy License Scan)

## 🔗 Related Documents

- **[LICENSE_COMPLIANCE.md](./LICENSE_COMPLIANCE.md)** - Detailed compliance documentation
- **[.reuse/README.md](./.reuse/README.md)** - REUSE configuration details
- **[SECURITY.md](./SECURITY.md)** - Security policy
- **[LICENSE](./LICENSE)** - Full Apache 2.0 text

## ❓ FAQ

**Q: Do I need to do anything for Spotless?**
A: Just run `./gradlew spotlessApply` before committing. It's probably already configured in your IDE.

**Q: What if I use a library with a GPL dependency?**
A: GPL is NOT compatible with Apache 2.0. You must either replace the library or get legal approval.

**Q: How often should I check?**
A: Always run `spotlessApply` before committing. Run full compliance check before submitting PRs.

**Q: What's the difference between REUSE and Spotless?**
A: Spotless = formatting + headers for Java/Kotlin/XML only. REUSE = license declarations for ALL files.

**Q: Can I use MIT, BSD, or ISC licensed libraries?**
A: Yes! These are compatible with Apache 2.0. Reports will show them clearly.

**Q: Where are the license reports?**
A: `build/reports/licenses/` after running `generateLicenseReport`.

**Q: What if CI fails on a new library I added?**
A: Check the license reports to identify incompatible licenses. Contact the team or replace the library.

## 🎓 Learning More

- **REUSE Software:** https://reuse.software/
- **SPDX Licenses:** https://spdx.org/licenses/
- **Trivy Documentation:** https://aquasecurity.github.io/trivy/
- **Apache 2.0 Compatibility:** https://www.apache.org/licenses/GPL-compatibility.html

## 💡 Tips

1. **Keep it simple:** Just run `spotlessApply` before every commit
2. **Check reports regularly:** Review `build/reports/licenses/index.html` when you add dependencies
3. **Be proactive:** Use Gradle reports locally before pushing to catch issues early
4. **Team communication:** If you find incompatible licenses, flag it early with the team

---

**Summary:** You now have comprehensive Apache 2.0 compliance checking across 4 layers. In 99% of cases, just run `./gradlew spotlessApply` before committing and the rest is automatic in CI.
