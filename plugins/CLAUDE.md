# Plugins CLAUDE.md

## Overview
Implementations of various Yukta plugins.

## Plugin Types
- **Processors**: Located in `processors/`. Example: `process-executor`.
- **Triggers**: Located in `triggers/`. Example: `api-trigger`.
- **Terminals**: Located in `terminals/`. Example: `console-terminal`.

## Build Commands
```bash
# Build a specific plugin
./gradlew :plugins:processors:process-executor:build
```

## Development
Each plugin should implement the interfaces defined in `plugin-api`.

## Imports
@.claude/rules/coding-standards.md
@.claude/rules/git-workflow.md
