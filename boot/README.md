# Yukta Boot

This module is the main entry point for the Yukta application. It handles the Spring Boot configuration, dependency injection, and native image compilation.

## Key Responsibilities

- **Application Lifecycle**: Bootstraps the application using `YuktaApplication`.
- **Dependency Wiring**: Combines all core, web, mcp, and plugin modules into a single executable.
- **Profile Management**: Manages different configuration profiles (e.g., `dev`, `prod`).
- **Native Image Configuration**: Contains GraalVM native-image configuration and build logic.
- **Global Error Handling**: Standardizes error responses across the application.

## Build and Run

### Run in Development Mode
```bash
./gradlew :boot:bootRun
```

### Build Native Image
```bash
./gradlew :boot:nativeCompile
```
The resulting binary will be located in `boot/build/native/nativeCompile/yukta`.

## Configuration
Main application properties are located in `src/main/resources/application.yml`.
