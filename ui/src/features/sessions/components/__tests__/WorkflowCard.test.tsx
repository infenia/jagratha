// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Infenia Private Limited

import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { WorkflowCard } from '../WorkflowCard';
import type { WorkflowSummary } from '../../types/session';

vi.mock('react-router', async () => {
  const actual = await vi.importActual('react-router');
  return {
    ...actual,
    useNavigate: () => vi.fn(),
  };
});

const mockWorkflow: WorkflowSummary = {
  workflowId: 'wf1',
  description: 'Test Workflow',
  nodeCount: 5,
  edgeCount: 4,
  status: 'SUCCESS',
};

describe('WorkflowCard', () => {
  it('renders workflow information', () => {
    render(
      <MemoryRouter>
        <WorkflowCard sessionId="sess-123" workflow={mockWorkflow} />
      </MemoryRouter>
    );

    expect(screen.getByText('wf1')).toBeInTheDocument();
    expect(screen.getByText('Test Workflow')).toBeInTheDocument();
    expect(screen.getByText('5 Nodes')).toBeInTheDocument();
    expect(screen.getByText('4 Edges')).toBeInTheDocument();
  });

  it('displays correct status badge for SUCCESS', () => {
    render(
      <MemoryRouter>
        <WorkflowCard sessionId="sess-123" workflow={mockWorkflow} />
      </MemoryRouter>
    );

    expect(screen.getByText('Completed')).toBeInTheDocument();
  });

  it('displays "Running" for RUNNING status', () => {
    const runningWorkflow = { ...mockWorkflow, status: 'RUNNING' as const };
    render(
      <MemoryRouter>
        <WorkflowCard sessionId="sess-123" workflow={runningWorkflow} />
      </MemoryRouter>
    );

    expect(screen.getByText('Running')).toBeInTheDocument();
  });

  it('displays "Failed" for FAILURE status', () => {
    const failedWorkflow = { ...mockWorkflow, status: 'FAILURE' as const };
    render(
      <MemoryRouter>
        <WorkflowCard sessionId="sess-123" workflow={failedWorkflow} />
      </MemoryRouter>
    );

    expect(screen.getByText('Failed')).toBeInTheDocument();
  });

  it('displays "Not Run" for null status', () => {
    const notRunWorkflow = { ...mockWorkflow, status: null };
    render(
      <MemoryRouter>
        <WorkflowCard sessionId="sess-123" workflow={notRunWorkflow} />
      </MemoryRouter>
    );

    expect(screen.getByText('Not Run')).toBeInTheDocument();
  });

  it('navigates on card click', () => {
    const { container } = render(
      <MemoryRouter>
        <WorkflowCard sessionId="sess-123" workflow={mockWorkflow} />
      </MemoryRouter>
    );

    const card = container.querySelector('.group');
    expect(card).toHaveClass('cursor-pointer');
  });
});
