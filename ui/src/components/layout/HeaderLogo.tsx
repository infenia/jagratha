// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Infenia Private Limited

import { Link } from 'react-router';
import { Button } from '@/components/ui/button';

export default function HeaderLogo() {
  return (
    <Link to="/" className="no-underline">
      <Button
        variant="ghost"
        size="default"
        className="flex items-center gap-spacing-sm hover:bg-surface-container-low"
      >
        {/* Yukta Logo Icon — Material Symbols Outlined clock_circle placeholder */}
        <svg
          className="h-6 w-6 text-on-surface dark:text-on-surface"
          viewBox="0 0 24 24"
          fill="currentColor"
          xmlns="http://www.w3.org/2000/svg"
        >
          {/* Simple clock icon as placeholder — replace with actual Yukta logo later */}
          <circle cx="12" cy="12" r="10" className="stroke-current" strokeWidth="2" fill="none" />
          <path d="M12 7v5h4" className="stroke-current" strokeWidth="2" strokeLinecap="round" />
        </svg>
        <span className="font-headline text-lg font-semibold tracking-tight text-on-surface dark:text-on-surface">
          Yukta
        </span>
      </Button>
    </Link>
  );
}
