# Unified Control Bus Implementation Progress

## Phase 1: Command Definitions ✅ COMPLETE

### Created Files:
1. **plugin-api**: `ExecutionControlCommand.java`
   - Marker interface for all execution control commands
   - 10 concrete record types:
     - `PauseWorkflowCommand`
     - `ResumeWorkflowCommand`
     - `PauseNodeCommand`
     - `ResumeNodeCommand`
     - `StopNodeCommand`
     - `SkipNodeCommand`
     - `EnableStepModeCommand`
     - `DisableStepModeCommand`
     - `StepNodeCommand`
     - `RestartCommand`
     - `RestartFromNodeCommand`

## Phase 2: Processors Implementation 🔄 IN PROGRESS

### Completed Processors:
1. ✅ `PauseWorkflowCommandProcessor` - pauses all nodes
2. ✅ `ResumeWorkflowCommandProcessor` - resumes all nodes
3. ✅ `PauseNodeCommandProcessor` - pauses specific node
4. ✅ `ResumeNodeCommandProcessor` - resumes specific node
5. ✅ `SkipNodeCommandProcessor` - marks node as skipped
6. ✅ `StopNodeCommandProcessor` - stops node with immediate/safe semantics

### Remaining Processors to Implement:
1. ⏳ `EnableStepModeCommandProcessor` - enable debug step mode
2. ⏳ `DisableStepModeCommandProcessor` - disable debug step mode
3. ⏳ `StepNodeCommandProcessor` - step to next element
4. ⏳ `RestartCommandProcessor` - restart entire execution
5. ⏳ `RestartFromNodeCommandProcessor` - restart from specific node

### Processor Pattern:
```java
@Slf4j
@Component
@RequiredArgsConstructor
public class XXXCommandProcessor implements ControlSignalProcessor {
  
  private final ExecutionControlRegistry registry;
  
  @Override
  public boolean canProcess(ControlCommand command) {
    return command instanceof XxxCommand;
  }
  
  @Override
  public Mono<WorkflowDirective> process(ControlCommand command) {
    XxxCommand cmd = (XxxCommand) command;
    return Mono.fromRunnable(() -> {
      ExecutionControl control = registry
          .findByExecutionId(cmd.executionId())
          .orElseThrow(...);
      // Apply control logic
      // Emit log
    });
  }
  
  @Override
  public int getPriority() {
    return 10; // or 15 for stop operations
  }
}
```

## Phase 3: Unified Control Gateway Interface & Implementation

### To Create:
1. **core**: `UnifiedControlBusGateway.java` (interface)
   - Methods: executeCommand, pauseWorkflow, resumeWorkflow, pauseNode, resumeNode,
     stopNode, skipNode, enableStepMode, disableStepMode, stepNode, 
     restart, restartFromNode, getActiveNodes, getLastHeartbeat, getLastStatistics

2. **core**: `DefaultUnifiedControlBusGateway.java` (implementation)
   - Wraps `ControlBusGateway` and `ExecutionControlRegistry`
   - Builds `ExecutionControlCommand` messages
   - Emits via ControlBus
   - Provides convenience methods

## Phase 4: Integration & Wiring

### To Update:
1. **core**: Update `DirectiveDispatcher` (if needed)
   - Verify it handles `ExecutionControlCommand` payloads
   - May need adaptation for no-directive commands (pause/resume/skip)

2. **boot**: Register all new processors as Spring beans
   - Auto-discovered via `@Component`

3. **web**: Update `ControlBusController` 
   - Replace `WorkflowControlApi` with `UnifiedControlBusGateway`

4. **ui**: Update `UiController`
   - Replace `WorkflowControlApi` with `UnifiedControlBusGateway`

## Phase 5: Deprecation

### To Implement:
1. Mark `WorkflowControlApi` as `@Deprecated`
2. Update CLAUDE.md documentation
3. Add migration guide

---

## Notes

### DirectiveDispatcher Adaptation
The existing `DirectiveDispatcher` currently:
1. Subscribes to control stream
2. Filters `ControlCommand` (the original record)
3. Calls `ControlSignalProcessor` to convert to `WorkflowDirective`
4. Applies directive

For `ExecutionControlCommand` handlers that don't produce a `WorkflowDirective`:
- Option 1: Create a no-op `WorkflowDirective` marker
- Option 2: Modify processors to not extend `ControlSignalProcessor`
- Option 3: Create new processor interface for direct-action commands

**Current approach**: Returning `null` from `Mono.fromRunnable()` - need to verify this works with DirectiveDispatcher pattern.

### Logging
- Using `log.atDebug()` pattern (structured logging)
- Include `executionId`, `nodeId` where relevant
- Include command-specific fields (e.g., `immediate`, `skip`, `reason`)

### Priority Levels
- Standard operations (pause, resume, skip): `priority = 10`
- Stop operations: `priority = 15` (higher priority)
- Future: Custom priorities for user-defined processors

---

## Testing Strategy

### Unit Tests:
1. Each processor: test canProcess, process, getPriority
2. Mock ExecutionControl and registry
3. Verify correct method calls on control valves/sinks

### Integration Tests:
1. Command flows through ControlBus
2. Execution state changes correctly
3. Concurrent commands are ordered

### Example Test:
```java
@Test
void testPauseWorkflowCommand() {
  ExecutionControl control = createTestControl();
  registry.register(control);
  
  StepVerifier.create(processor.process(new PauseWorkflowCommand(control.executionId())))
      .verifyComplete();
  
  verify(control.globalPauseValve()).pause();
}
```

---

## What's Next

1. **Implement remaining processors** (5 files)
2. **Create UnifiedControlBusGateway** interface & implementation
3. **Update DirectiveDispatcher** if needed
4. **Wire processors in Spring** (verify auto-discovery works)
5. **Integration testing**
6. **Update controllers & UI**
7. **Deprecate WorkflowControlApi**
8. **Documentation & migration guide**

