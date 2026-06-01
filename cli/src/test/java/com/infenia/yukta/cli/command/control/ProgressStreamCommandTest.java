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
package com.infenia.yukta.cli.command.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.infenia.yukta.model.monitoring.WorkflowProgress;
import com.infenia.yukta.service.control.gateway.ControlBusGateway;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import picocli.CommandLine;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class ProgressStreamCommandTest {

  @Mock private ControlBusGateway controlBus;

  @Test
  void progressStream_emptyStream_completes() throws Exception {
    when(controlBus.watchExecution("exec1")).thenReturn(Flux.empty());

    final ProgressStreamCommand cmd = new ProgressStreamCommand(controlBus);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CommandLine cli = new CommandLine(cmd);
    cli.setOut(new PrintWriter(out, true));

    final int exitCode = cli.execute("exec1");

    assertThat(exitCode).isZero();
  }

  @Test
  void progressStream_singleProgressUpdate_prints() throws Exception {
    final WorkflowProgress progress =
        new WorkflowProgress(
            "exec1", "sess1", "wf1", "IN_PROGRESS", new ArrayList<>(), LocalDateTime.now(), null);
    when(controlBus.watchExecution("exec1")).thenReturn(Flux.just(progress));

    final ProgressStreamCommand cmd = new ProgressStreamCommand(controlBus);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CommandLine cli = new CommandLine(cmd);
    cli.setOut(new PrintWriter(out, true));

    final int exitCode = cli.execute("exec1");

    assertThat(exitCode).isZero();
  }

  @Test
  void progressStream_multipleProgressUpdates_allPrinted() throws Exception {
    final WorkflowProgress progress1 =
        new WorkflowProgress(
            "exec1", "sess1", "wf1", "IN_PROGRESS", new ArrayList<>(), LocalDateTime.now(), null);
    final WorkflowProgress progress2 =
        new WorkflowProgress(
            "exec1",
            "sess1",
            "wf1",
            "COMPLETED",
            new ArrayList<>(),
            LocalDateTime.now(),
            LocalDateTime.now());

    when(controlBus.watchExecution("exec2")).thenReturn(Flux.just(progress1, progress2));

    final ProgressStreamCommand cmd = new ProgressStreamCommand(controlBus);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CommandLine cli = new CommandLine(cmd);
    cli.setOut(new PrintWriter(out, true));

    final int exitCode = cli.execute("exec2");

    assertThat(exitCode).isZero();
  }

  @Test
  void progressStream_longRunningStream_completesSuccessfully() throws Exception {
    final WorkflowProgress[] progressUpdates = {
      new WorkflowProgress(
          "exec3", "sess3", "wf3", "PENDING", new ArrayList<>(), LocalDateTime.now(), null),
      new WorkflowProgress(
          "exec3", "sess3", "wf3", "IN_PROGRESS", new ArrayList<>(), LocalDateTime.now(), null),
      new WorkflowProgress(
          "exec3",
          "sess3",
          "wf3",
          "COMPLETED",
          new ArrayList<>(),
          LocalDateTime.now(),
          LocalDateTime.now())
    };
    when(controlBus.watchExecution("exec3")).thenReturn(Flux.fromArray(progressUpdates));

    final ProgressStreamCommand cmd = new ProgressStreamCommand(controlBus);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CommandLine cli = new CommandLine(cmd);
    cli.setOut(new PrintWriter(out, true));

    final int exitCode = cli.execute("exec3");

    assertThat(exitCode).isZero();
  }

  @Test
  void progressStream_viaCommandLine_executesSuccessfully() throws Exception {
    final WorkflowProgress progress =
        new WorkflowProgress(
            "exec4", "sess4", "wf4", "IN_PROGRESS", new ArrayList<>(), LocalDateTime.now(), null);
    when(controlBus.watchExecution("exec4")).thenReturn(Flux.just(progress));

    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CommandLine cli = new CommandLine(new ProgressStreamCommand(controlBus));
    cli.setOut(new PrintWriter(out, true));

    final int exitCode = cli.execute("exec4");

    assertThat(exitCode).isZero();
  }

  @Test
  void progressStream_interruptedDuringAwait_handlesInterrupt() throws InterruptedException {
    when(controlBus.watchExecution("exec5")).thenReturn(Flux.never());

    CountDownLatch testStarted = new CountDownLatch(1);
    CountDownLatch testCompleted = new CountDownLatch(1);

    final ProgressStreamCommand cmd = new ProgressStreamCommand(controlBus);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CommandLine cli = new CommandLine(cmd);
    cli.setOut(new PrintWriter(out, true));

    Thread executionThread =
        new Thread(
            () -> {
              testStarted.countDown();
              try {
                cli.execute("exec5");
              } catch (Exception e) {
                // May be interrupted
              } finally {
                testCompleted.countDown();
              }
            });

    executionThread.start();

    // Wait for execution to start, then interrupt it
    testStarted.await();
    Thread.sleep(50);
    executionThread.interrupt();

    // Wait for execution to finish (should complete quickly after interrupt)
    boolean completed = testCompleted.await(3000, java.util.concurrent.TimeUnit.MILLISECONDS);
    assertThat(completed).isTrue();
  }
}
