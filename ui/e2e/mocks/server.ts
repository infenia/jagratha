// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Infenia Private Limited

import { setupServer } from 'msw/node';
import { handlers } from './handlers';

/**
 * This configures a request mocking server with the given request handlers.
 * Playwright tests will use this server to mock API responses.
 *
 * @see https://mswjs.io/docs/getting-started/integrate/node
 */
export const server = setupServer(...handlers);

// MSW recommends starting and stopping the server before/after test suites.
// However, in Playwright we use this server differently:
// The server is started globally via playwright.config.ts webServer configuration
// and communicates with the browser via Service Worker interception.
