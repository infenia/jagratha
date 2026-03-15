# Gradle Build Tool Plugin

Integrates Yukta with the Gradle build system.

## 🏗️ Purpose

This plugin allows Yukta to execute Gradle tasks (e.g., `test`, `checkstyleMain`) and capture their output for processing.

## ⚙️ Configuration

| Key | Type | Description |
| :--- | :--- | :--- |
| `gradlePath` | `String` | Path to the Gradle executable (defaults to `./gradlew`). |
| `projectRoot` | `String` | Path to the root of the Gradle project. |
