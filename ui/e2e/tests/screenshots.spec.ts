// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Infenia Private Limited

import { test, expect } from '../fixtures';
import { waitForLoadingComplete } from '../utils';

test.describe('Visual Regression - Screenshots', () => {
  test.describe('Session List Page', () => {
    test('should match screenshot - full page', async ({ page }) => {
      await page.goto('/');
      await waitForLoadingComplete(page);

      await expect(page).toHaveScreenshot('session-list-full-page.png', {
        maxDiffPixels: 100,
        threshold: 0.2,
      });
    });

    test('should match screenshot - table only', async ({ page }) => {
      await page.goto('/');
      await waitForLoadingComplete(page);

      const table = page.locator('table');
      await expect(table).toHaveScreenshot('session-list-table.png', {
        maxDiffPixels: 50,
        threshold: 0.15,
      });
    });

    test('should match screenshot - header', async ({ page }) => {
      await page.goto('/');
      await waitForLoadingComplete(page);

      const header = page.locator('header');
      await expect(header).toHaveScreenshot('session-list-header.png', {
        maxDiffPixels: 20,
        threshold: 0.1,
      });
    });
  });

  test.describe('Session List - Dark Mode', () => {
    test('should match screenshot - dark mode', async ({ page }) => {
      // Emulate dark color scheme
      await page.emulateMedia({ colorScheme: 'dark' });

      await page.goto('/');
      await waitForLoadingComplete(page);

      await expect(page).toHaveScreenshot('session-list-dark-mode.png', {
        maxDiffPixels: 100,
        threshold: 0.2,
      });
    });

    test('should match screenshot - light mode', async ({ page }) => {
      // Emulate light color scheme
      await page.emulateMedia({ colorScheme: 'light' });

      await page.goto('/');
      await waitForLoadingComplete(page);

      await expect(page).toHaveScreenshot('session-list-light-mode.png', {
        maxDiffPixels: 100,
        threshold: 0.2,
      });
    });
  });

  test.describe('Session List - Responsive', () => {
    test('should match screenshot - mobile viewport', async ({ page }) => {
      await page.setViewportSize({ width: 375, height: 667 });
      await page.goto('/');
      await waitForLoadingComplete(page);

      await expect(page).toHaveScreenshot('session-list-mobile.png', {
        maxDiffPixels: 100,
        threshold: 0.2,
      });
    });

    test('should match screenshot - tablet viewport', async ({ page }) => {
      await page.setViewportSize({ width: 768, height: 1024 });
      await page.goto('/');
      await waitForLoadingComplete(page);

      await expect(page).toHaveScreenshot('session-list-tablet.png', {
        maxDiffPixels: 100,
        threshold: 0.2,
      });
    });

    test('should match screenshot - desktop viewport', async ({ page }) => {
      await page.setViewportSize({ width: 1920, height: 1080 });
      await page.goto('/');
      await waitForLoadingComplete(page);

      await expect(page).toHaveScreenshot('session-list-desktop.png', {
        maxDiffPixels: 100,
        threshold: 0.2,
      });
    });
  });

  test.describe('Error States - Screenshots', () => {
    test('should match screenshot - error state', async ({ page }) => {
      await page.goto('/?error=true');
      await page.waitForTimeout(500);

      await expect(page).toHaveScreenshot('session-list-error-state.png', {
        maxDiffPixels: 100,
        threshold: 0.2,
      });
    });

    test('should match screenshot - empty state', async ({ page }) => {
      await page.goto('/?empty=true');
      await waitForLoadingComplete(page);

      await expect(page).toHaveScreenshot('session-list-empty-state.png', {
        maxDiffPixels: 100,
        threshold: 0.2,
      });
    });
  });

  test.describe('UI Components - Screenshots', () => {
    test('should match screenshot - table row hover', async ({ page }) => {
      await page.goto('/');
      await waitForLoadingComplete(page);

      const firstRow = page.locator('tbody tr').first();
      await firstRow.hover();

      await expect(firstRow).toHaveScreenshot('session-list-row-hover.png', {
        maxDiffPixels: 50,
        threshold: 0.15,
      });
    });

    test('should match screenshot - filter bar (if visible)', async ({ page }) => {
      await page.goto('/');
      await waitForLoadingComplete(page);

      const filterBar = page.locator('[data-testid="filter-bar"], .filter-bar').first();

      if (await filterBar.isVisible()) {
        await expect(filterBar).toHaveScreenshot('session-list-filter-bar.png', {
          maxDiffPixels: 50,
          threshold: 0.15,
        });
      }
    });

    test('should match screenshot - pagination (if visible)', async ({ page }) => {
      await page.goto('/');
      await waitForLoadingComplete(page);

      const pagination = page.locator('[data-testid="pagination"], nav[aria-label="pagination"]').first();

      if (await pagination.isVisible()) {
        await expect(pagination).toHaveScreenshot('session-list-pagination.png', {
          maxDiffPixels: 50,
          threshold: 0.15,
        });
      }
    });
  });
});
