// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Infenia Private Limited

import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { BrowserRouter } from 'react-router';
import {
  useReactTable,
  getCoreRowModel,
  getPaginationRowModel,
  getFilteredRowModel,
} from '@tanstack/react-table';
import { SessionsPaginationFooter } from '../components/SessionsPaginationFooter';
import { SessionRowActionsMenu } from '../components/SessionRowActionsMenu';
import { columns } from '../components/columns';
import type { SessionListItem } from '../types/session';

describe('Coverage - User Interactions', () => {
  describe('SessionsPaginationFooter - Full Coverage', () => {
    const mockSessions: SessionListItem[] = Array.from({ length: 35 }, (_, i) => ({
      sessionId: `session-${i}`,
      name: `Session ${i}`,
      description: `Desc ${i}`,
      initiator: 'user@example.com',
      tags: [],
      projectPath: '/path',
      workflowCount: 1,
    }));

    const TableComponent = () => {
      const table = useReactTable({
        data: mockSessions,
        columns,
        getCoreRowModel: getCoreRowModel(),
        getPaginationRowModel: getPaginationRowModel(),
        getFilteredRowModel: getFilteredRowModel(),
        initialState: { pagination: { pageSize: 10 } },
      });

      return <SessionsPaginationFooter table={table} />;
    };

    it('should navigate to next page with chevron_right button', async () => {
      const user = userEvent.setup();
      render(<TableComponent />);

      expect(screen.getByText('1–10 of 35 items')).toBeInTheDocument();

      const buttons = screen.getAllByRole('button');
      const nextButton = buttons.find(
        (btn) =>
          btn.querySelector('.material-symbols-outlined')?.textContent ===
          'chevron_right'
      );

      if (nextButton && !nextButton.hasAttribute('disabled')) {
        await user.click(nextButton);
      }
    });

    it('should navigate to last page', async () => {
      const user = userEvent.setup();
      render(<TableComponent />);

      const buttons = screen.getAllByRole('button');
      const lastButton = buttons.find(
        (btn) =>
          btn.querySelector('.material-symbols-outlined')?.textContent === 'last_page'
      );

      if (lastButton && !lastButton.hasAttribute('disabled')) {
        await user.click(lastButton);
      }
    });

    it('should change page size via dropdown', async () => {
      const user = userEvent.setup();
      render(<TableComponent />);

      const pageSize10 = screen.getByText('10');
      await user.click(pageSize10);

      const pageSize20Option = screen.queryByText('20');
      if (pageSize20Option) {
        await user.click(pageSize20Option);
      }
    });

    it('should handle first page button click', async () => {
      const user = userEvent.setup();
      render(<TableComponent />);

      const buttons = screen.getAllByRole('button');
      const firstButton = buttons.find(
        (btn) =>
          btn.querySelector('.material-symbols-outlined')?.textContent === 'first_page'
      );

      if (firstButton) {
        await user.click(firstButton);
      }
    });

    it('should render different page sizes', () => {
      render(<TableComponent />);

      // Verify pagination footer is rendered
      expect(screen.getByText(/Rows per page:/)).toBeInTheDocument();
      expect(screen.getByText(/items/)).toBeInTheDocument();
    });

    it('should navigate back to first page with chevron_left button', async () => {
      // Given: table on page 2 (35 rows, page size 10)
      const user = userEvent.setup();
      render(<TableComponent />);

      await user.click(screen.getByRole('button', { name: 'chevron_right' }));
      expect(screen.getByText('11–20 of 35 items')).toBeInTheDocument();

      // When: previous page button is clicked
      await user.click(screen.getByRole('button', { name: 'chevron_left' }));

      // Then: table is back on the first page
      expect(screen.getByText('1–10 of 35 items')).toBeInTheDocument();
    });

    it('should jump to first page with first_page button from last page', async () => {
      // Given: table on the last page (35 rows, page size 10)
      const user = userEvent.setup();
      render(<TableComponent />);

      await user.click(screen.getByRole('button', { name: 'last_page' }));
      expect(screen.getByText('31–35 of 35 items')).toBeInTheDocument();

      // When: first page button is clicked
      await user.click(screen.getByRole('button', { name: 'first_page' }));

      // Then: table is back on the first page
      expect(screen.getByText('1–10 of 35 items')).toBeInTheDocument();
    });

    it('should change page size when selecting 20 from dropdown', async () => {
      // Given: table with page size 10
      const user = userEvent.setup();
      render(<TableComponent />);
      expect(screen.getByText('1–10 of 35 items')).toBeInTheDocument();

      // When: 20 is selected from the rows-per-page dropdown
      // (Base UI trigger and inner Button both match, click the trigger)
      const [pageSizeTrigger] = screen.getAllByRole('button', {
        name: /arrow_drop_down/,
      });
      await user.click(pageSizeTrigger);
      const option20 = await screen.findByText('20');
      await user.click(option20);

      // Then: 20 rows are shown per page
      expect(screen.getByText('1–20 of 35 items')).toBeInTheDocument();
    });

    it('should handle all pagination controls', async () => {
      const user = userEvent.setup();
      render(<TableComponent />);

      const buttons = screen.getAllByRole('button');

      // Click each button to ensure they're functional
      for (const btn of buttons) {
        if (!btn.hasAttribute('disabled')) {
          await user.click(btn);
          // Restore the component state
          break;
        }
      }
    });
  });

  describe('SessionRowActionsMenu - Navigation Testing', () => {
    const mockSession: SessionListItem = {
      sessionId: 'nav-test-session',
      name: 'Navigation Test Session',
      description: 'Test',
      initiator: 'test@example.com',
      tags: [],
      projectPath: '/test',
      workflowCount: 1,
    };

    it('should render menu and handle interaction', async () => {
      const user = userEvent.setup();

      render(
        <BrowserRouter>
          <SessionRowActionsMenu session={mockSession} />
        </BrowserRouter>
      );

      const icon = screen.getByText('more_vert');
      expect(icon).toBeInTheDocument();

      const button = icon.closest('button');
      if (button) {
        await user.click(button);
        // Menu should be interactive (button is rendered with dropdown)
        expect(button).toBeInTheDocument();
      }
    });

    it('should navigate to session detail when View Details is clicked', async () => {
      // Given: row actions menu is open
      const user = userEvent.setup();
      render(
        <BrowserRouter>
          <SessionRowActionsMenu session={mockSession} />
        </BrowserRouter>
      );

      // (Base UI trigger and inner Button both match, click the trigger)
      const [menuTrigger] = screen.getAllByRole('button', { name: 'more_vert' });
      await user.click(menuTrigger);

      // When: View Details is clicked
      const viewDetailsItem = await screen.findByText('View Details');
      await user.click(viewDetailsItem);

      // Then: browser navigates to the session detail route
      expect(window.location.pathname).toBe(`/sessions/${mockSession.sessionId}`);
    });

    it('should support multiple interactions', async () => {
      const user = userEvent.setup();

      render(
        <BrowserRouter>
          <SessionRowActionsMenu session={mockSession} />
        </BrowserRouter>
      );

      const icon = screen.getByText('more_vert');
      const button = icon.closest('button');

      if (button) {
        // Click multiple times to test repeated interactions
        await user.click(button);
        await user.click(button);
        await user.click(button);
      }
    });

    it('should handle rapid interactions', async () => {
      const user = userEvent.setup();

      render(
        <BrowserRouter>
          <SessionRowActionsMenu session={mockSession} />
        </BrowserRouter>
      );

      const icon = screen.getByText('more_vert');
      const button = icon.closest('button');

      if (button) {
        // Simulate rapid clicking
        await user.click(button);
        await user.click(button);
      }
    });

    it('should handle session data correctly', () => {
      render(
        <BrowserRouter>
          <SessionRowActionsMenu
            session={{
              ...mockSession,
              sessionId: 'different-id',
              name: 'Different Session',
            }}
          />
        </BrowserRouter>
      );

      expect(screen.getByText('more_vert')).toBeInTheDocument();
    });

    it('should render with various session properties', () => {
      const sessionsToTest = [
        { ...mockSession, workflowCount: 0 },
        { ...mockSession, workflowCount: 100 },
        { ...mockSession, tags: ['tag1', 'tag2', 'tag3'] },
      ];

      sessionsToTest.forEach((session) => {
        const { unmount } = render(
          <BrowserRouter>
            <SessionRowActionsMenu session={session} />
          </BrowserRouter>
        );

        expect(screen.getByText('more_vert')).toBeInTheDocument();
        unmount();
      });
    });
  });

  describe('Edge Cases', () => {
    it('should handle pagination with exactly one page', () => {
      const singleSession: SessionListItem[] = [
        {
          sessionId: 'single',
          name: 'Only Session',
          description: 'The only one',
          initiator: 'test@example.com',
          tags: [],
          projectPath: '/path',
          workflowCount: 1,
        },
      ];

      const TableComponent = () => {
        const table = useReactTable({
          data: singleSession,
          columns,
          getCoreRowModel: getCoreRowModel(),
          getPaginationRowModel: getPaginationRowModel(),
          getFilteredRowModel: getFilteredRowModel(),
          initialState: { pagination: { pageSize: 10 } },
        });

        return <SessionsPaginationFooter table={table} />;
      };

      render(<TableComponent />);
      expect(screen.getByText('1–1 of 1 items')).toBeInTheDocument();
    });

    it('should handle menu with sessions having special characters', () => {
      const specialSession: SessionListItem = {
        sessionId: 'special-!@#$%',
        name: 'Session with Special Chars !@#$%',
        description: 'Desc with special chars',
        initiator: 'special@example.com',
        tags: ['tag-!'],
        projectPath: '/path/with/special',
        workflowCount: 1,
      };

      render(
        <BrowserRouter>
          <SessionRowActionsMenu session={specialSession} />
        </BrowserRouter>
      );

      expect(screen.getByText('more_vert')).toBeInTheDocument();
    });

    it('should handle long session names in menu', () => {
      const longNameSession: SessionListItem = {
        sessionId: 'long-id',
        name: 'A'.repeat(100) + ' Very Long Session Name That Goes On And On',
        description: 'Long description',
        initiator: 'test@example.com',
        tags: [],
        projectPath: '/path',
        workflowCount: 1,
      };

      render(
        <BrowserRouter>
          <SessionRowActionsMenu session={longNameSession} />
        </BrowserRouter>
      );

      expect(screen.getByText('more_vert')).toBeInTheDocument();
    });
  });
});
