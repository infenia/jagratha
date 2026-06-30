# UI Module CLAUDE.md

## Overview
JTE UI module with Tailwind CSS.

## Build & Development Commands
```bash
# Run UI tests
./gradlew :ui:test

# Tailwind build (managed via Gradle)
./gradlew :ui:tailwind
```

## Tech Stack
- **JTE**: Templates in `src/main/jte/`.
- **Tailwind CSS 4**: Processed by `pnpm exec tailwindcss`.
- **Alpine.js**: For client-side interactivity.

## Frontend Assets
- **CSS Input**: `src/main/resources/static/css/input.css`
- **Node Dependencies**: Managed via `pnpm`.

## Imports
@.claude/rules/coding-standards.md
@.claude/rules/git-workflow.md
