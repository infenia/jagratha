package io.jagratha.jagratha.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "jagratha")
public class JagrathaConfig {
    private ExternalProject externalProject = new ExternalProject();

    @Data
    public static class ExternalProject {
        private String path;
        private String gradlePath;
    }
}
