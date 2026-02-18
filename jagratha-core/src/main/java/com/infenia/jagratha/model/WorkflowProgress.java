package com.infenia.jagratha.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Represents the progress of a workflow.
 */
public record WorkflowProgress(
    String sessionId,
    String status,
    List<TaskProgress> tasks,
    LocalDateTime startTime,
    LocalDateTime endTime
) {
}
