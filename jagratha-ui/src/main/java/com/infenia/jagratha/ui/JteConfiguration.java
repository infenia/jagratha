package com.infenia.jagratha.ui;

import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JteConfiguration {

    @Bean
    public TemplateEngine templateEngine() {
        // This will look for precompiled templates in the classpath
        return TemplateEngine.createPrecompiled(ContentType.Html);
    }
}
