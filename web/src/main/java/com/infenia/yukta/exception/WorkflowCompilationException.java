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
package com.infenia.yukta.exception;

/** Thrown when a workflow fails to compile or cache during preparation. */
public class WorkflowCompilationException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final String sessionId;
  private final String workflowId;

  /**
   * Create a new workflow compilation exception.
   *
   * @param sessionId the session identifier
   * @param workflowId the workflow identifier
   * @param message error message
   * @param cause the underlying exception
   */
  public WorkflowCompilationException(
      final String sessionId,
      final String workflowId,
      final String message,
      final Throwable cause) {
    super(message, cause);
    this.sessionId = sessionId;
    this.workflowId = workflowId;
  }

  public String getSessionId() {
    return sessionId;
  }

  public String getWorkflowId() {
    return workflowId;
  }
}
