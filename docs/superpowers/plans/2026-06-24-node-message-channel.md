# NodeMessageChannel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Introduce a `NodeMessageChannel` abstraction that decouples message passing to/from plugins from the direct Flux wiring, making the transport pluggable without changing any plugin interface.

**Architecture:** Two interfaces (`NodeMessageChannel`, `NodeMessageChannelProvider`) live in `plugin-api`. A `DirectNodeMessageChannel` passthrough implementation and its provider live in `core`. The three assembler strategies (`Trigger`, `Processor`, `Terminal`) are updated to obtain a channel from the provider and route their respective inbound/outbound Flux through it. The default Spring bean is conditional (`@ConditionalOnMissingBean`) so future transports can replace it by declaring their own provider bean.

**Tech Stack:** Java 25, Project Reactor (`Flux`), Spring Boot 4 (`@ConditionalOnMissingBean`), JUnit 5 + Mockito + AssertJ.

## Global Constraints

- Every Java file must have the Apache License 2.0 header: `Copyright 2026 Infenia Private Limited`
- Google Java Style (2-space indent, 100-char line limit) — run `./gradlew spotlessApply` before commit
- `plugin-api` depends on `:messaging` and `spring-boot-starter-webflux`; it does NOT depend on `:core`. `WorkflowNode` is in `core`, so `NodeMessageChannelProvider` must use `String nodeId, Map<String, Object> config` — not `WorkflowNode`
- `@RequiredArgsConstructor` auto-generates constructors from `final` fields in declaration order — when adding a field to a strategy, it changes the constructor signature
- Run `./gradlew :plugin-api:test :core:test` after each task to verify

---

## File Map

| Action   | Path |
|----------|------|
| Create   | `plugin-api/src/main/java/com/infenia/yukta/plugin/channel/NodeMessageChannel.java` |
| Create   | `plugin-api/src/main/java/com/infenia/yukta/plugin/channel/NodeMessageChannelProvider.java` |
| Create   | `core/src/main/java/com/infenia/yukta/service/channel/DirectNodeMessageChannel.java` |
| Create   | `core/src/main/java/com/infenia/yukta/service/channel/DirectNodeMessageChannelProvider.java` |
| Create   | `core/src/test/java/com/infenia/yukta/service/channel/DirectNodeMessageChannelTest.java` |
| Create   | `core/src/test/java/com/infenia/yukta/service/channel/DirectNodeMessageChannelProviderTest.java` |
| Modify   | `core/src/main/java/com/infenia/yukta/service/orchestrator/strategy/TriggerNodeAssemblerStrategy.java` |
| Modify   | `core/src/test/java/com/infenia/yukta/service/orchestrator/strategy/TriggerNodeAssemblerStrategyTest.java` |
| Modify   | `core/src/main/java/com/infenia/yukta/service/orchestrator/strategy/ProcessorNodeAssemblerStrategy.java` |
| Modify   | `core/src/test/java/com/infenia/yukta/service/orchestrator/strategy/ProcessorNodeAssemblerStrategyTest.java` |
| Modify   | `core/src/main/java/com/infenia/yukta/service/orchestrator/strategy/TerminalNodeAssemblerStrategy.java` |
| Modify   | `core/src/test/java/com/infenia/yukta/service/orchestrator/strategy/TerminalNodeAssemblerStrategyTest.java` |

---

## Task 1: Channel Interfaces + Direct Implementation

**Files:**
- Create: `plugin-api/src/main/java/com/infenia/yukta/plugin/channel/NodeMessageChannel.java`
- Create: `plugin-api/src/main/java/com/infenia/yukta/plugin/channel/NodeMessageChannelProvider.java`
- Create: `core/src/main/java/com/infenia/yukta/service/channel/DirectNodeMessageChannel.java`
- Create: `core/src/main/java/com/infenia/yukta/service/channel/DirectNodeMessageChannelProvider.java`
- Create: `core/src/test/java/com/infenia/yukta/service/channel/DirectNodeMessageChannelTest.java`
- Create: `core/src/test/java/com/infenia/yukta/service/channel/DirectNodeMessageChannelProviderTest.java`

**Interfaces:**
- Produces:
  - `NodeMessageChannel.inbound(String nodeId, String executionId, Flux<Message<?>> upstream): Flux<Message<?>>`
  - `NodeMessageChannel.outbound(String nodeId, String executionId, Flux<Message<?>> pluginOutput): Flux<Message<?>>`
  - `NodeMessageChannelProvider.channelFor(String nodeId, Map<String, Object> config): NodeMessageChannel`

- [ ] **Step 1: Write failing tests for DirectNodeMessageChannel**

Create `core/src/test/java/com/infenia/yukta/service/channel/DirectNodeMessageChannelTest.java`:

```java
/*
 * Copyright 2026 Infenia Private Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.infenia.yukta.service.channel;

import static org.assertj.core.api.Assertions.assertThat;

import com.infenia.yukta.message.DefaultMessage;
import com.infenia.yukta.message.Message;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

class DirectNodeMessageChannelTest {

  private final DirectNodeMessageChannel channel = new DirectNodeMessageChannel();

  @Test
  void inbound_returnsUpstreamFluxUnchanged() {
    final Message<?> msg = DefaultMessage.create(UUID.randomUUID(), "payload");
    final Flux<Message<?>> upstream = Flux.just(msg);

    final Flux<Message<?>> result = channel.inbound("node-1", "exec-1", upstream);

    assertThat(result).isSameAs(upstream);
  }

  @Test
  void outbound_returnsPluginOutputFluxUnchanged() {
    final Message<?> msg = DefaultMessage.create(UUID.randomUUID(), "payload");
    final Flux<Message<?>> pluginOutput = Flux.just(msg);

    final Flux<Message<?>> result = channel.outbound("node-1", "exec-1", pluginOutput);

    assertThat(result).isSameAs(pluginOutput);
  }

  @Test
  void inbound_nullNodeId_returnsFluxUnchanged() {
    final Flux<Message<?>> upstream = Flux.empty();

    final Flux<Message<?>> result = channel.inbound(null, "exec-1", upstream);

    assertThat(result).isSameAs(upstream);
  }

  @Test
  void outbound_nullExecutionId_returnsFluxUnchanged() {
    final Flux<Message<?>> pluginOutput = Flux.empty();

    final Flux<Message<?>> result = channel.outbound("node-1", null, pluginOutput);

    assertThat(result).isSameAs(pluginOutput);
  }
}
```

- [ ] **Step 2: Write failing tests for DirectNodeMessageChannelProvider**

Create `core/src/test/java/com/infenia/yukta/service/channel/DirectNodeMessageChannelProviderTest.java`:

```java
/*
 * Copyright 2026 Infenia Private Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.infenia.yukta.service.channel;

import static org.assertj.core.api.Assertions.assertThat;

import com.infenia.yukta.plugin.channel.NodeMessageChannel;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DirectNodeMessageChannelProviderTest {

  private final DirectNodeMessageChannelProvider provider = new DirectNodeMessageChannelProvider();

  @Test
  void channelFor_returnsDirectNodeMessageChannel() {
    final NodeMessageChannel channel = provider.channelFor("node-1", Map.of());

    assertThat(channel).isInstanceOf(DirectNodeMessageChannel.class);
  }

  @Test
  void channelFor_multipleCalls_returnSameInstance() {
    final NodeMessageChannel first = provider.channelFor("node-1", Map.of());
    final NodeMessageChannel second = provider.channelFor("node-2", Map.of("key", "value"));

    assertThat(first).isSameAs(second);
  }

  @Test
  void channelFor_withNullConfig_returnsChannel() {
    final NodeMessageChannel channel = provider.channelFor("node-1", null);

    assertThat(channel).isNotNull();
  }
}
```

- [ ] **Step 3: Run tests to confirm compile failure**

```bash
./gradlew :core:test --tests "com.infenia.yukta.service.channel.*" 2>&1 | tail -20
```

Expected: compilation error — `DirectNodeMessageChannel` and `DirectNodeMessageChannelProvider` do not exist yet.

- [ ] **Step 4: Create NodeMessageChannel interface**

Create `plugin-api/src/main/java/com/infenia/yukta/plugin/channel/NodeMessageChannel.java`:

```java
/*
 * Copyright 2026 Infenia Private Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.infenia.yukta.plugin.channel;

import com.infenia.yukta.message.Message;
import reactor.core.publisher.Flux;

/** Transport abstraction between inter-node boundaries. */
public interface NodeMessageChannel {

  /**
   * Wraps the upstream Flux before the plugin receives it. Called by Processor and Terminal
   * assembler strategies.
   */
  Flux<Message<?>> inbound(String nodeId, String executionId, Flux<Message<?>> upstream);

  /**
   * Wraps the plugin's output Flux before it reaches downstream nodes. Called by Trigger and
   * Processor assembler strategies.
   */
  Flux<Message<?>> outbound(String nodeId, String executionId, Flux<Message<?>> pluginOutput);
}
```

- [ ] **Step 5: Create NodeMessageChannelProvider interface**

Create `plugin-api/src/main/java/com/infenia/yukta/plugin/channel/NodeMessageChannelProvider.java`:

```java
/*
 * Copyright 2026 Infenia Private Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.infenia.yukta.plugin.channel;

import java.util.Map;

/** Factory for obtaining the NodeMessageChannel to use for a given node. */
public interface NodeMessageChannelProvider {

  /**
   * Returns the channel to use for the given node. Called once per node assembly.
   *
   * @param nodeId the node identifier
   * @param config the node configuration
   */
  NodeMessageChannel channelFor(String nodeId, Map<String, Object> config);
}
```

- [ ] **Step 6: Create DirectNodeMessageChannel**

Create `core/src/main/java/com/infenia/yukta/service/channel/DirectNodeMessageChannel.java`:

```java
/*
 * Copyright 2026 Infenia Private Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.infenia.yukta.service.channel;

import com.infenia.yukta.message.Message;
import com.infenia.yukta.plugin.channel.NodeMessageChannel;
import reactor.core.publisher.Flux;

/** Zero-overhead passthrough channel. Both sides return the Flux unchanged. */
public class DirectNodeMessageChannel implements NodeMessageChannel {

  @Override
  public Flux<Message<?>> inbound(
      final String nodeId, final String executionId, final Flux<Message<?>> upstream) {
    return upstream;
  }

  @Override
  public Flux<Message<?>> outbound(
      final String nodeId, final String executionId, final Flux<Message<?>> pluginOutput) {
    return pluginOutput;
  }
}
```

- [ ] **Step 7: Create DirectNodeMessageChannelProvider**

Create `core/src/main/java/com/infenia/yukta/service/channel/DirectNodeMessageChannelProvider.java`:

```java
/*
 * Copyright 2026 Infenia Private Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.infenia.yukta.service.channel;

import com.infenia.yukta.plugin.channel.NodeMessageChannel;
import com.infenia.yukta.plugin.channel.NodeMessageChannelProvider;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/** Default provider — returns a singleton DirectNodeMessageChannel for every node. */
@Component
@ConditionalOnMissingBean(NodeMessageChannelProvider.class)
public class DirectNodeMessageChannelProvider implements NodeMessageChannelProvider {

  private static final DirectNodeMessageChannel INSTANCE = new DirectNodeMessageChannel();

  @Override
  public NodeMessageChannel channelFor(final String nodeId, final Map<String, Object> config) {
    return INSTANCE;
  }
}
```

- [ ] **Step 8: Run tests to confirm they pass**

```bash
./gradlew :plugin-api:test :core:test --tests "com.infenia.yukta.service.channel.*" 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL` — all 7 tests pass.

- [ ] **Step 9: Run format + full module check**

```bash
./gradlew spotlessApply && ./gradlew :plugin-api:check :core:check 2>&1 | tail -30
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 10: Commit**

```bash
git add \
  plugin-api/src/main/java/com/infenia/yukta/plugin/channel/NodeMessageChannel.java \
  plugin-api/src/main/java/com/infenia/yukta/plugin/channel/NodeMessageChannelProvider.java \
  core/src/main/java/com/infenia/yukta/service/channel/DirectNodeMessageChannel.java \
  core/src/main/java/com/infenia/yukta/service/channel/DirectNodeMessageChannelProvider.java \
  core/src/test/java/com/infenia/yukta/service/channel/DirectNodeMessageChannelTest.java \
  core/src/test/java/com/infenia/yukta/service/channel/DirectNodeMessageChannelProviderTest.java
git commit -m "feat: add NodeMessageChannel abstraction with direct passthrough implementation"
```

---

## Task 2: Wire TriggerNodeAssemblerStrategy

**Files:**
- Modify: `core/src/main/java/com/infenia/yukta/service/orchestrator/strategy/TriggerNodeAssemblerStrategy.java`
- Modify: `core/src/test/java/com/infenia/yukta/service/orchestrator/strategy/TriggerNodeAssemblerStrategyTest.java`

**Interfaces:**
- Consumes: `NodeMessageChannelProvider.channelFor(String, Map)`, `NodeMessageChannel.outbound(String, String, Flux)`
- The `Trigger` strategy calls only **outbound** — it produces messages, has no inbound from parents.

- [ ] **Step 1: Add mock and update setUp in TriggerNodeAssemblerStrategyTest**

In `TriggerNodeAssemblerStrategyTest.java`, add the mock field and update `setUp`:

Add field after `@Mock private StreamTopologyDecorator streamTopologyDecorator;`:
```java
@Mock private NodeMessageChannelProvider channelProvider;
```

Replace the `setUp` method body:
```java
@BeforeEach
void setUp() {
  when(channelProvider.channelFor(any(), any())).thenReturn(new DirectNodeMessageChannel());
  strategy =
      new TriggerNodeAssemblerStrategy(
          tracker, Schedulers.boundedElastic(), streamTopologyDecorator, channelProvider);
}
```

Add these imports:
```java
import com.infenia.yukta.plugin.channel.NodeMessageChannel;
import com.infenia.yukta.plugin.channel.NodeMessageChannelProvider;
import com.infenia.yukta.service.channel.DirectNodeMessageChannel;
```

- [ ] **Step 2: Run existing tests to confirm compile failure**

```bash
./gradlew :core:test --tests "com.infenia.yukta.service.orchestrator.strategy.TriggerNodeAssemblerStrategyTest" 2>&1 | tail -20
```

Expected: compilation error — `TriggerNodeAssemblerStrategy` constructor does not accept a 4th argument yet.

- [ ] **Step 3: Add channelProvider field to TriggerNodeAssemblerStrategy**

In `TriggerNodeAssemblerStrategy.java`, add this field after `streamTopologyDecorator`:

```java
private final NodeMessageChannelProvider channelProvider;
```

Add this import:
```java
import com.infenia.yukta.plugin.channel.NodeMessageChannelProvider;
```

- [ ] **Step 4: Run existing tests to confirm they still pass**

```bash
./gradlew :core:test --tests "com.infenia.yukta.service.orchestrator.strategy.TriggerNodeAssemblerStrategyTest" 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL` — all existing tests pass. Channel is not called yet, but mock is lenient so no failures.

- [ ] **Step 5: Write failing test for outbound channel invocation**

Add this test to `TriggerNodeAssemblerStrategyTest.java`:

```java
@Test
void createAssembler_assembleCallsOutboundChannel() {
  final Message<?> msg = DefaultMessage.create(UUID.randomUUID(), "hello");
  final NodeMessageChannel channel = mock(NodeMessageChannel.class);
  when(channelProvider.channelFor(eq(NODE_ID), any())).thenReturn(channel);
  when(channel.outbound(eq(NODE_ID), eq(EXECUTION_ID), any())).thenReturn(Flux.just(msg));

  final TriggerPlugin trigger = mock(TriggerPlugin.class);
  when(trigger.start(any())).thenReturn(Flux.just(msg));
  when(trigger.isBlocking()).thenReturn(false);

  final WorkflowNode node = new WorkflowNode(NODE_ID, "trigger", Map.of());
  final AssemblyContext context = buildContext(NODE_ID, null);

  when(streamTopologyDecorator.applyLoggingAndBroadcasting(
          anyString(), anyString(), any(), any(int.class), any(), any()))
      .thenReturn(Flux.just(msg));

  final NodeAssembler assembler =
      strategy.createAssembler(node, trigger, Duration.ofSeconds(5), 0, 1024, new ParentEdgeInfo[0]);
  assembler.assemble(context);

  verify(channelProvider).channelFor(eq(NODE_ID), any());
  verify(channel).outbound(eq(NODE_ID), eq(EXECUTION_ID), any());
}
```

Add this import at the top (if not already present):
```java
import static org.mockito.ArgumentMatchers.eq;
```

- [ ] **Step 6: Run to confirm new test fails**

```bash
./gradlew :core:test --tests "com.infenia.yukta.service.orchestrator.strategy.TriggerNodeAssemblerStrategyTest.createAssembler_assembleCallsOutboundChannel" 2>&1 | tail -20
```

Expected: `FAILED` — `channelProvider.channelFor` was not called (channel not yet wired).

- [ ] **Step 7: Wire outbound channel in TriggerNodeAssemblerStrategy.createAssembler**

In `TriggerNodeAssemblerStrategy.java`, inside the `createAssembler` lambda (around line 81), replace:

```java
    return context -> {
      final var control = context.control();
      // ... existing logging ...

      Flux<Message<?>> stream = trigger.start(node.config());
```

with:

```java
    return context -> {
      final var control = context.control();
      // ... existing logging ...

      final com.infenia.yukta.plugin.channel.NodeMessageChannel channel =
          channelProvider.channelFor(node.nodeId(), node.config());
      Flux<Message<?>> stream =
          channel.outbound(node.nodeId(), context.executionId(), trigger.start(node.config()));
```

Add this import:
```java
import com.infenia.yukta.plugin.channel.NodeMessageChannel;
```

Update the field reference from the fully-qualified name used above (you can now use the short name since the import is added). The lambda body should read:

```java
    return context -> {
      final var control = context.control();

      log.atDebug()
          .addKeyValue("nodeId", node.nodeId())
          .addKeyValue("executionId", context.executionId())
          .log("Starting trigger stream");

      final NodeMessageChannel channel = channelProvider.channelFor(node.nodeId(), node.config());
      Flux<Message<?>> stream =
          channel.outbound(node.nodeId(), context.executionId(), trigger.start(node.config()));
      if (trigger.isBlocking()) {
        log.atDebug()
            .addKeyValue("nodeId", node.nodeId())
            .log("Subscribing blocking trigger to virtual thread scheduler");
        stream = stream.subscribeOn(virtualThreadScheduler);
      }

      Flux<Message<?>> built =
          StreamAssemblyHelper.buildStreamWithContext(node, stream, timeout, tracker, context);

      final var nodeSafeSink = control.nodeSafeStopSinks().get(node.nodeId());
      if (nodeSafeSink != null) {
        log.atDebug()
            .addKeyValue("nodeId", node.nodeId())
            .log("Applying safe stop signal to trigger stream");
        built = built.takeUntilOther(nodeSafeSink.asMono());
      }

      context.streams()[index] =
          streamTopologyDecorator.applyLoggingAndBroadcasting(
              context.executionId(),
              node.nodeId(),
              built,
              bufferSize,
              context.disposables(),
              context.connectors());

      log.atDebug()
          .addKeyValue("nodeId", node.nodeId())
          .addKeyValue("executionId", context.executionId())
          .log("Trigger node stream assembled and registered");
    };
```

- [ ] **Step 8: Run all Trigger tests**

```bash
./gradlew :core:test --tests "com.infenia.yukta.service.orchestrator.strategy.TriggerNodeAssemblerStrategyTest" 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL` — all tests including the new channel test pass.

- [ ] **Step 9: Format and check**

```bash
./gradlew spotlessApply && ./gradlew :core:check 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 10: Commit**

```bash
git add \
  core/src/main/java/com/infenia/yukta/service/orchestrator/strategy/TriggerNodeAssemblerStrategy.java \
  core/src/test/java/com/infenia/yukta/service/orchestrator/strategy/TriggerNodeAssemblerStrategyTest.java
git commit -m "feat: wire NodeMessageChannel outbound into TriggerNodeAssemblerStrategy"
```

---

## Task 3: Wire ProcessorNodeAssemblerStrategy

**Files:**
- Modify: `core/src/main/java/com/infenia/yukta/service/orchestrator/strategy/ProcessorNodeAssemblerStrategy.java`
- Modify: `core/src/test/java/com/infenia/yukta/service/orchestrator/strategy/ProcessorNodeAssemblerStrategyTest.java`

**Interfaces:**
- Consumes: `NodeMessageChannelProvider.channelFor`, `NodeMessageChannel.inbound`, `NodeMessageChannel.outbound`
- `Processor` calls **both** inbound (before plugin) and outbound (after plugin). The skip-flag path bypasses the plugin entirely — no channel calls in that branch.

- [ ] **Step 1: Add mock and update setUp in ProcessorNodeAssemblerStrategyTest**

In `ProcessorNodeAssemblerStrategyTest.java`, add the mock field after `@Mock private StreamTopologyDecorator streamTopologyDecorator;`:

```java
@Mock private NodeMessageChannelProvider channelProvider;
```

Replace the `setUp` method body:
```java
@BeforeEach
void setUp() {
  when(channelProvider.channelFor(any(), any())).thenReturn(new DirectNodeMessageChannel());
  strategy =
      new ProcessorNodeAssemblerStrategy(
          tracker, Schedulers.boundedElastic(), streamTopologyDecorator, channelProvider);
}
```

Add these imports:
```java
import com.infenia.yukta.plugin.channel.NodeMessageChannel;
import com.infenia.yukta.plugin.channel.NodeMessageChannelProvider;
import com.infenia.yukta.service.channel.DirectNodeMessageChannel;
```

- [ ] **Step 2: Run to confirm compile failure**

```bash
./gradlew :core:test --tests "com.infenia.yukta.service.orchestrator.strategy.ProcessorNodeAssemblerStrategyTest" 2>&1 | tail -20
```

Expected: compilation error — constructor does not accept 4 arguments yet.

- [ ] **Step 3: Add channelProvider field to ProcessorNodeAssemblerStrategy**

In `ProcessorNodeAssemblerStrategy.java`, add this field after `streamTopologyDecorator`:

```java
private final NodeMessageChannelProvider channelProvider;
```

Add this import:
```java
import com.infenia.yukta.plugin.channel.NodeMessageChannel;
import com.infenia.yukta.plugin.channel.NodeMessageChannelProvider;
```

- [ ] **Step 4: Run existing tests to confirm they still pass**

```bash
./gradlew :core:test --tests "com.infenia.yukta.service.orchestrator.strategy.ProcessorNodeAssemblerStrategyTest" 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL` — all existing tests pass.

- [ ] **Step 5: Write failing test for inbound+outbound channel invocation (normal path)**

Add these tests to `ProcessorNodeAssemblerStrategyTest.java`:

```java
@Test
void createAssembler_normalPath_callsInboundAndOutboundChannel() {
  final Message<?> msg = DefaultMessage.create(UUID.randomUUID(), "input");
  final Message<?> out = DefaultMessage.create(UUID.randomUUID(), "output");
  final NodeMessageChannel channel = mock(NodeMessageChannel.class);
  when(channelProvider.channelFor(eq(NODE_ID), any())).thenReturn(channel);
  when(channel.inbound(eq(NODE_ID), eq(EXECUTION_ID), any())).thenAnswer(i -> i.getArgument(2));
  when(channel.outbound(eq(NODE_ID), eq(EXECUTION_ID), any())).thenAnswer(i -> i.getArgument(2));

  final ProcessorPlugin processor = mock(ProcessorPlugin.class);
  when(processor.process(any(), any())).thenReturn(Flux.just(out));
  when(processor.isBlocking()).thenReturn(false);

  final WorkflowNode node = new WorkflowNode(NODE_ID, "processor", Map.of());
  final Flux<Message<?>> parentStream = Flux.just(msg);
  final AssemblyContext context = buildContextNoSkipFlag(NODE_ID, parentStream);

  when(streamTopologyDecorator.mergeParentStreams(any(), any(ParentEdgeInfo[].class)))
      .thenReturn(parentStream);
  when(streamTopologyDecorator.applyLoggingAndBroadcasting(
          anyString(), anyString(), any(), any(int.class), any(), any()))
      .thenReturn(Flux.just(out));

  final NodeAssembler assembler =
      strategy.createAssembler(node, processor, Duration.ofSeconds(5), 0, 1024, new ParentEdgeInfo[0]);
  assembler.assemble(context);

  verify(channelProvider).channelFor(eq(NODE_ID), any());
  verify(channel).inbound(eq(NODE_ID), eq(EXECUTION_ID), any());
  verify(channel).outbound(eq(NODE_ID), eq(EXECUTION_ID), any());
}

@Test
void createAssembler_skipFlagTrue_doesNotCallChannelInboundOrOutbound() {
  final Message<?> msg = DefaultMessage.create(UUID.randomUUID(), "input");
  final NodeMessageChannel channel = mock(NodeMessageChannel.class);
  when(channelProvider.channelFor(any(), any())).thenReturn(channel);

  final ProcessorPlugin processor = mock(ProcessorPlugin.class);
  final AtomicBoolean skipFlag = new AtomicBoolean(true);

  final WorkflowNode node = new WorkflowNode(NODE_ID, "processor", Map.of());
  final Flux<Message<?>> parentStream = Flux.just(msg);
  final AssemblyContext context = buildContext(NODE_ID, parentStream, skipFlag);

  when(streamTopologyDecorator.mergeParentStreams(any(), any(ParentEdgeInfo[].class)))
      .thenReturn(parentStream);
  when(streamTopologyDecorator.applyLoggingAndBroadcasting(
          anyString(), anyString(), any(), any(int.class), any(), any()))
      .thenReturn(Flux.just(msg));

  final NodeAssembler assembler =
      strategy.createAssembler(node, processor, Duration.ofSeconds(5), 0, 1024, new ParentEdgeInfo[0]);
  assembler.assemble(context);

  verify(channel, never()).inbound(any(), any(), any());
  verify(channel, never()).outbound(any(), any(), any());
}
```

Add these imports if not present:
```java
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
```

- [ ] **Step 6: Run to confirm new tests fail**

```bash
./gradlew :core:test --tests "com.infenia.yukta.service.orchestrator.strategy.ProcessorNodeAssemblerStrategyTest.createAssembler_normalPath_callsInboundAndOutboundChannel" 2>&1 | tail -20
```

Expected: `FAILED` — channel methods were not called.

- [ ] **Step 7: Wire inbound+outbound channel in ProcessorNodeAssemblerStrategy.createAssembler**

In `ProcessorNodeAssemblerStrategy.java`, inside the `createAssembler` lambda, replace the skip-flag if-else block (lines 96–115) with:

```java
      final NodeMessageChannel channel = channelProvider.channelFor(node.nodeId(), node.config());

      Flux<Message<?>> stream;
      final AtomicBoolean skipFlag = control.nodeSkipFlags().get(node.nodeId());

      if (skipFlag != null && skipFlag.get()) {
        log.atInfo()
            .addKeyValue(LOG_KEY_NODE_ID, node.nodeId())
            .addKeyValue("executionId", context.executionId())
            .log("Node marked for skip, bypassing processor");
        stream = safeInput;
      } else {
        log.atDebug()
            .addKeyValue(LOG_KEY_NODE_ID, node.nodeId())
            .log("Processing stream with processor plugin");
        final Flux<Message<?>> channelInput =
            channel.inbound(node.nodeId(), context.executionId(), safeInput);
        Flux<Message<?>> pluginOutput = processor.process(channelInput, node.config());
        if (processor.isBlocking()) {
          log.atDebug()
              .addKeyValue(LOG_KEY_NODE_ID, node.nodeId())
              .log("Subscribing blocking processor to virtual thread scheduler");
          pluginOutput = pluginOutput.subscribeOn(virtualThreadScheduler);
        }
        stream = channel.outbound(node.nodeId(), context.executionId(), pluginOutput);
      }
```

- [ ] **Step 8: Run all Processor tests**

```bash
./gradlew :core:test --tests "com.infenia.yukta.service.orchestrator.strategy.ProcessorNodeAssemblerStrategyTest" 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL` — all tests pass.

- [ ] **Step 9: Format and check**

```bash
./gradlew spotlessApply && ./gradlew :core:check 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 10: Commit**

```bash
git add \
  core/src/main/java/com/infenia/yukta/service/orchestrator/strategy/ProcessorNodeAssemblerStrategy.java \
  core/src/test/java/com/infenia/yukta/service/orchestrator/strategy/ProcessorNodeAssemblerStrategyTest.java
git commit -m "feat: wire NodeMessageChannel inbound/outbound into ProcessorNodeAssemblerStrategy"
```

---

## Task 4: Wire TerminalNodeAssemblerStrategy + Full Verification

**Files:**
- Modify: `core/src/main/java/com/infenia/yukta/service/orchestrator/strategy/TerminalNodeAssemblerStrategy.java`
- Modify: `core/src/test/java/com/infenia/yukta/service/orchestrator/strategy/TerminalNodeAssemblerStrategyTest.java`

**Interfaces:**
- Consumes: `NodeMessageChannelProvider.channelFor`, `NodeMessageChannel.inbound`
- `Terminal` calls **only inbound** — it consumes messages, produces nothing downstream.

- [ ] **Step 1: Add mock and update setUp in TerminalNodeAssemblerStrategyTest**

In `TerminalNodeAssemblerStrategyTest.java`, add the mock field after `@Mock private StreamTopologyDecorator streamTopologyDecorator;`:

```java
@Mock private NodeMessageChannelProvider channelProvider;
```

Replace the `setUp` method body:
```java
@BeforeEach
void setUp() {
  when(channelProvider.channelFor(any(), any())).thenReturn(new DirectNodeMessageChannel());
  strategy =
      new TerminalNodeAssemblerStrategy(
          tracker, Schedulers.boundedElastic(), streamTopologyDecorator, channelProvider);
}
```

Add these imports:
```java
import com.infenia.yukta.plugin.channel.NodeMessageChannel;
import com.infenia.yukta.plugin.channel.NodeMessageChannelProvider;
import com.infenia.yukta.service.channel.DirectNodeMessageChannel;
```

- [ ] **Step 2: Run to confirm compile failure**

```bash
./gradlew :core:test --tests "com.infenia.yukta.service.orchestrator.strategy.TerminalNodeAssemblerStrategyTest" 2>&1 | tail -20
```

Expected: compilation error — constructor does not accept 4 arguments yet.

- [ ] **Step 3: Add channelProvider field to TerminalNodeAssemblerStrategy**

In `TerminalNodeAssemblerStrategy.java`, add this field after `streamTopologyDecorator`:

```java
private final NodeMessageChannelProvider channelProvider;
```

Add these imports:
```java
import com.infenia.yukta.plugin.channel.NodeMessageChannel;
import com.infenia.yukta.plugin.channel.NodeMessageChannelProvider;
```

- [ ] **Step 4: Run existing tests to confirm they still pass**

```bash
./gradlew :core:test --tests "com.infenia.yukta.service.orchestrator.strategy.TerminalNodeAssemblerStrategyTest" 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL` — all existing tests pass.

- [ ] **Step 5: Write failing test for inbound channel invocation**

Add this test to `TerminalNodeAssemblerStrategyTest.java`:

```java
@Test
void createAssembler_callsInboundChannel() {
  final Message<?> msg = DefaultMessage.create(UUID.randomUUID(), "input");
  final NodeMessageChannel channel = mock(NodeMessageChannel.class);
  when(channelProvider.channelFor(eq(NODE_ID), any())).thenReturn(channel);
  when(channel.inbound(eq(NODE_ID), eq(EXECUTION_ID), any())).thenAnswer(i -> i.getArgument(2));

  final TerminalPlugin terminal = mock(TerminalPlugin.class);
  when(terminal.consume(any(), any())).thenReturn(Mono.empty());
  when(terminal.isBlocking()).thenReturn(false);

  final WorkflowNode node = new WorkflowNode(NODE_ID, "terminal", Map.of());
  final AssemblyContext context = buildContext(NODE_ID, Flux.just(msg));

  when(streamTopologyDecorator.mergeParentStreams(any(), any(ParentEdgeInfo[].class)))
      .thenReturn(Flux.just(msg));

  final NodeAssembler assembler =
      strategy.createAssembler(node, terminal, Duration.ofSeconds(5), 0, 1024, new ParentEdgeInfo[0]);
  assembler.assemble(context);

  verify(channelProvider).channelFor(eq(NODE_ID), any());
  verify(channel).inbound(eq(NODE_ID), eq(EXECUTION_ID), any());
}
```

Add this import if not present:
```java
import static org.mockito.ArgumentMatchers.eq;
```

- [ ] **Step 6: Run to confirm new test fails**

```bash
./gradlew :core:test --tests "com.infenia.yukta.service.orchestrator.strategy.TerminalNodeAssemblerStrategyTest.createAssembler_callsInboundChannel" 2>&1 | tail -20
```

Expected: `FAILED` — channel inbound was not called.

- [ ] **Step 7: Wire inbound channel in TerminalNodeAssemblerStrategy.createAssembler**

In `TerminalNodeAssemblerStrategy.java`, inside the `createAssembler` lambda, add the channel lookup before the `terminal.consume()` call. The relevant section changes from:

```java
      log.atDebug()
          .addKeyValue(LOG_KEY_NODE_ID, node.nodeId())
          .addKeyValue("executionId", context.executionId())
          .log("Consuming stream with terminal plugin");

      Mono<Void> completion = terminal.consume(inputToTerminal, node.config());
```

to:

```java
      log.atDebug()
          .addKeyValue(LOG_KEY_NODE_ID, node.nodeId())
          .addKeyValue("executionId", context.executionId())
          .log("Consuming stream with terminal plugin");

      final NodeMessageChannel channel = channelProvider.channelFor(node.nodeId(), node.config());
      Mono<Void> completion =
          terminal.consume(
              channel.inbound(node.nodeId(), context.executionId(), inputToTerminal),
              node.config());
```

- [ ] **Step 8: Run all Terminal tests**

```bash
./gradlew :core:test --tests "com.infenia.yukta.service.orchestrator.strategy.TerminalNodeAssemblerStrategyTest" 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL` — all tests pass.

- [ ] **Step 9: Format and full build**

```bash
./gradlew spotlessApply && ./gradlew check 2>&1 | tail -30
```

Expected: `BUILD SUCCESSFUL` — all modules, all quality gates.

- [ ] **Step 10: Commit**

```bash
git add \
  core/src/main/java/com/infenia/yukta/service/orchestrator/strategy/TerminalNodeAssemblerStrategy.java \
  core/src/test/java/com/infenia/yukta/service/orchestrator/strategy/TerminalNodeAssemblerStrategyTest.java
git commit -m "feat: wire NodeMessageChannel inbound into TerminalNodeAssemblerStrategy"
```
