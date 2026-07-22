# Playwright E2E Testing Guide for AI Agents

## Purpose
This guide enables AI agents to write comprehensive Playwright E2E tests with MSW mocking for the Yukta UI application.

## Project Context

**Application**: React SPA (Vite) - Session management with TanStack Query/Table
**Test Framework**: Playwright 1.61.1
**API Mocking**: Mock Service Worker (MSW 2.15.0)
**Base URL**: http://localhost:5173
**API Endpoint Pattern**: `/api/**`

## Test Infrastructure Already Set Up

### Fixtures (`e2e/fixtures.ts`)
Provides injectable test data:
- `mockSessions`: Array of 3 SessionListItem objects with all required fields
- `page`: Playwright page object
- `expect`: Assertion library

### Utilities (`e2e/utils.ts`)
Available helper functions:
```typescript
waitForApiResponse(page, { url, method?, timeout? }): Promise<Response>
fillForm(page, fields: Record<string, string>): Promise<void>
waitForLoadingComplete(page, timeout?): Promise<void>
getTableRow(page, cellText: string): Promise<Locator>
assertTableColumns(page, columns: string[]): Promise<void>
countTableRows(page): Promise<number>
getTextContent(locator): Promise<string>
getTextContents(locator): Promise<string[]>
assertElementText(page, selector, text): Promise<void>
clickAndWait(page, selector, options?): Promise<void>
```

### MSW Handlers (`e2e/mocks/handlers.ts`)
Pre-configured endpoints:
- `GET /api/sessions/summaries` - Returns list of sessions
- `GET /api/sessions/summaries?empty=true` - Empty list
- `GET /api/sessions/summaries?error=true` - 500 error response
- `GET /api/sessions/:sessionId` - Single session details

## How to Write Tests

### Test File Structure
Create files in `e2e/tests/` with `.spec.ts` extension.

```typescript
import { test, expect } from '../fixtures';
import { waitForApiResponse, getTableRow, countTableRows } from '../utils';

test.describe('Feature Name', () => {
  test.beforeEach(async ({ page }) => {
    // Setup that runs before each test
    await page.goto('/');
    await waitForLoadingComplete(page);
  });

  test.describe('Group Name', () => {
    test('should do something', async ({ page, mockSessions }) => {
      // Test implementation
      const row = await getTableRow(page, mockSessions[0].name);
      await expect(row).toBeVisible();
    });
  });
});
```

### Import Statement
Always import from fixtures:
```typescript
import { test, expect } from '../fixtures';
```

This provides fixtures, assertions, and proper typing.

## Test Writing Patterns

### Pattern 1: List Page Testing
```typescript
test('should display list', async ({ page, mockSessions }) => {
  await page.goto('/');
  await waitForLoadingComplete(page);
  
  // Verify API response
  const response = await waitForApiResponse(page, { url: '/api/sessions' });
  expect(response.status()).toBe(200);
  
  // Check list rendering
  const rowCount = await countTableRows(page);
  expect(rowCount).toBe(mockSessions.length);
  
  // Verify specific row
  const row = await getTableRow(page, mockSessions[0].name);
  expect(await row.textContent()).toContain(mockSessions[0].initiator);
});
```

### Pattern 2: API Error Handling
```typescript
test('should handle API error', async ({ page }) => {
  await page.goto('/?error=true');
  
  const response = await waitForApiResponse(page, { url: '/api/sessions' });
  expect(response.status()).toBe(500);
  
  // Verify error is displayed
  const error = page.locator('[role="alert"]');
  await expect(error).toBeVisible();
});
```

### Pattern 3: Form Submission
```typescript
test('should submit form', async ({ page }) => {
  await page.goto('/create');
  
  await fillForm(page, {
    'Name': 'New Session',
    'Description': 'Test',
  });
  
  await clickAndWait(page, 'button[type="submit"]', {
    waitForResponse: '/api/sessions',
  });
  
  await expect(page.locator('[data-testid="success"]')).toBeVisible();
});
```

### Pattern 4: User Interaction
```typescript
test('should navigate on click', async ({ page }) => {
  await page.goto('/');
  
  const row = await getTableRow(page, 'Build Pipeline');
  await clickAndWait(page, row.locator('a'), {
    waitForUrl: '/sessions/**',
  });
  
  await expect(page).toHaveURL(/\/sessions\/\w+/);
});
```

### Pattern 5: Responsive Testing
```typescript
test('should work on mobile', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 667 });
  await page.goto('/');
  await waitForLoadingComplete(page);
  
  const table = page.locator('table');
  await expect(table).toBeVisible();
});
```

### Pattern 6: Accessibility Testing
```typescript
test('should have accessible headers', async ({ page }) => {
  await page.goto('/');
  
  const headers = page.locator('th');
  expect(await headers.count()).toBeGreaterThan(0);
  
  await assertTableColumns(page, ['Name', 'Initiator', 'Tags']);
});
```

### Pattern 7: Conditional Testing
```typescript
test('should work with optional feature', async ({ page }) => {
  const element = page.locator('[data-testid="theme-toggle"]');
  
  if (!(await element.isVisible())) {
    test.skip();
  }
  
  await element.click();
  // Continue test...
});
```

## Selector Hierarchy (Most to Least Stable)

1. **data-testid** (if available in component)
   ```typescript
   page.locator('[data-testid="submit"]')
   ```

2. **Accessible roles** (use getByRole)
   ```typescript
   page.getByRole('button', { name: 'Submit' })
   page.getByLabel('Email')
   ```

3. **Text content** (getByText)
   ```typescript
   page.getByText('Build Pipeline')
   ```

4. **CSS selectors** (last resort)
   ```typescript
   page.locator('table tbody tr')
   ```

## Wait Strategies (Always Use These, Never waitForTimeout)

```typescript
// Wait for element visibility
await page.locator('table').waitFor({ state: 'visible' });

// Wait for URL change
await page.waitForURL('/sessions/123');

// Wait for network to settle
await waitForLoadingComplete(page);

// Wait for specific API response
const response = await waitForApiResponse(page, { url: '/api/sessions' });

// Built-in waiting on actions (automatic)
await page.click('button');  // Waits for clickable state
await page.fill('input', 'text');  // Waits for visible & enabled
```

## Assertions (Use These, Not Vague Checks)

```typescript
// Element visibility
await expect(page.locator('h1')).toBeVisible();
await expect(page.locator('modal')).toBeHidden();

// Text content
await expect(page.locator('h1')).toContainText('Sessions');
await expect(page.locator('h1')).toHaveText('Exact Text');

// Element state
await expect(page.locator('button')).toBeEnabled();
await expect(page.locator('input')).toBeFocused();

// Attributes
await expect(page.locator('a')).toHaveAttribute('href', '/path');
await expect(page.locator('div')).toHaveClass('active');

// Collections
await expect(page.locator('tr')).toHaveCount(5);
await expect(page.locator('tag')).toContainText(['tag1', 'tag2']);
```

## Session Type Definition

When tests reference sessions, use `SessionListItem`:

```typescript
interface SessionListItem {
  sessionId: string;
  name: string;
  description: string;
  initiator: string;
  tags: string[];
  projectPath: string;
  workflowCount: number;
}
```

The fixture `mockSessions` provides 3 pre-configured instances.

## API Response Structure

All mocked API responses follow this format:

```typescript
{
  timestamp: ISO string,
  status: number (200, 201, 500, etc),
  message: string,
  path: string,
  data: T,  // Generic type (SessionListItems, etc)
  error?: string,
  errors?: Record<string, string[]>,
}
```

For sessions endpoint, `data` is: `{ sessions: SessionListItem[] }`

## Adding New Handlers

To mock a new endpoint:

1. Add to `e2e/mocks/handlers.ts`:
```typescript
http.post(`${BASE_URL}/api/sessions`, async ({ request }) => {
  const body = await request.json();
  return HttpResponse.json({
    timestamp: new Date().toISOString(),
    status: 201,
    message: 'Session created',
    path: '/api/sessions',
    data: { sessionId: 'new-123', ...body },
  }, { status: 201 });
});
```

2. Export in handlers array (automatic)

3. Use in tests:
```typescript
const response = await waitForApiResponse(page, {
  url: '/api/sessions',
  method: 'POST',
});
```

## Common Application Routes

Based on `src/routes/router.tsx`:

- `/` - Session list page (default)
- `/sessions/:sessionId` - Session details (coming soon)
- `/sessions/:sessionId/workflow/:workflowId` - Workflow view (coming soon)
- `/control` - Control bus (coming soon)
- `/history` - History view (coming soon)

## Component-Specific Info

### SessionListPage (`src/features/sessions/components/SessionListPage.tsx`)
- Displays table of sessions
- Uses TanStack Table & Query
- Has filter bar (if implemented)
- May have pagination
- Columns: Name, Initiator, Tags, Project Path, Workflow Count

### Session Data Source
- Fetched from `/api/sessions/summaries`
- Uses hook `useSessionSummaries()`
- Wrapped in `ApiResponse<SessionListItems>`

## Test Organization Guidelines

1. **File per major feature**: `session-list.spec.ts`, `workflow-detail.spec.ts`, etc
2. **Group by concern**: Initial Load, User Interactions, Error Handling, etc
3. **One describe block per test group**
4. **Descriptive test names**: "should display session list on load"
5. **Setup in beforeEach**: Avoid duplication across tests
6. **Independent tests**: Each test should pass/fail independently

## Common Mistakes to Avoid

❌ **Using arbitrary timeouts**
```typescript
await page.waitForTimeout(2000);  // DON'T
```

✅ **Use semantic waits**
```typescript
await waitForLoadingComplete(page);  // DO
```

---

❌ **Hardcoding test data**
```typescript
const session = { sessionId: '123', name: 'Build', ... };  // DON'T
```

✅ **Use fixtures**
```typescript
test('...', async ({ page, mockSessions }) => {
  const session = mockSessions[0];  // DO
});
```

---

❌ **Fragile selectors**
```typescript
page.locator('div > div > button:nth-child(3)')  // DON'T
```

✅ **Stable selectors**
```typescript
page.locator('[data-testid="submit"]')  // DO
page.getByRole('button', { name: 'Submit' })  // DO
```

---

❌ **Test dependencies**
```typescript
// DON'T: Tests that depend on each other
test('create', () => { sessionId = '123'; });
test('edit', () => { /* uses sessionId */ });
```

✅ **Independent tests**
```typescript
// DO: Each test is standalone
test('create', async ({ page }) => { /* complete flow */ });
test('edit', async ({ page }) => { /* complete flow */ });
```

## Running Tests as AI

```bash
# All tests (from ui directory)
pnpm test:e2e

# Specific file
pnpm test:e2e e2e/tests/session-list.spec.ts

# Matching pattern
pnpm test:e2e --grep "pattern"

# Headed (see browser)
pnpm test:e2e --headed

# CI mode (with retries)
CI=true pnpm test:e2e
```

## Debugging During Test Development

```bash
# Interactive step-through
pnpm test:e2e --debug

# See browser + slow motion
pnpm test:e2e --headed --slow-mo=1000

# View results
pnpm exec playwright show-report
```

## Test Checklist Before Committing

- [ ] Test name describes what should happen
- [ ] Setup code in `beforeEach()`, not repeated in test
- [ ] Uses fixture data (`mockSessions`), not hardcoded
- [ ] Uses semantic waits (`waitForApiResponse`, `waitForLoadingComplete`)
- [ ] Uses stable selectors (`[data-testid]` or `getByRole`)
- [ ] No arbitrary `waitForTimeout()` calls
- [ ] Specific assertions (not generic truthiness)
- [ ] Test is isolated (doesn't depend on other tests)
- [ ] Error scenarios covered (if applicable)
- [ ] Test runs in < 5 seconds
- [ ] Runs locally: `pnpm test:e2e --grep "test name"`

## Example Test Templates

### Complete Session List Test
```typescript
import { test, expect } from '../fixtures';
import { waitForLoadingComplete, countTableRows, getTableRow } from '../utils';

test.describe('Session List Page', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await waitForLoadingComplete(page);
  });

  test('should display all sessions', async ({ page, mockSessions }) => {
    const rowCount = await countTableRows(page);
    expect(rowCount).toBe(mockSessions.length);
  });

  test('should have table headers', async ({ page }) => {
    const headers = page.locator('th');
    expect(await headers.count()).toBeGreaterThan(0);
  });

  test('should show session details in row', async ({ page, mockSessions }) => {
    const session = mockSessions[0];
    const row = await getTableRow(page, session.name);
    const text = await row.textContent();
    expect(text).toContain(session.initiator);
  });
});
```

## Key Files Reference

| File | Purpose | When Used |
|------|---------|-----------|
| `e2e/fixtures.ts` | Import test data & setup | Every test file (import at top) |
| `e2e/utils.ts` | Helper functions | When testing specific patterns |
| `e2e/mocks/handlers.ts` | API mocks | When adding endpoints |
| `e2e/tests/*.spec.ts` | Actual test files | Template for new tests |
| `playwright.config.ts` | Framework config | Usually not modified |

## This Guide Enables

✅ Writing tests without human documentation
✅ Understanding existing test patterns
✅ Adding new endpoints to MSW
✅ Fixing failing tests
✅ Adding test coverage for new features
✅ Debugging test issues
✅ Following project conventions

Use this guide when:
- Writing new test files
- Adding tests for features
- Fixing failing tests
- Extending MSW handlers
- Debugging test issues
