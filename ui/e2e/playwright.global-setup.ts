// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Infenia Private Limited

import { chromium } from '@playwright/test';

/**
 * Global setup hook for Playwright
 *
 * This runs once before all tests and can be used to:
 * - Start the MSW server
 * - Seed test databases
 * - Verify API endpoints are reachable
 * - Set up environment variables
 *
 * @see https://playwright.dev/docs/test-global-setup-teardown
 */
async function globalSetup() {
  console.log('🔧 Running global setup...');

  // Verify that the dev server is running
  const browser = await chromium.launch();
  const page = await browser.newPage();

  try {
    const response = await page.goto('http://localhost:5173', {
      waitUntil: 'networkidle',
      timeout: 30000,
    });
    if (response?.ok()) {
      console.log('✅ Dev server is running at http://localhost:5173');
    } else {
      console.warn('⚠️  Dev server may not be ready (HTTP ' + response?.status + ')');
    }
  } catch {
    console.warn(
      '⚠️  Could not verify dev server. Make sure "pnpm dev" is running.'
    );
  } finally {
    await browser.close();
  }

  console.log('✅ Global setup complete');
}

export default globalSetup;
