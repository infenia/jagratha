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
package com.infenia.yukta.plugin.core;

/**
 * Enumeration of node execution states in a workflow.
 *
 * <p>States follow the progression: PENDING → READY → RUNNING → (COMPLETED | FAILED | CANCELLED)
 */
public enum NodeState {
  /** Node is pending, waiting for dependencies to complete. */
  PENDING,

  /** Node is ready, all dependencies have completed. */
  READY,

  /** Node is currently executing. */
  RUNNING,

  /** Node completed successfully. */
  COMPLETED,

  /** Node failed during execution. */
  FAILED,

  /** Node was cancelled during execution. */
  CANCELLED;

  /**
   * Check if this state is terminal.
   *
   * @return true if COMPLETED, FAILED, or CANCELLED; false otherwise
   */
  public boolean isTerminal() {
    return this == COMPLETED || this == FAILED || this == CANCELLED;
  }
}
