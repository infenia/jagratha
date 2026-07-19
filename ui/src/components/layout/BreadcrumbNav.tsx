// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Infenia Private Limited

import { useLocation } from 'react-router';
import { Link } from 'react-router';
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbSeparator,
} from '@/components/ui/breadcrumb';
import { parseBreadcrumbsFromPath } from '@/lib/breadcrumb-utils';

export default function BreadcrumbNav() {
  const { pathname } = useLocation();
  const breadcrumbs = parseBreadcrumbsFromPath(pathname);

  return (
    <div
      className="flex items-center w-full px-spacing-md h-8 bg-surface-container-low border-t border-outline-variant"
      role="region"
      aria-label="Breadcrumb navigation"
    >
      <Breadcrumb>
        <BreadcrumbList className="gap-spacing-sm">
          {breadcrumbs.map((item, index) => (
            <>
              {index > 0 && (
                <BreadcrumbSeparator className="text-outline-variant">
                  <span className="material-symbols-outlined text-xs">chevron_right</span>
                </BreadcrumbSeparator>
              )}
              <BreadcrumbItem key={`${item.label}-${index}`}>
                {item.isCurrent ? (
                  <span className="text-xs font-medium text-on-surface">{item.label}</span>
                ) : item.href ? (
                  <BreadcrumbLink asChild>
                    <Link
                      to={item.href}
                      className="text-xs text-on-surface-variant hover:text-on-surface transition-colors"
                    >
                      {item.label}
                    </Link>
                  </BreadcrumbLink>
                ) : (
                  <span className="text-xs text-on-surface-variant">{item.label}</span>
                )}
              </BreadcrumbItem>
            </>
          ))}
        </BreadcrumbList>
      </Breadcrumb>
    </div>
  );
}
