// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Infenia Private Limited

import HeaderLogo from './HeaderLogo';
import BreadcrumbNav from './BreadcrumbNav';
import ThemeToggle from './ThemeToggle';

export default function AppHeader() {
  return (
    <header className="fixed top-0 left-0 right-0 z-50 bg-surface border-b border-outline-variant">
      {/* Top row: Logo + Theme Toggle */}
      <div className="flex items-center justify-between w-full h-12 px-spacing-md">
        <HeaderLogo />
        <ThemeToggle />
      </div>

      {/* Bottom row: Breadcrumb Navigation */}
      <BreadcrumbNav />
    </header>
  );
}
