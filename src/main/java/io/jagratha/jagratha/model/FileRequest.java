package io.jagratha.jagratha.model;

import jakarta.validation.constraints.NotBlank;

public record FileRequest(@NotBlank String path, @NotBlank String content) {}
