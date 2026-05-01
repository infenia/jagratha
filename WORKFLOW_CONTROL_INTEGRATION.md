# WorkflowOrchestrator Integration Guide: Execution Control Refactoring

## Overview

This guide documents how to integrate the new `WorkflowControlApi` and `ExecutionControl` mechanisms into `WorkflowOrchestrator.java`. The refactoring achieves fine-grained, per-node control over workflow execution by applying pre-processing and post-processing control transformations at each node in the DAG.

## Architecture

```
Execution Control Hierarchy
├── Global Level
│   ├── immediateStopSink: Hard stop (cancel upstream immediately)
│   ├── safeStopSink: Drain & stop (complete streams after inflight work)
│   └── globalPauseValve: Pause all nodes via backpressure
├── Per-Node Level
│   ├── nodeImmediateStopSinks[nodeId]: Hard stop for specific node
│   ├── nodeSafeStopSinks[nodeId]: Drain & stop for specific node
│   ├── nodePauseValves[nodeId]: Per-node pause via backpressure
│   └── nodeSkipFlags[nodeId]: Bypass processor, pass through input
```

## Integration Steps

### Step 1: Update Execution Control Factory

**File**: `WorkflowOrchestrator.java` - `createExecutionControl()` method

Ensure all node maps and sinks are initialized for every node in the workflow:

```java
private ExecutionControl createExecutionControl(
    final String sessionId,
    final String workflowId,
    final String executionId,
    final PreparedWorkflow prepared) {
  
  final Map<String, Sinks.One<Void>> nodeImmediateSinks = new ConcurrentHashMap<>();
  final Map<String, Sinks.One<Void>> nodeSafeSinks = new ConcurrentHashMap<>();
  final Map<String, ReactiveControlValve> nodePauseValves = new ConcurrentHashMap<>();
  final Map<String, AtomicBoolean> nodeSkipFlags = new ConcurrentHashMap<>();

  // Initialize for every node in the workflow definition
  for (final WorkflowDefinition.Node node : prepared.workflow().nodes()) {
    nodeImmediateSinks.put(node.nodeId(), Sinks.one());
    nodeSafeSinks.put(node.nodeId(), Sinks.one());
    nodePauseValves.put(node.nodeId(), new ReactiveControlValve());
    nodeSkipFlags.put(node.nodeId(), new AtomicBoolean(false));
  }

  return new ExecutionControl(
      sessionId,
      workflowId,
      executionId,
      prepared,
      payload,
      Sinks.one(),
      Sinks.one(),
      new ReactiveControlValve(),
      nodeImmediateSinks,
      nodeSafeSinks,
      nodePauseValves,
      nodeSkipFlags);
}
```

### Step 2: Global Execution Termination

**File**: `WorkflowOrchestrator.java` - `execute()` method

Update the main execution termination condition to use both global stop sinks:

```java
private Mono<Void> executeTemplate(
    final ExecutionContext context,
    final ExecutionControl control,
    final PreparedWorkflow prepared) {
  
  // ... setup code ...

  final Mono<Void> executionTerminator = Mono.firstWithSignal(
      control.immediateStopSink().asMono(),
      control.safeStopSink().asMono()
  );

  return Flux.merge(/* all assembled node streams */)
      .takeUntilOther(executionTerminator)
      .then();
}
```

### Step 3: Trigger Node Assembly (Source Nodes)

**File**: `WorkflowOrchestrator.java` - `createTriggerAssembler()` method

Triggers are sources—they generate data but receive no upstream input. Apply safe stop only (triggers don't have pre-processing input):

```java
private Flux<?> createTriggerAssembler(
    final ExecutionContext context,
    final ExecutionControl control,
    final WorkflowDefinition.Node node,
    final TriggerPlugin trigger) {

  Flux<?> built = trigger.trigger(context);

  // 1. Apply Safe Stop (allow inflight work to complete, then stop producing)
  final Sinks.One<Void> nodeSafeSink = control.nodeSafeStopSinks().get(node.nodeId());
  if (nodeSafeSink != null) {
    built = built.takeUntilOther(nodeSafeSink.asMono());
  }

  // 2. Apply Post-Processing Controls (Immediate Stop & Pauses)
  built = control.applyPostProcessingControls(node.nodeId(), built);

  // 3. Apply execution context (correlation, tracing, etc.)
  built = contextBuilder.applyContextTo(built);

  return built;
}
```

### Step 4: Processor Node Assembly (Mid-Flight Nodes)

**File**: `WorkflowOrchestrator.java` - `createProcessorAssembler()` method

Processors handle mid-flight data. Use Pre/Post control paradigm to achieve fine-grained control:

```java
private Flux<?> createProcessorAssembler(
    final ExecutionContext context,
    final ExecutionControl control,
    final WorkflowDefinition.Node node,
    final ProcessorPlugin processor,
    final Flux<?> mergedInput) {

  // 1. Apply Pre-Processing Controls (Safe Stop & Skip Detection)
  Flux<?> safeInput = control.applyPreProcessingControls(node.nodeId(), mergedInput);

  // 2. Execute processor (or skip if flagged)
  Flux<?> built;
  final AtomicBoolean skipFlag = control.nodeSkipFlags().get(node.nodeId());
  
  if (skipFlag != null && skipFlag.get()) {
    // Node is skipped: pass through without processing
    log.atDebug()
        .addKeyValue("nodeId", node.nodeId())
        .log("Node marked for skip, bypassing processor");
    built = safeInput;
  } else {
    // Node is active: invoke processor
    built = processor.process(safeInput, node.config());
    
    // Handle blocking processors with virtual thread scheduler
    if (processor.isBlocking()) {
      built = built.subscribeOn(virtualThreadScheduler);
    }
  }

  // 3. Apply Post-Processing Controls (Immediate Stop & Pauses)
  built = control.applyPostProcessingControls(node.nodeId(), built);

  // 4. Apply execution context
  built = contextBuilder.applyContextTo(built);

  return built;
}
```

### Step 5: Terminal Node Assembly (Sink Nodes)

**File**: `WorkflowOrchestrator.java` - `createTerminalAssembler()` method

Terminals consume data. Apply pre-processing to handle safe stop before the terminal processes:

```java
private Flux<?> createTerminalAssembler(
    final ExecutionContext context,
    final ExecutionControl control,
    final WorkflowDefinition.Node node,
    final TerminalPlugin terminal,
    final Flux<?> mergedInput) {

  // 1. Apply Pre-Processing Controls (Safe Stop & Skip)
  Flux<?> safeInput = control.applyPreProcessingControls(node.nodeId(), mergedInput);

  // 2. Invoke terminal
  Flux<?> built = terminal.terminal(safeInput, node.config());

  // 3. Apply Post-Processing Controls (mainly for consistency)
  built = control.applyPostProcessingControls(node.nodeId(), built);

  // 4. Apply execution context
  built = contextBuilder.applyContextTo(built);

  return built;
}
```

## Control Flow Examples

### Example 1: Pause a Node

1. User/system calls: `workflowControlApi.pauseNode(executionId, "node-1")`
2. `DefaultWorkflowControlApi` retrieves the execution control and calls `nodePauseValves.get("node-1").pause()`
3. The pause valve is queried in `applyPostProcessingControls()` via `filterWhen(valve.allowPassage())`
4. Messages are held at the backpressure point until `valve.resume()` is called

### Example 2: Skip a Node

1. User/system calls: `workflowControlApi.skipNode(executionId, "node-1", true)`
2. `DefaultWorkflowControlApi` retrieves execution control and sets `nodeSkipFlags.get("node-1").set(true)`
3. Next time data arrives at "node-1", the processor check `if (skipFlag.get())` bypasses the processor
4. Data passes through unchanged

### Example 3: Stop Safely (Drain Then Stop)

1. User/system calls: `workflowControlApi.stopSafely(executionId)`
2. `DefaultWorkflowControlApi` completes `control.safeStopSink()`
3. All nodes' `applyPreProcessingControls()` checks `takeUntilOther(nodeSafeStopSink.asMono())`
4. New upstream input is cut off, but processors finish inflight work
5. Streams complete naturally after inflight work

### Example 4: Stop Immediately (Hard Cancel)

1. User/system calls: `workflowControlApi.stopImmediately(executionId)`
2. `DefaultWorkflowControlApi` completes `control.immediateStopSink()`
3. All nodes' `applyPostProcessingControls()` checks `takeUntilOther(immediateSink.asMono())`
4. Streams cancel immediately (no graceful drain)

## Integration Checklist

- [ ] **ExecutionControl Initialization**: All node maps initialized in `createExecutionControl()`
- [ ] **Global Termination**: `execute()` method uses both `immediateStopSink` and `safeStopSink`
- [ ] **Trigger Assembly**: Safe stop applied before post-processing
- [ ] **Processor Assembly**: Pre-processing → processor/skip → post-processing chain
- [ ] **Terminal Assembly**: Pre-processing → terminal → post-processing chain
- [ ] **WorkflowControlApi Integration**: `DefaultWorkflowControlApi` injected into orchestrator for mutation calls
- [ ] **Test Coverage**: Unit tests for each assembler method with control scenarios
- [ ] **Integration Tests**: End-to-end workflow execution with control operations

## Notes

- **Payload Immutability**: ExecutionControl's compact constructor enforces `payload = Map.copyOf(payload)` for safety
- **Thread Safety**: All node maps are `ConcurrentHashMap`; sinks are thread-safe via Project Reactor
- **Backpressure**: Pause valves use `filterWhen(valve.allowPassage())` to apply backpressure naturally
- **Skip Logic**: Skip flag is checked at processor invocation time; it does not retroactively affect inflight messages already past the decision point
- **No Global Pause on Triggers**: Triggers have no upstream to pause; global pause is enforced downstream via post-processing

## Migration Path

1. **Phase 1**: Add `ExecutionControl` with all maps but no control operations
2. **Phase 2**: Integrate pre/post-processing into assemblers without changing existing behavior
3. **Phase 3**: Wire `WorkflowControlApi` for mutation operations
4. **Phase 4**: Add test coverage and edge case handling
5. **Phase 5**: Deprecate old stop/pause mechanisms in favor of `WorkflowControlApi`
