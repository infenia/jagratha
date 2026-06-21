# Conditional Store Bean Creation Design

**Date:** 2026-06-21  
**Author:** Arun  
**Status:** Design  
**Objective:** Only instantiate the configured `SessionConfigStore` implementation, avoiding unnecessary bean initialization as new stores (DB, Redis, etc.) are added.

---

## Problem Statement

Currently, `SessionConfigStoreFactory` autowires `InMemorySessionConfigStore` as a Spring bean dependency. This causes Spring to instantiate it even when only `FileSessionConfigStore` is configured for use. As additional store implementations are added (DB, Redis, etc.), this problem scales—all stores get instantiated regardless of configuration, wasting resources.

## Solution Approach

Use Spring's `@ConditionalOnProperty` annotation to gate each store implementation's bean creation. Only the store matching the `yukta.session.store-type` configuration property will be instantiated as a Spring bean.

---

## Design Details

### 1. Store Implementation Changes

#### InMemorySessionConfigStore
```java
@Component
@ConditionalOnProperty(
    name = "yukta.session.store-type",
    havingValue = "in-memory"
)
public class InMemorySessionConfigStore implements SessionConfigStore {
    // ... existing implementation unchanged
}
```

**Changes:**
- Add `@ConditionalOnProperty` annotation
- No other modifications

#### FileSessionConfigStore
```java
@Component
@ConditionalOnProperty(
    name = "yukta.session.store-type",
    havingValue = "file"
)
public class FileSessionConfigStore implements SessionConfigStore {
    @Autowired
    public FileSessionConfigStore(
        final SessionConfigProperties props,
        final ObjectMapper objectMapper,
        final WorkflowDefinitionStore workflowDefinitionStore) {
        // ... existing constructor logic
    }
    
    // ... existing implementation unchanged
}
```

**Changes:**
- Add `@ConditionalOnProperty` annotation
- Add `@Component` annotation (currently not a component—created manually)
- Accept dependencies via constructor autowiring (instead of being created manually in the factory)

---

### 2. SessionConfigStoreFactory Changes

```java
@Component
public class SessionConfigStoreFactory {

  private final SessionConfigStore configuredStore;

  private final SessionConfigProperties props;

  @Autowired
  public SessionConfigStoreFactory(
      final SessionConfigStore configuredStore,
      final SessionConfigProperties props) {
    this.configuredStore = configuredStore;
    this.props = props;
  }

  public SessionConfigStore getStore() {
    final String storeType = props.getStoreType();
    log.info("Using SessionConfigStore with type: {}", storeType);
    return configuredStore;
  }
}
```

**Changes:**
- Remove explicit injection of `InMemorySessionConfigStore` and manual creation of `FileSessionConfigStore`
- Accept a single `SessionConfigStore` bean (whichever was conditionally created by Spring)
- Simplify `getStore()` to log and return the configured store
- Remove the switch statement (no longer needed—Spring provides the correct implementation)

---

### 3. AppConfiguration Changes

```java
@Bean
@ConditionalOnMissingBean
public SessionConfigStore sessionConfigStore(final SessionConfigStoreFactory factory) {
  return factory.getStore();
}
```

**No changes required.** The method remains unchanged—it continues to delegate to the factory.

---

### 4. Error Handling

**Scenario:** User provides an invalid `store-type` value (e.g., `redis` before Redis store is implemented).

**Behavior:**
- Neither `InMemorySessionConfigStore` nor `FileSessionConfigStore` bean is created
- `SessionConfigStoreFactory` autowiring fails with a clear error: `"No qualifying bean of type 'SessionConfigStore' available"`
- Spring startup fails, alerting the user to the invalid configuration

**Alternative (optional):** Add validation in `SessionConfigProperties` to restrict values to known store types at configuration time.

---

## Scalability

When adding new store implementations (e.g., `RedisSessionConfigStore`):

```java
@Component
@ConditionalOnProperty(
    name = "yukta.session.store-type",
    havingValue = "redis"
)
public class RedisSessionConfigStore implements SessionConfigStore {
    // ... implementation
}
```

- No changes to `SessionConfigStoreFactory` or `AppConfiguration`
- New store is automatically discovered and available for selection via configuration
- Only the chosen store is instantiated

---

## Testing Strategy

1. **Unit Tests for Conditional Beans:**
   - Test that `InMemorySessionConfigStore` bean exists when `store-type=in-memory`
   - Test that `FileSessionConfigStore` bean exists when `store-type=file`
   - Test that both beans do NOT exist when the other store type is configured

2. **Integration Tests:**
   - Start application context with `store-type=in-memory` and verify correct bean is used
   - Start application context with `store-type=file` and verify correct bean is used
   - Verify `SessionConfigStoreFactory.getStore()` returns the correct implementation

3. **Error Case:**
   - Verify application startup fails gracefully with invalid `store-type` value

---

## Files Modified

| File | Changes |
|------|---------|
| `core/src/main/java/com/infenia/yukta/service/session/InMemorySessionConfigStore.java` | Add `@ConditionalOnProperty` |
| `core/src/main/java/com/infenia/yukta/service/session/FileSessionConfigStore.java` | Add `@Component` and `@ConditionalOnProperty`; move dependencies to constructor |
| `core/src/main/java/com/infenia/yukta/service/session/SessionConfigStoreFactory.java` | Simplify to accept single `SessionConfigStore` bean; remove manual store creation |
| `core/src/main/java/com/infenia/yukta/config/AppConfiguration.java` | No changes |

---

## Backward Compatibility

✅ **Fully backward compatible.** Existing configurations and behavior remain unchanged:
- `store-type=in-memory` continues to use in-memory store
- `store-type=file` continues to use file-based store
- Default behavior (invalid store type) fails fast with clear error message

---

## Benefits

- **Resource Efficiency:** Only the selected store is instantiated
- **Scalability:** New stores can be added without modifying factory logic or configuration class
- **Clarity:** Each store declares its own activation condition
- **Maintainability:** Decouples store instantiation from factory logic
