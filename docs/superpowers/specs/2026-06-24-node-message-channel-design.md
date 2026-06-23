# NodeMessageChannel — Pluggable Message Transport Design

**Date:** 2026-06-24  
**Status:** Approved  
**Scope:** Interface + DirectNodeMessageChannel passthrough. External transports (DB, queue, Redis) are future work.

---

## Problem

Messages between plugins flow entirely in-memory through Reactor `Flux` pipelines. The three assembler strategies (`TriggerNodeAssemblerStrategy`, `ProcessorNodeAssemblerStrategy`, `TerminalNodeAssemblerStrategy`) call plugin methods directly with live `Flux` references. There is no seam to substitute a different transport without changing the plugin API contracts.

---

## Goal

Introduce a transport abstraction at each inter-node boundary so that future implementations can substitute DB, message queue, or Redis-backed channels without modifying plugin interfaces or the plugin API module.

**Non-goals:**
- No external transport implementations in this change.
- No serialization contract (deferred to whichever transport needs it first).
- No per-node or per-workflow channel configuration (future work).

---

## Design

### Interfaces — `plugin-api` module

Package: `com.infenia.yukta.plugin.channel`

#### `NodeMessageChannel`

Single interface covering both directions. Assembler strategies only call the side relevant to the plugin type; the other side is unused for that type.

```java
public interface NodeMessageChannel {
    Flux<Message<?>> inbound(String nodeId, String executionId, Flux<Message<?>> upstream);
    Flux<Message<?>> outbound(String nodeId, String executionId, Flux<Message<?>> pluginOutput);
}
```

- **`inbound`** — wraps the merged upstream `Flux` before the plugin receives it. Used by Processor and Terminal strategies.
- **`outbound`** — wraps the plugin's output `Flux` before it reaches downstream nodes and `applyLoggingAndBroadcasting`. Used by Trigger and Processor strategies.

#### `NodeMessageChannelProvider`

```java
public interface NodeMessageChannelProvider {
    NodeMessageChannel channelFor(WorkflowNode node);
}
```

Called once per node assembly. Returns the channel instance to use for that node. The default implementation always returns the same singleton direct channel.

---

### Default Implementation — `core` module

Package: `com.infenia.yukta.service.channel`

#### `DirectNodeMessageChannel`

Zero-overhead passthrough. Both methods return the input `Flux` unchanged.

```java
public class DirectNodeMessageChannel implements NodeMessageChannel {
    public Flux<Message<?>> inbound(String nodeId, String executionId, Flux<Message<?>> upstream) {
        return upstream;
    }
    public Flux<Message<?>> outbound(String nodeId, String executionId, Flux<Message<?>> pluginOutput) {
        return pluginOutput;
    }
}
```

#### `DirectNodeMessageChannelProvider`

Spring `@Component` annotated with `@ConditionalOnMissingBean(NodeMessageChannelProvider.class)`. Holds a single `DirectNodeMessageChannel` instance and returns it for every node.

---

### Wiring — Assembler Strategies

`NodeMessageChannelProvider` is injected via `@RequiredArgsConstructor` into all three strategies.

#### `TriggerNodeAssemblerStrategy` (outbound only)

Current (line 89):
```java
Flux<Message<?>> stream = trigger.start(node.config());
```
After:
```java
Flux<Message<?>> stream = channel.outbound(node.nodeId(), context.executionId(),
    trigger.start(node.config()));
```

#### `ProcessorNodeAssemblerStrategy` (inbound + outbound)

Current (line 108):
```java
stream = processor.process(safeInput, node.config());
```
After:
```java
Flux<Message<?>> channelInput = channel.inbound(node.nodeId(), context.executionId(), safeInput);
stream = channel.outbound(node.nodeId(), context.executionId(),
    processor.process(channelInput, node.config()));
```

The skip-flag path (line 103) bypasses the plugin but still passes `safeInput` through, so only `outbound` wraps the passthrough stream in that branch (no `inbound` needed since no plugin receives it).

#### `TerminalNodeAssemblerStrategy` (inbound only)

Current (line 128):
```java
Mono<Void> completion = terminal.consume(inputToTerminal, node.config());
```
After:
```java
Flux<Message<?>> channelInput = channel.inbound(node.nodeId(), context.executionId(), inputToTerminal);
Mono<Void> completion = terminal.consume(channelInput, node.config());
```

---

## Data Flow (Before vs After)

**Before:**
```
mergeParentStreams() → safeInput → processor.process() → applyLoggingAndBroadcasting()
```

**After:**
```
mergeParentStreams() → safeInput → channel.inbound() → processor.process() → channel.outbound() → applyLoggingAndBroadcasting()
```

For the direct channel, `channel.inbound()` and `channel.outbound()` are identity functions, so behaviour is identical.

---

## Tests

### `DirectNodeMessageChannel`
- `inbound` returns the exact same `Flux` instance (passthrough)
- `outbound` returns the exact same `Flux` instance (passthrough)
- Verifies no transformation, no buffering, no side-effects

### `DirectNodeMessageChannelProvider`
- `channelFor(node)` returns a `DirectNodeMessageChannel`
- Multiple calls return the same instance (singleton reuse)

### Assembler strategy tests (existing test updates)
- Each strategy test receives a mock `NodeMessageChannelProvider`
- Verify `channelFor(node)` is called once per node assembly
- Verify the correct side (inbound/outbound) is called per strategy type
- Verify direct channel integration: assembled stream behaviour is unchanged from pre-channel baseline

---

## Future Extension Points

When adding an external transport, a developer must:
1. Implement `NodeMessageChannel` with the desired transport logic (inbound serialises to transport, returns consumer Flux; outbound does the same for plugin output)
2. Implement `NodeMessageChannelProvider` as a Spring `@Component` (auto-disables `DirectNodeMessageChannelProvider` via `@ConditionalOnMissingBean`)
3. Add a serialisation strategy for `Message<?>` payloads (first transport to be built will define this contract)
4. Wire idempotency via the existing `IdempotencyStore` in `plugin-api` for at-least-once safety

No changes to plugin interfaces, assembler strategies, or this module are needed.

---

## Files Changed

| Action | Path |
|--------|------|
| New | `plugin-api/src/main/java/com/infenia/yukta/plugin/channel/NodeMessageChannel.java` |
| New | `plugin-api/src/main/java/com/infenia/yukta/plugin/channel/NodeMessageChannelProvider.java` |
| New | `core/src/main/java/com/infenia/yukta/service/channel/DirectNodeMessageChannel.java` |
| New | `core/src/main/java/com/infenia/yukta/service/channel/DirectNodeMessageChannelProvider.java` |
| Modified | `core/src/main/java/com/infenia/yukta/service/orchestrator/strategy/TriggerNodeAssemblerStrategy.java` |
| Modified | `core/src/main/java/com/infenia/yukta/service/orchestrator/strategy/ProcessorNodeAssemblerStrategy.java` |
| Modified | `core/src/main/java/com/infenia/yukta/service/orchestrator/strategy/TerminalNodeAssemblerStrategy.java` |
| New | `plugin-api/src/test/java/com/infenia/yukta/plugin/channel/NodeMessageChannelTest.java` |
| New | `core/src/test/java/com/infenia/yukta/service/channel/DirectNodeMessageChannelTest.java` |
| New | `core/src/test/java/com/infenia/yukta/service/channel/DirectNodeMessageChannelProviderTest.java` |
