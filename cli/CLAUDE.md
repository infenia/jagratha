# Yukta Go CLI - AI Guidance

Lightweight Go-based CLI for Yukta.

## Build & Run Commands
- Build binary: `./gradlew :cli:goBuild`
- Run tests: `./gradlew :cli:goTest`
- Quality check: `./gradlew :cli:goCheck`

## Key Locations
- Entry point: `main.go`
- Commands: `cmd/commands/`
- API Client: `pkg/api/client.go`

## Patterns
- **Cobra & Viper**: Standard Go libraries for CLI commands and configuration.
- **Thin Client**: The CLI should remain lightweight, delegating complex logic to the Yukta server via REST.
- **Cross-Platform**: Ensure all commands work on Linux, macOS, and Windows.
