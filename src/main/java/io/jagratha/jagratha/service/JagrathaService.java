package io.jagratha.jagratha.service;

import io.jagratha.jagratha.config.JagrathaConfig;
import io.jagratha.jagratha.model.TaskResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class JagrathaService {

    private final JagrathaConfig config;

    public Mono<Void> saveFile(String relativePath, String content) {
        return Mono.fromRunnable(() -> {
            try {
                String projectRoot = config.getExternalProject().getPath();
                if (projectRoot == null || projectRoot.isEmpty()) {
                    throw new IllegalStateException("External project path is not configured");
                }
                Path fullPath = Paths.get(projectRoot).resolve(relativePath).normalize();

                // Security check: ensure the path is within the project root
                if (!fullPath.startsWith(Paths.get(projectRoot).normalize())) {
                    throw new IllegalArgumentException("Invalid file path: " + relativePath);
                }

                Files.createDirectories(fullPath.getParent());
                Files.writeString(fullPath, content);
                log.info("Saved file to {}", fullPath);
            } catch (IOException e) {
                log.error("Failed to save file", e);
                throw new RuntimeException("Failed to save file: " + e.getMessage(), e);
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    public Mono<TaskResponse> runQualityChecks() {
        return Mono.fromCallable(() -> {
            String projectRoot = config.getExternalProject().getPath();
            String gradlePath = config.getExternalProject().getGradlePath();

            if (projectRoot == null || projectRoot.isEmpty()) {
                return TaskResponse.builder()
                        .status("FAILURE")
                        .output("External project path is not configured")
                        .build();
            }

            File projectDir = new File(projectRoot);
            if (!projectDir.exists() || !projectDir.isDirectory()) {
                return TaskResponse.builder()
                        .status("FAILURE")
                        .output("External project directory does not exist: " + projectRoot)
                        .build();
            }

            List<String> command = new ArrayList<>();
            command.add(gradlePath != null ? gradlePath : "./gradlew");
            command.add("spotlessApply");
            command.add("spotlessCheck");
            command.add("checkstyleMain");
            command.add("test");

            log.info("Running quality checks in {}: {}", projectRoot, String.join(" ", command));

            try {
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.directory(projectDir);
                pb.redirectErrorStream(true);
                Process process = pb.start();

                String output = new String(process.getInputStream().readAllBytes());
                boolean finished = process.waitFor(10, TimeUnit.MINUTES);

                if (!finished) {
                    process.destroyForcibly();
                    return TaskResponse.builder()
                            .status("FAILURE")
                            .output("Timeout while running quality checks.\n" + output)
                            .build();
                }

                int exitCode = process.exitValue();
                log.info("Quality checks finished with exit code {}", exitCode);
                return TaskResponse.builder()
                        .status(exitCode == 0 ? "SUCCESS" : "FAILURE")
                        .output(output)
                        .build();

            } catch (Exception e) {
                log.error("Error running quality checks", e);
                return TaskResponse.builder()
                        .status("FAILURE")
                        .output("Error executing Gradle: " + e.getMessage())
                        .build();
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
