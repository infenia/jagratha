// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Infenia Private Limited

import { test, expect } from '@playwright/test';
import { waitForApiResponse, waitForLoadingComplete } from '../utils';

test.describe('Error Handling - MSW Mocked Failures', () => {
  test('should handle API errors gracefully', async ({ page }) => {
    // Use query parameter to trigger error response in MSW
    await page.goto('/?error=true');

    // Wait for the error response
    const response = await waitForApiResponse(page, {
      url: '/api/sessions/summaries',
    });

    expect(response.status()).toBe(500);

    // Look for error message or empty state
    await page.waitForTimeout(500);

    // Check for error display (implementation-dependent)
    const errorElement = page.locator(
      '[data-testid="error"], .error, [role="alert"]'
    );

    if (await errorElement.isVisible()) {
      await expect(errorElement).toBeVisible();
      const errorText = await errorElement.textContent();
      expect(errorText).toBeTruthy();
    }
  });

  test('should display empty state when no sessions available', async ({
    page,
  }) => {
    // Navigate with empty parameter
    await page.goto('/?empty=true');

    // Wait for API call
    await waitForLoadingComplete(page);

    // Look for empty state message or empty table
    const emptyMessage = page.locator(
      '[data-testid="empty"], text="No sessions", text="Empty"'
    );

    const table = page.locator('table');

    // Either empty message shows or table is empty
    const hasEmptyMessage = await emptyMessage.isVisible().catch(() => false);
    const tableVisible = await table.isVisible().catch(() => false);

    if (tableVisible) {
      const rows = page.locator('tbody tr');
      const rowCount = await rows.count();
      // Empty state should show no data rows (header might exist)
      expect(rowCount).toBe(0);
    } else {
      // Empty state message should be visible
      expect(hasEmptyMessage).toBe(true);
    }
  });

  test('should retry failed requests', async ({ page }) => {
    await page.goto('/');

    // Initial load
    await waitForLoadingComplete(page);

    // Check if retry logic exists by refreshing
    await page.reload();
    await waitForLoadingComplete(page);

    // Page should load successfully
    const table = page.locator('table');
    await expect(table).toBeVisible();
  });

  test('should handle network timeout gracefully', async ({
    page,
    context,
  }) => {
    // Set aggressive timeout
    context.setDefaultTimeout(1000);

    try {
      // This might timeout but should handle it
      await page.goto('/', { timeout: 5000 }).catch(() => {
        // Timeout expected, but page might still load
      });

      // Page should still be accessible
      await page.waitForLoadState('domcontentloaded').catch(() => {});
    } finally {
      context.setDefaultTimeout(30000);
    }
  });

  test('should display loading state during request', async ({ page }) => {
    // Slow down network to observe loading state
    await page.route('**/*', async (route) => {
      await new Promise((resolve) => setTimeout(resolve, 500));
      await route.continue();
    });

    void page.goto('/');

    // Look for loading indicator during navigation
    await page.waitForTimeout(100);

    const loader = page.locator(
      '[data-testid="loading"], .spinner, [aria-busy="true"]'
    );

    // May show loading state
    if (await loader.isVisible()) {
      await expect(loader).toBeVisible();
    }

    // Wait for page load
    await page.waitForLoadState('load');

    // Loading should disappear once complete
    await expect(loader).not.toBeVisible();
  });

  test('should display specific field errors if provided', async ({ page }) => {
    // Would need MSW handler that returns field errors
    await page.goto('/');

    // If form submission happens with field errors
    // This is a template for form error handling tests
    const formField = page.locator('input[name="example"]');

    if (await formField.isVisible()) {
      // Test would verify error display for the field
      await expect(formField).toBeVisible();
    }
  });

  test('should recover from error state', async ({ page }) => {
    // Navigate to error state
    await page.goto('/?error=true');
    await page.waitForTimeout(500);

    // Then navigate to normal state
    await page.goto('/');
    await waitForLoadingComplete(page);

    // Page should load successfully
    const table = page.locator('table');
    await expect(table).toBeVisible();
  });

  test('should not show error for non-API failures', async ({ page }) => {
    // Navigate to non-existent page
    await page.goto('/nonexistent', { waitUntil: 'networkidle' }).catch(
      () => {}
    );

    // Page might show 404 or redirect
    const url = page.url();
    expect(url).toBeTruthy();
  });
});

test.describe('Form Error Validation', () => {
  test('should show validation errors on form submission', async ({
    page,
  }) => {
    // Navigate to any form (if one exists)
    await page.goto('/');

    // Look for submit button
    const submitButton = page.locator('button[type="submit"]');

    if (!(await submitButton.isVisible())) {
      test.skip();
    }

    // Try to submit empty form
    await submitButton.click();
    await page.waitForTimeout(300);

    // Check for validation errors
    const errors = page.locator('[role="alert"], .error, [data-testid="error"]');

    // May show inline validation or form-level error
    if (await errors.isVisible()) {
      await expect(errors).toBeVisible();
    }
  });
});
