# REUSE Software Compliance Configuration

This directory contains the REUSE Software license compliance configuration for the Yukta project.

## Structure

```
LICENSES/                # Top-level directory with REUSE license files (SPDX standard)
  Apache-2.0.txt         # Apache License 2.0 (primary license for Yukta)
  MIT.txt                # (if used by dependencies)
  (other SPDX licenses)

.reuse/
  README.md              # This file - REUSE compliance documentation
```

## About REUSE

REUSE Software is a specification for declaring copyright and licensing information in software projects. It ensures:

1. Every file has a clear license declaration
2. License texts are available locally
3. Compliance can be verified automatically

For more info: https://reuse.software/

## License Files

Each file in the top-level `LICENSES/` directory must match an SPDX license identifier and contain the full text of that license.

Current licenses:
- **LICENSES/Apache-2.0.txt** - Apache License 2.0 (used by Yukta project itself)

## SPDX Headers

Files must declare their license using SPDX format:

**Option 1: In-header declaration** (preferred for source files)
```java
/*
 * Copyright 2026 Infenia Private Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ...
 */
```

**Option 2: SPDX-License-Identifier comment**
```java
// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Infenia Private Limited
```

## Verification

Check compliance locally:
```bash
# Install REUSE (requires Python 3.7+)
pip install reuse

# Verify compliance
reuse lint

# Generate SBOM
reuse spdx > sbom.spdx
```

Compliance is verified automatically in CI (see `.github/workflows/license-compliance.yml`).

## Adding New License

If a new license type is discovered in dependencies:

1. Download the license text from SPDX: https://spdx.org/licenses/
2. Save as `LICENSES/{SPDX_ID}.txt` (top-level directory)
3. Update any files that reference it
4. Commit and verify in CI

## Exemptions

Files that don't need license declarations:
- `node_modules/`, `build/`, `dist/`, `.gradle/` - build artifacts
- Binary files (images, fonts, icons) - covered by project license
- Generated code (build output) - covered by project license
