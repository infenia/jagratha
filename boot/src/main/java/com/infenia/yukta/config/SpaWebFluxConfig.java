// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Infenia Private Limited

package com.infenia.yukta.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

/** Serves the React SPA static assets from classpath:/static/. */
@Configuration
public class SpaWebFluxConfig implements WebFluxConfigurer {

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    // Serve index.html at root for SPA entry point (no cache)
    registry
        .addResourceHandler("/")
        .addResourceLocations("classpath:/static/index.html")
        .setCacheControl(org.springframework.http.CacheControl.noCache().mustRevalidate());

    // Serve all static assets (JS, CSS, fonts, images) with cache
    registry
        .addResourceHandler(
            "/**/*.js",
            "/**/*.css",
            "/**/*.woff",
            "/**/*.woff2",
            "/**/*.ttf",
            "/**/*.svg",
            "/**/*.png",
            "/**/*.ico",
            "/**/*.webmanifest")
        .addResourceLocations("classpath:/static/")
        .setCacheControl(
            org.springframework.http.CacheControl.maxAge(365, java.util.concurrent.TimeUnit.DAYS));

    // Serve all other HTML files (SPA routes) as index.html for client-side routing
    // This handles /sessions/:id, /history, etc. by serving index.html
    registry
        .addResourceHandler("/**")
        .addResourceLocations("classpath:/static/index.html")
        .setCacheControl(org.springframework.http.CacheControl.noCache().mustRevalidate());
  }
}
