// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Infenia Private Limited

package com.infenia.yukta.config;

import lombok.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.reactive.config.PathMatchConfigurer;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.resource.PathResourceResolver;
import org.springframework.web.reactive.resource.ResourceChainRegistration;

/** Serves the React SPA static assets with a fallback to index.html for client-side routing. */
@Slf4j
@Configuration
public class SpaWebFluxConfig implements WebFluxConfigurer {

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry
        .addResourceHandler("/**")
        .addResourceLocations("classpath:/static/")
        .resourceChain(true)
        .addResolver(new PathResourceResolver() {
          @Override
          protected org.springframework.core.io.Resource resolveResourceInternal(
              org.springframework.web.server.ServerWebExchange exchange,
              String requestPath,
              java.util.List<? extends org.springframework.core.io.Resource>
                  locations,
              org.springframework.web.reactive.resource.ResourceResolverChain chain) {
            // Try to find the actual resource
            var resource = chain.resolveResource(
                exchange, requestPath, locations);

            if (resource != null && resource.isReadable()) {
              return resource;
            }

            // Don't fall back for API, actuator, or SSE routes
            if (requestPath.startsWith("/api")
                || requestPath.startsWith("/actuator")
                || requestPath.startsWith("/sse")) {
              return null;
            }

            // Don't fall back for file requests (has an extension with a dot)
            if (requestPath.contains(".") && !requestPath.endsWith("/")) {
              return null;
            }

            // Fall back to index.html for client-side routes
            log.debug("Falling back to index.html for route: {}", requestPath);
            try {
              return chain.resolveResource(
                  exchange, "index.html", locations);
            } catch (Exception e) {
              log.warn("Failed to resolve index.html fallback", e);
              return null;
            }
          }
        })
        .addTransformer(
            new org.springframework.web.reactive.resource.
                CssLinkResourceTransformer())
        .addTransformer(
            new org.springframework.web.reactive.resource.
                VersionResourceResolver()
                .addFixedVersionStrategy(
                    "1.0.0",
                    "/**/*.js",
                    "/**/*.css",
                    "/**/*.woff",
                    "/**/*.woff2",
                    "/**/*.ttf"));

    registry.addResourceHandler("/index.html")
        .addResourceLocations("classpath:/static/index.html")
        .setCacheControl(CacheControl.noCache().mustRevalidate());
  }

  @Override
  public void configurePathMatching(PathMatchConfigurer configurer) {
    configurer.setUseTrailingSlashMatch(true);
  }
}
