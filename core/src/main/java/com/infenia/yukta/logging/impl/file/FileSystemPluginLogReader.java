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
package com.infenia.yukta.logging.impl.file;

import com.infenia.yukta.logging.api.ExecutionSummary;
import com.infenia.yukta.logging.api.LogLevel;
import com.infenia.yukta.logging.api.LogStream;
import com.infenia.yukta.logging.api.PluginLogEntry;
import com.infenia.yukta.logging.api.PluginLogReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * File-system based implementation of PluginLogReader.
 *
 * <p>Reads logs from /var/log/yukta/plugins/{sessionId}/*.log files.
 */
@Slf4j
@RequiredArgsConstructor
public class FileSystemPluginLogReader implements PluginLogReader {

  private static final DateTimeFormatter TIMESTAMP_FORMAT =
      DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault());

  /** Base directory for log files. */
  private final String baseLogDir;

  @Override
  public Flux<PluginLogEntry> readExecution(final String executionId) {
    return Mono.fromCallable(
            () -> {
              final List<PluginLogEntry> entries = new ArrayList<>();
              final Path baseDir = Path.of(baseLogDir);

              if (!Files.exists(baseDir)) {
                return entries;
              }

              try (final var sessions = Files.list(baseDir)) {
                for (final Path sessionDir : sessions.toArray(Path[]::new)) {
                  final Path logFile = sessionDir.resolve(executionId + ".log");
                  if (Files.exists(logFile)) {
                    entries.addAll(readLogFile(logFile, sessionDir.getFileName().toString()));
                  }
                }
              } catch (final IOException e) {
                log.atWarn()
                    .addKeyValue("baseDir", baseLogDir)
                    .setCause(e)
                    .log("Error reading execution logs");
              }

              return entries;
            })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMapIterable(entries -> entries);
  }

  @Override
  public Flux<PluginLogEntry> readSession(final String sessionId) {
    return Mono.fromCallable(
            () -> {
              final List<PluginLogEntry> entries = new ArrayList<>();
              final Path sessionDir = Path.of(baseLogDir).resolve(sessionId);

              if (!Files.exists(sessionDir)) {
                return entries;
              }

              try (final var files = Files.list(sessionDir)) {
                for (final Path logFile : files.toArray(Path[]::new)) {
                  if (logFile.toString().endsWith(".log")) {
                    entries.addAll(readLogFile(logFile, sessionId));
                  }
                }
              } catch (final IOException e) {
                log.atWarn()
                    .addKeyValue("sessionId", sessionId)
                    .setCause(e)
                    .log("Error reading session logs");
              }

              return entries;
            })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMapIterable(entries -> entries);
  }

  @Override
  public Mono<List<ExecutionSummary>> listExecutions(final String sessionId) {
    return Mono.fromCallable(
            () -> {
              final List<ExecutionSummary> summaries = new ArrayList<>();
              final Path sessionDir = Path.of(baseLogDir).resolve(sessionId);

              if (!Files.exists(sessionDir)) {
                return summaries;
              }

              try (final var files = Files.list(sessionDir)) {
                for (final Path logFile : files.toArray(Path[]::new)) {
                  if (logFile.toString().endsWith(".log")) {
                    final String executionId = logFile.getFileName().toString().replace(".log", "");
                    final long entryCount;
                    try (final var lineStream = Files.lines(logFile)) {
                      entryCount = lineStream.count();
                    }
                    final List<PluginLogEntry> entries = readLogFile(logFile, sessionId);

                    if (!entries.isEmpty()) {
                      final Instant startInstant = entries.getFirst().timestamp();
                      final Instant endInstant = entries.getLast().timestamp();
                      final var startTime =
                          ZonedDateTime.ofInstant(startInstant, ZoneId.systemDefault())
                              .toLocalDateTime();
                      final var endTime =
                          ZonedDateTime.ofInstant(endInstant, ZoneId.systemDefault())
                              .toLocalDateTime();
                      summaries.add(
                          new ExecutionSummary(
                              executionId, sessionId, startTime, endTime, entryCount));
                    }
                  }
                }
              } catch (final IOException e) {
                log.atWarn()
                    .addKeyValue("sessionId", sessionId)
                    .setCause(e)
                    .log("Error listing executions");
              }

              return summaries.stream()
                  .sorted((a, b) -> b.startTime().compareTo(a.startTime()))
                  .collect(Collectors.toList());
            })
        .subscribeOn(Schedulers.boundedElastic());
  }

  @Override
  public Mono<String> getRawContent(final String executionId) {
    return Mono.fromCallable(
            () -> {
              final Path baseDir = Path.of(baseLogDir);

              if (!Files.exists(baseDir)) {
                return "";
              }

              try (final var sessions = Files.list(baseDir)) {
                for (final Path sessionDir : sessions.toArray(Path[]::new)) {
                  final Path logFile = sessionDir.resolve(executionId + ".log");
                  if (Files.exists(logFile)) {
                    return Files.readString(logFile, StandardCharsets.UTF_8);
                  }
                }
              } catch (final IOException e) {
                log.atWarn()
                    .addKeyValue("executionId", executionId)
                    .setCause(e)
                    .log("Error reading raw log content");
              }

              return "";
            })
        .subscribeOn(Schedulers.boundedElastic());
  }

  private List<PluginLogEntry> readLogFile(final Path logFile, final String sessionId) {
    final List<PluginLogEntry> entries = new ArrayList<>();
    final String executionId = logFile.getFileName().toString().replace(".log", "");

    try {
      final List<String> lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
      for (final String line : lines) {
        final PluginLogEntry entry = parseLine(line, executionId, sessionId);
        if (entry != null) {
          entries.add(entry);
        }
      }
    } catch (final IOException e) {
      log.atWarn().addKeyValue("logFile", logFile).setCause(e).log("Error reading log file");
    }

    return entries;
  }

  private PluginLogEntry parseLine(
      final String line, final String executionId, final String sessionId) {
    try {
      final List<String> parts = List.of(line.split(" \\| ", -1));
      if (parts.size() >= 4) {
        final String timestampStr = parts.get(0).trim();
        final Instant timestamp = parseTimestamp(timestampStr);
        final LogStream stream = LogStream.valueOf(parts.get(1).trim());
        final String pluginId = parts.get(2).trim();
        final String pluginName = parts.get(2).trim();
        final String message = parts.get(3).trim();

        return new PluginLogEntry(
            executionId,
            sessionId,
            pluginId,
            pluginName,
            stream,
            message,
            LogLevel.INFO,
            timestamp);
      }
    } catch (final Exception e) {
      log.atTrace()
          .addKeyValue("line", line.substring(0, Math.min(50, line.length())))
          .setCause(e)
          .log("Failed to parse log line");
    }

    return null;
  }

  private Instant parseTimestamp(final String timestampStr) {
    try {
      final ZonedDateTime zonedDateTime = ZonedDateTime.parse(timestampStr, TIMESTAMP_FORMAT);
      return zonedDateTime.toInstant();
    } catch (final Exception e) {
      try {
        return java.time.LocalDateTime.parse(timestampStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            .atZone(ZoneId.systemDefault())
            .toInstant();
      } catch (final Exception e2) {
        return Instant.now();
      }
    }
  }
}
