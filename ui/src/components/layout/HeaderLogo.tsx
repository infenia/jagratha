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
        {/* Yukta Logo Icon from public/favicon.svg */}
        <img
          src="/favicon.svg"
          alt="Yukta"
          className="h-6 w-6 object-contain"
        />
        <span className="font-headline text-lg font-semibold tracking-tight text-on-surface dark:text-on-surface">
          Yukta
        </span>
      </Button>
    </Link>
  );
}
