package com.infenia.jagratha.config;

import com.infenia.jagratha.model.PluginRegistration;
import com.infenia.jagratha.model.WorkflowConfig;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service for managing app configuration with runtime overrides. All configuration is session-based
 * and updated via API.
 */
@Service
@SuppressWarnings({"PMD.UseConcurrentHashMap", "PMD.LinguisticNaming"})
public class AppConfigService {

  private static final String DEFAULT_BASE_DIR = System.getProperty("user.home") + "/.jagratha";
  private static final String DEFAULT_FILE_LOG = DEFAULT_BASE_DIR + "/modified-files";
  private static final String DEFAULT_RES_LOG = DEFAULT_BASE_DIR + "/results";
  private static final long DEFAULT_TIMEOUT = 300L;
  private static final List<String> DEFAULT_TASKS =
      List.of("spotlessApply", "spotlessCheck", "checkstyleMain", "test");

  private final Map<String, String> projectPaths = new ConcurrentHashMap<>();
  private final Map<String, List<PluginRegistration>> pluginsMap = new ConcurrentHashMap<>();
  private final Map<String, List<WorkflowConfig>> workflowsMap = new ConcurrentHashMap<>();

  /** Public constructor. */
  public AppConfigService() {
    super();
  }

  /**
   * Get the external project path for a session.
   *
   * @param sessionId the session identifier
   * @return Mono containing the project path
   */
  public Mono<String> getProjectPath(final String sessionId) {
    final String result = sessionId != null ? projectPaths.get(sessionId) : null;
    return Mono.just(result != null ? result : "");
  }

  /**
   * Set the external project path override for a session.
   *
   * @param sessionId the session identifier
   * @param path the project path
   * @return Mono that completes when the path is set
   */
  public Mono<Void> setProjectPath(final String sessionId, final String path) {
    if (sessionId != null && path != null) {
      projectPaths.put(sessionId, path);
    }
    return Mono.empty();
  }

  /**
   * Get all registered plugins for a session.
   *
   * @param sessionId the session identifier
   * @return Flux of plugins
   */
  public Flux<PluginRegistration> getPlugins(final String sessionId) {
    final List<PluginRegistration> result = sessionId != null ? pluginsMap.get(sessionId) : null;
    return Flux.fromIterable(result != null ? result : List.of());
  }

  /**
   * Set the registered plugins for a session.
   *
   * @param sessionId the session identifier
   * @param plugins the list of plugins
   * @return Mono that completes when plugins are set
   */
  public Mono<Void> setPlugins(final String sessionId, final List<PluginRegistration> plugins) {
    if (sessionId != null && plugins != null) {
      pluginsMap.put(sessionId, List.copyOf(plugins));
    }
    return Mono.empty();
  }

  /**
   * Get the plugin name for a session. For now, returns the first plugin's name.
   *
   * @param sessionId the session identifier
   * @return Mono containing the plugin name
   */
  public Mono<String> getPluginName(final String sessionId) {
    return getPlugins(sessionId).next().map(PluginRegistration::name).defaultIfEmpty("");
  }

  /**
   * Get the plugin configuration for a session. For now, returns the first plugin's configuration.
   *
   * @param sessionId the session identifier
   * @return Mono containing the plugin configuration map
   */
  public Mono<Map<String, Object>> getPluginConfig(final String sessionId) {
    return getPlugins(sessionId)
        .next()
        .map(PluginRegistration::pluginConfig)
        .defaultIfEmpty(Map.of());
  }

  /**
   * Get the list of tasks for a session. Defaults to a standard list if not set.
   *
   * @param sessionId the session identifier
   * @return Flux of tasks
   */
  public Flux<String> getTasks(final String sessionId) {
    return Flux.fromIterable(DEFAULT_TASKS);
  }

  /**
   * Get the list of workflows for a session.
   *
   * @param sessionId the session identifier
   * @return Flux of workflows
   */
  public Flux<WorkflowConfig> getWorkflows(final String sessionId) {
    final List<WorkflowConfig> override = sessionId != null ? workflowsMap.get(sessionId) : null;
    return Flux.fromIterable(override != null ? override : List.of());
  }

  /**
   * Set the workflows override for a session.
   *
   * @param sessionId the session identifier
   * @param workflowsList the list of workflows
   * @return Mono that completes when workflows are set
   */
  public Mono<Void> setWorkflows(final String sessionId, final List<WorkflowConfig> workflowsList) {
    if (sessionId != null) {
      if (workflowsList != null) {
        workflowsMap.put(sessionId, List.copyOf(workflowsList));
      } else {
        workflowsMap.remove(sessionId);
      }
    }
    return Mono.empty();
  }

  /**
   * Get the execution timeout in seconds for a session.
   *
   * @param sessionId the session identifier
   * @return Mono containing the timeout
   */
  public Mono<Long> getExecutionTimeout(final String sessionId) {
    return Mono.just(DEFAULT_TIMEOUT);
  }

  /**
   * Get the modified files log directory for a session.
   *
   * @param sessionId the session identifier
   * @return Mono containing the log directory
   */
  public Mono<String> getFileLogDir(final String sessionId) {
    return Mono.just(DEFAULT_FILE_LOG);
  }

  /**
   * Get the results log directory for a session.
   *
   * @param sessionId the session identifier
   * @return Mono containing the log directory
   */
  public Mono<String> getResultLogDir(final String sessionId) {
    return Mono.just(DEFAULT_RES_LOG);
  }

  /**
   * Get all configurations for a session as a map.
   *
   * @param sessionId the session identifier
   * @return Mono containing map of configurations
   */
  public Mono<Map<String, Object>> getAllConfigs(final String sessionId) {
    return Mono.zip(
            getProjectPath(sessionId),
            getPlugins(sessionId).collectList(),
            getWorkflows(sessionId).collectList(),
            getExecutionTimeout(sessionId),
            getFileLogDir(sessionId),
            getResultLogDir(sessionId))
        .map(
            tuple -> {
              final Map<String, Object> configs = new java.util.LinkedHashMap<>();
              configs.put("projectPath", tuple.getT1());
              configs.put("plugins", tuple.getT2());
              configs.put("workflows", tuple.getT3());
              configs.put("executionTimeout", tuple.getT4());
              configs.put("fileLogDir", tuple.getT5());
              configs.put("resultLogDir", tuple.getT6());
              return configs;
            });
  }

  /**
   * Get all active session IDs currently in memory.
   *
   * @return Flux of session IDs
   */
  public Flux<String> getActiveSessionIds() {
    final java.util.Set<String> active = new java.util.HashSet<>();
    active.addAll(projectPaths.keySet());
    active.addAll(pluginsMap.keySet());
    active.addAll(workflowsMap.keySet());
    return Flux.fromIterable(active);
  }

  /**
   * Check if a session is active in memory.
   *
   * @param sessionId the session identifier
   * @return Mono containing true if active
   */
  public Mono<Boolean> isActive(final String sessionId) {
    return getActiveSessionIds()
        .collectList()
        .map(list -> sessionId != null && list.contains(sessionId));
  }
}
