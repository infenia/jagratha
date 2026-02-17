package io.jagratha.jagratha.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JagrathaConfigServiceTest {

  private JagrathaConfig staticConfig;
  private JagrathaConfigService configService;

  @BeforeEach
  void setUp() {
    staticConfig = new JagrathaConfig(
        new JagrathaConfig.ExternalProject("/static/path", "/static/gradle"),
        List.of("staticTask"),
        100L,
        new JagrathaConfig.Logs("/static/files", "/static/results")
    );
    configService = new JagrathaConfigService(staticConfig);
  }

  @Test
  void testDefaultValues() {
    assertEquals("/static/path", configService.getExternalProjectPath());
    assertEquals("/static/gradle", configService.getGradlePath());
    assertEquals(List.of("staticTask"), configService.getTasks());
    assertEquals(100L, configService.getExecutionTimeout());
    assertEquals("/static/files", configService.getModifiedFilesLogDir());
    assertEquals("/static/results", configService.getGradleResultsLogDir());
  }

  @Test
  void testApiOverrides() {
    configService.setExternalProjectPath("/api/path");
    configService.setGradlePath("/api/gradle");
    configService.setTasks(List.of("apiTask"));
    configService.setExecutionTimeout(200L);
    configService.setModifiedFilesLogDir("/api/files");
    configService.setGradleResultsLogDir("/api/results");

    assertEquals("/api/path", configService.getExternalProjectPath());
    assertEquals("/api/gradle", configService.getGradlePath());
    assertEquals(List.of("apiTask"), configService.getTasks());
    assertEquals(200L, configService.getExecutionTimeout());
    assertEquals("/api/files", configService.getModifiedFilesLogDir());
    assertEquals("/api/results", configService.getGradleResultsLogDir());
  }

  @Test
  void testPartialApiOverrides() {
    configService.setExternalProjectPath("/api/path");

    assertEquals("/api/path", configService.getExternalProjectPath());
    assertEquals("/static/gradle", configService.getGradlePath()); // still static
  }
}
