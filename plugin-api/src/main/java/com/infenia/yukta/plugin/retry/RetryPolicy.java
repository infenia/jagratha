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
package com.infenia.yukta.plugin.retry;

/**
 * Retry policy configuration for node execution failures.
 *
 * @param maxAttempts maximum number of retry attempts (0 = no retries)
 * @param backoffType the backoff strategy (FIXED or EXPONENTIAL)
 * @param initialDelay initial delay in milliseconds before first retry
 * @param maxDelay maximum delay in milliseconds between retries (for EXPONENTIAL backoff)
 */
public record RetryPolicy(
    int maxAttempts, BackoffType backoffType, long initialDelay, long maxDelay) {

  /**
   * Create a no-retry policy (zero attempts).
   *
   * @return a RetryPolicy with maxAttempts = 0
   */
  public static RetryPolicy none() {
    return new RetryPolicy(0, BackoffType.FIXED, 0, 0);
  }

  /**
   * Create a fixed-delay retry policy.
   *
   * @param maxAttempts the maximum number of retry attempts
   * @param delayMs the fixed delay in milliseconds between retries
   * @return a RetryPolicy with FIXED backoff
   */
  public static RetryPolicy fixed(final int maxAttempts, final long delayMs) {
    return new RetryPolicy(maxAttempts, BackoffType.FIXED, delayMs, delayMs);
  }

  /**
   * Create an exponential backoff retry policy.
   *
   * @param maxAttempts the maximum number of retry attempts
   * @param initialDelayMs the initial delay in milliseconds
   * @param maxDelayMs the maximum delay in milliseconds
   * @return a RetryPolicy with EXPONENTIAL backoff
   */
  public static RetryPolicy exponential(
      final int maxAttempts, final long initialDelayMs, final long maxDelayMs) {
    return new RetryPolicy(maxAttempts, BackoffType.EXPONENTIAL, initialDelayMs, maxDelayMs);
  }
}
