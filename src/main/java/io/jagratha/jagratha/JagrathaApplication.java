package io.jagratha.jagratha;

import io.jagratha.jagratha.config.JagrathaConfig;
import lombok.experimental.UtilityClass;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(JagrathaConfig.class)
@UtilityClass
public class JagrathaApplication {

  public static void main(final String[] args) {
    SpringApplication.run(JagrathaApplication.class, args);
  }
}
