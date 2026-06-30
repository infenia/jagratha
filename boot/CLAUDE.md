# Yukta Boot - AI Guidance

This module is the entry point for the Yukta Spring Boot application.

## Build & Run Commands
- Run application: `./gradlew :boot:bootRun`
- Build native image: `./gradlew :boot:nativeCompile`
- Run tests: `./gradlew :boot:test`
- Run quality checks: `./gradlew :boot:check`

## Key Locations
- Main Class: `src/main/java/com/infenia/yukta/YuktaApplication.java`
- Configuration: `src/main/resources/application.yml`
- Native Config: `build.gradle.kts` (graalvmNative block)

## Patterns
- **AOT/Native Compatibility**: Avoid dynamic reflection or classpath scanning without explicit hints.
- **Profiles**: Uses `dev` for local development and `prod` for native builds.
- **Dependency Aggregation**: This module brings together all other submodules (`core`, `web`, `ui`, `mcp`, `plugins`).
