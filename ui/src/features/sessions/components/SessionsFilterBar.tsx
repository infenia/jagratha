// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Infenia Private Limited

import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import type { SessionListItem } from '../types/session';

interface SessionsFilterBarProps {
  data: SessionListItem[];
  globalFilter: string;
  onGlobalFilterChange: (value: string) => void;
  onReset: () => void;
}

export function SessionsFilterBar({
  globalFilter,
  onGlobalFilterChange,
  onReset,
}: SessionsFilterBarProps) {
  return (
    <div className="flex flex-col gap-4 bg-surface-container-lowest px-6 py-4 dark:bg-surface-container-low">
      <div>
        <Input
          placeholder="Search by name or session ID..."
          value={globalFilter}
          onChange={(e) => onGlobalFilterChange(e.target.value)}
          className="w-full"
        />
      </div>

      <div className="flex gap-2">
        <Button variant="outline" size="sm" onClick={onReset}>
          <span className="material-symbols-outlined mr-1">restart_alt</span>
          Reset Filters
        </Button>
      </div>
    </div>
  );
}
