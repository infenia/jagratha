# Manual Trigger Plugin — Design Spec

**Date:** 2026-06-18
**Status:** Approved

---

## Summary

A new trigger plugin that fires a workflow with no input and no configuration. Calling the workflow trigger API endpoint is sufficient — the plugin completely ignores any caller-supplied data and emits a single empty message to start execution.

---

## Motivation

The existing `ApiTriggerPlugin` forwards the caller's payload into the workflow. `ConfigVariableSource` seeds the workflow with pre-configured variables. Neither serves the case where a workflow simply needs to be kicked off — no data, no config, just a signal. `ManualTrigger` fills this gap.

---

## Design

### Module

New Gradle subproject: `plugins/triggers/manual-trigger/`

Structure mirrors the existing `api-trigger` module:
```
plugins/triggers/manual-trigger/
  build.gradle.kts
  src/main/java/com/infenia/yukta/plugin/trigger/ManualTrigger.java
  src/test/java/com/infenia/yukta/plugin/trigger/ManualTriggerTest.java
```

Register in `settings.gradle.kts`:
```kotlin
include("plugins:triggers:manual-trigger")
```

### Build file

Depends only on `:plugin-api` and `spring-boot-starter-webflux` (for `@Component` and Reactor). No `:core` dependency needed.

### Class: `ManualTrigger`

| Field | Value |
|-------|-------|
| Package | `com.infenia.yukta.plugin.trigger` |
| Implements | `TriggerPlugin` |
| Type string | `"MANUAL"` |
| Annotations | `@Slf4j`, `@Component` |
| Constructor | Default (no dependencies) |

**`start()` — the entire implementation:**
```java
return Flux.just(DefaultMessage.create(UUID.randomUUID(), Map.of()));
```

Emits one message with an empty map payload and a fresh UUID as the trace ID. All other lifecycle methods (`validateConfig`, `initialize`, `prepare`, `shutdown`) inherit the no-op defaults from `WorkflowPlugin`.

### Message emitted

| Field | Value |
|-------|-------|
| `payload` | `Map.of()` (empty, non-null) |
| `traceId` | Fresh `UUID.randomUUID()` |
| `metadata` | `Map.of()` (default) |
| All other fields | Defaults from `DefaultMessage.create()` |

### UI Design

Follows the same card style as `ApiTriggerPlugin` — coloured background, material icon, node ID label. Uses a distinct colour to distinguish it visually from the API trigger.

---

## What is NOT in scope

- Reading any caller-supplied payload or headers
- Supporting configuration variables
- Any lifecycle setup beyond default no-ops

---

## Testing

One test class `ManualTriggerTest` covering:
1. `getType()` returns `"MANUAL"`
2. `start()` emits exactly one message
3. Emitted message has an empty map payload
4. Emitted message has a non-null trace ID
5. `validateConfig()` completes without error on any input
