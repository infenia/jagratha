# Plugin API Module CLAUDE.md

## Overview
Plugin abstraction interfaces and base classes.

## Build & Development Commands
```bash
# Run tests
./gradlew :plugin-api:test
```

## Plugin Categories
1. **TriggerPlugin**: Initiates workflows.
2. **ProcessorPlugin**: Transforms or validates data.
3. **TerminalPlugin**: Finalizes workflows.

## Key Files
- `com.infenia.yukta.plugin.Plugin`: Base interface.
- `com.infenia.yukta.plugin.core.WorkflowPlugin`: Workflow-aware plugin.

## Imports
@.claude/rules/coding-standards.md
@.claude/rules/git-workflow.md
