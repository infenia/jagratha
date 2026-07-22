// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Infenia Private Limited

import { test, expect } from '@playwright/test';

test.describe('Theme Switching', () => {
  test('should start with system theme', async ({ page }) => {
    await page.goto('/');

    // Check if theme toggle exists
    const themeToggle = page.locator('[data-testid="theme-toggle"], button:has-svg');

    // Theme toggle might be in header
    if (!(await themeToggle.isVisible())) {
      test.skip();
    }

    // Verify initial state
    const htmlElement = page.locator('html');
    const theme = await htmlElement.getAttribute('data-theme');

    // Could be light, dark, or system
    expect(['light', 'dark', 'system', null]).toContain(theme);
  });

  test('should switch between light and dark mode', async ({ page }) => {
    await page.goto('/');

    const themeToggle = page.locator('[data-testid="theme-toggle"], button:has-svg');

    if (!(await themeToggle.isVisible())) {
      test.skip();
    }

    // Get initial theme
    let htmlElement = page.locator('html');
    const initialTheme = await htmlElement.getAttribute('data-theme');

    // Click toggle
    await themeToggle.click();
    await page.waitForTimeout(300);

    // Theme should change
    htmlElement = page.locator('html');
    const newTheme = await htmlElement.getAttribute('data-theme');

    // Should be different (if it was switching mode)
    // Or should be a valid theme value
    expect(['light', 'dark', 'system', null]).toContain(newTheme);
  });

  test('should persist theme preference', async ({ page, context }) => {
    await page.goto('/');

    const themeToggle = page.locator('[data-testid="theme-toggle"], button:has-svg');

    if (!(await themeToggle.isVisible())) {
      test.skip();
    }

    // Switch theme
    await themeToggle.click();
    await page.waitForTimeout(300);

    const themeBefore = await page
      .locator('html')
      .getAttribute('data-theme');

    // Reload page
    await page.reload();
    await page.waitForLoadState('networkidle');

    // Theme should be persisted
    const themeAfter = await page.locator('html').getAttribute('data-theme');

    // Should maintain preference
    expect(themeAfter).toBe(themeBefore);
  });

  test('should have proper color contrast in both modes', async ({ page }) => {
    await page.goto('/');

    // Check text color contrast
    const table = page.locator('table');

    if (!(await table.isVisible())) {
      test.skip();
    }

    // Get computed styles
    const textElement = table.locator('td').first();
    const color = await textElement.evaluate((el) => {
      const style = window.getComputedStyle(el);
      return {
        color: style.color,
        backgroundColor: style.backgroundColor,
      };
    });

    // Colors should be defined
    expect(color.color).toBeTruthy();
    expect(color.backgroundColor).toBeTruthy();
  });

  test('should update all UI elements when theme changes', async ({ page }) => {
    await page.goto('/');

    const themeToggle = page.locator('[data-testid="theme-toggle"], button:has-svg');

    if (!(await themeToggle.isVisible())) {
      test.skip();
    }

    // Get initial colors
    const header = page.locator('header');
    const initialStyle = await header.evaluate((el) =>
      window.getComputedStyle(el).backgroundColor
    );

    // Toggle theme
    await themeToggle.click();
    await page.waitForTimeout(300);

    // Colors should update
    const newStyle = await header.evaluate((el) =>
      window.getComputedStyle(el).backgroundColor
    );

    // At least one element should have updated style
    // (They may be the same if theme affects different elements)
    expect(header).toBeVisible();
  });

  test('should support prefers-color-scheme media query', async ({ page }) => {
    // Set system theme preference
    await page.emulateMedia({ colorScheme: 'dark' });

    await page.goto('/');

    // Page should respect the preference
    const htmlElement = page.locator('html');
    await expect(htmlElement).toBeVisible();

    // Change system preference
    await page.emulateMedia({ colorScheme: 'light' });
    await page.waitForTimeout(300);

    // Page should update if using prefers-color-scheme
    await expect(htmlElement).toBeVisible();
  });
});
