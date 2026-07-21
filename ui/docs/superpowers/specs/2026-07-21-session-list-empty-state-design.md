# Session List Empty State Design

**Date:** 2026-07-21  
**Feature:** Empty state message for SessionListPage when no sessions exist  
**Status:** Design approved

## Overview

When the session list table contains no data (no sessions have been created), display a minimal, centered message within the table body to guide the user and confirm the application state.

## Requirements

- **Message text:** "No sessions yet"
- **Display:** Single table row spanning all columns, centered horizontally and vertically
- **Trigger:** When `sessions.length === 0` after data load completes
- **Persistence:** Remains visible through filter interactions (if filters yield no results, show same message)

## Design Details

### Visual Presentation

**Typography & Color:**
- Font: `body-md` (14px, IBM Plex Sans, regular weight)
- Color: `on-surface-variant` (#424656) — secondary text per design system
- Alignment: Center (horizontal and vertical within the row)

**Layout:**
- Renders as a single `<TableRow>` containing a `<TableCell>` that spans all columns via `colSpan={columns.length}`
- Padding: `py-8` (32px vertical) to provide breathing room and visual prominence
- Background: Inherit table body background (`bg-surface-container`)—no special container styling

**Icon (Optional):**
- A Material Symbols icon (e.g., `inbox`, `folder_open`) may be placed above the text for visual interest
- Not required for MVP—text alone is sufficient per "direct & minimal" directive

### Component Integration

**Location:** `SessionListPage.tsx` — within the `<TableBody>` rendering logic

**Condition:**
```typescript
if (table.getRowModel().rows.length === 0) {
  // Render empty state row
} else {
  // Render regular rows
}
```

**No changes to:**
- `columns.tsx` — column definitions unchanged
- `SessionsHeader.tsx` — header remains as-is
- `SessionsFilterBar.tsx` — filter bar unaffected
- `SessionsPaginationFooter.tsx` — footer hidden when no rows (standard table behavior)

### Behavior

- **On initial load:** If `sessions` array is empty after `useSessionSummaries` completes, empty state displays
- **On filter:** If global filter or column filters result in zero rows, empty state displays
- **On reset:** Clicking reset with no sessions displays empty state again
- **Pagination:** Footer is not rendered when table has no rows (TanStack Table handles this automatically)

## Testing Strategy

- Unit test: Verify empty state renders when `sessions.length === 0`
- Unit test: Verify empty state is hidden when `sessions.length > 0`
- Integration test: Verify empty state persists through filter/reset cycles with zero results

## Accessibility

- Empty state message is semantic table content (within `<TableCell>`)
- Screen readers will announce the message as part of the table structure
- No special ARIA labels needed—text is self-explanatory

## Rollout

No feature flag or gradual rollout needed—this is a simple UX improvement to an existing component with no behavioral changes to data flow or API contracts.
