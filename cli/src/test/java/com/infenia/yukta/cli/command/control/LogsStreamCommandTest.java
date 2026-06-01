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

import com.infenia.yukta.service.control.gateway.ControlBusGateway;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import picocli.CommandLine;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class LogsStreamCommandTest {

  @Mock private ControlBusGateway controlBus;

  @Test
  void logsStream_emptyStream_completes() throws Exception {
    when(controlBus.watchLogs("exec1")).thenReturn(Flux.empty());

    final LogsStreamCommand cmd = new LogsStreamCommand(controlBus);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CommandLine cli = new CommandLine(cmd);
    cli.setOut(new PrintWriter(out, true));

    final int exitCode = cli.execute("exec1");

    assertThat(exitCode).isZero();
  }

  @Test
  void logsStream_singleLogEntry_subscribes() throws Exception {
    final String logEntry = "2024-01-01 10:00:00 INFO: Task started";
    when(controlBus.watchLogs("exec1")).thenReturn(Flux.just(logEntry));

    final LogsStreamCommand cmd = new LogsStreamCommand(controlBus);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CommandLine cli = new CommandLine(cmd);
    cli.setOut(new PrintWriter(out, true));

    final int exitCode = cli.execute("exec1");

    assertThat(exitCode).isZero();
  }

  @Test
  void logsStream_multipleLogEntries_completesSuccessfully() throws Exception {
    final String log1 = "Task 1 started";
    final String log2 = "Task 1 in progress";
    final String log3 = "Task 1 completed";

    when(controlBus.watchLogs("exec2")).thenReturn(Flux.just(log1, log2, log3));

    final LogsStreamCommand cmd = new LogsStreamCommand(controlBus);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CommandLine cli = new CommandLine(cmd);
    cli.setOut(new PrintWriter(out, true));

    final int exitCode = cli.execute("exec2");

    assertThat(exitCode).isZero();
  }

  @Test
  void logsStream_largeStreamOfLogs_completesSuccessfully() throws Exception {
    final String[] logs = {"Log line 1", "Log line 2", "Log line 3", "Log line 4", "Log line 5"};
    when(controlBus.watchLogs("exec3")).thenReturn(Flux.fromArray(logs));

    final LogsStreamCommand cmd = new LogsStreamCommand(controlBus);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CommandLine cli = new CommandLine(cmd);
    cli.setOut(new PrintWriter(out, true));

    final int exitCode = cli.execute("exec3");

    assertThat(exitCode).isZero();
  }

  @Test
  void logsStream_viaPicoCLI_executesSuccessfully() throws Exception {
    when(controlBus.watchLogs("exec4")).thenReturn(Flux.just("test log"));

    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CommandLine cli = new CommandLine(new LogsStreamCommand(controlBus));
    cli.setOut(new PrintWriter(out, true));

    final int exitCode = cli.execute("exec4");

    assertThat(exitCode).isZero();
  }

  @Test
  void logsStream_interruptedDuringAwait_handlesInterrupt() throws InterruptedException {
    when(controlBus.watchLogs("exec5")).thenReturn(Flux.never());

    CountDownLatch testStarted = new CountDownLatch(1);
    CountDownLatch testCompleted = new CountDownLatch(1);

    final LogsStreamCommand cmd = new LogsStreamCommand(controlBus);
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
