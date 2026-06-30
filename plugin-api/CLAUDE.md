# Yukta Plugin API - AI Guidance

The SPI for Yukta extensibility.

## Build & Run Commands
- Run checks: `./gradlew :plugin-api:check`

## Key Locations
- Base Interfaces: `src/main/java/com/infenia/yukta/plugin/core/`
- Plugin Types: `src/main/java/com/infenia/yukta/plugin/type/`
- Exceptions: `src/main/java/com/infenia/yukta/plugin/exception/`

## Patterns
- **Plugin Lifecycle**: Plugins must implement `initialize` and `destroy` (default implementations available).
- **Configuration Record**: Define plugin configuration using Java records with Bean Validation annotations.
- **Port Strategy**: Default output port is "default". Use `getOutputPorts()` for multi-port routing (e.g., Branch, Splitter).
