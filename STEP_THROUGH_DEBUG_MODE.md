# Step-Through Debug Mode: Node-Level Single-Element Execution

## Overview

Step-through debug mode enables fine-grained control over workflow execution: each node can be stepped one element at a time, allowing step-by-step debugging of data flow through the DAG.

## Architecture

### Control Flow

```
WorkflowControlApi.enableNodeStepMode(executionId, nodeId)
  ↓
DefaultWorkflowControlApi retrieves ExecutionControl
  ↓
Retrieves ReactiveControlValve for the node
  ↓
valve.enableStepMode() sets stepMode flag + pauses valve
  ↓
allowPassage() now checks stepSink instead of resumeSink
  ↓
WorkflowControlApi.stepNode() calls valve.step()
  ↓
valve.step() emits null to stepSink
  ↓
One element passes through, then blocks again
```

### Components

**ReactiveControlValve**:
- `stepMode: AtomicBoolean` — tracks step-through active state
- `stepSink: Many<Void>` — multicast sink for step signals (one signal = one element)
- `enableStepMode()` — activates step-through, pauses the valve
- `disableStepMode()` — deactivates step-through, returns to normal pause/resume
- `step()` — signals next element passage; returns false if step mode not active
- `isStepMode()` — checks current step-through state
- `allowPassage()` — checks step mode first; if enabled, awaits stepSink emission; otherwise checks paused state

**ExecutionControl**:
- `nodeStepModes: Map<String, AtomicBoolean>` — per-node step mode flags (keyed by nodeId)
- `nodeStepSinks: Map<String, Sinks.Many<Void>>` — per-node step signals (keyed by nodeId)

**WorkflowControlApi**:
```java
Mono<Void> enableNodeStepMode(String executionId, String nodeId);
Mono<Void> disableNodeStepMode(String executionId, String nodeId);
Mono<Void> stepNode(String executionId, String nodeId);
```

**DefaultWorkflowControlApi**:
- `enableNodeStepMode()` — retrieves valve, calls enableStepMode()
- `disableNodeStepMode()` — retrieves valve, calls disableStepMode()
- `stepNode()` — retrieves valve, calls step(), errors if step mode not active

### Initialization

In `WorkflowOrchestrator.createExecutionControl()`, for each node in the workflow:
```java
nodeStepModes.put(nodeId, new AtomicBoolean(false));
nodeStepSinks.put(nodeId, Sinks.many().multicast().onBackpressureBuffer());
```

The step sink uses **multicast** (not replay) so that old step signals don't replay to new subscriptions. The **onBackpressureBuffer** accumulates step signals during backpressure, preventing loss.

## Usage

### Enable step-through on a node

```java
api.enableNodeStepMode(executionId, "node-1").block();
```

Node "node-1" is now paused and in step mode. The first element attempting to pass through will block.

### Step to the next element

```java
api.stepNode(executionId, "node-1").block();
```

One element passes through. The node blocks again, waiting for the next step signal.

### Multiple steps

```java
for (int i = 0; i < 3; i++) {
  api.stepNode(executionId, "node-1").block();
  // Process one element
}
```

### Disable step-through

```java
api.disableNodeStepMode(executionId, "node-1").block();
```

Node returns to normal pause/resume behavior (not paused unless explicitly paused).

## Semantics

### Step Mode Active

When step mode is enabled on a node:
1. The valve is paused
2. `allowPassage()` returns `Mono.empty().then(stepSink.asFlux().next()).thenReturn(true)`
3. Each element must be explicitly stepped via `stepNode()`

### Step Signal

`stepNode()` calls `valve.step()` which emits `null` to the `stepSink`. The step sink is a `Many<Void>` multicast with backpressure buffering:
- **Multicast**: each `step()` signal is not replayed to future subscribers, preventing stale steps
- **Backpressure buffer**: if `allowPassage()` is not yet subscribed, the step signal accumulates

### Combining with Pause

Step mode and pause are independent:
- If step mode is **disabled**, the valve respects pause/resume normally
- If step mode is **enabled**, pause state is ignored (valve is automatically paused)
- Disabling step mode restores pause state (returns to not-paused unless pause was called separately)

## Testing

### ReactiveControlValveTest

- `testEnableStepMode()` — verifies step mode and paused state
- `testDisableStepMode()` — verifies step mode disabled and not paused
- `testStepWithoutStepMode()` — step() returns false when step mode inactive
- `testStepWithStepMode()` — step() returns true when active
- `testStepModeAllowsOneElementThenBlocks()` — full flow: enable, step 1, await block, step 2, etc.
- `testDisableStepModeReturnsToNormal()` — disabling restores normal pause/resume

### DefaultWorkflowControlApiTest

- `testEnableNodeStepMode()` — API call enables step mode on target node
- `testDisableNodeStepMode()` — API call disables step mode
- `testStepNode()` — API call emits step signal
- `testStepNodeNotInStepMode()` — errors if step called on non-step-mode node

## Constraints & Limitations

1. **One node at a time**: Step-through is per-node. Multiple nodes can be in step mode independently, but synchronizing across multiple nodes requires external orchestration (e.g., waiting for a node to emit before stepping the next).

2. **No retroactive steps**: If you call `stepNode()` before `allowPassage()` subscribes, the step signal accumulates in the multicast buffer (due to backpressure buffering). This is safe but means extra calls to `stepNode()` don't "skip ahead"—only the next subscribed allowPassage gets the signal.

3. **Step mode state leaks on node skip**: If a node is skipped (via `skipNode()`) and in step mode, the skip bypasses the processor, but the pause valve still blocks at post-processing. This is by design (pause applies to output). If you need to skip without blocking, disable step mode first or don't enable step mode on skipped nodes.

4. **Terminal nodes**: Terminals return `Mono<Void>` (not `Flux<Message<?>>`), so post-processing pause doesn't apply. Pre-processing (via step mode on the terminal's input source) still works.

## Future Enhancements

- **Multi-node synchronization**: A `stepGroup(executionId, Set<nodeId>)` method that steps multiple nodes in sync
- **Conditional stepping**: Step only if a predicate matches (e.g., "step while output size < 10")
- **Replay safeguards**: Distinguish between "step already consumed" and "step pending" to prevent accidental re-emission
