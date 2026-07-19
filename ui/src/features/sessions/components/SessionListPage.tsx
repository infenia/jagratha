// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Infenia Private Limited

import { useState } from 'react';
import {
  useReactTable,
  getCoreRowModel,
  getFilteredRowModel,
  getPaginationRowModel,
  flexRender,
} from '@tanstack/react-table';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useSessionSummaries } from '../hooks/useSessionSummaries';
import { columns } from './columns';
import { SessionsHeader } from './SessionsHeader';
import { SessionsFilterBar } from './SessionsFilterBar';
import { SessionsPaginationFooter } from './SessionsPaginationFooter';

export function SessionListPage() {
  const { data: sessions = [], isLoading, error } = useSessionSummaries();
  const [globalFilter, setGlobalFilter] = useState('');
  const [columnFilters, setColumnFilters] = useState<any[]>([]);

  const table = useReactTable({
    data: sessions,
    columns,
    getCoreRowModel: getCoreRowModel(),
    getFilteredRowModel: getFilteredRowModel(),
    getPaginationRowModel: getPaginationRowModel(),
    state: {
      globalFilter,
      columnFilters,
    },
    onGlobalFilterChange: setGlobalFilter,
    onColumnFiltersChange: setColumnFilters,
    globalFilterFn: (row, _columnId, filterValue) => {
      const { name, sessionId } = row.original;
      return (
        name.toLowerCase().includes(filterValue.toLowerCase()) ||
        sessionId.toLowerCase().includes(filterValue.toLowerCase())
      );
    },
    initialState: {
      pagination: {
        pageSize: 10,
      },
    },
  });

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <p className="text-on-surface-variant">Loading sessions...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <p className="text-error">Failed to load sessions</p>
      </div>
    );
  }

  const handleReset = () => {
    setGlobalFilter('');
    setColumnFilters([]);
    table.setPageIndex(0);
  };

  return (
    <div className="flex flex-col gap-0 min-h-screen bg-background">
      <div className="border-b border-outline-variant">
        <SessionsHeader sessionCount={sessions.length} />
      </div>

      <SessionsFilterBar
        data={sessions}
        globalFilter={globalFilter}
        onGlobalFilterChange={setGlobalFilter}
        onReset={handleReset}
      />

      <div className="flex-1 overflow-auto">
        <div className="rounded-lg border border-outline bg-surface-container-lowest dark:bg-surface-container-low">
          <Table>
            <TableHeader>
              {table.getHeaderGroups().map((headerGroup) => (
                <TableRow key={headerGroup.id} className="border-b border-outline-variant">
                  {headerGroup.headers.map((header) => (
                    <TableHead key={header.id} className="h-12">
                      {header.isPlaceholder
                        ? null
                        : flexRender(header.column.columnDef.header, header.getContext())}
                    </TableHead>
                  ))}
                </TableRow>
              ))}
            </TableHeader>
            <TableBody>
              {table.getRowModel().rows.map((row, idx) => (
                <TableRow
                  key={row.id}
                  className={`border-b border-outline-variant py-2 dark:py-3 cursor-pointer hover:bg-surface-container-high dark:hover:bg-surface-container ${
                    idx % 2 === 1
                      ? 'bg-surface-variant dark:bg-surface-variant'
                      : 'bg-surface-container-lowest dark:bg-surface-container-low'
                  }`}
                >
                  {row.getVisibleCells().map((cell) => (
                    <TableCell key={cell.id} className="px-4">
                      {flexRender(cell.column.columnDef.cell, cell.getContext())}
                    </TableCell>
                  ))}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      </div>

      <SessionsPaginationFooter table={table} />
    </div>
  );
}
