# Yukta Boot

`yukta-boot` is the executable entry point for the Yukta application. It assembles all modules and starts the Spring Boot context.

## 🏗️ Architecture

This module contains the `@SpringBootApplication` and is responsible for:
- Auto-configuration of plugins.
- Initializing the Web and MCP servers.
- Managing the application lifecycle.

## ⚙️ Configuration

Application-wide configuration is managed via `src/main/resources/application.yaml`.

## 📦 Dependencies & Key Classes

### Internal Dependencies
- `:yukta-web`
- `:yukta-mcp`
- `:yukta-core`
- `:yukta-ui`
- All official `:plugins:*`

### Key Classes
- `com.infenia.yukta.YuktaApplication`: The main class.

## 🚀 Execution

```bash
./gradlew :yukta-boot:bootRun
```
