// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Infenia Private Limited

import { describe, it, expect, beforeAll, afterAll, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router';
import { SessionDetailsPage } from '../SessionDetailsPage';
import { setupServer } from 'msw/node';
import { http, HttpResponse } from 'msw';
import { createTestQueryClient } from '@/test/utils/testUtils';
import { QueryClientProvider } from '@tanstack/react-query';

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

  it('shows loading state', () => {
    server.use(
      http.get('/api/sessions/:sessionId', () => new Promise(() => {}))
    );

    const queryClient = createTestQueryClient();
    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>
          <SessionDetailsPage />
        </MemoryRouter>
      </QueryClientProvider>
    );

    expect(screen.getByText('Loading session details...')).toBeInTheDocument();
  });

  it('shows not found state when session is null', () => {
    const queryClient = createTestQueryClient();
    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>
          <SessionDetailsPage />
        </MemoryRouter>
      </QueryClientProvider>
    );

    expect(screen.queryByText('Session not found')).not.toBeInTheDocument();
  });
});
