// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Infenia Private Limited

import { describe, it, expect, beforeAll, afterAll, vi, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { SessionDetailsPage } from '../SessionDetailsPage';
import { setupServer } from 'msw/node';
import { http, HttpResponse } from 'msw';
import { QueryClientProvider } from '@tanstack/react-query';

const createTestQueryClient = () => {
  return new (require('@tanstack/react-query').QueryClient)({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
};

vi.mock('react-router', async () => {
  const actual = await vi.importActual('react-router');
  return {
    ...actual,
    useParams: () => ({ sessionId: 'sess-123' }),
  };
});

const server = setupServer(
  http.get('/api/sessions/sess-123', () => {
    return HttpResponse.json({
      status: 200,
      data: {
        sessionId: 'sess-123',
        name: 'Test Session',
        description: 'Desc',
        initiator: 'user1',
        tags: ['tag1'],
        projectPath: '/path',
        workflowIds: ['wf1'],
      },
    });
  }),
  http.get('/api/sessions/sess-123/workflows', () => {
    return HttpResponse.json({
      status: 200,
      data: {
        workflows: [
          {
            workflowId: 'wf1',
            description: 'Workflow 1',
            nodeCount: 5,
            edgeCount: 4,
            status: 'SUCCESS',
          },
        ],
      },
    });
  })
);

beforeAll(() => server.listen());
afterAll(() => server.close());

describe('SessionDetailsPage', () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it('renders session details and workflows', async () => {
    const queryClient = createTestQueryClient();
    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>
          <SessionDetailsPage />
        </MemoryRouter>
      </QueryClientProvider>
    );

    await waitFor(() => {
      expect(screen.getByText('Test Session')).toBeInTheDocument();
    });

    await waitFor(() => {
      expect(screen.getByText('Associated Workflows (1)')).toBeInTheDocument();
    });
  });
});
