// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Infenia Private Limited

import { http, HttpResponse } from 'msw';
import type { SessionListItems } from '../../src/features/sessions/types/session';

const BASE_URL = process.env.BASE_URL || 'http://localhost:5173';

export const apiHandlers = [
  // Get session summaries
  http.get(`${BASE_URL}/api/sessions/summaries`, ({ request }) => {
    return HttpResponse.json(
      {
        timestamp: new Date().toISOString(),
        status: 200,
        message: 'Sessions retrieved successfully',
        path: '/api/sessions/summaries',
        data: {
          sessions: [
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
        } as SessionListItems,
      },
      { status: 200 }
    );
  }),

  // Get empty sessions list
  http.get(`${BASE_URL}/api/sessions/summaries?empty=true`, () => {
    return HttpResponse.json(
      {
        timestamp: new Date().toISOString(),
        status: 200,
        message: 'Sessions retrieved successfully',
        path: '/api/sessions/summaries',
        data: {
          sessions: [],
        } as SessionListItems,
      },
      { status: 200 }
    );
  }),

  // Get single session details
  http.get(`${BASE_URL}/api/sessions/:sessionId`, ({ params }) => {
    const { sessionId } = params;
    return HttpResponse.json(
      {
        timestamp: new Date().toISOString(),
        status: 200,
        message: 'Session retrieved successfully',
        path: `/api/sessions/${sessionId}`,
        data: {
          sessionId,
          name: 'Build Pipeline',
          description: 'CI/CD workflow for main branch',
          initiator: 'github-actions',
          tags: ['production', 'automated'],
          projectPath: 'infenia/yukta',
          workflowCount: 3,
        },
      },
      { status: 200 }
    );
  }),

  // Simulate server error
  http.get(`${BASE_URL}/api/sessions/summaries?error=true`, () => {
    return HttpResponse.json(
      {
        timestamp: new Date().toISOString(),
        status: 500,
        message: 'Internal server error',
        path: '/api/sessions/summaries',
        error: 'Database connection failed',
      },
      { status: 500 }
    );
  }),
];

export const handlers = apiHandlers;
