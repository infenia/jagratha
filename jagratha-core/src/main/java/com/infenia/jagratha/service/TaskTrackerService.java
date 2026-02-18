package com.infenia.jagratha.service;

import com.infenia.jagratha.model.TaskProgress;
import com.infenia.jagratha.model.WorkflowProgress;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * Service for tracking the progress of workflows and tasks.
 */
@Service
public class TaskTrackerService {

    private final Map<String, WorkflowState> states = new ConcurrentHashMap<>();
    private final Map<String, Sinks.Many<String>> logSinks = new ConcurrentHashMap<>();
    private final Map<String, Sinks.Many<String>> statusSinks = new ConcurrentHashMap<>();

    /**
     * Start tracking a new workflow for a session.
     */
    public void startWorkflow(String sessionId, List<String> taskNames) {
        List<TaskProgress> initialTasks = taskNames.stream()
            .map(name -> new TaskProgress(name, "", "PENDING", null, null))
            .toList();
        states.put(sessionId, new WorkflowState(sessionId, "RUNNING", new ArrayList<>(initialTasks), LocalDateTime.now()));

        Sinks.Many<String> sink = Sinks.many().multicast().directBestEffort();
        logSinks.put(sessionId, sink);

        Sinks.Many<String> statusSink = Sinks.many().multicast().directBestEffort();
        statusSinks.put(sessionId, statusSink);
    }

    /**
     * Update the status of a specific task.
     */
    public void updateTaskStatus(String sessionId, String taskName, String module, String status) {
        WorkflowState state = states.get(sessionId);
        if (state != null) {
            state.updateTask(taskName, module, status);
            notifyStatusChange(sessionId);
        }
    }

    /**
     * Finish the workflow.
     */
    public void finishWorkflow(String sessionId, String status) {
        WorkflowState state = states.get(sessionId);
        if (state != null) {
            state.setStatus(status);
            state.setEndTime(LocalDateTime.now());
            notifyStatusChange(sessionId);
        }
    }

    /**
     * Append a log line to the live output.
     */
    public void appendLog(String sessionId, String line) {
        Sinks.Many<String> sink = logSinks.get(sessionId);
        if (sink != null) {
            sink.tryEmitNext(line);
        }
    }

    /**
     * Get the current progress of a workflow.
     */
    public WorkflowProgress getProgress(String sessionId) {
        WorkflowState state = states.get(sessionId);
        if (state == null) return null;
        return new WorkflowProgress(
            state.sessionId,
            state.status,
            List.copyOf(state.tasks),
            state.startTime,
            state.endTime
        );
    }

    /**
     * Get the log stream for a session.
     */
    public Flux<String> getLogStream(String sessionId) {
        Sinks.Many<String> sink = logSinks.get(sessionId);
        return sink != null ? sink.asFlux() : Flux.empty();
    }

    /**
     * List all active sessions being tracked.
     */
    public List<String> getActiveSessions() {
        return List.copyOf(states.keySet());
    }

    /**
     * Remove tracking data for a session.
     */
    public void removeSession(String sessionId) {
        states.remove(sessionId);
        logSinks.remove(sessionId);
        statusSinks.remove(sessionId);
    }

    private void notifyStatusChange(String sessionId) {
        Sinks.Many<String> sink = statusSinks.get(sessionId);
        if (sink != null) {
            sink.tryEmitNext("update");
        }
    }

    /**
     * Get the status stream for a session.
     */
    public Flux<String> getStatusStream(String sessionId) {
        Sinks.Many<String> sink = statusSinks.get(sessionId);
        return sink != null ? sink.asFlux() : Flux.empty();
    }

    private static class WorkflowState {
        private final String sessionId;
        private String status;
        private final List<TaskProgress> tasks;
        private final LocalDateTime startTime;
        private LocalDateTime endTime;

        WorkflowState(String sessionId, String status, List<TaskProgress> tasks, LocalDateTime startTime) {
            this.sessionId = sessionId;
            this.status = status;
            this.tasks = tasks;
            this.startTime = startTime;
        }

        void updateTask(String taskName, String module, String status) {
            for (int i = 0; i < tasks.size(); i++) {
                TaskProgress tp = tasks.get(i);
                if (tp.taskName().equals(taskName)) {
                    LocalDateTime sTime = tp.startTime();
                    LocalDateTime eTime = tp.endTime();
                    if ("RUNNING".equals(status) && sTime == null) {
                        sTime = LocalDateTime.now();
                    } else if (("SUCCESS".equals(status) || "FAILURE".equals(status)) && eTime == null) {
                        eTime = LocalDateTime.now();
                    }
                    tasks.set(i, new TaskProgress(taskName, module, status, sTime, eTime));
                    break;
                }
            }
        }

        void setStatus(String status) { this.status = status; }
        void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    }
}
