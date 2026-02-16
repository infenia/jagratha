package io.jagratha.jagratha;

import io.jagratha.jagratha.config.JagrathaConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class JagrathaConfigTest {

    @Autowired
    private JagrathaConfig config;

    @Test
    void contextLoads() {
        assertNotNull(config);
        assertNotNull(config.getExternalProject());
    }

    @Test
    void testConfigValues() {
        // Values from config.yaml
        assertEquals("/tmp/external-project", config.getExternalProject().getPath());
        assertEquals("./gradlew", config.getExternalProject().getGradlePath());
    }
}
