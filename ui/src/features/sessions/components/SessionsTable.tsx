// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Infenia Private Limited

import { useState } from 'react';
import {
  useReactTable,
  getCoreRowModel,
  getFilteredRowModel,
  getPaginationRowModel,
  flexRender,
  type ColumnFiltersState,
} from '@tanstack/react-table';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import type { SessionListItem } from '../types/session';
import { columns } from './columns';

interface SessionsTableProps {
  data: SessionListItem[];
}

export function SessionsTable({ data }: SessionsTableProps) {
  const [globalFilter, setGlobalFilter] = useState('');
  const [columnFilters, setColumnFilters] = useState<ColumnFiltersState>([]);

  // React Compiler skips memoizing useReactTable (headless API returns unstable functions); accepted
  // eslint-disable-next-line react-hooks/incompatible-library
  const table = useReactTable({
    data,
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

  return (
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
              onClick={() => {
                // Row click navigation handled per-row via SessionRowActionsMenu
              }}
              className={`cursor-pointer border-b border-outline-variant py-2 hover:bg-surface-container-high dark:py-3 dark:hover:bg-surface-container ${
                idx % 2 === 1 ? 'bg-surface-variant dark:bg-surface-variant' : 'bg-surface-container-lowest dark:bg-surface-container-low'
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
  );
}

