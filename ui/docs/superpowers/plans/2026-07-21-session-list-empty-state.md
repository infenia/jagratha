# Session List Empty State Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Display a minimal "No sessions yet" message centered in the table when the session list is empty.

**Architecture:** Add a conditional render in the `<TableBody>` of SessionListPage that checks if `table.getRowModel().rows.length === 0`. If true, render a single `<TableRow>` with a `<TableCell>` spanning all columns, centered with `on-surface-variant` text color and `py-8` padding. No new components required—inline implementation within SessionListPage.

**Tech Stack:** React 19, TanStack Table 8, Tailwind CSS 4 (design tokens)

## Global Constraints

- Message text: "No sessions yet"
- Typography: `body-md` (14px) in `on-surface-variant` (#424656)
- Layout: Single table row, all columns spanned, centered
- Padding: `py-8` (32px vertical)
- No new dependencies or component files

---

### Task 1: Update SessionListPage to render empty state

**Files:**
- Modify: `ui/src/features/sessions/components/SessionListPage.tsx:109-125`
- Test: `ui/src/features/sessions/__tests__/SessionListPage.test.tsx`

**Interfaces:**
- Consumes: `table.getRowModel().rows` (from TanStack Table hook, already used)
- Produces: Empty state row rendered when `rows.length === 0`

- [ ] **Step 1: Read the current TableBody section to understand structure**

Run: `cat ui/src/features/sessions/components/SessionListPage.tsx | sed -n '109,125p'`

Expected output: The current `<TableBody>` with map over rows

- [ ] **Step 2: Write the failing test for empty state**

Create a test case in `ui/src/features/sessions/__tests__/SessionListPage.test.tsx`:

```typescript
it('should display empty state message when no sessions exist', () => {
  vi.mocked(useSessionSummaries).mockReturnValue({
    data: [],
    isLoading: false,
    error: undefined,
  });

  render(<SessionListPage />);

  expect(screen.getByText('No sessions yet')).toBeInTheDocument();
});
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `cd ui && pnpm test SessionListPage.test.tsx -t "empty state"`

Expected: FAIL — "No sessions yet" not found in document

- [ ] **Step 4: Implement the empty state in SessionListPage**

Replace the `<TableBody>` section (lines 109-125) in `SessionListPage.tsx`:

```typescript
<TableBody>
  {table.getRowModel().rows.length === 0 ? (
    <TableRow>
      <TableCell colSpan={columns.length} className="py-8 text-center">
        <p className="text-body-md text-on-surface-variant">No sessions yet</p>
      </TableCell>
    </TableRow>
  ) : (
    table.getRowModel().rows.map((row, idx) => (
      <TableRow
        key={row.id}
        className={`border-b border-outline-variant py-3 hover:bg-surface-container-high ${
          idx % 2 === 1 ? 'bg-surface-container-low' : ''
        }`}
      >
        {row.getVisibleCells().map((cell) => (
          <TableCell key={cell.id} className="px-4">
            {flexRender(cell.column.columnDef.cell, cell.getContext())}
          </TableCell>
        ))}
      </TableRow>
    ))
  )}
</TableBody>
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd ui && pnpm test SessionListPage.test.tsx -t "empty state"`

Expected: PASS

- [ ] **Step 6: Run all SessionListPage tests to ensure no regressions**

Run: `cd ui && pnpm test SessionListPage.test.tsx`

Expected: All tests pass

- [ ] **Step 7: Verify the component visually (optional)**

Run the dev server:
```bash
cd ui && pnpm dev
```

Open http://localhost:5173 and navigate to the sessions page. Mock the hook to return empty data to see the empty state render (or manually verify through browser dev tools).

- [ ] **Step 8: Commit**

```bash
git add ui/src/features/sessions/components/SessionListPage.tsx ui/src/features/sessions/__tests__/SessionListPage.test.tsx
git commit -m "feat(ui): add empty state message to session list table"
```

---

### Task 2: Update existing tests to account for empty state

**Files:**
- Modify: `ui/src/features/sessions/__tests__/SessionListPage.placeholder.test.tsx`
- Modify: `ui/src/features/sessions/__tests__/coverage.test.tsx`

**Interfaces:**
- Consumes: SessionListPage component (now renders empty state)
- Produces: Tests updated to avoid false failures from empty state rendering

- [ ] **Step 1: Review placeholder and coverage tests**

Run: `grep -n "No sessions" ui/src/features/sessions/__tests__/SessionListPage.*.test.tsx coverage.test.tsx`

Expected: No existing references to "No sessions" (baseline check)

- [ ] **Step 2: Check if placeholder test mocks empty data**

Run: `cat ui/src/features/sessions/__tests__/SessionListPage.placeholder.test.tsx`

If it renders with empty sessions data and expects specific DOM structure, update mocks to return at least one session.

- [ ] **Step 3: Update placeholder test if needed**

If the test was checking for specific row counts or table structure with empty data, update the mock:

```typescript
vi.mocked(useSessionSummaries).mockReturnValue({
  data: [
    {
      name: 'Test Session',
      sessionId: 'test-123',
      description: 'Test description',
      initiator: 'test-user',
      tags: [],
      projectPath: '/test/path',
      workflowCount: 1,
    },
  ],
  isLoading: false,
  error: undefined,
});
```

- [ ] **Step 4: Check coverage test**

Run: `cat ui/src/features/sessions/__tests__/coverage.test.tsx | grep -A 20 "SessionListPage"`

If coverage test renders SessionListPage with empty data, ensure it's updated similarly.

- [ ] **Step 5: Run all tests to verify no failures**

Run: `cd ui && pnpm test SessionListPage`

Expected: All tests pass

- [ ] **Step 6: Commit**

```bash
git add ui/src/features/sessions/__tests__/SessionListPage.placeholder.test.tsx ui/src/features/sessions/__tests__/coverage.test.tsx
git commit -m "test: update session list tests to account for empty state"
```

---

## Self-Review Checklist

1. **Spec coverage:** 
   - ✅ Message text "No sessions yet" — Task 1, Step 4
   - ✅ Single table row, all columns spanned — Task 1, Step 4 (`colSpan={columns.length}`)
   - ✅ Centered, `on-surface-variant` color — Task 1, Step 4 (`text-center`, `text-on-surface-variant`)
   - ✅ `py-8` padding — Task 1, Step 4
   - ✅ Conditional render when `sessions.length === 0` — Task 1, Step 4 (`rows.length === 0`)
   - ✅ Testing — Task 1, Steps 2-6

2. **Placeholder scan:** No "TBD", "TODO", or incomplete references. All code is complete and testable.

3. **Type consistency:** `columns.length` used in `colSpan` matches TanStack Table's column array. No type mismatches.

4. **Test coverage:** Empty state test added. Existing tests reviewed and updated if they relied on non-empty data.

---
