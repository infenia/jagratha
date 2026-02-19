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
package com.infenia.jagratha.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request object for file operations.
 *
 * @param path the file path
 * @param sessionId the session identifier
 * @param content the file content (optional)
 */
@Schema(description = "Request object for logging a file path")
public record FileRequest(
    @Schema(description = "The relative path of the file", example = "src/main/java/App.java")
        @NotBlank(message = "Path is required")
        String path,
    @Schema(description = "The unique session identifier", example = "session-123")
        @NotBlank(message = "Session ID is required")
        @Pattern(regexp = "^(?!.*\\.\\.)[^/\\\\]*$", message = "Invalid session ID format")
        String sessionId,
    @Schema(description = "The content of the file (optional)", example = "public class App {}")
        String content) {}
