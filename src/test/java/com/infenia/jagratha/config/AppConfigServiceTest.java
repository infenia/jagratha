package com.infenia.jagratha.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AppConfigServiceTest {

  private AppConfig staticConfig;
  private AppConfigService configService;

  @BeforeEach
  void setUp() {
    staticConfig =
        new AppConfig(
            new AppConfig.ExternalProject("/static/path"),
            "gradle",
            Map.of("gradlePath", "/static/gradle"),
            List.of("staticTask"),
            List.of(),
            100L,
            new AppConfig.Logs("/static/files", "/static/results"));
    configService = new AppConfigService(staticConfig);
  }

  @Test
  void testDefaultValues() {
    String sessionId = "sess-1";
    assertEquals("/static/path", configService.getProjectPath(sessionId));
    assertEquals("gradle", configService.getPluginName(sessionId));
    assertEquals(Map.of("gradlePath", "/static/gradle"), configService.getPluginConfig(sessionId));
    assertEquals(List.of("staticTask"), configService.getTasks(sessionId));
    assertEquals(100L, configService.getExecutionTimeout(sessionId));
    assertEquals("/static/files", configService.getFileLogDir(sessionId));
    assertEquals("/static/results", configService.getResultLogDir(sessionId));
  }

  @Test
  void testApiOverrides() {
    String sessionId = "sess-1";
    configService.setProjectPath(sessionId, "/api/path");
    configService.setPluginName(sessionId, "maven");
    configService.setPluginConfig(sessionId, Map.of("mavenPath", "/api/maven"));
    configService.setTasks(sessionId, List.of("apiTask"));
    configService.setExecutionTimeout(sessionId, 200L);
    configService.setFileLogDir(sessionId, "/api/files");
    configService.setResultLogDir(sessionId, "/api/results");

    assertEquals("/api/path", configService.getProjectPath(sessionId));
    assertEquals("maven", configService.getPluginName(sessionId));
    assertEquals(Map.of("mavenPath", "/api/maven"), configService.getPluginConfig(sessionId));
    assertEquals(List.of("apiTask"), configService.getTasks(sessionId));
    assertEquals(200L, configService.getExecutionTimeout(sessionId));
    assertEquals("/api/files", configService.getFileLogDir(sessionId));
    assertEquals("/api/results", configService.getResultLogDir(sessionId));

    // Another session should still have defaults
    String otherSession = "sess-2";
    assertEquals("/static/path", configService.getProjectPath(otherSession));
  }

  @Test
  void testPartialApiOverrides() {
    String sessionId = "sess-1";
    configService.setProjectPath(sessionId, "/api/path");

    assertEquals("/api/path", configService.getProjectPath(sessionId));
    assertEquals(
        Map.of("gradlePath", "/static/gradle"),
        configService.getPluginConfig(sessionId)); // still static
  }

  @Test
  void testFallbacks() {
    AppConfig emptyConfig = new AppConfig(null, null, null, null, null, null, null);
    AppConfigService emptyService = new AppConfigService(emptyConfig);
    String sessionId = "sess-1";

    assertEquals(300L, emptyService.getExecutionTimeout(sessionId));
    String home = System.getProperty("user.home");
    assertEquals(home + "/.jagratha/modified-files", emptyService.getFileLogDir(sessionId));
    assertEquals(home + "/.jagratha/results", emptyService.getResultLogDir(sessionId));
  }
}
