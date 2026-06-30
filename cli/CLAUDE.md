# Yukta Go CLI CLAUDE.md

## Overview
A lightweight, cross-platform command-line interface (CLI) for Yukta, built in Go.

## Build & Development Commands

### Gradle Commands
```bash
# Build for current platform
./gradlew :cli:goBuild

# Build for all platforms
./gradlew :cli:goBuildAll

# Run Go tests
./gradlew :cli:goTest

# Run Go linters
./gradlew :cli:goCheck
```

### Makefile Commands (from cli/ folder)
```bash
make build     # Build for current OS/arch
make build-all # Build for all platforms
make test      # Run Go tests
make lint      # Run Go linters
make fmt       # Format code
```

## Project Structure
- `main.go`: Entry point.
- `cmd/`: Command definitions (Cobra).
- `pkg/api/`: HTTP client and types.
- `pkg/config/`: Configuration management.

## Tech Stack
- **Go 1.21+**
- **Cobra** (CLI framework)

## Imports
@.claude/rules/git-workflow.md
