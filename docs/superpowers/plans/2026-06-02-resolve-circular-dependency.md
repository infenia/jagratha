# Resolve ControlBusGateway Circular Dependency

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Break the circular dependency between orchestrator and ControlBusGateway by introducing an `ExecutionStatusPublisher` interface that allows the orchestrator to publish status events without depending on ControlBusGateway.

**Architecture:** 
The orchestrator modules will depend on a lightweight `ExecutionStatusPublisher` interface that only handles status event publication. The `ControlBusGateway` will implement this interface and register itself as a status event listener. This inverts the dependency: orchestrator → ExecutionStatusPublisher ← ControlBusGateway (unidirectional, no cycle). Status events flow from orchestrator through the publisher to all registered listeners (control bus, task tracker, etc.).

**Tech Stack:** 
- Java 25, Spring Boot 4.0.2 (dependency injection)
- Project Reactor (reactive streams)
- Lombok (annotations)

---

## File Structure

**New files to create:**
- `core/src/main/java/com/infenia/yukta/service/execution/status/ExecutionStatusPublisher.java` - Interface for publishing execution status events
- `core/src/main/java/com/infenia/yukta/service/execution/status/ExecutionStatusEvent.java` - Event record for status updates
- `core/src/main/java/com/infenia/yukta/service/execution/status/DefaultExecutionStatusPublisher.java` - Implementation managing registered listeners

**Modified files:**
- `core/src/main/java/com/infenia/yukta/service/orchestrator/stream/StreamBuilder.java` - Replace ControlBusGateway with ExecutionStatusPublisher
- `core/src/main/java/com/infenia/yukta/service/orchestrator/strategy/ProcessorNodeAssemblerStrategy.java` - Replace ControlBusGateway with ExecutionStatusPublisher
- `core/src/main/java/com/infenia/yukta/service/orchestrator/strategy/TriggerNodeAssemblerStrategy.java` - Replace ControlBusGateway with ExecutionStatusPublisher
- `core/src/main/java/com/infenia/yukta/service/orchestrator/strategy/TerminalNodeAssemblerStrategy.java` - Replace ControlBusGateway with ExecutionStatusPublisher
- `core/src/main/java/com/infenia/yukta/service/orchestrator/strategy/StreamAssemblyHelper.java` - Update parameter types
- `core/src/main/java/com/infenia/yukta/service/orchestrator/compiler/HeartbeatBuilder.java` - Replace ControlBusGateway with ExecutionStatusPublisher
- `core/src/main/java/com/infenia/yukta/service/orchestrator/compiler/WorkflowCompiler.java` - Replace ControlBusGateway with ExecutionStatusPublisher
- `core/src/main/java/com/infenia/yukta/service/orchestrator/preparator/WorkflowPreparator.java` - Replace ControlBusGateway with ExecutionStatusPublisher
- `core/src/main/java/com/infenia/yukta/service/control/gateway/DefaultControlBusGateway.java` - Implement ExecutionStatusPublisher and register as listener
- `core/src/main/java/com/infenia/yukta/service/session/SessionService.java` - Replace ControlBusGateway with ExecutionStatusPublisher

**Test files to create/modify:**
- `core/src/test/java/com/infenia/yukta/service/execution/status/DefaultExecutionStatusPublisherTest.java` - Unit tests for publisher
- `core/src/test/java/com/infenia/yukta/service/orchestrator/stream/StreamBuilderTest.java` - Update tests to use ExecutionStatusPublisher

---

## Tasks

### Task 1: Create ExecutionStatusEvent Record

**Files:**
- Create: `core/src/main/java/com/infenia/yukta/service/execution/status/ExecutionStatusEvent.java`

- [ ] **Step 1: Write the event record class**

```java
package com.infenia.yukta.service.execution.status;

import com.infenia.yukta.validation.ExecutionId;
import com.infenia.yukta.validation.NodeId;
import com.infenia.yukta.validation.SessionId;
import com.infenia.yukta.validation.WorkflowId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Event representing a status update for a node execution.
 *
 * <p>Published by ExecutionStatusPublisher to notify listeners of status changes during workflow
 * execution (RUNNING, SUCCESS, FAILURE, etc.).
 */
public record ExecutionStatusEvent(
    @NotBlank String executionId,
    @NotBlank String nodeId,
    @NotBlank String workflowId,
    @NotBlank String sessionId,
    @NotBlank String status,
    @NotBlank String module,
    @Nullable Map<String, Object> metadata,
    @Nullable Throwable error,
    @NotNull Instant timestamp) {

  /**
   * Create a new ExecutionStatusEvent with current timestamp.
   *
   * @param executionId the execution identifier
   * @param nodeId the node identifier
   * @param workflowId the workflow identifier
   * @param sessionId the session identifier
   * @param status the status value (e.g., "RUNNING", "SUCCESS", "FAILURE")
   * @param module the module name
   * @param metadata optional metadata map
   * @param error optional error/exception
   * @return a new ExecutionStatusEvent with current timestamp
   */
  public static ExecutionStatusEvent of(
      @NotBlank final String executionId,
      @NotBlank final String nodeId,
      @NotBlank final String workflowId,
      @NotBlank final String sessionId,
      @NotBlank final String status,
      @NotBlank final String module,
      @Nullable final Map<String, Object> metadata,
      @Nullable final Throwable error) {
    return new ExecutionStatusEvent(
        executionId, nodeId, workflowId, sessionId, status, module, metadata, error, Instant.now());
  }
}
```

- [ ] **Step 2: Format code with Spotless**

```bash
cd /media/arun/Infenia/Infenia/Development/Public/yukta
./gradlew spotlessApply
```

Expected: Code formatted without errors.

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/infenia/yukta/service/execution/status/ExecutionStatusEvent.java
git commit -m "feat: create ExecutionStatusEvent record for status updates"
```

---

### Task 2: Create ExecutionStatusPublisher Interface

**Files:**
- Create: `core/src/main/java/com/infenia/yukta/service/execution/status/ExecutionStatusPublisher.java`

- [ ] **Step 1: Write the interface**

```java
package com.infenia.yukta.service.execution.status;

import com.infenia.yukta.plugin.message.Message;
import jakarta.validation.constraints.NotNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Publishes execution status events from the orchestrator.
 *
 * <p>This interface decouples the orchestrator from the control bus. The orchestrator publishes
 * status events (RUNNING, SUCCESS, FAILURE) through this publisher, and the control bus (or other
 * listeners) subscribes to receive updates.
 *
 * <p>This breaks the circular dependency: Orchestrator → ExecutionStatusPublisher ← ControlBusGateway
 */
public interface ExecutionStatusPublisher {

  /**
   * Publish a status event.
   *
   * @param event the execution status event
   * @return a Mono that completes when the event is published
   */
  Mono<Void> publishStatus(@NotNull ExecutionStatusEvent event);

  /**
   * Get a stream of all status events (for internal use by the control bus).
   *
   * @return a Flux of status events
   */
  Flux<ExecutionStatusEvent> statusStream();
}
```

- [ ] **Step 2: Format code with Spotless**

```bash
./gradlew spotlessApply
```

Expected: Code formatted without errors.

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/infenia/yukta/service/execution/status/ExecutionStatusPublisher.java
git commit -m "feat: create ExecutionStatusPublisher interface"
```

---

### Task 3: Create DefaultExecutionStatusPublisher Implementation

**Files:**
- Create: `core/src/main/java/com/infenia/yukta/service/execution/status/DefaultExecutionStatusPublisher.java`

- [ ] **Step 1: Write the implementation**

```java
package com.infenia.yukta.service.execution.status;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.util.concurrent.Queues;

/**
 * Default implementation of ExecutionStatusPublisher using Reactor Sinks.
 *
 * <p>Maintains an internal multicast sink that receives status events from the orchestrator and
 * distributes them to all subscribers (control bus, task tracker, monitoring systems, etc.).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultExecutionStatusPublisher implements ExecutionStatusPublisher {

  private static final Sinks.EmitFailureHandler RETRY_HANDLER =
      Sinks.EmitFailureHandler.busyLooping(Duration.ofMillis(100));

  private final Sinks.Many<ExecutionStatusEvent> statusSink =
      Sinks.many().multicast().onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);

  @Override
  public Mono<Void> publishStatus(@NotNull final ExecutionStatusEvent event) {
    return Mono.create(
        sink -> {
          try {
            statusSink.emitNext(event, RETRY_HANDLER);
            sink.success();
          } catch (final RuntimeException e) {
            log.atError()
                .setCause(e)
                .addKeyValue("executionId", event.executionId())
                .log("Failed to publish status event");
            sink.error(new IllegalStateException("Status event publish failed", e));
          }
        });
  }

  @Override
  public Flux<ExecutionStatusEvent> statusStream() {
    return statusSink.asFlux();
  }
}
```

- [ ] **Step 2: Format code with Spotless**

```bash
./gradlew spotlessApply
```

Expected: Code formatted without errors.

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/infenia/yukta/service/execution/status/DefaultExecutionStatusPublisher.java
git commit -m "feat: implement DefaultExecutionStatusPublisher with Reactor Sinks"
```

---

### Task 4: Create Unit Tests for DefaultExecutionStatusPublisher

**Files:**
- Create: `core/src/test/java/com/infenia/yukta/service/execution/status/DefaultExecutionStatusPublisherTest.java`

- [ ] **Step 1: Write unit tests**

```java
package com.infenia.yukta.service.execution.status;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

@DisplayName("DefaultExecutionStatusPublisher")
class DefaultExecutionStatusPublisherTest {

  private DefaultExecutionStatusPublisher publisher;

  @BeforeEach
  void setUp() {
    publisher = new DefaultExecutionStatusPublisher();
  }

  @Test
  @DisplayName("publishStatus should emit event to statusStream")
  void testPublishStatusEmitsToStream() {
    final ExecutionStatusEvent event =
        new ExecutionStatusEvent(
            "exec-123",
            "node-456",
            "workflow-789",
            "session-001",
            "RUNNING",
            "test-module",
            null,
            null,
            Instant.now());

    StepVerifier.create(
            publisher.statusStream().take(1).doOnNext(e -> publisher.publishStatus(event).block()))
        .expectNext(event)
        .verifyComplete();
  }

  @Test
  @DisplayName("publishStatus should return Mono that completes successfully")
  void testPublishStatusReturnsCompletingMono() {
    final ExecutionStatusEvent event =
        new ExecutionStatusEvent(
            "exec-123",
            "node-456",
            "workflow-789",
            "session-001",
            "SUCCESS",
            "test-module",
            null,
            null,
            Instant.now());

    StepVerifier.create(publisher.publishStatus(event))
        .expectComplete()
        .verify();
  }

  @Test
  @DisplayName("statusStream should support multiple subscribers")
  void testStatusStreamMultipleSubscribers() {
    final ExecutionStatusEvent event1 =
        ExecutionStatusEvent.of(
            "exec-123", "node-1", "workflow-789", "session-001", "RUNNING", "module-1", null, null);
    final ExecutionStatusEvent event2 =
        ExecutionStatusEvent.of(
            "exec-123", "node-2", "workflow-789", "session-001", "SUCCESS", "module-2", null, null);

    StepVerifier.create(
            publisher
                .statusStream()
                .take(2)
                .doOnSubscribe(
                    s -> {
                      publisher.publishStatus(event1).block();
                      publisher.publishStatus(event2).block();
                    }))
        .expectNext(event1, event2)
        .verifyComplete();
  }
}
```

- [ ] **Step 2: Format code with Spotless**

```bash
./gradlew spotlessApply
```

Expected: Code formatted without errors.

- [ ] **Step 3: Run tests to verify they pass**

```bash
./gradlew :core:test --tests "com.infenia.yukta.service.execution.status.DefaultExecutionStatusPublisherTest" -v
```

Expected: All 3 tests PASS.

- [ ] **Step 4: Commit**

```bash
git add core/src/test/java/com/infenia/yukta/service/execution/status/DefaultExecutionStatusPublisherTest.java
git commit -m "test: add unit tests for DefaultExecutionStatusPublisher"
```

---

### Task 5: Update StreamBuilder to Use ExecutionStatusPublisher

**Files:**
- Modify: `core/src/main/java/com/infenia/yukta/service/orchestrator/stream/StreamBuilder.java`

- [ ] **Step 1: Read the current StreamBuilder implementation**

Expected: File contains dependency on ControlBusGateway (line 72), used in error handling and status tracking.

- [ ] **Step 2: Replace the ControlBusGateway dependency with ExecutionStatusPublisher**

Old imports:
```java
import com.infenia.yukta.service.control.gateway.ControlBusGateway;
```

New imports:
```java
import com.infenia.yukta.service.execution.status.ExecutionStatusPublisher;
import com.infenia.yukta.service.execution.status.ExecutionStatusEvent;
```

Old field (line 72):
```java
private final ControlBusGateway controlBusGateway;
```

New field:
```java
private final ExecutionStatusPublisher statusPublisher;
```

Old constructor (line 90-98):
```java
public StreamBuilder(
    final WorkflowNode node,
    final Duration timeout,
    final TaskTrackerService taskTrackerService,
    final ControlBusGateway controlBusGateway) {
  this.node = node;
  this.timeout = timeout;
  this.taskTrackerService = taskTrackerService;
  this.controlBusGateway = controlBusGateway;
}
```

New constructor:
```java
public StreamBuilder(
    final WorkflowNode node,
    final Duration timeout,
    final TaskTrackerService taskTrackerService,
    final ExecutionStatusPublisher statusPublisher) {
  this.node = node;
  this.timeout = timeout;
  this.taskTrackerService = taskTrackerService;
  this.statusPublisher = statusPublisher;
}
```

- [ ] **Step 3: Update error handling to publish status events instead of emitting to control bus**

Find the section around line 238 (the .emit call for ControlError). Replace:

Old code:
```java
.emit(
    DefaultMessage.create(null, new ControlError(...))
        .withSourceNodeId(node.nodeId())
        .withWorkflowId(context.workflowId())
        .withControl(true))
```

New code:
```java
.flatMap(
    error ->
        statusPublisher.publishStatus(
            ExecutionStatusEvent.of(
                context.executionId(),
                node.nodeId(),
                context.workflowId(),
                context.sessionId(),
                "FAILURE",
                "StreamBuilder",
                null,
                error)))
```

- [ ] **Step 4: Format code with Spotless**

```bash
./gradlew spotlessApply
```

Expected: Code formatted without errors.

- [ ] **Step 5: Run related tests to ensure no breakage**

```bash
./gradlew :core:test --tests "*StreamBuilder*" -v
```

Expected: Tests pass (may need to update test fixtures to provide ExecutionStatusPublisher).

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/infenia/yukta/service/orchestrator/stream/StreamBuilder.java
git commit -m "refactor: replace ControlBusGateway with ExecutionStatusPublisher in StreamBuilder"
```

---

### Task 6: Update StreamAssemblyHelper to Use ExecutionStatusPublisher

**Files:**
- Modify: `core/src/main/java/com/infenia/yukta/service/orchestrator/strategy/StreamAssemblyHelper.java`

- [ ] **Step 1: Read the current implementation**

Expected: File has parameter `ControlBusGateway controlBusGateway` (line 49).

- [ ] **Step 2: Update imports**

Replace:
```java
import com.infenia.yukta.service.control.gateway.ControlBusGateway;
```

With:
```java
import com.infenia.yukta.service.execution.status.ExecutionStatusPublisher;
```

- [ ] **Step 3: Update method signature**

Change (line 44-50):
```java
static Flux<Message<?>> buildStreamWithContext(
    final WorkflowNode node,
    final Flux<Message<?>> stream,
    final Duration timeout,
    final TaskTrackerService tracker,
    final ControlBusGateway controlBusGateway,
    final AssemblyContext context) {
```

To:
```java
static Flux<Message<?>> buildStreamWithContext(
    final WorkflowNode node,
    final Flux<Message<?>> stream,
    final Duration timeout,
    final TaskTrackerService tracker,
    final ExecutionStatusPublisher statusPublisher,
    final AssemblyContext context) {
```

- [ ] **Step 4: Update StreamBuilder instantiation**

Change (line 62):
```java
new StreamBuilder(node, timeout, tracker, controlBusGateway)
```

To:
```java
new StreamBuilder(node, timeout, tracker, statusPublisher)
```

- [ ] **Step 5: Format code with Spotless**

```bash
./gradlew spotlessApply
```

Expected: Code formatted without errors.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/infenia/yukta/service/orchestrator/strategy/StreamAssemblyHelper.java
git commit -m "refactor: update StreamAssemblyHelper to use ExecutionStatusPublisher"
```

---

### Task 7: Update ProcessorNodeAssemblerStrategy

**Files:**
- Modify: `core/src/main/java/com/infenia/yukta/service/orchestrator/strategy/ProcessorNodeAssemblerStrategy.java`

- [ ] **Step 1: Update imports**

Replace:
```java
import com.infenia.yukta.service.control.gateway.ControlBusGateway;
```

With:
```java
import com.infenia.yukta.service.execution.status.ExecutionStatusPublisher;
```

- [ ] **Step 2: Update field**

Change (line 44):
```java
private final ControlBusGateway controlBusGateway;
```

To:
```java
private final ExecutionStatusPublisher statusPublisher;
```

- [ ] **Step 3: Update StreamAssemblyHelper call**

Change (line 94-95):
```java
Flux<Message<?>> built =
    StreamAssemblyHelper.buildStreamWithContext(
        node, stream, timeout, tracker, controlBusGateway, context);
```

To:
```java
Flux<Message<?>> built =
    StreamAssemblyHelper.buildStreamWithContext(
        node, stream, timeout, tracker, statusPublisher, context);
```

- [ ] **Step 4: Format code with Spotless**

```bash
./gradlew spotlessApply
```

Expected: Code formatted without errors.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/infenia/yukta/service/orchestrator/strategy/ProcessorNodeAssemblerStrategy.java
git commit -m "refactor: replace ControlBusGateway with ExecutionStatusPublisher in ProcessorNodeAssemblerStrategy"
```

---

### Task 8: Update TriggerNodeAssemblerStrategy

**Files:**
- Modify: `core/src/main/java/com/infenia/yukta/service/orchestrator/strategy/TriggerNodeAssemblerStrategy.java`

- [ ] **Step 1: Read the file to understand its structure**

Expected: File has ControlBusGateway dependency similar to ProcessorNodeAssemblerStrategy.

- [ ] **Step 2: Update imports**

Replace:
```java
import com.infenia.yukta.service.control.gateway.ControlBusGateway;
```

With:
```java
import com.infenia.yukta.service.execution.status.ExecutionStatusPublisher;
```

- [ ] **Step 3: Update field**

Change:
```java
private final ControlBusGateway controlBusGateway;
```

To:
```java
private final ExecutionStatusPublisher statusPublisher;
```

- [ ] **Step 4: Update StreamAssemblyHelper call**

Find the line calling StreamAssemblyHelper.buildStreamWithContext and replace `controlBusGateway` with `statusPublisher`.

- [ ] **Step 5: Format code with Spotless**

```bash
./gradlew spotlessApply
```

Expected: Code formatted without errors.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/infenia/yukta/service/orchestrator/strategy/TriggerNodeAssemblerStrategy.java
git commit -m "refactor: replace ControlBusGateway with ExecutionStatusPublisher in TriggerNodeAssemblerStrategy"
```

---

### Task 9: Update TerminalNodeAssemblerStrategy

**Files:**
- Modify: `core/src/main/java/com/infenia/yukta/service/orchestrator/strategy/TerminalNodeAssemblerStrategy.java`

- [ ] **Step 1: Update imports**

Replace:
```java
import com.infenia.yukta.service.control.gateway.ControlBusGateway;
```

With:
```java
import com.infenia.yukta.service.execution.status.ExecutionStatusPublisher;
```

- [ ] **Step 2: Update field**

Change:
```java
private final ControlBusGateway controlBusGateway;
```

To:
```java
private final ExecutionStatusPublisher statusPublisher;
```

- [ ] **Step 3: Update StreamAssemblyHelper call**

Find and replace `controlBusGateway` with `statusPublisher` in the StreamAssemblyHelper.buildStreamWithContext call.

- [ ] **Step 4: Format code with Spotless**

```bash
./gradlew spotlessApply
```

Expected: Code formatted without errors.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/infenia/yukta/service/orchestrator/strategy/TerminalNodeAssemblerStrategy.java
git commit -m "refactor: replace ControlBusGateway with ExecutionStatusPublisher in TerminalNodeAssemblerStrategy"
```

---

### Task 10: Update HeartbeatBuilder

**Files:**
- Modify: `core/src/main/java/com/infenia/yukta/service/orchestrator/compiler/HeartbeatBuilder.java`

- [ ] **Step 1: Read the file to identify all ControlBusGateway usages**

Expected: File imports and uses ControlBusGateway, likely for heartbeat emission.

- [ ] **Step 2: Update imports**

Replace:
```java
import com.infenia.yukta.service.control.gateway.ControlBusGateway;
```

With:
```java
import com.infenia.yukta.service.execution.status.ExecutionStatusPublisher;
```

- [ ] **Step 3: Update field**

Change:
```java
private final ControlBusGateway controlBusGateway;
```

To:
```java
private final ExecutionStatusPublisher statusPublisher;
```

- [ ] **Step 4: Update heartbeat emission logic**

If HeartbeatBuilder emits heartbeats via the control bus gateway, update those calls to use statusPublisher.publishStatus() with appropriate ExecutionStatusEvent records.

- [ ] **Step 5: Format code with Spotless**

```bash
./gradlew spotlessApply
```

Expected: Code formatted without errors.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/infenia/yukta/service/orchestrator/compiler/HeartbeatBuilder.java
git commit -m "refactor: replace ControlBusGateway with ExecutionStatusPublisher in HeartbeatBuilder"
```

---

### Task 11: Update WorkflowCompiler

**Files:**
- Modify: `core/src/main/java/com/infenia/yukta/service/orchestrator/compiler/WorkflowCompiler.java`

- [ ] **Step 1: Update imports**

Replace:
```java
import com.infenia.yukta.service.control.gateway.ControlBusGateway;
```

With:
```java
import com.infenia.yukta.service.execution.status.ExecutionStatusPublisher;
```

- [ ] **Step 2: Update field**

Change:
```java
private final ControlBusGateway controlBusGateway;
```

To:
```java
private final ExecutionStatusPublisher statusPublisher;
```

- [ ] **Step 3: Update all references to controlBusGateway**

Replace all constructor/method parameters and field usages of `controlBusGateway` with `statusPublisher`.

- [ ] **Step 4: Format code with Spotless**

```bash
./gradlew spotlessApply
```

Expected: Code formatted without errors.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/infenia/yukta/service/orchestrator/compiler/WorkflowCompiler.java
git commit -m "refactor: replace ControlBusGateway with ExecutionStatusPublisher in WorkflowCompiler"
```

---

### Task 12: Update WorkflowPreparator

**Files:**
- Modify: `core/src/main/java/com/infenia/yukta/service/orchestrator/preparator/WorkflowPreparator.java`

- [ ] **Step 1: Update imports**

Replace:
```java
import com.infenia.yukta.service.control.gateway.ControlBusGateway;
```

With:
```java
import com.infenia.yukta.service.execution.status.ExecutionStatusPublisher;
```

- [ ] **Step 2: Update field**

Change:
```java
private final ControlBusGateway controlBusGateway;
```

To:
```java
private final ExecutionStatusPublisher statusPublisher;
```

- [ ] **Step 3: Update method calls and constructor parameters**

Replace all references to `controlBusGateway` with `statusPublisher`.

- [ ] **Step 4: Format code with Spotless**

```bash
./gradlew spotlessApply
```

Expected: Code formatted without errors.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/infenia/yukta/service/orchestrator/preparator/WorkflowPreparator.java
git commit -m "refactor: replace ControlBusGateway with ExecutionStatusPublisher in WorkflowPreparator"
```

---

### Task 13: Update SessionService

**Files:**
- Modify: `core/src/main/java/com/infenia/yukta/service/session/SessionService.java`

- [ ] **Step 1: Update imports**

Replace:
```java
import com.infenia.yukta.service.control.gateway.ControlBusGateway;
```

With:
```java
import com.infenia.yukta.service.execution.status.ExecutionStatusPublisher;
```

- [ ] **Step 2: Update field**

Change:
```java
private final ControlBusGateway controlBusGateway;
```

To:
```java
private final ExecutionStatusPublisher statusPublisher;
```

- [ ] **Step 3: Update all references**

Replace all usages of `controlBusGateway` with `statusPublisher`.

- [ ] **Step 4: Format code with Spotless**

```bash
./gradlew spotlessApply
```

Expected: Code formatted without errors.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/infenia/yukta/service/session/SessionService.java
git commit -m "refactor: replace ControlBusGateway with ExecutionStatusPublisher in SessionService"
```

---

### Task 14: Make DefaultControlBusGateway Implement ExecutionStatusPublisher

**Files:**
- Modify: `core/src/main/java/com/infenia/yukta/service/control/gateway/DefaultControlBusGateway.java`

- [ ] **Step 1: Add new import**

```java
import com.infenia.yukta.service.execution.status.ExecutionStatusPublisher;
import com.infenia.yukta.service.execution.status.ExecutionStatusEvent;
import reactor.core.publisher.Flux;
```

- [ ] **Step 2: Update class declaration to implement ExecutionStatusPublisher**

Change:
```java
public class DefaultControlBusGateway implements ControlBusGateway {
```

To:
```java
public class DefaultControlBusGateway implements ControlBusGateway, ExecutionStatusPublisher {
```

- [ ] **Step 3: Add field for status event subscription**

Add this field after existing fields:
```java
private final ExecutionStatusPublisher statusPublisher;
```

- [ ] **Step 4: Update constructor to inject ExecutionStatusPublisher**

Add the statusPublisher parameter:
```java
public DefaultControlBusGateway(
    final ControlBusService controlBusService,
    final DefaultTaskTrackerServiceService taskTracker,
    final ExecutionStatusPublisher statusPublisher) {
  this.controlBusService = controlBusService;
  this.taskTracker = taskTracker;
  this.statusPublisher = statusPublisher;
}
```

- [ ] **Step 5: Subscribe to status events on initialization**

Add a @PostConstruct method to consume status events:

```java
@PostConstruct
public void subscribeToStatusEvents() {
  statusPublisher
      .statusStream()
      .subscribe(
          event -> {
            // Forward status updates to task tracker
            taskTracker
                .updateTaskStatus(
                    event.executionId(),
                    event.nodeId(),
                    event.module(),
                    event.status(),
                    event.metadata() != null ? event.metadata() : Map.of())
                .subscribe();
          },
          error -> log.atError().setCause(error).log("Status event stream error"));
}
```

- [ ] **Step 6: Implement ExecutionStatusPublisher interface methods**

Add these two methods to the class:

```java
@Override
public Mono<Void> publishStatus(@NotNull final ExecutionStatusEvent event) {
  return statusPublisher.publishStatus(event);
}

@Override
public Flux<ExecutionStatusEvent> statusStream() {
  return statusPublisher.statusStream();
}
```

- [ ] **Step 7: Format code with Spotless**

```bash
./gradlew spotlessApply
```

Expected: Code formatted without errors.

- [ ] **Step 8: Run tests for DefaultControlBusGateway**

```bash
./gradlew :core:test --tests "com.infenia.yukta.service.control.gateway.DefaultControlBusGatewayTest" -v
```

Expected: Tests pass (may need minor fixture updates).

- [ ] **Step 9: Commit**

```bash
git add core/src/main/java/com/infenia/yukta/service/control/gateway/DefaultControlBusGateway.java
git commit -m "feat: make DefaultControlBusGateway implement ExecutionStatusPublisher"
```

---

### Task 15: Verify Circular Dependency is Broken

**Files:**
- No files to modify, verification only

- [ ] **Step 1: Run a full build to check for circular dependency errors**

```bash
./gradlew clean build -x test
```

Expected: Build succeeds without "circular package dependency" or similar errors from the Java compiler.

- [ ] **Step 2: Run all tests to ensure functionality**

```bash
./gradlew test
```

Expected: All tests pass (or only pre-existing failures, not new ones related to dependency changes).

- [ ] **Step 3: Check for any remaining imports of ControlBusGateway in orchestrator modules**

```bash
grep -r "import.*ControlBusGateway" \
  core/src/main/java/com/infenia/yukta/service/orchestrator/ \
  core/src/main/java/com/infenia/yukta/service/orchestrator/strategy/ \
  core/src/main/java/com/infenia/yukta/service/orchestrator/compiler/ \
  core/src/main/java/com/infenia/yukta/service/orchestrator/preparator/
```

Expected: No output (all imports should be ExecutionStatusPublisher now).

- [ ] **Step 4: Verify ExecutionStatusPublisher is in use**

```bash
grep -r "ExecutionStatusPublisher" \
  core/src/main/java/com/infenia/yukta/service/orchestrator/ \
  core/src/main/java/com/infenia/yukta/service/control/gateway/DefaultControlBusGateway.java
```

Expected: Multiple references in orchestrator modules and DefaultControlBusGateway.

- [ ] **Step 5: Commit verification results**

```bash
git log --oneline -15
```

Expected: See all 14 previous commits related to this refactoring. If verification passed, no additional commit needed.

---

## Summary

This plan breaks the circular dependency through a clean architectural pattern:

1. **Creates `ExecutionStatusPublisher`** - A lightweight interface for publishing status events
2. **Replaces direct ControlBusGateway dependencies** - All orchestrator modules now depend only on ExecutionStatusPublisher
3. **Makes ControlBusGateway implement ExecutionStatusPublisher** - The control bus subscribes to status events
4. **Unidirectional dependency flow** - Orchestrator → ExecutionStatusPublisher ← ControlBusGateway (no cycle)

The result is a cleaner separation of concerns where the orchestration layer doesn't need to know about control commands or the control bus—it just publishes what happened. The control bus can then react to those events as needed.

