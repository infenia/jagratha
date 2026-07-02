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

import jakarta.annotation.Nullable;

/**
 * Search criteria for filtering plugin logs.
 *
 * @param executionId execution identifier (required)
 * @param pluginId filter by plugin ID (optional)
 * @param stream filter by log stream type (optional)
 * @param limit maximum number of entries to return
 * @param offset starting index for pagination
 */
public record LogSearchCriteria(
    String executionId,
    @Nullable String pluginId,
    @Nullable LogStream stream,
    int limit,
    int offset) {

  /** Default limit for log searches. */
  public static final int DEFAULT_LIMIT = 1000;

  /**
   * Create search criteria with defaults.
   *
   * @param executionId execution identifier
   * @return search criteria with default limit and zero offset
   */
  public static LogSearchCriteria of(final String executionId) {
    return new LogSearchCriteria(executionId, null, null, DEFAULT_LIMIT, 0);
  }
}
