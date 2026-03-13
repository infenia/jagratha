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
package com.infenia.yukta.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infenia.yukta.service.session.SessionConfigStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class LogRetrievalServiceTest {

  private SessionConfigStore configService;
  private LogRetrievalService service;

  @TempDir Path tempDir;

  @BeforeEach
  void setUp() {
    configService = mock(SessionConfigStore.class);
    service = new LogRetrievalService(configService);
  }

  @Test
  void testListLogs() throws IOException {
    String sessionId = "s1";
    Path resultsDir = tempDir.resolve("results");
    Path fileDir = tempDir.resolve("files");

    Files.createDirectories(resultsDir.resolve(sessionId));
    Files.createDirectories(fileDir.resolve(sessionId));

    Files.createFile(resultsDir.resolve(sessionId).resolve("log1.txt"));
    Files.createFile(fileDir.resolve(sessionId).resolve("log2.txt"));

    when(configService.getResultLogDir(sessionId)).thenReturn(Mono.just(resultsDir.toString()));
    when(configService.getFileLogDir(sessionId)).thenReturn(Mono.just(fileDir.toString()));

    StepVerifier.create(service.listLogs(sessionId))
        .expectNextMatches(logs -> logs.containsAll(List.of("log1.txt", "log2.txt")))
        .verifyComplete();
  }

  @Test
  void testListLogsEmptyDirs() {
    String sessionId = "s1";
    when(configService.getResultLogDir(sessionId)).thenReturn(Mono.just(""));
    when(configService.getFileLogDir(sessionId)).thenReturn(Mono.just(""));

    StepVerifier.create(service.listLogs(sessionId)).expectNext(List.of()).verifyComplete();
  }

  @Test
  void testListLogsNullDirs() {
    String sessionId = "s1";
    when(configService.getResultLogDir(sessionId)).thenReturn(Mono.empty());
    when(configService.getFileLogDir(sessionId)).thenReturn(Mono.empty());

    StepVerifier.create(service.listLogs(sessionId)).expectNext(List.of()).verifyComplete();
  }

  @Test
  void testListLogsSessionDirMissing() throws IOException {
    String sessionId = "s1";
    Path resultsDir = tempDir.resolve("results-missing");
    Files.createDirectories(resultsDir); // resultsDir exists, but resultsDir/s1 does not

    when(configService.getResultLogDir(sessionId)).thenReturn(Mono.just(resultsDir.toString()));
    when(configService.getFileLogDir(sessionId)).thenReturn(Mono.just(""));

    StepVerifier.create(service.listLogs(sessionId)).expectNext(List.of()).verifyComplete();
  }

  @Test
  void testListLogsNotADirectory() throws IOException {
    String sessionId = "s1";
    Path resultsDir = tempDir.resolve("results-not-dir");
    Files.createDirectories(resultsDir);
    Files.createFile(resultsDir.resolve(sessionId)); // resultsDir/s1 is a FILE

    when(configService.getResultLogDir(sessionId)).thenReturn(Mono.just(resultsDir.toString()));
    when(configService.getFileLogDir(sessionId)).thenReturn(Mono.just(""));

    StepVerifier.create(service.listLogs(sessionId)).expectNext(List.of()).verifyComplete();
  }

  @Test
  void testListLogsIOException() throws Exception {
    String sessionId = "s1";
    Path resultsDir = tempDir.resolve("results-io");
    Files.createDirectories(resultsDir.resolve(sessionId));
    resultsDir.resolve(sessionId).toFile().setReadable(false);

    try {
      when(configService.getResultLogDir(sessionId)).thenReturn(Mono.just(resultsDir.toString()));
      when(configService.getFileLogDir(sessionId)).thenReturn(Mono.just(""));

      StepVerifier.create(service.listLogs(sessionId)).expectNext(List.of()).verifyComplete();
    } finally {
      resultsDir.resolve(sessionId).toFile().setReadable(true);
    }
  }

  @Test
  void testGetLogContent() throws IOException {
    String sessionId = "s1";
    Path resultsDir = tempDir.resolve("results");
    Files.createDirectories(resultsDir.resolve(sessionId));
    Path logFile = resultsDir.resolve(sessionId).resolve("log1.txt");
    Files.writeString(logFile, "content");

    when(configService.getResultLogDir(sessionId)).thenReturn(Mono.just(resultsDir.toString()));
    when(configService.getFileLogDir(sessionId)).thenReturn(Mono.just(""));

    StepVerifier.create(service.getLogContent(sessionId, "log1.txt"))
        .expectNext("content")
        .verifyComplete();
  }

  @Test
  void testGetLogContentNotFound() {
    String sessionId = "s1";
    when(configService.getResultLogDir(sessionId)).thenReturn(Mono.just(tempDir.toString()));
    when(configService.getFileLogDir(sessionId)).thenReturn(Mono.just(""));

    StepVerifier.create(service.getLogContent(sessionId, "missing.txt"))
        .verifyError(IOException.class);
  }

  @Test
  void testGetLogContentNullDirs() {
    String sessionId = "s1";
    when(configService.getResultLogDir(sessionId)).thenReturn(Mono.empty());
    when(configService.getFileLogDir(sessionId)).thenReturn(Mono.empty());

    StepVerifier.create(service.getLogContent(sessionId, "log.txt")).verifyError(IOException.class);
  }
}
