/*
 * Copyright 2026 Infenia Private Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.infenia.yukta.logging.api;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for plugin log storage.
 *
 * <p>Manages retention period settings with hardcoded maximum enforcement. User-configured
 * retention is capped at the hardcoded maximum.
 */
@Component
@ConfigurationProperties(prefix = "yukta.logs.store")
@RequiredArgsConstructor
public class PluginLogStoreConfig {

  /** Hardcoded maximum retention period (24 hours). Non-configurable. */
  private static final int MAX_RETENTION_MINUTES = 1440;

  /** User-configurable default retention period in minutes. */
  private Retention retention = new Retention();

  /**
   * Get the effective retention duration.
   *
   * <p>Returns the minimum of user-configured retention and hardcoded maximum.
   *
   * @return effective retention duration
   */
  public Duration getEffectiveRetention() {
    int configured = retention.getDefaultPeriodMinutes();
    int capped = Math.min(configured, MAX_RETENTION_MINUTES);
    return Duration.ofMinutes(capped);
  }

  /**
   * Get the hardcoded maximum retention in minutes.
   *
   * @return max retention minutes
   */
  public int getMaxRetentionMinutes() {
    return MAX_RETENTION_MINUTES;
  }

  /** Nested retention configuration. */
  public static class Retention {
    /** Default retention period in minutes. */
    private int defaultPeriodMinutes = 30;

    public int getDefaultPeriodMinutes() {
      return defaultPeriodMinutes;
    }

    public void setDefaultPeriodMinutes(int defaultPeriodMinutes) {
      this.defaultPeriodMinutes = defaultPeriodMinutes;
    }
  }

  public Retention getRetention() {
    return retention;
  }

  public void setRetention(Retention retention) {
    this.retention = retention;
  }
}
