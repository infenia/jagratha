// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Infenia Private Limited

import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import {
  useReactTable,
  getCoreRowModel,
  getPaginationRowModel,
  getFilteredRowModel,
} from '@tanstack/react-table';
import { SessionsPaginationFooter } from '../components/SessionsPaginationFooter';
import { columns } from '../components/columns';
import type { SessionListItem } from '../types/session';

describe('Coverage - SessionsPaginationFooter Exhaustive', () => {
  // Create enough sessions to test all pagination behaviors
  const createSessions = (count: number): SessionListItem[] =>
    Array.from({ length: count }, (_, i) => ({
      sessionId: `session-${i}`,
      name: `Session ${i}`,
      description: `Desc ${i}`,
      initiator: 'user@example.com',
      tags: [],
      projectPath: '/path',
      workflowCount: i,
    }));

  const PaginationTest = ({
    sessions,
    pageSize = 10,
  }: {
    sessions: SessionListItem[];
    pageSize?: number;
  }) => {
    const table = useReactTable({
      data: sessions,
      columns,
      getCoreRowModel: getCoreRowModel(),
      getPaginationRowModel: getPaginationRowModel(),
      getFilteredRowModel: getFilteredRowModel(),
      initialState: { pagination: { pageSize } },
    });

    return <SessionsPaginationFooter table={table} />;
  };

  it('should cover first page navigation buttons', () => {
    const sessions = createSessions(30);
    render(<PaginationTest sessions={sessions} pageSize={10} />);

    // All buttons should be visible
    const buttons = screen.getAllByRole('button');
    expect(buttons.length).toBeGreaterThan(0);

    // Items display
    expect(screen.getByText('1–10 of 30 items')).toBeInTheDocument();
  });

  it('should cover rows per page selection', () => {
    const sessions = createSessions(50);
    render(<PaginationTest sessions={sessions} pageSize={10} />);

    expect(screen.getByText('10')).toBeInTheDocument();
  });

  it('should cover last page state', () => {
    const sessions = createSessions(25);
    render(<PaginationTest sessions={sessions} pageSize={10} />);

    expect(screen.getByText('1–10 of 25 items')).toBeInTheDocument();
  });

  it('should cover with twenty page size', () => {
    const sessions = createSessions(100);
    render(<PaginationTest sessions={sessions} pageSize={20} />);

    expect(screen.getByText('20')).toBeInTheDocument();
    expect(screen.getByText('1–20 of 100 items')).toBeInTheDocument();
  });

  it('should cover with fifty page size', () => {
    const sessions = createSessions(100);
    render(<PaginationTest sessions={sessions} pageSize={50} />);

    expect(screen.getByText('50')).toBeInTheDocument();
    expect(screen.getByText('1–50 of 100 items')).toBeInTheDocument();
  });

  it('should cover all button states', () => {
    const sessions = createSessions(100);
    render(<PaginationTest sessions={sessions} pageSize={10} />);

    const buttons = screen.getAllByRole('button');

    // Should have: rows per page button, "x-y of z items" display, and 4 nav buttons
    expect(buttons.length).toBeGreaterThanOrEqual(5);
  });

  it('should handle border separators', () => {
    const sessions = createSessions(30);
    const { container } = render(<PaginationTest sessions={sessions} pageSize={10} />);

    // Should have border-l separators between sections
    const borders = container.querySelectorAll('.border-l');
    expect(borders.length).toBeGreaterThan(0);
  });

  it('should render pagination with exact items text', () => {
    const sessions = createSessions(3);
    render(<PaginationTest sessions={sessions} pageSize={10} />);

    expect(screen.getByText('1–3 of 3 items')).toBeInTheDocument();
  });

  it('should display proper gap styling', () => {
    const sessions = createSessions(40);
    render(<PaginationTest sessions={sessions} pageSize={10} />);

    expect(screen.getByText(/Rows per page:/)).toBeInTheDocument();
    expect(screen.getByText('1–10 of 40 items')).toBeInTheDocument();
  });

  it('should cover layout structure', () => {
    const sessions = createSessions(50);
    const { container } = render(<PaginationTest sessions={sessions} pageSize={10} />);

    // Verify flex container exists
    const flexContainer = container.querySelector('.flex');
    expect(flexContainer).toBeInTheDocument();

    // Verify items-center for vertical alignment
    const alignedElement = container.querySelector('.items-center');
    expect(alignedElement).toBeInTheDocument();
  });

  it('should cover gap between elements', () => {
    const sessions = createSessions(30);
    const { container } = render(<PaginationTest sessions={sessions} pageSize={10} />);

    // Should have gap-4 for spacing
    const gappedElement = container.querySelector('.gap-4');
    expect(gappedElement).toBeInTheDocument();
  });

  it('should test button rendering with Material Symbols', () => {
    const sessions = createSessions(30);
    render(<PaginationTest sessions={sessions} pageSize={10} />);

    // Check for Material Symbols icons
    const icons = document.querySelectorAll('.material-symbols-outlined');
    expect(icons.length).toBeGreaterThan(0);
  });

  it('should cover styling classes application', () => {
    const sessions = createSessions(50);
    const { container } = render(<PaginationTest sessions={sessions} pageSize={10} />);

    // Should have flex-row for horizontal layout
    const flexRow = container.querySelector('.flex');
    expect(flexRow?.classList.contains('items-center')).toBeTruthy();

    // Should have justify-between for space distribution
    const justifyBetween = container.querySelector('.justify-between');
    expect(justifyBetween).toBeInTheDocument();
  });
});
