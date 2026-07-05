# Plugin Log Storage Architecture

## Overview

The plugin log storage system captures execution logs with configurable retention periods and enables streaming both historical and live logs to clients.

## Features

- **Non-blocking writes**: Log entries are written asynchronously on a bounded elastic scheduler
- **In-memory storage**: Caffeine cache with automatic expiration based on retention period
- **Configurable retention**: User-configurable retention period with hardcoded maximum (24 hours)
- **Historical + Live streaming**: API endpoints emit cached historical logs first, then live updates
- **Storage abstraction**: Interface-based design allows future backends (file, database, S3)

## Configuration

Configure log storage behavior in `application.yaml`:

```yaml
yukta:
  logs:
    store:
      backend: memory
      retention:
        default-period-minutes: 30
```

### Configuration Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `yukta.logs.store.backend` | String | `memory` | Storage backend (memory, file, database) |
| `yukta.logs.store.retention.default-period-minutes` | Integer | `30` | Retention period in minutes |

### Hard Limits

- **Maximum retention**: 1440 minutes (24 hours) — hardcoded, non-configurable
- If user sets `default-period-minutes` > 1440, the system enforces the 1440-minute limit

## API Usage

### Stream Logs with History

```bash
curl -N \
  http://localhost:8080/api/sessions/{sessionId}/executions/{executionId}/logs \
  -H "Accept: text/event-stream"
```

Response: Text event stream with historical log entries first, then live updates.

### Log Entry Format

Each log entry is formatted as:

```
[2026-07-05T10:30:45.123Z] [INFO] [processor-1/Data Processor] STDOUT: Processing started
```

## Architecture

### Core Components

**PluginLogEntry** (record)
- Immutable log entry with execution context, plugin metadata, message, and timestamp
- Methods: `format()` to render as human-readable string

**PluginLogStore** (interface)
- Abstraction for log storage and retrieval
- Methods:
  - `write(PluginLogEntry)`: Non-blocking write
  - `readExecution(executionId)`: Chronological read for execution
  - `cleanup(executionId)`: Delete logs after retention expires
  - `getEffectiveRetention()`: Get effective retention duration

**InMemoryPluginLogStore** (implementation)
- Caffeine cache-backed in-memory storage
- Automatic expiration after configured retention period
- Thread-safe via Caffeine's internal synchronization

**LogStoreSubscriber** (Spring component)
- Subscribes to `DefaultTaskTrackerService` log events
- Writes entries asynchronously on `Schedulers.boundedElastic()`
- Non-blocking; errors are logged but don't interrupt execution

### Data Flow

```
DefaultTaskTrackerService (log emission)
  ↓
LogStoreSubscriber (subscribes, writes async)
  ↓
InMemoryPluginLogStore (Caffeine cache)
  ↓
LogManagementController.streamExecutionLogs()
  ├─ Phase 1: Read historical from store
  └─ Phase 2: Merge with live stream from ControlBusGateway
```

## Testing

### Unit Tests

- `InMemoryPluginLogStoreTest`: Tests write, read, cleanup, retention capping
- `LogStoreSubscriberTest`: Tests async subscription and error handling

### Integration Tests

- `LogManagementControllerIntegrationTest`: Tests historical + live streaming end-to-end

Run tests:
```bash
./gradlew :core:test --tests "com.infenia.yukta.logging.*"
./gradlew :web:test --tests "*LogManagementController*"
```

## Future Enhancements

### File-Based Storage
Implement `FileSystemPluginLogStore` with properties:
```yaml
yukta:
  logs:
    store:
      backend: file
      file:
        directory: /var/log/yukta/plugins
        compression: gzip
```

### Database Storage
Implement `DatabasePluginLogStore` for distributed deployments and long-term archival.

### S3 Storage
Implement `S3PluginLogStore` for cloud-native deployments.

Each backend would implement the `PluginLogStore` interface and be activated via Spring's `@ConditionalOnProperty`.

## Performance Considerations

- **Memory usage**: Proportional to number of concurrent executions × average logs per execution
- **Retention cleanup**: Handled automatically by Caffeine after configured TTL expires
- **Write latency**: Minimal, bounded elastic scheduler with queue depth monitoring
- **Read latency**: O(n) where n = number of logs for execution (typically small)

## Troubleshooting

### Logs disappear after 30 minutes

This is expected behavior. By default, logs are retained for 30 minutes after execution completion. To extend:

```yaml
yukta:
  logs:
    store:
      retention:
        default-period-minutes: 120  # Increase to 2 hours (max: 1440)
```

### High memory usage

Monitor in-flight executions:
- Reduce `default-period-minutes` to clean up logs faster
- Implement file-based storage backend for long-term retention

```yaml
yukta:
  logs:
    store:
      backend: file  # When available
      file:
        directory: /var/log/yukta/plugins
```
