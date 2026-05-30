# Uniform Control Bus Architecture Design

## Vision: CPU-Like Control Bus

Transform the control system into a **single unified channel** where:
- **ALL signals** (heartbeats, stats, commands) flow through ControlBus
- **ALL execution control** (pause, stop, skip, step) is command-based via ControlBus
- **Single API** for inspection and control
- **No direct access** to ExecutionControl or WorkflowControlApi from external systems

### Architecture Analogy (CPU)
```
┌──────────────────────────────────────────────────┐
│           Workflow Execution Cores                │
│  (Node Processors, Trigger, Terminal Plugins)    │
└──────────────┬───────────────────────────────────┘
               │ emit heartbeats, stats
               ▼
┌──────────────────────────────────────────────────┐
│            UNIFIED CONTROL BUS (Memory Bus)       │
│  • Single pub/sub channel for all signals        │
│  • Routes commands to directive handlers         │
│  • Provides unified query/inspection API         │
│  • Manages execution lifecycle                   │
└──────────────┬───────────────────────────────────┘
               │ control signals (stop, pause, skip)
               ▼
┌──────────────────────────────────────────────────┐
│    Controllers, UI, External Systems             │
│  (All control via ControlBus commands)           │
└──────────────────────────────────────────────────┘
```

---

## Current Issues

### 1. Dual Access Paths
- **DirectPath**: `WorkflowControlApi` → directly manipulates `ExecutionControl` sinks
- **IndirectPath**: `ControlBusGateway.emit(ControlCommand)` → `DirectiveDispatcher` → manipulates sinks
- **Result**: Inconsistent state, race conditions, observability gaps

### 2. Asymmetric Communication
- **From Execution**: Flows through ControlBus (heartbeats, stats)
- **To Execution**: Bypasses ControlBus (WorkflowControlApi direct access)
- **Result**: No centralized audit trail, hard to trace control flow

### 3. Multiple Responsibility Layers
- `ExecutionControl`: Holds sinks/valves
- `DefaultWorkflowControlApi`: Manipulates sinks directly
- `DirectiveDispatcher`: Converts signals to directives, then manipulates sinks
- `DefaultControlBusGateway`: Routes signals
- **Result**: Scattered control logic, hard to maintain

---

## Proposed Solution

### Phase 1: Establish Control Command Protocol
Convert **all execution control** into **ControlCommand signals**:

```java
// plugin-api: New command types extending ControlCommand

public sealed interface ExecutionControlCommand extends ControlCommand
    permits PauseWorkflowCommand, ResumeWorkflowCommand, 
            PauseNodeCommand, ResumeNodeCommand,
            StopNodeCommand, RestartCommand,
            SkipNodeCommand, EnableStepModeCommand,
            DisableStepModeCommand, StepNodeCommand {
  
  String executionId();
}

// Specific command types
public record PauseWorkflowCommand(String executionId) 
    implements ExecutionControlCommand {
  @Override public String type() { return "execution.pause-workflow"; }
}

public record ResumeWorkflowCommand(String executionId) 
    implements ExecutionControlCommand {
  @Override public String type() { return "execution.resume-workflow"; }
}

public record PauseNodeCommand(String executionId, String nodeId) 
    implements ExecutionControlCommand {
  @Override public String type() { return "execution.pause-node"; }
}

public record ResumeNodeCommand(String executionId, String nodeId) 
    implements ExecutionControlCommand {
  @Override public String type() { return "execution.resume-node"; }
}

public record StopNodeCommand(
    String executionId, String nodeId, boolean immediate, String reason) 
    implements ExecutionControlCommand {
  @Override public String type() { return "execution.stop-node"; }
}

public record SkipNodeCommand(String executionId, String nodeId, boolean skip) 
    implements ExecutionControlCommand {
  @Override public String type() { return "execution.skip-node"; }
}

public record EnableStepModeCommand(String executionId, String nodeId) 
    implements ExecutionControlCommand {
  @Override public String type() { return "execution.enable-step-mode"; }
}

public record StepNodeCommand(String executionId, String nodeId) 
    implements ExecutionControlCommand {
  @Override public String type() { return "execution.step-node"; }
}

public record RestartCommand(String executionId, String fromNodeId) 
    implements ExecutionControlCommand {
  @Override public String type() { return "execution.restart"; }
}
```

### Phase 2: Control Signal Processors
Implement processors for each command type:

```java
// core: Processor for pause workflow command

@Component
@RequiredArgsConstructor
public class PauseWorkflowCommandProcessor implements ControlSignalProcessor {
  
  private final ExecutionControlRegistry registry;
  
  @Override
  public boolean canProcess(ControlCommand command) {
    return command instanceof PauseWorkflowCommand;
  }
  
  @Override
  public Mono<WorkflowDirective> process(ControlCommand command) {
    PauseWorkflowCommand pause = (PauseWorkflowCommand) command;
    return Mono.fromSupplier(() -> {
      ExecutionControl control = registry
          .findByExecutionId(pause.executionId())
          .orElseThrow(() -> new IllegalArgumentException(
              "Execution not found: " + pause.executionId()));
      control.globalPauseValve().pause();
      return null; // Signal complete (no directive needed)
    });
  }
}

// Similarly: ResumeWorkflowCommandProcessor, PauseNodeCommandProcessor, etc.
```

### Phase 3: Unified Control Gateway
Single entry point replacing both `WorkflowControlApi` and direct `ControlBusGateway` usage:

```java
/**
 * Unified execution control interface.
 * All execution control flows through ControlBus commands.
 */
public interface UnifiedControlBusGateway {
  
  // Emit any control command
  <T extends ExecutionControlCommand> Mono<Void> executeCommand(Message<T> command);
  
  // Convenience methods (build command + emit)
  Mono<Void> pauseWorkflow(String executionId);
  Mono<Void> resumeWorkflow(String executionId);
  Mono<Void> pauseNode(String executionId, String nodeId);
  Mono<Void> resumeNode(String executionId, String nodeId);
  Mono<Void> stopNode(String executionId, String nodeId, boolean immediate, String reason);
  Mono<Void> skipNode(String executionId, String nodeId, boolean skip);
  Mono<Void> enableStepMode(String executionId, String nodeId);
  Mono<Void> disableStepMode(String executionId, String nodeId);
  Mono<Void> stepNode(String executionId, String nodeId);
  Mono<String> restart(String executionId, String fromNodeId);
  
  // Query/inspect
  List<String> getActiveNodes();
  List<String> getActiveNodes(String workflowId);
  Message<?> getLastHeartbeat(String workflowId, String nodeId);
  Message<?> getLastStatistics(String workflowId, String nodeId);
  Mono<ExecutionStatus> getExecutionStatus(String executionId);
}
```

### Phase 4: Unified Implementation
```java
@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultUnifiedControlBusGateway implements UnifiedControlBusGateway {
  
  private final ControlBusGateway controlBusGateway;
  private final ExecutionControlRegistry registry;
  
  @Override
  public <T extends ExecutionControlCommand> Mono<Void> executeCommand(
      Message<T> command) {
    return controlBusGateway.emit(command);
  }
  
  @Override
  public Mono<Void> pauseWorkflow(String executionId) {
    Message<PauseWorkflowCommand> msg = Message.builder()
        .payload(new PauseWorkflowCommand(executionId))
        .executionId(executionId)
        .sourceNodeId("CONTROL_BUS") // Internal command
        .priority(100) // High priority
        .build();
    return executeCommand(msg);
  }
  
  @Override
  public Mono<Void> stopNode(String executionId, String nodeId, 
      boolean immediate, String reason) {
    Message<StopNodeCommand> msg = Message.builder()
        .payload(new StopNodeCommand(executionId, nodeId, immediate, reason))
        .executionId(executionId)
        .sourceNodeId("CONTROL_BUS")
        .priority(101) // Higher than pause
        .build();
    return executeCommand(msg);
  }
  
  @Override
  public Mono<String> restart(String executionId, String fromNodeId) {
    Message<RestartCommand> msg = Message.builder()
        .payload(new RestartCommand(executionId, fromNodeId))
        .executionId(executionId)
        .sourceNodeId("CONTROL_BUS")
        .priority(102) // Highest priority
        .build();
    
    return executeCommand(msg)
        .then(Mono.fromSupplier(() -> UUID.randomUUID().toString()));
  }
  
  @Override
  public List<String> getActiveNodes() {
    return controlBusGateway.getActiveNodes();
  }
  
  @Override
  public Mono<ExecutionStatus> getExecutionStatus(String executionId) {
    return Mono.fromSupplier(() -> {
      ExecutionControl control = registry
          .findByExecutionId(executionId)
          .orElseThrow();
      
      return ExecutionStatus.builder()
          .executionId(executionId)
          .workflowId(control.workflowId())
          .status("RUNNING")
          .build();
    });
  }
}
```

### Phase 5: Deprecate Direct Access
```java
@Deprecated(since = "2.0", forRemoval = true)
public interface WorkflowControlApi {
  // Mark all methods as deprecated
  @Deprecated
  Mono<Void> stopImmediately(String executionId);
  // ... rest of methods
}
```

Clients must migrate to `UnifiedControlBusGateway`.

---

## Benefits

| Issue | Resolution |
|-------|-----------|
| **Dual access paths** | Single channel through ControlBus |
| **Race conditions** | All control via ordered message queue |
| **Audit trail** | Every command flows through ControlBus (loggable, queryable) |
| **Observability** | All signals and commands in one place |
| **Testability** | Mock ControlBus for testing, not ExecutionControl sinks |
| **Extensibility** | Add new commands/processors without touching DirectiveDispatcher |
| **Consistency** | Command semantics enforced by processors |

---

## Migration Path

### Step 1: Add Command Types (Non-breaking)
- Add sealed interface `ExecutionControlCommand`
- Add concrete command types
- No changes to existing code

### Step 2: Implement Processors
- For each command type, create a `ControlSignalProcessor`
- Reuse existing DirectiveDispatcher logic
- No changes to public APIs

### Step 3: Create Unified Gateway
- Implement `UnifiedControlBusGateway` wrapping `ControlBusGateway`
- Provide convenience methods
- Keep `WorkflowControlApi` unchanged

### Step 4: Wire in Dependencies
- Register all new processors as Spring beans
- Inject `UnifiedControlBusGateway` in controllers
- Keep both APIs side-by-side

### Step 5: Deprecation & Migration
- Mark `WorkflowControlApi` as deprecated
- Update documentation
- Gradual client migration

### Step 6: Cleanup (v3.0)
- Remove `WorkflowControlApi`
- Merge `DirectiveDispatcher` into unified bus layer
- Simplify architecture

---

## Implementation Sequence

1. **Define commands** (plugin-api)
   - New sealed interface `ExecutionControlCommand`
   - Concrete record types

2. **Create processors** (core)
   - PauseWorkflowCommandProcessor
   - ResumeWorkflowCommandProcessor
   - PauseNodeCommandProcessor
   - ... (one per command type)

3. **Implement unified gateway** (core)
   - `UnifiedControlBusGateway` interface
   - `DefaultUnifiedControlBusGateway` implementation

4. **Wire dependencies** (boot)
   - Register new processors
   - Inject gateway in controllers

5. **Update controllers** (web, ui)
   - Replace WorkflowControlApi with UnifiedControlBusGateway
   - Use new convenience methods

6. **Add integration tests**
   - Verify command flow through ControlBus
   - Verify execution state changes

---

## Example: Complete Flow (Pause Workflow)

```
User clicks "Pause" in UI
  ↓
UiController.pauseWorkflow(executionId)
  ↓
UnifiedControlBusGateway.pauseWorkflow(executionId)
  ├─ creates Message<PauseWorkflowCommand>
  └─ calls controlBusGateway.emit()
    ↓
ControlBusService.emit()
  ├─ adds to controlSink
  └─ batches with other signals
    ↓
ControlBusService.handleControlBatch()
  ├─ sorts by priority
  └─ for each signal: find handler & process
    ↓
PauseWorkflowCommandProcessor.process()
  ├─ finds ExecutionControl from registry
  ├─ calls control.globalPauseValve().pause()
  └─ completes
    ↓
Node output streams apply backpressure
  ├─ elements slow down
  └─ execution appears paused to external observer
```

**All of this is traceable through ControlBus** — you can log, audit, replay, or analyze any pause command.

---

## Testing Strategy

```java
@Test
void testPauseWorkflowCommand() {
  // Setup
  ExecutionControl control = createMockControl();
  registry.register(control);
  
  // Act
  gateway.pauseWorkflow(control.executionId()).block();
  
  // Assert
  verify(control.globalPauseValve()).pause();
}

@Test
void testCommandRoutingThroughBus() {
  // Verify command appears on control bus
  StepVerifier.create(controlBusService.getControlStream())
      .expectNextMatches(msg -> msg.getPayload() instanceof PauseWorkflowCommand)
      .verifyComplete();
}
```

---

## Open Questions for Discussion

1. **Priority levels** — Should different command types have different priorities?
   - Example: StopNode (high) vs PauseNode (medium)

2. **Command acknowledgment** — Should processors return confirmation?
   - Useful for UI feedback ("pause initiated")

3. **Command queuing** — What happens if 2 conflicting commands arrive?
   - Example: PauseNode + StopNode for same node

4. **Execution status** — Should `ExecutionStatus` be a control signal?
   - Or query-only via separate API?

5. **Backwards compatibility** — Phase-in WorkflowControlApi removal?
   - Or hard cutover in next major version?
