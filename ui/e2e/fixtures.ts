// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Infenia Private Limited

import { test as base } from '@playwright/test';
import type { SessionListItem } from '../src/features/sessions/types/session';

export interface TestFixtures {
  mockSessions: SessionListItem[];
}

export const test = base.extend<TestFixtures>({
  mockSessions: [
    {
      sessionId: 'session-1',
      name: 'Build Pipeline',
      description: 'CI/CD workflow for main branch',
      initiator: 'github-actions',
      tags: ['production', 'automated'],
      projectPath: 'infenia/yukta',
      workflowCount: 3,
    },
    {
      sessionId: 'session-2',
      name: 'Code Review',
      description: 'Quality gate for PR #42',
      initiator: 'arun@infenia.com',
      tags: ['review', 'quality-gate'],
      projectPath: 'infenia/yukta',
      workflowCount: 5,
    },
    {
      sessionId: 'session-3',
      name: 'Data Pipeline',
      description: 'Nightly ETL job',
      initiator: 'scheduler',
      tags: ['nightly'],
      projectPath: 'infenia/data-platform',
      workflowCount: 2,
    },
  ],
});

export { expect } from '@playwright/test';
